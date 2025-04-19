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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
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
    private List<TaskExecution> taskExecutions;
    
    @BeforeEach
    void setUp() {
        // Create task definitions
        List<TaskDefinition> taskDefinitions = new ArrayList<>();
        
        TaskDefinition task1 = new TaskDefinition();
        task1.setId(1L);
        task1.setName("Task 1");
        task1.setType("http");
        task1.setExecutionOrder(0);
        
        TaskDefinition task2 = new TaskDefinition();
        task2.setId(2L);
        task2.setName("Task 2");
        task2.setType("custom");
        task2.setExecutionOrder(1);
        
        taskDefinitions.add(task1);
        taskDefinitions.add(task2);
        
        // Create workflow definition
        workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setId(1L);
        workflowDefinition.setName("Test Workflow");
        workflowDefinition.setVersion("1.0.0");
        workflowDefinition.setTasks(taskDefinitions);
        workflowDefinition.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        
        // Create workflow execution
        workflowExecution = new WorkflowExecution();
        workflowExecution.setId(1L);
        workflowExecution.setWorkflowDefinition(workflowDefinition);
        workflowExecution.setStatus(WorkflowStatus.CREATED);
        workflowExecution.setStartedAt(LocalDateTime.now());
        workflowExecution.setVariables(new HashMap<>());
        workflowExecution.setCorrelationId("test-correlation");
        workflowExecution.setCurrentTaskIndex(0);
        
        // Create task executions
        taskExecutions = new ArrayList<>();
        
        TaskExecution taskExec1 = new TaskExecution();
        taskExec1.setId(1L);
        taskExec1.setTaskDefinition(task1);
        taskExec1.setWorkflowExecutionId(workflowExecution.getId());
        taskExec1.setStatus(TaskStatus.PENDING);
        
        TaskExecution taskExec2 = new TaskExecution();
        taskExec2.setId(2L);
        taskExec2.setTaskDefinition(task2);
        taskExec2.setWorkflowExecutionId(workflowExecution.getId());
        taskExec2.setStatus(TaskStatus.PENDING);
        
        taskExecutions.add(taskExec1);
        taskExecutions.add(taskExec2);
    }
    
    @Test
    void executeWorkflow_WhenCreated_ShouldStartAndExecute() {
        // Arrange
        workflowExecution.setStatus(WorkflowStatus.CREATED);
        
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL))
                .thenReturn(sequentialStrategy);
        when(sequentialStrategy.execute(workflowExecution))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.COMPLETED));
        
        // Act
        workflowEngine.executeWorkflow(1L);
        
        // Assert
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.RUNNING);
        verify(eventPublisherService).publishWorkflowStartedEvent(workflowExecution);
        verify(executionStrategies).get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        verify(sequentialStrategy).execute(workflowExecution);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.COMPLETED);
        verify(eventPublisherService).publishWorkflowCompletedEvent(workflowExecution);
    }
    
    @Test
    void executeWorkflow_WhenRunning_ShouldContinueExecutingWithoutStatusChange() {
        // Arrange
        workflowExecution.setStatus(WorkflowStatus.RUNNING);
        
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL))
                .thenReturn(sequentialStrategy);
        when(sequentialStrategy.execute(workflowExecution))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.COMPLETED));
        
        // Act
        workflowEngine.executeWorkflow(1L);
        
        // Assert
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(workflowExecutionService, never()).updateWorkflowExecutionStatus(1L, WorkflowStatus.RUNNING);
        verify(eventPublisherService, never()).publishWorkflowStartedEvent(workflowExecution);
        verify(executionStrategies).get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        verify(sequentialStrategy).execute(workflowExecution);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.COMPLETED);
        verify(eventPublisherService).publishWorkflowCompletedEvent(workflowExecution);
    }
    
    @Test
    void executeWorkflow_FailedExecution_ShouldUpdateStatusToFailed() {
        // Arrange
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL))
                .thenReturn(sequentialStrategy);
        when(sequentialStrategy.execute(workflowExecution))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.FAILED));
        
        // Act
        workflowEngine.executeWorkflow(1L);
        
        // Assert
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.RUNNING);
        verify(executionStrategies).get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        verify(sequentialStrategy).execute(workflowExecution);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.FAILED);
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
        verify(eventPublisherService, never()).publishWorkflowCompletedEvent(workflowExecution);
    }
    
    @Test
    void executeWorkflow_ExceptionDuringExecution_ShouldHandleAndFailWorkflow() {
        // Arrange
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL))
                .thenThrow(new RuntimeException("Test exception"));
        
        // Act
        workflowEngine.executeWorkflow(1L);
        
        // Assert
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.RUNNING);
        verify(executionStrategies).get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.FAILED);
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
    }
    
    @Test
    void executeWorkflow_InvalidStatus_ShouldNotExecute() {
        // Arrange
        workflowExecution.setStatus(WorkflowStatus.COMPLETED);
        
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        
        // Act
        workflowEngine.executeWorkflow(1L);
        
        // Assert
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(executionStrategies, never()).get(any());
        verify(sequentialStrategy, never()).execute(any());
        verify(workflowExecutionService, never()).updateWorkflowExecutionStatus(anyLong(), any());
    }
    
    @Test
    void restartTask_ShouldResetTaskAndContinueExecution() {
        // Arrange
        TaskExecution taskExecution = taskExecutions.get(0);
        taskExecution.setStatus(TaskStatus.FAILED);
        taskExecution.setErrorMessage("Previous error");
        
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(taskExecutionService.getTaskExecution(1L)).thenReturn(taskExecution);
        when(taskExecutionService.getTaskExecutionsForWorkflow(1L)).thenReturn(taskExecutions);
        
        // Act
        workflowEngine.restartTask(1L, 1L);
        
        // Assert
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(taskExecutionService).getTaskExecution(1L);
        verify(workflowExecution).setStatus(WorkflowStatus.RUNNING);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.RUNNING);
        
        verify(taskExecution).setStatus(TaskStatus.PENDING);
        verify(taskExecution).setStartedAt(null);
        verify(taskExecution).setCompletedAt(null);
        verify(taskExecution).setErrorMessage(null);
        verify(taskExecution).setRetryCount(0);
        verify(taskExecution).setOutputs(any());
        verify(taskExecutionService).saveTaskExecution(taskExecution);
        
        // Verify workflow task index is updated
        verify(workflowExecution).setCurrentTaskIndex(0);
        
        // Verify workflow execution is continued
        verify(workflowEngine).executeWorkflow(1L);
    }
    
    @Test
    void restartTask_ExceptionDuringRestart_ShouldHandleAndFailWorkflow() {
        // Arrange
        TaskExecution taskExecution = taskExecutions.get(0);
        
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(taskExecutionService.getTaskExecution(1L)).thenReturn(taskExecution);
        when(taskExecutionService.saveTaskExecution(any())).thenThrow(new RuntimeException("Test exception"));
        
        // Act
        workflowEngine.restartTask(1L, 1L);
        
        // Assert
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(taskExecutionService).getTaskExecution(1L);
        verify(workflowExecution).setStatus(WorkflowStatus.RUNNING);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.RUNNING);
        
        // Verify error handling
        verify(workflowExecution).setErrorMessage(anyString());
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.FAILED);
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
    }
    
    @Test
    void executeTaskSubset_ShouldExecuteSelectedTasks() {
        // Arrange
        List<Long> taskIds = List.of(1L, 2L);
        
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL))
                .thenReturn(sequentialStrategy);
        when(sequentialStrategy.executeSubset(workflowExecution, taskIds))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.COMPLETED));
        
        // Act
        workflowEngine.executeTaskSubset(1L, taskIds);
        
        // Assert
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(workflowExecution).setStatus(WorkflowStatus.RUNNING);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.RUNNING);
        verify(executionStrategies).get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        verify(sequentialStrategy).executeSubset(workflowExecution, taskIds);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.COMPLETED);
        verify(eventPublisherService).publishWorkflowCompletedEvent(workflowExecution);
    }
    
    @Test
    void executeTaskSubset_FailedExecution_ShouldUpdateStatusToFailed() {
        // Arrange
        List<Long> taskIds = List.of(1L, 2L);
        
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL))
                .thenReturn(sequentialStrategy);
        when(sequentialStrategy.executeSubset(workflowExecution, taskIds))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.FAILED));
        
        // Act
        workflowEngine.executeTaskSubset(1L, taskIds);
        
        // Assert
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(workflowExecution).setStatus(WorkflowStatus.RUNNING);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.RUNNING);
        verify(executionStrategies).get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        verify(sequentialStrategy).executeSubset(workflowExecution, taskIds);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.FAILED);
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
    }
    
    @Test
    void executeTaskSubset_ExceptionDuringExecution_ShouldHandleAndFailWorkflow() {
        // Arrange
        List<Long> taskIds = List.of(1L, 2L);
        
        when(workflowExecutionService.getWorkflowExecution(1L)).thenReturn(workflowExecution);
        when(executionStrategies.get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL))
                .thenThrow(new RuntimeException("Test exception"));
        
        // Act
        workflowEngine.executeTaskSubset(1L, taskIds);
        
        // Assert
        verify(workflowExecutionService).getWorkflowExecution(1L);
        verify(workflowExecution).setStatus(WorkflowStatus.RUNNING);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.RUNNING);
        verify(executionStrategies).get(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        
        // Verify error handling
        verify(workflowExecution).setErrorMessage(anyString());
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.FAILED);
        verify(eventPublisherService).publishWorkflowFailedEvent(workflowExecution);
    }
}
