package com.example.workfloworchestrator.event;

/**
 * Enum for workflow event types
 */
public enum WorkflowEventType {
    CREATED,
    STARTED,
    COMPLETED,
    FAILED,
    PAUSED,
    RESUMED,
    CANCELLED,
    RETRY,
    STATUS_CHANGED
}

/**
 * Enum for task event types
 */
enum TaskEventType {
    CREATED,
    STARTED,
    COMPLETED,
    FAILED,
    SKIPPED,
    RETRY_SCHEDULED
}

/**
 * Enum for user review event types
 */
enum UserReviewEventType {
    REQUESTED,
    COMPLETED
}
