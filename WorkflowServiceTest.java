package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.model.TaskDefinition;
import com.example.workfloworchestrator.model.WorkflowDefinition;
import com.example.workfloworchestrator.repository.WorkflowDefinitionRepository;
import com.example.workfloworchestrator.util.WorkflowVersioning;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkflowServiceTest {
    
    @Mock
    private WorkflowDefinitionRepository workflowDefinitionRepository;
    
    @Mock
    private WorkflowVersioning workflowVersioning;
    
    @InjectMocks
    private WorkflowService workflowService;
    
    private WorkflowDefinition sampleWorkflow;
    
    @BeforeEach
    void setUp() {
        sampleWorkflow = new WorkflowDefinition();
        sampleWorkflow.setId(1L);
        sampleWorkflow.setName("test-workflow");
        sampleWorkflow.setDescription("Test workflow");
        sampleWorkflow.setVersion("1.0.0");
        sampleWorkflow.setCreatedAt(LocalDateTime.now());
        sampleWorkflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        
        TaskDefinition task = new TaskDefinition();
        task.setId(1L);
        task.setName("test-task");
        task.setType("http");
        task.setExecutionOrder(0);
        
        List<TaskDefinition> tasks = new ArrayList<>();
        tasks.add(task);
        sampleWorkflow.setTasks(tasks);
    }
    
    @Test
    void testGetAllWorkflowDefinitions() {
        List<WorkflowDefinition> workflows = List.of(sampleWorkflow);
        when(workflowDefinitionRepository.findAll()).thenReturn(workflows);
        
        List<WorkflowDefinition> result = workflowService.getAllWorkflowDefinitions();
        
        assertEquals(1, result.size());
        assertEquals("test-workflow", result.get(0).getName());
        verify(workflowDefinitionRepository).findAll();
    }
    
    @Test
    void testGetWorkflowDefinitionById() {
        when(workflowDefinitionRepository.findById(1L)).thenReturn(Optional.of(sampleWorkflow));
        
        Optional<WorkflowDefinition> result = workflowService.getWorkflowDefinition(1L);
        
        assertTrue(result.isPresent());
        assertEquals("test-workflow", result.get().getName());
        verify(workflowDefinitionRepository).findById(1L);
    }
    
    @Test
    void testGetLatestWorkflowDefinition() {
        when(workflowDefinitionRepository.findFirstByNameOrderByCreatedAtDesc("test-workflow"))
                .thenReturn(Optional.of(sampleWorkflow));
        
        Optional<WorkflowDefinition> result = workflowService.getLatestWorkflowDefinition("test-workflow");
        
        assertTrue(result.isPresent());
        assertEquals("1.0.0", result.get().getVersion());
        verify(workflowDefinitionRepository).findFirstByNameOrderByCreatedAtDesc("test-workflow");
    }
    
    @Test
    void testCreateWorkflowDefinition() {
        WorkflowDefinition newWorkflow = new WorkflowDefinition();
        newWorkflow.setName("new-workflow");
        newWorkflow.setDescription("New workflow");
        newWorkflow.setTasks(new ArrayList<>());
        
        when(workflowVersioning.generateNextVersion("new-workflow")).thenReturn("1.0.0");
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class))).thenReturn(newWorkflow);
        
        WorkflowDefinition result = workflowService.createWorkflowDefinition(newWorkflow);
        
        assertEquals("new-workflow", result.getName());
        assertEquals("1.0.0", result.getVersion());
        assertNotNull(result.getCreatedAt());
        verify(workflowVersioning).generateNextVersion("new-workflow");
        verify(workflowDefinitionRepository).save(any(WorkflowDefinition.class));
    }
    
    @Test
    void testUpdateWorkflowDefinition() {
        WorkflowDefinition updatedWorkflow = new WorkflowDefinition();
        updatedWorkflow.setDescription("Updated description");
        updatedWorkflow.setTasks(new ArrayList<>());
        updatedWorkflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.PARALLEL);
        
        when(workflowDefinitionRepository.findById(1L)).thenReturn(Optional.of(sampleWorkflow));
        when(workflowVersioning.generateNextVersion("test-workflow")).thenReturn("1.0.1");
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class))).thenAnswer(i -> i.getArguments()[0]);
        
        WorkflowDefinition result = workflowService.updateWorkflowDefinition(1L, updatedWorkflow);
        
        assertEquals("test-workflow", result.getName());
        assertEquals("1.0.1", result.getVersion());
        assertEquals("Updated description", result.getDescription());
        assertEquals(WorkflowDefinition.ExecutionStrategyType.PARALLEL, result.getStrategyType());
        verify(workflowDefinitionRepository).findById(1L);
        verify(workflowVersioning).generateNextVersion("test-workflow");
        verify(workflowDefinitionRepository).save(any(WorkflowDefinition.class));
    }
    
    @Test
    void testDeleteWorkflowDefinition() {
        workflowService.deleteWorkflowDefinition(1L);
        verify(workflowDefinitionRepository).deleteById(1L);
    }
}
