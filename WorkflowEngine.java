package com.example.workfloworchestrator.engine;

import com.example.workfloworchestrator.engine.strategy.ExecutionStrategy;
import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.EventPublisherService;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Core workflow engine that orchestrates workflow execution
 * Coordinates different execution strategies and manages workflow state
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {
    
    private final WorkflowExecutionService workflowExecutionService;
    private final TaskExecutionService taskExecutionService;
    private final EventPublisherService eventPublisherService;
    private final Map<WorkflowDefinition.ExecutionStrategyType, ExecutionStrategy> executionStrategies;
    
    /**
     * Execute a workflow asynchronously
     * 
     * @param workflowExecutionId the workflow execution ID
     */
    @Async("taskExecutor")
    @Transactional
    public void executeWorkflow(Long workflowExecutionId) {
        WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(workflowExecutionId);
        
        try {
            // Check if workflow is in a valid state to execute
            if (workflowExecution.getStatus() != WorkflowStatus.CREATED && 
                workflowExecution.getStatus() != WorkflowStatus.RUNNING) {
                log.info("Workflow {} cannot be executed in current state: {}", 
                        workflowExecutionId, workflowExecution.getStatus());
                return;
            }
            
            // Set status to RUNNING if not already
            if (workflowExecution.getStatus() == WorkflowStatus.CREATED) {
                workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.RUNNING);
                eventPublisherService.publishWorkflowStartedEvent(workflowExecution);
            }
            
            // Get the appropriate execution strategy
            WorkflowDefinition workflowDefinition = workflowExecution.getWorkflowDefinition();
            ExecutionStrategy strategy = getExecutionStrategy(workflowDefinition.getStrategyType());
            
            // Execute workflow using the selected strategy
            CompletableFuture<WorkflowStatus> futureStatus = strategy.execute(workflowExecution);
            
            futureStatus.thenAccept(status -> {
                // Update workflow status based on execution result
                workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, status);
                
                if (status == WorkflowStatus.COMPLETED) {
                    eventPublisherService.publishWorkflowCompletedEvent(workflowExecution);
                } else if (status == WorkflowStatus.FAILED) {
                    eventPublisherService.publishWorkflowFailedEvent(workflowExecution);
                }
            });
            
        } catch (Exception e) {
            // Handle unexpected errors
            log.error("Error executing workflow {}", workflowExecutionId, e);
            workflowExecution.setErrorMessage(e.getMessage());
            workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.FAILED);
            eventPublisherService.publishWorkflowFailedEvent(workflowExecution);
        }
    }
    
    /**
     * Restart a specific task in a workflow
     * Resets the task and continues workflow execution from that point
     * 
     * @param workflowExecutionId the workflow execution ID
     * @param taskExecutionId the task execution ID to restart
     */
    @Async("taskExecutor")
    @Transactional
    public void restartTask(Long workflowExecutionId, Long taskExecutionId) {
        WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(workflowExecutionId);
        TaskExecution taskExecution = taskExecutionService.getTaskExecution(taskExecutionId);
        
        try {
            // Update workflow status
            workflowExecution.setStatus(WorkflowStatus.RUNNING);
            workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.RUNNING);
            
            // Reset task for execution
            taskExecution.setStatus(TaskStatus.PENDING);
            taskExecution.setStartedAt(null);
            taskExecution.setCompletedAt(null);
            taskExecution.setErrorMessage(null);
            taskExecution.setRetryCount(0);
            taskExecution.setOutputs(new HashMap<>());
            
            // Save task changes
            taskExecutionService.saveTaskExecution(taskExecution);
            
            // Set current task index in workflow to point to this task
            int taskIndex = getTaskIndex(workflowExecution, taskExecution);
            if (taskIndex >= 0) {
                workflowExecution.setCurrentTaskIndex(taskIndex);
                workflowExecutionService.save(workflowExecution);
            }
            
            // Continue workflow execution
            executeWorkflow(workflowExecutionId);
            
        } catch (Exception e) {
            log.error("Error restarting task {} in workflow {}", taskExecutionId, workflowExecutionId, e);
            workflowExecution.setErrorMessage(e.getMessage());
            workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.FAILED);
            eventPublisherService.publishWorkflowFailedEvent(workflowExecution);
        }
    }
    
    /**
     * Execute a subset of tasks within a workflow
     * Useful for retry scenarios or partial workflow execution
     * 
     * @param workflowExecutionId the workflow execution ID
     * @param taskIds the list of task IDs to execute
     */
    @Async("taskExecutor")
    @Transactional
    public void executeTaskSubset(Long workflowExecutionId, List<Long> taskIds) {
        WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(workflowExecutionId);
        
        try {
            // Update workflow status
            workflowExecution.setStatus(WorkflowStatus.RUNNING);
            workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.RUNNING);
            
            // Get the appropriate execution strategy
            WorkflowDefinition workflowDefinition = workflowExecution.getWorkflowDefinition();
            ExecutionStrategy strategy = getExecutionStrategy(workflowDefinition.getStrategyType());
            
            // Execute the subset of tasks
            CompletableFuture<WorkflowStatus> futureStatus = strategy.executeSubset(workflowExecution, taskIds);
            
            futureStatus.thenAccept(status -> {
                workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, status);
                
                if (status == WorkflowStatus.COMPLETED) {
                    eventPublisherService.publishWorkflowCompletedEvent(workflowExecution);
                } else if (status == WorkflowStatus.FAILED) {
                    eventPublisherService.publishWorkflowFailedEvent(workflowExecution);
                }
            });
            
        } catch (Exception e) {
            log.error("Error executing task subset for workflow {}", workflowExecutionId, e);
            workflowExecution.setErrorMessage(e.getMessage());
            workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, WorkflowStatus.FAILED);
            eventPublisherService.publishWorkflowFailedEvent(workflowExecution);
        }
    }
    
    /**
     * Get the appropriate execution strategy based on type
     * Falls back to sequential strategy if requested type not found
     * 
     * @param strategyType the execution strategy type
     * @return the execution strategy
     * @throws WorkflowException if no strategy can be found
     */
    private ExecutionStrategy getExecutionStrategy(WorkflowDefinition.ExecutionStrategyType strategyType) {
        ExecutionStrategy strategy = executionStrategies.get(strategyType);
        if (strategy == null) {
            log.warn("No execution strategy found for type: {}, using sequential strategy", strategyType);
            strategy = executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        }
        if (strategy == null) {
            throw new WorkflowException("No execution strategy available");
        }
        return strategy;
    }
    
    /**
     * Get the index of a task within a workflow
     * 
     * @param workflowExecution the workflow execution
     * @param taskExecution the task execution
     * @return the index of the task, or -1 if not found
     */
    private int getTaskIndex(WorkflowExecution workflowExecution, TaskExecution taskExecution) {
        List<TaskExecution> taskExecutions = taskExecutionService.getTaskExecutionsForWorkflow(workflowExecution.getId());
        for (int i = 0; i < taskExecutions.size(); i++) {
            if (taskExecutions.get(i).getId().equals(taskExecution.getId())) {
                return i;
            }
        }
        return -1;
    }
}
