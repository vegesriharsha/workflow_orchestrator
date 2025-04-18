package com.example.workfloworchestrator.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Event for workflow-related operations
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkflowEvent extends BaseEvent {
    
    /**
     * Event type
     */
    private WorkflowEventType eventType;
    
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
     * Workflow version
     */
    private String workflowVersion;
    
    /**
     * Correlation ID
     */
    private String correlationId;
    
    /**
     * Constructor
     * 
     * @param source the event source
     */
    public WorkflowEvent(Object source) {
        super(source);
    }
    
    @Override
    public String getEventTypeString() {
        return eventType != null ? "WORKFLOW_" + eventType.name() : "WORKFLOW_EVENT";
    }
}
