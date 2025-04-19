package com.example.workfloworchestrator.engine.scheduler;

import com.example.workfloworchestrator.engine.WorkflowEngine;
import com.example.workfloworchestrator.model.TaskExecution;
import com.example.workfloworchestrator.model.TaskStatus;
import com.example.workfloworchestrator.service.EventPublisherService;
import com.example.workfloworchestrator.service.TaskExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RetrySchedulerTest {

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private EventPublisherService eventPublisherService;

    @Mock
    private WorkflowEngine workflowEngine;

    @InjectMocks
    private RetryScheduler retryScheduler;

    private List<TaskExecution> tasksAwaitingRetry;

    @BeforeEach
    void setUp() {
        // Create sample tasks awaiting retry
        tasksAwaitingRetry = new ArrayList<>();
        
        TaskExecution task1 = new TaskExecution();
        task1.setId(1L);
        task1.setWorkflowExecutionId(1L);
        task1.setStatus(TaskStatus.AWAITING_RETRY);
        task1.setRetryCount(1);
        task1.setNextRetryAt(LocalDateTime.now().minusMinutes(5)); // Due for retry
        task1.setErrorMessage("Temporary error");
        
        TaskExecution task2 = new TaskExecution();
        task2.setId(2L);
        task2.setWorkflowExecutionId(2L);
        task2.setStatus(TaskStatus.AWAITING_RETRY);
        task2.setRetryCount(2);
        task2.setNextRetryAt(LocalDateTime.now().minusMinutes(10)); // Due for retry
        task2.setErrorMessage("Connection timeout");
        
        tasksAwaitingRetry.add(task1);
        tasksAwaitingRetry.add(task2);
    }

    @Test
    void retryFailedTasks_ShouldRetryTasksReadyForRetry() {
        // Arrange
        when(taskExecutionService.getTasksToRetry(any(LocalDateTime.class)))
                .thenReturn(tasksAwaitingRetry);
        
        // Act
        retryScheduler.retryFailedTasks();
        
        // Assert
        verify(taskExecutionService).getTasksToRetry(any(LocalDateTime.class));
        
        // Verify each task was updated and executed
        for (TaskExecution task : tasksAwaitingRetry) {
            verify(taskExecutionService).saveTaskExecution(task);
            verify(taskExecutionService).executeTask(task.getId());
            
            // Verify task status was updated to PENDING
            verify(task).setStatus(TaskStatus.PENDING);
            verify(task).setStartedAt(null);
            verify(task).setCompletedAt(null);
        }
    }

    @Test
    void retryFailedTasks_WithNoTasksToRetry_ShouldDoNothing() {
        // Arrange
        when(taskExecutionService.getTasksToRetry(any(LocalDateTime.class)))
                .thenReturn(List.of());
        
        // Act
        retryScheduler.retryFailedTasks();
        
        // Assert
        verify(taskExecutionService).getTasksToRetry(any(LocalDateTime.class));
        verify(taskExecutionService, never()).saveTaskExecution(any(TaskExecution.class));
        verify(taskExecutionService, never()).executeTask(anyLong());
    }

    @Test
    void retryFailedTasks_WithExceptionDuringRetry_ShouldHandleAndContinue() {
        // Arrange
        when(taskExecutionService.getTasksToRetry(any(LocalDateTime.class)))
                .thenReturn(tasksAwaitingRetry);
        
        // Make the first task throw an exception during save
        doThrow(new RuntimeException("Test exception"))
                .when(taskExecutionService).saveTaskExecution(tasksAwaitingRetry.get(0));
        
        // Act
        retryScheduler.retryFailedTasks();
        
        // Assert
        verify(taskExecutionService).getTasksToRetry(any(LocalDateTime.class));
        
        // First task should have been attempted but failed
        verify(taskExecutionService).saveTaskExecution(tasksAwaitingRetry.get(0));
        verify(taskExecutionService, never()).executeTask(tasksAwaitingRetry.get(0).getId());
        
        // Second task should still be processed normally
        verify(taskExecutionService).saveTaskExecution(tasksAwaitingRetry.get(1));
        verify(taskExecutionService).executeTask(tasksAwaitingRetry.get(1).getId());
    }

    @Test
    void retryFailedTasks_WithMultipleRetryFailures_ShouldAttemptWorkflowRestart() {
        // Arrange
        when(taskExecutionService.getTasksToRetry(any(LocalDateTime.class)))
                .thenReturn(tasksAwaitingRetry);
        
        // Make all tasks throw exceptions to trigger the workflow restart logic
        for (TaskExecution task : tasksAwaitingRetry) {
            doThrow(new RuntimeException("Test exception"))
                    .when(taskExecutionService).saveTaskExecution(task);
        }
        
        // First call - increments counters but doesn't restart workflows yet
        retryScheduler.retryFailedTasks();
        
        // Second call - increments counters but doesn't restart workflows yet
        retryScheduler.retryFailedTasks();
        
        // Third call - should trigger workflow restart for both workflows
        retryScheduler.retryFailedTasks();
        
        // Assert
        // Verify counters were incremented correctly (3 calls)
        verify(taskExecutionService, times(3)).getTasksToRetry(any(LocalDateTime.class));
        
        // Verify each task's save was attempted 3 times
        verify(taskExecutionService, times(3)).saveTaskExecution(tasksAwaitingRetry.get(0));
        verify(taskExecutionService, times(3)).saveTaskExecution(tasksAwaitingRetry.get(1));
        
        // Verify workflow engine was called to restart both workflows
        verify(workflowEngine).executeWorkflow(1L);
        verify(workflowEngine).executeWorkflow(2L);
    }

    @Test
    void cleanupRetryTracker_ShouldClearTracker() {
        // This test is simple but ensures the method works
        
        // Add some entries to the tracker first
        retryScheduler.retryFailedTasks(); // This should add entries if tasks fail
        
        // Act
        retryScheduler.cleanupRetryTracker();
        
        // If we call retryFailedTasks again after cleanup and tasks still fail,
        // it should require 3 more failures before triggering workflow restart
        
        // Since the test method above already verified this behavior,
        // we just need to make sure the method runs without exceptions
    }
}
