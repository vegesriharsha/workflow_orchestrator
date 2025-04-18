package com.example.workfloworchestrator.engine.scheduler;

import com.example.workfloworchestrator.engine.WorkflowEngine;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.model.WorkflowStatus;
import com.example.workfloworchestrator.service.EventPublisherService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduler for monitoring workflow executions
 * Handles stuck workflows and performs cleanup operations
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowScheduler {
    
    private final WorkflowExecutionService workflowExecutionService;
    private final EventPublisherService eventPublisherService;
    private final WorkflowEngine workflowEngine;
    
    @Value("${workflow.scheduler.stuck-workflow-timeout-minutes:30}")
    private int stuckWorkflowTimeoutMinutes;
    
    @Value("${workflow.scheduler.stuck-workflow-auto-retry:true}")
    private boolean stuckWorkflowAutoRetry;
    
    @Value("${workflow.scheduler.completed-workflow-retention-days:30}")
    private int completedWorkflowRetentionDays;
    
    /**
     * Check for stuck workflows every 5 minutes
     */
    @Scheduled(fixedRateString = "${workflow.scheduler.stuck-workflows-check-interval:300000}")
    @Transactional
    public void checkStuckWorkflows() {
        log.info("Checking for stuck workflows");
        
        // Find workflows that have been running for too long
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(stuckWorkflowTimeoutMinutes);
        List<WorkflowExecution> stuckWorkflows = workflowExecutionService.getStuckWorkflowExecutions(threshold);
        
        log.info("Found {} stuck workflows", stuckWorkflows.size());
        
        // Handle stuck workflows
        for (WorkflowExecution execution : stuckWorkflows) {
            log.warn("Workflow {} is stuck in status {} since {}", 
                    execution.getId(), execution.getStatus(), execution.getStartedAt());
            
            if (stuckWorkflowAutoRetry) {
                try {
                    // Attempt to resume the workflow using the workflow engine
                    log.info("Attempting to resume stuck workflow {}", execution.getId());
                    workflowEngine.executeWorkflow(execution.getId());
                } catch (Exception e) {
                    log.error("Failed to resume stuck workflow {}", execution.getId(), e);
                    
                    // Update status to FAILED if we couldn't resume
                    execution.setErrorMessage("Workflow execution timed out and auto-retry failed: " + e.getMessage());
                    workflowExecutionService.updateWorkflowExecutionStatus(execution.getId(), WorkflowStatus.FAILED);
                    eventPublisherService.publishWorkflowFailedEvent(execution);
                }
            } else {
                // Just mark as failed if auto-retry is disabled
                execution.setErrorMessage("Workflow execution timed out");
                workflowExecutionService.updateWorkflowExecutionStatus(execution.getId(), WorkflowStatus.FAILED);
                eventPublisherService.publishWorkflowFailedEvent(execution);
            }
        }
    }
    
    /**
     * Clean up old workflow executions periodically (once a day at midnight)
     */
    @Scheduled(cron = "${workflow.scheduler.cleanup-cron:0 0 0 * * ?}")
    @Transactional
    public void cleanupOldWorkflowExecutions() {
        log.info("Cleaning up old workflow executions");
        
        // Calculate retention threshold
        LocalDateTime retentionThreshold = LocalDateTime.now()
                .minus(completedWorkflowRetentionDays, ChronoUnit.DAYS);
        
        // Find completed/failed workflows older than retention threshold
        List<WorkflowExecution> oldWorkflows = workflowExecutionService.findCompletedWorkflowsOlderThan(retentionThreshold);
        
        if (!oldWorkflows.isEmpty()) {
            log.info("Found {} old workflow executions to clean up", oldWorkflows.size());
            
            // Delete old workflows
            for (WorkflowExecution execution : oldWorkflows) {
                try {
                    log.debug("Deleting old workflow execution: {}, completed at: {}", 
                            execution.getId(), execution.getCompletedAt());
                    workflowExecutionService.deleteWorkflowExecution(execution.getId());
                } catch (Exception e) {
                    log.error("Error deleting workflow execution {}", execution.getId(), e);
                }
            }
        } else {
            log.debug("No old workflow executions to clean up");
        }
    }
    
    /**
     * Recover paused workflows that might have been forgotten
     * Runs every hour
     */
    @Scheduled(cron = "${workflow.scheduler.paused-workflow-check-cron:0 0 * * * ?}")
    @Transactional
    public void checkPausedWorkflows() {
        log.info("Checking for long-paused workflows");
        
        // Find workflows paused for more than 24 hours
        LocalDateTime pausedThreshold = LocalDateTime.now().minusHours(24);
        List<WorkflowExecution> longPausedWorkflows = workflowExecutionService.findPausedWorkflowsOlderThan(pausedThreshold);
        
        if (!longPausedWorkflows.isEmpty()) {
            log.info("Found {} workflows paused for more than 24 hours", longPausedWorkflows.size());
            
            // Log the workflows but don't take action automatically
            // This is just to bring attention to potentially forgotten workflows
            for (WorkflowExecution execution : longPausedWorkflows) {
                log.warn("Workflow {} has been paused since {}", execution.getId(), execution.getStartedAt());
            }
        }
    }
}
