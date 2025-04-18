package com.example.workfloworchestrator.engine.scheduler;

import com.example.workfloworchestrator.engine.WorkflowEngine;
import com.example.workfloworchestrator.model.TaskExecution;
import com.example.workfloworchestrator.model.TaskStatus;
import com.example.workfloworchestrator.service.EventPublisherService;
import com.example.workfloworchestrator.service.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduler for handling task retries
 * Periodically checks for tasks that need to be retried and initiates their execution
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryScheduler {
    
    private final TaskExecutionService taskExecutionService;
    private final EventPublisherService eventPublisherService;
    private final WorkflowEngine workflowEngine;
    
    // Map to track retry attempts for workflow executions
    private final Map<Long, Integer> workflowRetryTracker = new HashMap<>();
    
    /**
     * Check for tasks to retry every 10 seconds
     */
    @Scheduled(fixedRateString = "${workflow.scheduler.retry-check-interval:10000}")
    @Transactional
    public void retryFailedTasks() {
        log.debug("Checking for tasks to retry");
        
        // Find tasks that are scheduled for retry and are ready
        List<TaskExecution> tasksToRetry = taskExecutionService.getTasksToRetry(LocalDateTime.now());
        
        if (!tasksToRetry.isEmpty()) {
            log.info("Found {} tasks to retry", tasksToRetry.size());
            
            // Process each task
            for (TaskExecution taskExecution : tasksToRetry) {
                Long workflowExecutionId = taskExecution.getWorkflowExecutionId();
                log.info("Retrying task {}, attempt {}, workflow {}", 
                        taskExecution.getId(), 
                        taskExecution.getRetryCount(),
                        workflowExecutionId);
                
                try {
                    // Update status to PENDING for retry
                    taskExecution.setStatus(TaskStatus.PENDING);
                    taskExecution.setStartedAt(null);
                    taskExecution.setCompletedAt(null);
                    taskExecutionService.saveTaskExecution(taskExecution);
                    
                    // Execute the task again
                    taskExecutionService.executeTask(taskExecution.getId());
                    
                } catch (Exception e) {
                    log.error("Error retrying task {}", taskExecution.getId(), e);
                    
                    // Increment retry count for this workflow
                    int retryCount = workflowRetryTracker.getOrDefault(workflowExecutionId, 0) + 1;
                    workflowRetryTracker.put(workflowExecutionId, retryCount);
                    
                    // If we've had too many consecutive failures, attempt to restart the workflow
                    if (retryCount >= 3) {
                        log.warn("Multiple retry failures for workflow {}, attempting to restart workflow", 
                                workflowExecutionId);
                        
                        try {
                            // Use the workflow engine to restart the workflow
                            workflowEngine.executeWorkflow(workflowExecutionId);
                            
                            // Reset the retry counter
                            workflowRetryTracker.remove(workflowExecutionId);
                            
                        } catch (Exception we) {
                            log.error("Failed to restart workflow {}", workflowExecutionId, we);
                        }
                    }
                }
            }
        } else {
            log.debug("No tasks found for retry");
        }
    }
    
    /**
     * Cleanup old entries in the retry tracker
     * Runs once per hour
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupRetryTracker() {
        log.debug("Cleaning up retry tracker");
        workflowRetryTracker.clear();
    }
}
