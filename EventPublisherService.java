package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.event.*;
import com.example.workfloworchestrator.model.TaskExecution;
import com.example.workfloworchestrator.model.UserReviewPoint;
import com.example.workfloworchestrator.model.WorkflowExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service for publishing workflow and task related events
 * Can be used for monitoring, auditing, and integration with external systems
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisherService {
    
    private final ApplicationEventPublisher eventPublisher;
    
    @Value("${workflow.events.enabled:true}")
    private boolean eventsEnabled;
    
    @Value("${workflow.events.log-level:INFO}")
    private String logLevel;
    
    /**
     * Publish a workflow created event
     */
    public void publishWorkflowCreatedEvent(WorkflowExecution workflowExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Workflow created: {}, definition: {}, version: {}", 
                workflowExecution.getId(),
                workflowExecution.getWorkflowDefinition().getName(),
                workflowExecution.getWorkflowDefinition().getVersion());
        
        WorkflowEvent event = createWorkflowEvent(
                workflowExecution, 
                WorkflowEventType.CREATED);
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a workflow started event
     */
    public void publishWorkflowStartedEvent(WorkflowExecution workflowExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Workflow started: {}", workflowExecution.getId());
        
        WorkflowEvent event = createWorkflowEvent(
                workflowExecution, 
                WorkflowEventType.STARTED);
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a workflow completed event
     */
    public void publishWorkflowCompletedEvent(WorkflowExecution workflowExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Workflow completed: {}", workflowExecution.getId());
        
        WorkflowEvent event = createWorkflowEvent(
                workflowExecution, 
                WorkflowEventType.COMPLETED);
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a workflow failed event
     */
    public void publishWorkflowFailedEvent(WorkflowExecution workflowExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Workflow failed: {}, error: {}", 
                workflowExecution.getId(), 
                workflowExecution.getErrorMessage());
        
        WorkflowEvent event = createWorkflowEvent(
                workflowExecution, 
                WorkflowEventType.FAILED);
        
        event.setErrorMessage(workflowExecution.getErrorMessage());
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a workflow status changed event
     */
    public void publishWorkflowStatusChangedEvent(WorkflowExecution workflowExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Workflow status changed: {}, status: {}", 
                workflowExecution.getId(), 
                workflowExecution.getStatus());
        
        WorkflowEvent event = createWorkflowEvent(
                workflowExecution, 
                WorkflowEventType.STATUS_CHANGED);
        
        event.addProperty("status", workflowExecution.getStatus().name());
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a workflow paused event
     */
    public void publishWorkflowPausedEvent(WorkflowExecution workflowExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Workflow paused: {}", workflowExecution.getId());
        
        WorkflowEvent event = createWorkflowEvent(
                workflowExecution, 
                WorkflowEventType.PAUSED);
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a workflow resumed event
     */
    public void publishWorkflowResumedEvent(WorkflowExecution workflowExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Workflow resumed: {}", workflowExecution.getId());
        
        WorkflowEvent event = createWorkflowEvent(
                workflowExecution, 
                WorkflowEventType.RESUMED);
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a workflow cancelled event
     */
    public void publishWorkflowCancelledEvent(WorkflowExecution workflowExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Workflow cancelled: {}", workflowExecution.getId());
        
        WorkflowEvent event = createWorkflowEvent(
                workflowExecution, 
                WorkflowEventType.CANCELLED);
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a workflow retry event
     */
    public void publishWorkflowRetryEvent(WorkflowExecution workflowExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Workflow retry: {}, attempt: {}", 
                workflowExecution.getId(),
                workflowExecution.getRetryCount());
        
        WorkflowEvent event = createWorkflowEvent(
                workflowExecution, 
                WorkflowEventType.RETRY);
        
        event.addProperty("retryCount", workflowExecution.getRetryCount().toString());
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a task created event
     */
    public void publishTaskCreatedEvent(WorkflowExecution workflowExecution, TaskExecution taskExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Task created: {}, workflow: {}, type: {}", 
                taskExecution.getId(),
                workflowExecution.getId(),
                taskExecution.getTaskDefinition().getType());
        
        TaskEvent event = createTaskEvent(
                workflowExecution, 
                taskExecution, 
                TaskEventType.CREATED);
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a task started event
     */
    public void publishTaskStartedEvent(TaskExecution taskExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Task started: {}, type: {}", 
                taskExecution.getId(),
                taskExecution.getTaskDefinition().getType());
        
        TaskEvent event = createTaskEvent(
                taskExecution.getWorkflowExecutionId(), 
                taskExecution, 
                TaskEventType.STARTED);
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a task completed event
     */
    public void publishTaskCompletedEvent(TaskExecution taskExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Task completed: {}, type: {}", 
                taskExecution.getId(),
                taskExecution.getTaskDefinition().getType());
        
        TaskEvent event = createTaskEvent(
                taskExecution.getWorkflowExecutionId(), 
                taskExecution, 
                TaskEventType.COMPLETED);
        
        // Add output variable names as properties
        if (taskExecution.getOutputs() != null) {
            event.addProperty("outputKeys", String.join(",", taskExecution.getOutputs().keySet()));
        }
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a task failed event
     */
    public void publishTaskFailedEvent(TaskExecution taskExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Task failed: {}, type: {}, error: {}", 
                taskExecution.getId(),
                taskExecution.getTaskDefinition().getType(),
                taskExecution.getErrorMessage());
        
        TaskEvent event = createTaskEvent(
                taskExecution.getWorkflowExecutionId(), 
                taskExecution, 
                TaskEventType.FAILED);
        
        event.setErrorMessage(taskExecution.getErrorMessage());
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a task skipped event
     */
    public void publishTaskSkippedEvent(TaskExecution taskExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Task skipped: {}, type: {}", 
                taskExecution.getId(),
                taskExecution.getTaskDefinition().getType());
        
        TaskEvent event = createTaskEvent(
                taskExecution.getWorkflowExecutionId(), 
                taskExecution, 
                TaskEventType.SKIPPED);
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a task retry scheduled event
     */
    public void publishTaskRetryScheduledEvent(TaskExecution taskExecution) {
        if (!eventsEnabled) return;
        
        logEvent("Task retry scheduled: {}, type: {}, attempt: {}, nextRetry: {}", 
                taskExecution.getId(),
                taskExecution.getTaskDefinition().getType(),
                taskExecution.getRetryCount(),
                taskExecution.getNextRetryAt());
        
        TaskEvent event = createTaskEvent(
                taskExecution.getWorkflowExecutionId(), 
                taskExecution, 
                TaskEventType.RETRY_SCHEDULED);
        
        event.addProperty("retryCount", taskExecution.getRetryCount().toString());
        event.addProperty("nextRetryAt", taskExecution.getNextRetryAt().toString());
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a user review requested event
     */
    public void publishUserReviewRequestedEvent(WorkflowExecution workflowExecution, UserReviewPoint reviewPoint) {
        if (!eventsEnabled) return;
        
        logEvent("User review requested: {}, workflow: {}, task: {}", 
                reviewPoint.getId(),
                workflowExecution.getId(),
                reviewPoint.getTaskExecutionId());
        
        UserReviewEvent event = createUserReviewEvent(
                workflowExecution, 
                reviewPoint, 
                UserReviewEventType.REQUESTED);
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish a user review completed event
     */
    public void publishUserReviewCompletedEvent(WorkflowExecution workflowExecution, UserReviewPoint reviewPoint) {
        if (!eventsEnabled) return;
        
        logEvent("User review completed: {}, workflow: {}, task: {}, decision: {}, reviewer: {}", 
                reviewPoint.getId(),
                workflowExecution.getId(),
                reviewPoint.getTaskExecutionId(),
                reviewPoint.getDecision(),
                reviewPoint.getReviewer());
        
        UserReviewEvent event = createUserReviewEvent(
                workflowExecution, 
                reviewPoint, 
                UserReviewEventType.COMPLETED);
        
        event.addProperty("decision", reviewPoint.getDecision().name());
        event.addProperty("reviewer", reviewPoint.getReviewer());
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Create a workflow event with common properties
     */
    private WorkflowEvent createWorkflowEvent(WorkflowExecution workflowExecution, WorkflowEventType eventType) {
        WorkflowEvent event = new WorkflowEvent(this);
        
        event.setEventType(eventType);
        event.setTimestamp(LocalDateTime.now());
        event.setWorkflowExecutionId(workflowExecution.getId());
        event.setWorkflowDefinitionId(workflowExecution.getWorkflowDefinition().getId());
        event.setWorkflowName(workflowExecution.getWorkflowDefinition().getName());
        event.setWorkflowVersion(workflowExecution.getWorkflowDefinition().getVersion());
        event.setCorrelationId(workflowExecution.getCorrelationId());
        
        return event;
    }
    
    /**
     * Create a task event with common properties
     */
    private TaskEvent createTaskEvent(WorkflowExecution workflowExecution, 
                                     TaskExecution taskExecution, 
                                     TaskEventType eventType) {
        
        TaskEvent event = new TaskEvent(this);
        
        event.setEventType(eventType);
        event.setTimestamp(LocalDateTime.now());
        event.setWorkflowExecutionId(workflowExecution.getId());
        event.setWorkflowDefinitionId(workflowExecution.getWorkflowDefinition().getId());
        event.setWorkflowName(workflowExecution.getWorkflowDefinition().getName());
        event.setCorrelationId(workflowExecution.getCorrelationId());
        
        event.setTaskExecutionId(taskExecution.getId());
        event.setTaskDefinitionId(taskExecution.getTaskDefinition().getId());
        event.setTaskName(taskExecution.getTaskDefinition().getName());
        event.setTaskType(taskExecution.getTaskDefinition().getType());
        
        return event;
    }
    
    /**
     * Create a task event with common properties (alternative method)
     */
    private TaskEvent createTaskEvent(Long workflowExecutionId, 
                                     TaskExecution taskExecution, 
                                     TaskEventType eventType) {
        
        TaskEvent event = new TaskEvent(this);
        
        event.setEventType(eventType);
        event.setTimestamp(LocalDateTime.now());
        event.setWorkflowExecutionId(workflowExecutionId);
        event.setTaskExecutionId(taskExecution.getId());
        event.setTaskDefinitionId(taskExecution.getTaskDefinition().getId());
        event.setTaskName(taskExecution.getTaskDefinition().getName());
        event.setTaskType(taskExecution.getTaskDefinition().getType());
        
        return event;
    }
    
    /**
     * Create a user review event with common properties
     */
    private UserReviewEvent createUserReviewEvent(WorkflowExecution workflowExecution, 
                                                UserReviewPoint reviewPoint, 
                                                UserReviewEventType eventType) {
        
        UserReviewEvent event = new UserReviewEvent(this);
        
        event.setEventType(eventType);
        event.setTimestamp(LocalDateTime.now());
        event.setWorkflowExecutionId(workflowExecution.getId());
        event.setWorkflowDefinitionId(workflowExecution.getWorkflowDefinition().getId());
        event.setWorkflowName(workflowExecution.getWorkflowDefinition().getName());
        event.setCorrelationId(workflowExecution.getCorrelationId());
        
        event.setReviewPointId(reviewPoint.getId());
        event.setTaskExecutionId(reviewPoint.getTaskExecutionId());
        
        if (reviewPoint.getReviewedAt() != null) {
            event.addProperty("reviewedAt", reviewPoint.getReviewedAt().toString());
        }
        
        if (reviewPoint.getComment() != null) {
            event.addProperty("comment", reviewPoint.getComment());
        }
        
        return event;
    }
    
    /**
     * Log an event message with the configured log level
     */
    private void logEvent(String message, Object... args) {
        switch (logLevel.toUpperCase()) {
            case "TRACE":
                log.trace(message, args);
                break;
            case "DEBUG":
                log.debug(message, args);
                break;
            case "WARN":
                log.warn(message, args);
                break;
            case "ERROR":
                log.error(message, args);
                break;
            case "INFO":
            default:
                log.info(message, args);
        }
    }
}
