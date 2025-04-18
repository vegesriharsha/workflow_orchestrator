package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.engine.executor.TaskExecutor;
import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.messaging.RabbitMQSender;
import com.example.workfloworchestrator.messaging.TaskMessage;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.repository.TaskExecutionRepository;
import com.example.workfloworchestrator.util.RetryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing task executions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService {
    
    private final TaskExecutionRepository taskExecutionRepository;
    private final WorkflowExecutionService workflowExecutionService;
    private final Map<String, TaskExecutor> taskExecutors;
    private final RabbitMQSender rabbitMQSender;
    private final EventPublisherService eventPublisherService;
    private final RetryUtil retryUtil;
    
    /**
     * Create a new task execution for a workflow
     * 
     * @param workflowExecution the workflow execution context
     * @param taskDefinition the task definition
     * @param inputs input parameters for the task
     * @return the created task execution
     */
    @Transactional
    public TaskExecution createTaskExecution(WorkflowExecution workflowExecution, TaskDefinition taskDefinition, 
                                            Map<String, String> inputs) {
        TaskExecution taskExecution = new TaskExecution();
        taskExecution.setTaskDefinition(taskDefinition);
        taskExecution.setStatus(TaskStatus.PENDING);
        taskExecution.setExecutionMode(taskDefinition.getExecutionMode());
        taskExecution.setInputs(inputs);
        taskExecution.setRetryCount(0);
        taskExecution.setWorkflowExecutionId(workflowExecution.getId());
        
        TaskExecution savedExecution = taskExecutionRepository.save(taskExecution);
        
        // Publish event for task created
        eventPublisherService.publishTaskCreatedEvent(workflowExecution, savedExecution);
        
        return savedExecution;
    }
    
    /**
     * Execute a task
     * 
     * @param taskExecutionId the task execution ID
     * @return CompletableFuture with the task execution result
     */
    @Transactional
    public CompletableFuture<TaskExecution> executeTask(Long taskExecutionId) {
        TaskExecution taskExecution = getTaskExecution(taskExecutionId);
        TaskDefinition taskDefinition = taskExecution.getTaskDefinition();
        
        // Update status to RUNNING
        taskExecution.setStatus(TaskStatus.RUNNING);
        taskExecution.setStartedAt(LocalDateTime.now());
        taskExecutionRepository.save(taskExecution);
        
        // Publish event for task started
        eventPublisherService.publishTaskStartedEvent(taskExecution);
        
        // Execute based on the execution mode
        if (taskExecution.getExecutionMode() == ExecutionMode.RABBITMQ) {
            // Send to RabbitMQ
            return executeTaskViaRabbitMQ(taskExecution);
        } else {
            // Execute via API directly
            return executeTaskViaAPI(taskExecution);
        }
    }
    
    /**
     * Execute a task via API call
     * 
     * @param taskExecution the task execution to process
     * @return CompletableFuture with the task execution result
     */
    private CompletableFuture<TaskExecution> executeTaskViaAPI(TaskExecution taskExecution) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String taskType = taskExecution.getTaskDefinition().getType();
                TaskExecutor executor = getTaskExecutor(taskType);
                
                // Create execution context
                ExecutionContext context = new ExecutionContext();
                taskExecution.getInputs().forEach((key, value) -> context.setVariable(key, value));
                
                // Execute task
                Map<String, Object> result = executor.execute(taskExecution.getTaskDefinition(), context);
                
                // Update task execution with results
                taskExecution.setStatus(TaskStatus.COMPLETED);
                taskExecution.setCompletedAt(LocalDateTime.now());
                
                // Convert result values to string for storage
                Map<String, String> outputs = convertResultToStringMap(result);
                taskExecution.setOutputs(outputs);
                
                TaskExecution savedExecution = taskExecutionRepository.save(taskExecution);
                
                // Publish event for task completed
                eventPublisherService.publishTaskCompletedEvent(savedExecution);
                
                return savedExecution;
            } catch (Exception e) {
                return handleTaskExecutionError(taskExecution, e);
            }
        });
    }
    
    /**
     * Execute a task via RabbitMQ
     * 
     * @param taskExecution the task execution to process
     * @return CompletableFuture with the task execution result
     */
    private CompletableFuture<TaskExecution> executeTaskViaRabbitMQ(TaskExecution taskExecution) {
        CompletableFuture<TaskExecution> future = new CompletableFuture<>();
        
        try {
            // Create task message
            TaskMessage taskMessage = new TaskMessage();
            taskMessage.setTaskExecutionId(taskExecution.getId());
            taskMessage.setTaskType(taskExecution.getTaskDefinition().getType());
            taskMessage.setInputs(taskExecution.getInputs());
            taskMessage.setConfiguration(taskExecution.getTaskDefinition().getConfiguration());
            
            // Send to RabbitMQ
            rabbitMQSender.sendTaskMessage(taskMessage);
            
            // The task will be completed asynchronously when result is received
            // Return current state for now
            future.complete(taskExecution);
        } catch (Exception e) {
            log.error("Error sending task to RabbitMQ", e);
            TaskExecution failedExecution = handleTaskExecutionError(taskExecution, e);
            future.complete(failedExecution);
        }
        
        return future;
    }
    
    /**
     * Complete a task execution with results
     * 
     * @param taskExecutionId the task execution ID
     * @param outputs the output results
     * @return the updated task execution
     */
    @Transactional
    public TaskExecution completeTaskExecution(Long taskExecutionId, Map<String, String> outputs) {
        TaskExecution taskExecution = getTaskExecution(taskExecutionId);
        
        taskExecution.setStatus(TaskStatus.COMPLETED);
        taskExecution.setCompletedAt(LocalDateTime.now());
        taskExecution.setOutputs(outputs != null ? outputs : new HashMap<>());
        
        TaskExecution savedExecution = taskExecutionRepository.save(taskExecution);
        
        // Publish event for task completed
        eventPublisherService.publishTaskCompletedEvent(savedExecution);
        
        return savedExecution;
    }
    
    /**
     * Mark a task execution as failed
     * 
     * @param taskExecutionId the task execution ID
     * @param errorMessage the error message
     * @return the updated task execution
     */
    @Transactional
    public TaskExecution failTaskExecution(Long taskExecutionId, String errorMessage) {
        TaskExecution taskExecution = getTaskExecution(taskExecutionId);
        TaskDefinition taskDefinition = taskExecution.getTaskDefinition();
        
        taskExecution.setErrorMessage(errorMessage);
        
        // Check if retry is possible
        int retryCount = taskExecution.getRetryCount() != null ? taskExecution.getRetryCount() : 0;
        int retryLimit = taskDefinition.getRetryLimit() != null ? taskDefinition.getRetryLimit() : 0;
        
        if (retryCount < retryLimit) {
            // Calculate next retry time
            LocalDateTime nextRetryAt = retryUtil.calculateNextRetryTime(retryCount);
            
            taskExecution.setStatus(TaskStatus.AWAITING_RETRY);
            taskExecution.setRetryCount(retryCount + 1);
            taskExecution.setNextRetryAt(nextRetryAt);
            
            // Publish retry scheduled event
            eventPublisherService.publishTaskRetryScheduledEvent(taskExecution);
        } else {
            taskExecution.setStatus(TaskStatus.FAILED);
            taskExecution.setCompletedAt(LocalDateTime.now());
            
            // Publish task failed event
            eventPublisherService.publishTaskFailedEvent(taskExecution);
        }
        
        return taskExecutionRepository.save(taskExecution);
    }
    
    /**
     * Skip a task execution
     * 
     * @param taskExecutionId the task execution ID
     * @return the updated task execution
     */
    @Transactional
    public TaskExecution skipTaskExecution(Long taskExecutionId) {
        TaskExecution taskExecution = getTaskExecution(taskExecutionId);
        
        taskExecution.setStatus(TaskStatus.SKIPPED);
        taskExecution.setCompletedAt(LocalDateTime.now());
        
        TaskExecution savedExecution = taskExecutionRepository.save(taskExecution);
        
        // Publish event for task skipped
        eventPublisherService.publishTaskSkippedEvent(savedExecution);
        
        return savedExecution;
    }
    
    /**
     * Save a task execution
     * 
     * @param taskExecution the task execution to save
     * @return the saved task execution
     */
    @Transactional
    public TaskExecution saveTaskExecution(TaskExecution taskExecution) {
        return taskExecutionRepository.save(taskExecution);
    }
    
    /**
     * Create a user review point for a task
     * 
     * @param taskExecutionId the task execution ID
     * @return the created review point
     */
    @Transactional
    public UserReviewPoint createUserReviewPoint(Long taskExecutionId) {
        TaskExecution taskExecution = getTaskExecution(taskExecutionId);
        
        // Create review point
        UserReviewPoint reviewPoint = new UserReviewPoint();
        reviewPoint.setTaskExecutionId(taskExecutionId);
        reviewPoint.setCreatedAt(LocalDateTime.now());
        
        // Save through the related workflow execution
        WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(
                taskExecution.getWorkflowExecutionId());
        workflowExecution.getReviewPoints().add(reviewPoint);
        workflowExecutionService.save(workflowExecution);
        
        // Publish event
        eventPublisherService.publishUserReviewRequestedEvent(workflowExecution, reviewPoint);
        
        return reviewPoint;
    }
    
    /**
     * Get task executions for a workflow
     * 
     * @param workflowExecutionId the workflow execution ID
     * @return list of task executions
     */
    @Transactional(readOnly = true)
    public List<TaskExecution> getTaskExecutionsForWorkflow(Long workflowExecutionId) {
        return taskExecutionRepository.findByWorkflowExecutionIdOrderByTaskDefinitionExecutionOrderAsc(workflowExecutionId);
    }
    
    /**
     * Get tasks that are ready for retry
     * 
     * @param now the current time
     * @return list of task executions ready for retry
     */
    @Transactional(readOnly = true)
    public List<TaskExecution> getTasksToRetry(LocalDateTime now) {
        return taskExecutionRepository.findTasksToRetry(TaskStatus.AWAITING_RETRY, now);
    }
    
    /**
     * Get a task execution by ID
     * 
     * @param id the task execution ID
     * @return the task execution
     * @throws TaskExecutionException if task execution not found
     */
    @Transactional(readOnly = true)
    public TaskExecution getTaskExecution(Long id) {
        return taskExecutionRepository.findById(id)
                .orElseThrow(() -> new TaskExecutionException("Task execution not found with id: " + id));
    }
    
    /**
     * Get a task executor by type
     * 
     * @param taskType the task type
     * @return the task executor
     * @throws TaskExecutionException if executor not found
     */
    private TaskExecutor getTaskExecutor(String taskType) {
        return Optional.ofNullable(taskExecutors.get(taskType))
                .orElseThrow(() -> new TaskExecutionException("No executor found for task type: " + taskType));
    }
    
    /**
     * Handle task execution error
     * 
     * @param taskExecution the task execution
     * @param exception the exception
     * @return the updated task execution
     */
    private TaskExecution handleTaskExecutionError(TaskExecution taskExecution, Exception exception) {
        log.error("Error executing task: {}", taskExecution.getId(), exception);
        
        String errorMessage = exception.getMessage();
        if (errorMessage == null || errorMessage.isEmpty()) {
            errorMessage = "Unknown error during task execution";
        }
        
        return failTaskExecution(taskExecution.getId(), errorMessage);
    }
    
    /**
     * Convert a map with object values to a map with string values
     * 
     * @param result the map with object values
     * @return map with string values
     */
    private Map<String, String> convertResultToStringMap(Map<String, Object> result) {
        Map<String, String> stringMap = new HashMap<>();
        
        if (result != null) {
            for (Map.Entry<String, Object> entry : result.entrySet()) {
                if (entry.getValue() != null) {
                    stringMap.put(entry.getKey(), entry.getValue().toString());
                } else {
                    stringMap.put(entry.getKey(), null);
                }
            }
        }
        
        return stringMap;
    }
}
