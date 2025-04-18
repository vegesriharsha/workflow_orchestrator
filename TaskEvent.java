package com.example.workfloworchestrator.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Event for task-related operations
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskEvent extends BaseEvent {
    
    /**
     * Event type
     */
    private TaskEventType eventType;
    
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
     * Task execution ID
     */
    private Long taskExecutionId;
    
    /**
     * Task definition ID
     */
    private Long taskDefinitionId;
    
    /**
     * Task name
     */
    private String taskName;
    
    /**
     * Task type
     */
    private String taskType;
    
    /**
     * Constructor
     * 
     * @param source the event source
     */
    public TaskEvent(Object source) {
        super(source);
    }
    
    @Override
    public String getEventTypeString() {
        return eventType != null ? "TASK_" + eventType.name() : "TASK_EVENT";
    }
}
