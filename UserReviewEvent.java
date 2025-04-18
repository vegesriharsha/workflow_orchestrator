package com.example.workfloworchestrator.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Event for user review operations
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserReviewEvent extends BaseEvent {
    
    /**
     * Event type
     */
    private UserReviewEventType eventType;
    
    /**
     * Workflow execution ID
     */
    private Long workflowExecutionId;
    
    /**
     * Workflow definition ID
     */
    private Long workflowDefinitionId;
    
    /**
     * Workflow name
     */
    private String workflowName;
    
    /**
     * Correlation ID
     */
    private String correlationId;
    
    /**
     * Review point ID
     */
    private Long reviewPointId;
    
    /**
     * Task execution ID
     */
    private Long taskExecutionId;
    
    /**
     * Constructor
     * 
     * @param source the event source
     */
    public UserReviewEvent(Object source) {
        super(source);
    }
    
    @Override
    public String getEventTypeString() {
        return eventType != null ? "USER_REVIEW_" + eventType.name() : "USER_REVIEW_EVENT";
    }
}
