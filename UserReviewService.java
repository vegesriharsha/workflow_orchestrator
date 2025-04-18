package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.engine.WorkflowEngine;
import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.TaskExecution;
import com.example.workfloworchestrator.model.UserReviewPoint;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.model.WorkflowStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for managing user review operations in workflows
 * Handles review points that require human intervention
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserReviewService {
    
    private final WorkflowExecutionService workflowExecutionService;
    private final TaskExecutionService taskExecutionService;
    private final WorkflowEngine workflowEngine;
    private final EventPublisherService eventPublisherService;
    
    /**
     * Create a user review point for a specific task
     * Moves the workflow to AWAITING_USER_REVIEW status
     * 
     * @param taskExecutionId the task execution ID
     * @return the created user review point
     */
    @Transactional
    public UserReviewPoint createUserReviewPoint(Long taskExecutionId) {
        TaskExecution taskExecution = taskExecutionService.getTaskExecution(taskExecutionId);
        WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(taskExecution.getWorkflowExecutionId());
        
        // Set workflow to awaiting review status
        workflowExecutionService.updateWorkflowExecutionStatus(workflowExecution.getId(), WorkflowStatus.AWAITING_USER_REVIEW);
        
        // Create review point
        UserReviewPoint reviewPoint = new UserReviewPoint();
        reviewPoint.setTaskExecutionId(taskExecutionId);
        reviewPoint.setCreatedAt(LocalDateTime.now());
        
        // Add review point to workflow execution
        workflowExecution.getReviewPoints().add(reviewPoint);
        workflowExecutionService.save(workflowExecution);
        
        // Publish event
        eventPublisherService.publishUserReviewRequestedEvent(workflowExecution, reviewPoint);
        
        return reviewPoint;
    }
    
    /**
     * Submit a review decision for a task
     * Resumes workflow execution based on the decision
     * 
     * @param reviewPointId the review point ID
     * @param decision the review decision (APPROVE, REJECT, RESTART)
     * @param reviewer the reviewer's identity
     * @param comment optional comment
     * @return the updated workflow execution
     */
    @Transactional
    public WorkflowExecution submitUserReview(Long reviewPointId, UserReviewPoint.ReviewDecision decision, 
                                            String reviewer, String comment) {
        WorkflowExecution workflowExecution = getWorkflowExecutionByReviewPointId(reviewPointId);
        
        UserReviewPoint reviewPoint = workflowExecution.getReviewPoints().stream()
                .filter(rp -> rp.getId().equals(reviewPointId))
                .findFirst()
                .orElseThrow(() -> new WorkflowException("Review point not found with id: " + reviewPointId));
        
        // Update review point
        reviewPoint.setReviewedAt(LocalDateTime.now());
        reviewPoint.setReviewer(reviewer);
        reviewPoint.setComment(comment);
        reviewPoint.setDecision(decision);
        
        // Process the decision
        TaskExecution taskExecution = taskExecutionService.getTaskExecution(reviewPoint.getTaskExecutionId());
        
        switch (decision) {
            case APPROVE:
                // Mark task as completed and continue workflow
                taskExecutionService.completeTaskExecution(taskExecution.getId(), taskExecution.getOutputs());
                workflowExecutionService.updateWorkflowExecutionStatus(workflowExecution.getId(), WorkflowStatus.RUNNING);
                workflowEngine.executeWorkflow(workflowExecution.getId());
                break;
                
            case REJECT:
                // Mark task as failed and continue with failure handling
                taskExecutionService.failTaskExecution(taskExecution.getId(), "Rejected by user: " + reviewer);
                workflowExecutionService.updateWorkflowExecutionStatus(workflowExecution.getId(), WorkflowStatus.RUNNING);
                workflowEngine.executeWorkflow(workflowExecution.getId());
                break;
                
            case RESTART:
                // Restart the specific task
                workflowExecutionService.updateWorkflowExecutionStatus(workflowExecution.getId(), WorkflowStatus.RUNNING);
                workflowEngine.restartTask(workflowExecution.getId(), taskExecution.getId());
                break;
                
            default:
                throw new WorkflowException("Unsupported review decision: " + decision);
        }
        
        // Publish event
        eventPublisherService.publishUserReviewCompletedEvent(workflowExecution, reviewPoint);
        
        return workflowExecution;
    }
    
    /**
     * Get all pending user review points
     * 
     * @return list of pending review points
     */
    @Transactional(readOnly = true)
    public List<UserReviewPoint> getPendingReviewPoints() {
        return workflowExecutionService.getWorkflowExecutionsByStatus(WorkflowStatus.AWAITING_USER_REVIEW).stream()
                .flatMap(we -> we.getReviewPoints().stream())
                .filter(rp -> rp.getReviewedAt() == null)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Find the workflow execution containing a specific review point
     * 
     * @param reviewPointId the review point ID
     * @return the workflow execution
     * @throws WorkflowException if not found
     */
    private WorkflowExecution getWorkflowExecutionByReviewPointId(Long reviewPointId) {
        return workflowExecutionService.getAllWorkflowExecutions().stream()
                .filter(we -> we.getReviewPoints().stream()
                        .anyMatch(rp -> rp.getId().equals(reviewPointId)))
                .findFirst()
                .orElseThrow(() -> new WorkflowException("No workflow execution found for review point id: " + reviewPointId));
    }
}
