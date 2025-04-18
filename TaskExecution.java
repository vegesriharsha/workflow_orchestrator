package com.example.workfloworchestrator.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Entity representing a single task execution within a workflow
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "task_executions")
public class TaskExecution {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "task_definition_id", nullable = false)
    private TaskDefinition taskDefinition;
    
    @Column(name = "workflow_execution_id", nullable = false)
    private Long workflowExecutionId;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "execution_mode")
    @Enumerated(EnumType.STRING)
    private ExecutionMode executionMode;
    
    @ElementCollection
    @CollectionTable(name = "task_execution_inputs", 
                    joinColumns = @JoinColumn(name = "task_execution_id"))
    @MapKeyColumn(name = "input_key")
    @Column(name = "input_value")
    private Map<String, String> inputs = new HashMap<>();
    
    @ElementCollection
    @CollectionTable(name = "task_execution_outputs", 
                    joinColumns = @JoinColumn(name = "task_execution_id"))
    @MapKeyColumn(name = "output_key")
    @Column(name = "output_value")
    private Map<String, String> outputs = new HashMap<>();
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;
}
