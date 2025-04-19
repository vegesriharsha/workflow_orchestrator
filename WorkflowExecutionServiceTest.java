package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.engine.WorkflowEngine;
import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.repository.WorkflowExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkflowExecutionServiceTest {
    
    @Mock
    private WorkflowService workflowService;
    
    @Mock
    private WorkflowExecutionRepository workflowExecutionRepository;
    
    @Mock
    private WorkflowEngine workflowEngine;
    
    @Mock
    private EventPublisherService eventPublisherService;
    
    @InjectMocks
    private WorkflowExecutionService workflowExecutionService;
    
    @Captor
    private ArgumentCaptor<WorkflowExecution> workflowExecutionCaptor;
    
    private WorkflowDefinition sampleWorkflowDefinition;
    private WorkflowExecution sampleWorkflowExecution;
    
    @BeforeEach
    void setUp() {
        // Create sample workflow definition
        sampleWorkflowDefinition = new WorkflowDefinition();
        sampleWorkflowDefinition.setId(1L);
        sampleWorkflowDefinition.setName("test-workflow");
        sampleWorkflowDefinition.setDescription("Test workflow");
        sampleWorkflowDefinition.setVersion("1.0.0");
        sampleWorkflowDefinition.setCreatedAt(LocalDateTime.now());
        sampleWorkflowDefinition.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        
        // Create sample tasks
        List<TaskDefinition> tasks = new ArrayList<>();
        
        TaskDefinition task1 = new TaskDefinition();
        task1.setId(1L);
        task1.setName("task1");
        task1.setDescription("First task");
        task1.setType("http");
        task1.setExecutionOrder(0);
        
        TaskDefinition task2 = new TaskDefinition();
        task2.setId(2L);
        task2.setName("task2");
        task2.setDescription("Second task");
        task2.setType("custom");
        task2.setExecutionOrder(1);
        
        tasks.add(task1);
        tasks.add(task2);
        sampleWorkflowDefinition.setTasks(tasks);
        
        // Create sample workflow execution
        sampleWorkflowExecution = new WorkflowExecution();
        sampleWorkflowExecution.setId(1L);
        sampleWorkflowExecution.setWorkflowDefinition(sampleWorkflowDefinition);
        sampleWorkflowExecution.setCorrelationId("test-correlation-id");
        sampleWorkflowExecution.setStatus(WorkflowStatus.CREATED);
        sampleWorkflowExecution.setStartedAt(LocalDateTime.now());
        sampleWorkflowExecution.setCurrentTaskIndex(0);
        sampleWorkflowExecution.setVariables(new HashMap<>());
        sampleWorkflowExecution.setRetryCount(0);
    }
    
    @Test
    void startWorkflow_ShouldCreateExecutionAndStartEngine() {
        // Arrange
        String workflowName = "test-workflow";
        String version = "1.0.0";
        Map<String, String> variables = Map.of("key1", "value1", "key2", "value2");
        
        when(workflowService.getWorkflowDefinition(workflowName, version))
                .thenReturn(Optional.of(sampleWorkflowDefinition));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(sampleWorkflowExecution);
        doNothing().when(workflowEngine).executeWorkflow(anyLong());
        
        // Act
        WorkflowExecution result = workflowExecutionService.startWorkflow(workflowName, version, variables);
        
        // Assert
        assertNotNull(result);
        assertEquals(sampleWorkflowDefinition, result.getWorkflowDefinition());
        assertEquals(WorkflowStatus.CREATED, result.getStatus());
        verify(workflowService).getWorkflowDefinition(workflowName, version);
        verify(workflowExecutionRepository).save(any(WorkflowExecution.class));
        verify(workflowEngine).executeWorkflow(result.getId());
    }
    
    @Test
    void startWorkflow_WithoutVersion_ShouldUseLatestVersion() {
        // Arrange
        String workflowName = "test-workflow";
        Map<String, String> variables = Map.of("key1", "value1");
        
        when(workflowService.getLatestWorkflowDefinition(workflowName))
                .thenReturn(Optional.of(sampleWorkflowDefinition));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(sampleWorkflowExecution);
        doNothing().when(workflowEngine).executeWorkflow(anyLong());
        
        // Act
        WorkflowExecution result = workflowExecutionService.startWorkflow(workflowName, null, variables);
        
        // Assert
        assertNotNull(result);
        assertEquals(sampleWorkflowDefinition, result.getWorkflowDefinition());
        verify(workflowService).getLatestWorkflowDefinition(workflowName);
        verify(workflowExecutionRepository).save(any(WorkflowExecution.class));
        verify(workflowEngine).executeWorkflow(result.getId());
    }
    
    @Test
    void startWorkflow_WithNonexistentWorkflow_ShouldThrowException() {
        // Arrange
        String workflowName = "nonexistent-workflow";
        String version = "1.0.0";
        Map<String, String> variables = Map.of("key1", "value1");
        
        when(workflowService.getWorkflowDefinition(workflowName, version))
                .thenReturn(Optional.empty());
        
        // Act & Assert
        WorkflowException exception = assertThrows(WorkflowException.class, () -> {
            workflowExecutionService.startWorkflow(workflowName, version, variables);
        });
        
        assertTrue(exception.getMessage().contains("Workflow definition not found"));
        verify(workflowService).getWorkflowDefinition(workflowName, version);
        verify(workflowExecutionRepository, never()).save(any(WorkflowExecution.class));
        verify(workflowEngine, never()).executeWorkflow(anyLong());
    }
    
    @Test
    void getWorkflowExecution_WithValidId_ShouldReturnExecution() {
        // Arrange
        when(workflowExecutionRepository.findById(1L)).thenReturn(Optional.of(sampleWorkflowExecution));
        
        // Act
        WorkflowExecution result = workflowExecutionService.getWorkflowExecution(1L);
        
        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("test-correlation-id", result.getCorrelationId());
        verify(workflowExecutionRepository).findById(1L);
    }
    
    @Test
    void getWorkflowExecution_WithInvalidId_ShouldThrowException() {
        // Arrange
        when(workflowExecutionRepository.findById(999L)).thenReturn(Optional.empty());
        
        // Act & Assert
        WorkflowException exception = assertThrows(WorkflowException.class, () -> {
            workflowExecutionService.getWorkflowExecution(999L);
        });
        
        assertTrue(exception.getMessage().contains("Workflow execution not found"));
        verify(workflowExecutionRepository).findById(999L);
    }
    
    @Test
    void getWorkflowExecutionByCorrelationId_ShouldReturnExecution() {
        // Arrange
        String correlationId = "test-correlation-id";
        when(workflowExecutionRepository.findByCorrelationId(correlationId))
                .thenReturn(Optional.of(sampleWorkflowExecution));
        
        // Act
        WorkflowExecution result = workflowExecutionService.getWorkflowExecutionByCorrelationId(correlationId);
        
        // Assert
        assertNotNull(result);
        assertEquals(correlationId, result.getCorrelationId());
        verify(workflowExecutionRepository).findByCorrelationId(correlationId);
    }
    
    @Test
    void updateWorkflowExecutionStatus_ShouldUpdateStatusAndPublishEvent() {
        // Arrange
        Long id = 1L;
        WorkflowStatus newStatus = WorkflowStatus.COMPLETED;
        
        when(workflowExecutionRepository.findById(id)).thenReturn(Optional.of(sampleWorkflowExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class))).thenReturn(sampleWorkflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.updateWorkflowExecutionStatus(id, newStatus);
        
        // Assert
        assertEquals(newStatus, result.getStatus());
        assertNotNull(result.getCompletedAt());
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).save(workflowExecutionCaptor.capture());
        verify(eventPublisherService).publishWorkflowStatusChangedEvent(sampleWorkflowExecution);
        
        WorkflowExecution savedExecution = workflowExecutionCaptor.getValue();
        assertEquals(newStatus, savedExecution.getStatus());
    }
    
    @Test
    void pauseWorkflowExecution_WhenRunning_ShouldPauseAndPublishEvent() {
        // Arrange
        Long id = 1L;
        sampleWorkflowExecution.setStatus(WorkflowStatus.RUNNING);
        
        when(workflowExecutionRepository.findById(id)).thenReturn(Optional.of(sampleWorkflowExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class))).thenReturn(sampleWorkflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.pauseWorkflowExecution(id);
        
        // Assert
        assertEquals(WorkflowStatus.PAUSED, result.getStatus());
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).save(sampleWorkflowExecution);
        verify(eventPublisherService).publishWorkflowPausedEvent(sampleWorkflowExecution);
    }
    
    @Test
    void resumeWorkflowExecution_WhenPaused_ShouldResumeAndStartEngine() {
        // Arrange
        Long id = 1L;
        sampleWorkflowExecution.setStatus(WorkflowStatus.PAUSED);
        
        when(workflowExecutionRepository.findById(id)).thenReturn(Optional.of(sampleWorkflowExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class))).thenReturn(sampleWorkflowExecution);
        doNothing().when(workflowEngine).executeWorkflow(id);
        
        // Act
        WorkflowExecution result = workflowExecutionService.resumeWorkflowExecution(id);
        
        // Assert
        assertEquals(WorkflowStatus.RUNNING, result.getStatus());
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).save(sampleWorkflowExecution);
        verify(workflowEngine).executeWorkflow(id);
        verify(eventPublisherService).publishWorkflowResumedEvent(sampleWorkflowExecution);
    }
    
    @Test
    void cancelWorkflowExecution_WhenRunning_ShouldCancelAndPublishEvent() {
        // Arrange
        Long id = 1L;
        sampleWorkflowExecution.setStatus(WorkflowStatus.RUNNING);
        
        when(workflowExecutionRepository.findById(id)).thenReturn(Optional.of(sampleWorkflowExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class))).thenReturn(sampleWorkflowExecution);
        
        // Act
        WorkflowExecution result = workflowExecutionService.cancelWorkflowExecution(id);
        
        // Assert
        assertEquals(WorkflowStatus.CANCELLED, result.getStatus());
        assertNotNull(result.getCompletedAt());
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).save(sampleWorkflowExecution);
        verify(eventPublisherService).publishWorkflowCancelledEvent(sampleWorkflowExecution);
    }
    
    @Test
    void retryWorkflowExecution_WhenFailed_ShouldRetryAndStartEngine() {
        // Arrange
        Long id = 1L;
        sampleWorkflowExecution.setStatus(WorkflowStatus.FAILED);
        sampleWorkflowExecution.setRetryCount(0);
        
        when(workflowExecutionRepository.findById(id)).thenReturn(Optional.of(sampleWorkflowExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class))).thenReturn(sampleWorkflowExecution);
        doNothing().when(workflowEngine).executeWorkflow(id);
        
        // Act
        WorkflowExecution result = workflowExecutionService.retryWorkflowExecution(id);
        
        // Assert
        assertEquals(WorkflowStatus.RUNNING, result.getStatus());
        assertEquals(1, result.getRetryCount());
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).save(sampleWorkflowExecution);
        verify(workflowEngine).executeWorkflow(id);
        verify(eventPublisherService).publishWorkflowRetryEvent(sampleWorkflowExecution);
    }
    
    @Test
    void getWorkflowExecutionsByStatus_ShouldReturnMatchingExecutions() {
        // Arrange
        WorkflowStatus status = WorkflowStatus.RUNNING;
        when(workflowExecutionRepository.findByStatus(status))
                .thenReturn(List.of(sampleWorkflowExecution));
        
        // Act
        List<WorkflowExecution> results = workflowExecutionService.getWorkflowExecutionsByStatus(status);
        
        // Assert
        assertEquals(1, results.size());
        assertEquals(sampleWorkflowExecution, results.get(0));
        verify(workflowExecutionRepository).findByStatus(status);
    }
    
    @Test
    void findCompletedWorkflowsOlderThan_ShouldReturnOldWorkflows() {
        // Arrange
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        
        WorkflowExecution oldExecution = new WorkflowExecution();
        oldExecution.setId(2L);
        oldExecution.setStatus(WorkflowStatus.COMPLETED);
        oldExecution.setCompletedAt(LocalDateTime.now().minusDays(45));
        
        when(workflowExecutionRepository.findByStatusIn(anyList()))
                .thenReturn(List.of(sampleWorkflowExecution, oldExecution));
        
        // Act
        List<WorkflowExecution> results = workflowExecutionService.findCompletedWorkflowsOlderThan(threshold);
        
        // Assert
        assertEquals(1, results.size());
        assertEquals(2L, results.get(0).getId());
        verify(workflowExecutionRepository).findByStatusIn(anyList());
    }
    
    @Test
    void deleteWorkflowExecution_WithCompletedWorkflow_ShouldDelete() {
        // Arrange
        Long id = 1L;
        sampleWorkflowExecution.setStatus(WorkflowStatus.COMPLETED);
        
        when(workflowExecutionRepository.findById(id)).thenReturn(Optional.of(sampleWorkflowExecution));
        doNothing().when(workflowExecutionRepository).deleteById(id);
        
        // Act
        workflowExecutionService.deleteWorkflowExecution(id);
        
        // Assert
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository).deleteById(id);
    }
    
    @Test
    void deleteWorkflowExecution_WithActiveWorkflow_ShouldThrowException() {
        // Arrange
        Long id = 1L;
        sampleWorkflowExecution.setStatus(WorkflowStatus.RUNNING);
        
        when(workflowExecutionRepository.findById(id)).thenReturn(Optional.of(sampleWorkflowExecution));
        
        // Act & Assert
        WorkflowException exception = assertThrows(WorkflowException.class, () -> {
            workflowExecutionService.deleteWorkflowExecution(id);
        });
        
        assertTrue(exception.getMessage().contains("Cannot delete workflow that is not in a terminal state"));
        verify(workflowExecutionRepository).findById(id);
        verify(workflowExecutionRepository, never()).deleteById(id);
    }
}
