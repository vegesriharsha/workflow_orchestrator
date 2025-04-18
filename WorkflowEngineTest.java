package com.example.workfloworchestrator.engine;

import com.example.workfloworchestrator.engine.strategy.ExecutionStrategy;
import com.example.workfloworchestrator.engine.strategy.SequentialExecutionStrategy;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.EventPublisherService;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkflowEngineTest {
    
    @Mock
    private WorkflowExecutionService workflowExecutionService;
    
    @Mock
    private TaskExecutionService taskExecutionService;
    
    @Mock
    private EventPublisherService eventPublisherService;
    
    @Mock
    private Map<WorkflowDefinition.ExecutionStrategyType, ExecutionStrategy> executionStrategies;
    
    @Mock
    private SequentialExecutionStrategy sequentialStrategy;
    
    @InjectMocks
    private WorkflowEngine workflowEngine;
    
    private WorkflowDefinition workflowDefinition;
    private WorkflowExecution workflowExecution;
    
    @BeforeEach
    void setUp() {
        // Create workflow definition
        workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setId(1L);
        workflowDefinition.setName("test-workflow");
        workflowDefinition.setVersion("1.0.0");
        workflowDefinition.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        
        // Create task definitions
        TaskDefinition task1 = new TaskDefinition();
        task1.setId(1L);
        task1.setName("task1");
        task1.setType("http");
        task1.setExecutionOrder(0);
        
        TaskDefinition task2 = new TaskDefinition();
        task2.setId(2L);
        task2.setName("task2");
        task2.setType("custom");
        task2.setExecutionOrder(1);
        
        workflowDefinition.setTasks(List.of(task1, task2));
        
        // Create workflow execution
        workflowExecution = new WorkflowExecution();
        workflowExecution.setId(1L);
        workflowExecution.setWorkflowDefinition(workflowDefinition);
        workflowExecution.setCorrelationId("test-correlation-id");
        workflowExecution.setStatus(WorkflowStatus.CREATED);
        workflowExecution.setStartedAt(LocalDateTime.now());
        workflowExecution.setVariables(new HashMap<>());
    }
    
    @Test
    void testExecuteWorkflow_Created() {
        // Mock dependencies
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL)).thenReturn(sequentialStrategy);
        when(sequentialStrategy.execute(any(WorkflowExecution.class)))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.COMPLETED));
        
        // Execute
        workflowEngine.executeWorkflow(1L);
        
        // Verify
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(eq(1L), eq(WorkflowStatus.RUNNING));
        verify(eventPublisherService).publishWorkflowStartedEvent(workflowExecution);
        verify(sequentialStrategy).execute(workflowExecution);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(eq(1L), eq(WorkflowStatus.COMPLETED));
        verify(eventPublisherService).publishWorkflowCompletedEvent(workflowExecution);
    }
    
    @Test
    void testExecuteWorkflow_Running() {
        // Set workflow already running
        workflowExecution.setStatus(WorkflowStatus.RUNNING);
        
        // Mock dependencies
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL)).thenReturn(sequentialStrategy);
        when(sequentialStrategy.execute(any(WorkflowExecution.class)))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.COMPLETED));
        
        // Execute
        workflowEngine.executeWorkflow(1L);
        
        // Verify
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(workflowExecutionService, never()).updateWorkflowExecutionStatus(eq(1L), eq(WorkflowStatus.RUNNING));
        verify(eventPublisherService, never()).publishWorkflowStartedEvent(workflowExecution);
        verify(sequentialStrategy).execute(workflowExecution);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(eq(1L), eq(WorkflowStatus.COMPLETED));
        verify(eventPublisherService).publishWorkflowCompletedEvent(workflowExecution);
    }
    
    @Test
    void testExecuteWorkflow_Failed() {
        // Mock dependencies
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL)).thenReturn(sequentialStrategy);
        when(sequentialStrategy.execute(any(WorkflowExecution.class)))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.FAILED));
        
        // Execute
        workflowEngine.executeWorkflow(1L);
        
        // Verify
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(eq(1L), eq(WorkflowStatus.RUNNING));
        verify(sequentialStrategy).execute(workflowExecution);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(eq(1L), eq(WorkflowStatus.FAILED));
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
        verify(eventPublisherService, never()).publishWorkflowCompletedEvent(workflowExecution);
    }
    
    @Test
    void testExecuteWorkflow_Exception() {
        // Mock dependencies
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL)).thenThrow(new RuntimeException("Test exception"));
        
        // Execute
        workflowEngine.executeWorkflow(1L);
        
        // Verify
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(eq(1L), eq(WorkflowStatus.RUNNING));
        verify(workflowExecutionService).updateWorkflowExecutionStatus(eq(1L), eq(WorkflowStatus.FAILED));
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
    }
    
    @Test
    void testRestartTask() {
        // Create task execution
        TaskExecution taskExecution = new TaskExecution();
        taskExecution.setId(1L);
        taskExecution.setTaskDefinition(workflowDefinition.getTasks().get(0));
        taskExecution.setStatus(TaskStatus.FAILED);
        
        // Mock dependencies
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(taskExecutionService.getTaskExecution(1L)).thenReturn(taskExecution);
        when(taskExecutionService.getTaskExecutionsForWorkflow(1L)).thenReturn(List.of(taskExecution));
        
        // Execute
        workflowEngine.restartTask(1L, 1L);
        
        // Verify
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(taskExecutionService).getTaskExecution(1L);
        verify(workflowExecution).setStatus(WorkflowStatus.RUNNING);
        verify(taskExecution).setStatus(TaskStatus.PENDING);
        verify(taskExecution).setStartedAt(null);
        verify(taskExecution).setCompletedAt(null);
        verify(taskExecution).setErrorMessage(null);
        verify(taskExecution).setRetryCount(0);
        verify(taskExecution).setOutputs(any());
        verify(taskExecutionService).saveTaskExecution(taskExecution);
        verify(workflowExecution).setCurrentTaskIndex(0);
    }
    
    @Test
    void testExecuteTaskSubset() {
        // Create task IDs list
        List<Long> taskIds = List.of(1L, 2L);
        
        // Mock dependencies
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL)).thenReturn(sequentialStrategy);
        when(sequentialStrategy.executeSubset(any(WorkflowExecution.class), eq(taskIds)))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.COMPLETED));
        
        // Execute
        workflowEngine.executeTaskSubset(1L, taskIds);
        
        // Verify
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(eq(1L), eq(WorkflowStatus.RUNNING));
        verify(sequentialStrategy).executeSubset(workflowExecution, taskIds);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(eq(1L), eq(WorkflowStatus.COMPLETED));
        verify(eventPublisherService).publishWorkflowCompletedEvent(workflowExecution);
    }
    
    @Test
    void testExecuteTaskSubset_Failed() {
        // Create task IDs list
        List<Long> taskIds = List.of(1L, 2L);
        
        // Mock dependencies
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL)).thenReturn(sequentialStrategy);
        when(sequentialStrategy.executeSubset(any(WorkflowExecution.class), eq(taskIds)))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.FAILED));
        
        // Execute
        workflowEngine.executeTaskSubset(1L, taskIds);
        
        // Verify
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(eq(1L), eq(WorkflowStatus.RUNNING));
        verify(sequentialStrategy).executeSubset(workflowExecution, taskIds);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(eq(1L), eq(WorkflowStatus.FAILED));
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
    }
    
    @Test
    void testExecuteTaskSubset_Exception() {
        // Create task IDs list
        List<Long> taskIds = List.of(1L, 2L);
        
        // Mock dependencies
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL)).thenThrow(new RuntimeException("Test exception"));
        
        // Execute
        workflowEngine.executeTaskSubset(1L, taskIds);
        
        // Verify
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(eq(1L), eq(WorkflowStatus.RUNNING));
        verify(workflowExecutionService).updateWorkflowExecutionStatus(eq(1L), eq(WorkflowStatus.FAILED));
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
    }
}