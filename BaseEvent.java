package com.example.workfloworchestrator.event;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base event class for all workflow orchestrator events
 */
@Data
public abstract class BaseEvent {
    
    /**
     * Unique event ID
     */
    private String eventId;
    
    /**
     * Event timestamp
     */
    private LocalDateTime timestamp;
    
    /**
     * Event source (the component that published the event)
     */
    private Object source;
    
    /**
     * Error message (if applicable)
     */
    private String errorMessage;
    
    /**
     * Additional event properties
     */
    private Map<String, String> properties = new HashMap<>();
    
    /**
     * Constructor
     * 
     * @param source the event source
     */
    public BaseEvent(Object source) {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.source = source;
    }
    
    /**
     * Add a property to the event
     * 
     * @param key property key
     * @param value property value
     */
    public void addProperty(String key, String value) {
        if (key != null && value != null) {
            properties.put(key, value);
        }
    }
    
    /**
     * Get a property value
     * 
     * @param key property key
     * @return property value or null if not found
     */
    public String getProperty(String key) {
        return properties.get(key);
    }
    
    /**
     * Get event type string
     * 
     * @return string representation of event type
     */
    public abstract String getEventTypeString();
}
