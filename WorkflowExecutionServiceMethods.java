package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.model.WorkflowStatus;
import com.example.workfloworchestrator.repository.WorkflowExecutionRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * These are the missing methods in WorkflowExecutionService that are required by WorkflowScheduler
 * Add these methods to the existing WorkflowExecutionService class
 */
public class WorkflowExecutionServiceMethods {

    /**
     * Find completed/failed/cancelled workflows older than a specified date
     * Used for cleanup operations
     * 
     * @param before the date threshold
     * @return list of old workflow executions
     */
    @Transactional(readOnly = true)
    public List<WorkflowExecution> findCompletedWorkflowsOlderThan(LocalDateTime before) {
        // Find workflows with terminal statuses that completed before the threshold
        List<WorkflowExecution> completedWorkflows = workflowExecutionRepository.findByStatusIn(
                List.of(WorkflowStatus.COMPLETED, WorkflowStatus.FAILED, WorkflowStatus.CANCELLED));
        
        // Filter for those completed before the threshold date
        return completedWorkflows.stream()
                .filter(we -> we.getCompletedAt() != null && we.getCompletedAt().isBefore(before))
                .collect(Collectors.toList());
    }
    
    /**
     * Find workflows that have been paused longer than a specified duration
     * Used for monitoring potentially forgotten workflows
     * 
     * @param before the date threshold
     * @return list of long-paused workflow executions
     */
    @Transactional(readOnly = true)
    public List<WorkflowExecution> findPausedWorkflowsOlderThan(LocalDateTime before) {
        List<WorkflowExecution> pausedWorkflows = workflowExecutionRepository.findByStatus(WorkflowStatus.PAUSED);
        
        // Filter for workflows paused before the threshold date
        // For paused workflows, we use startedAt since there's no specific "pausedAt" timestamp
        return pausedWorkflows.stream()
                .filter(we -> we.getStartedAt() != null && we.getStartedAt().isBefore(before))
                .collect(Collectors.toList());
    }
    
    /**
     * Delete a workflow execution and all associated task executions and review points
     * Used for cleanup operations
     * 
     * @param workflowExecutionId the workflow execution ID
     */
    @Transactional
    public void deleteWorkflowExecution(Long workflowExecutionId) {
        WorkflowExecution workflowExecution = getWorkflowExecution(workflowExecutionId);
        
        // Only allow deletion of terminated workflows
        if (workflowExecution.getStatus() != WorkflowStatus.COMPLETED && 
            workflowExecution.getStatus() != WorkflowStatus.FAILED &&
            workflowExecution.getStatus() != WorkflowStatus.CANCELLED) {
            
            throw new WorkflowException("Cannot delete workflow that is not in a terminal state: " + 
                    workflowExecution.getStatus());
        }
        
        // Delete the workflow execution
        // Task executions, review points, and variables should be deleted by cascade
        workflowExecutionRepository.deleteById(workflowExecutionId);
    }
    
    /**
     * To be added to WorkflowExecutionRepository interface:
     * 
     * List<WorkflowExecution> findByStatusIn(List<WorkflowStatus> statuses);
     */
}
