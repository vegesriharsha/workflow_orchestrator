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
    
    private WorkflowDefinition sampleWorkflowDefinition;
    private List<TaskDefinition> sampleTasks;
    
    @BeforeEach
    void setUp() {
        // Create sample task list
        sampleTasks = new ArrayList<>();
        
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
        
        sampleTasks.add(task1);
        sampleTasks.add(task2);
        
        // Create sample workflow definition
        sampleWorkflowDefinition = new WorkflowDefinition();
        sampleWorkflowDefinition.setId(1L);
        sampleWorkflowDefinition.setName("test-workflow");
        sampleWorkflowDefinition.setDescription("Test workflow");
        sampleWorkflowDefinition.setVersion("1.0.0");
        sampleWorkflowDefinition.setCreatedAt(LocalDateTime.now());
        sampleWorkflowDefinition.setTasks(sampleTasks);
        sampleWorkflowDefinition.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
    }
    
    @Test
    void getAllWorkflowDefinitions_ShouldReturnAllWorkflows() {
        // Arrange
        List<WorkflowDefinition> workflows = List.of(sampleWorkflowDefinition);
        when(workflowDefinitionRepository.findAll()).thenReturn(workflows);
        
        // Act
        List<WorkflowDefinition> result = workflowService.getAllWorkflowDefinitions();
        
        // Assert
        assertEquals(1, result.size());
        assertEquals("test-workflow", result.get(0).getName());
        assertEquals("1.0.0", result.get(0).getVersion());
        assertEquals(2, result.get(0).getTasks().size());
        verify(workflowDefinitionRepository).findAll();
    }
    
    @Test
    void getWorkflowDefinition_WithValidId_ShouldReturnWorkflow() {
        // Arrange
        when(workflowDefinitionRepository.findById(1L)).thenReturn(Optional.of(sampleWorkflowDefinition));
        
        // Act
        Optional<WorkflowDefinition> result = workflowService.getWorkflowDefinition(1L);
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals("test-workflow", result.get().getName());
        assertEquals("1.0.0", result.get().getVersion());
        verify(workflowDefinitionRepository).findById(1L);
    }
    
    @Test
    void getWorkflowDefinition_WithInvalidId_ShouldReturnEmpty() {
        // Arrange
        when(workflowDefinitionRepository.findById(999L)).thenReturn(Optional.empty());
        
        // Act
        Optional<WorkflowDefinition> result = workflowService.getWorkflowDefinition(999L);
        
        // Assert
        assertFalse(result.isPresent());
        verify(workflowDefinitionRepository).findById(999L);
    }
    
    @Test
    void getLatestWorkflowDefinition_ShouldReturnLatestVersion() {
        // Arrange
        when(workflowDefinitionRepository.findFirstByNameOrderByCreatedAtDesc("test-workflow"))
                .thenReturn(Optional.of(sampleWorkflowDefinition));
        
        // Act
        Optional<WorkflowDefinition> result = workflowService.getLatestWorkflowDefinition("test-workflow");
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals("test-workflow", result.get().getName());
        assertEquals("1.0.0", result.get().getVersion());
        verify(workflowDefinitionRepository).findFirstByNameOrderByCreatedAtDesc("test-workflow");
    }
    
    @Test
    void getWorkflowDefinitionByNameAndVersion_ShouldReturnMatchingWorkflow() {
        // Arrange
        when(workflowDefinitionRepository.findByNameAndVersion("test-workflow", "1.0.0"))
                .thenReturn(Optional.of(sampleWorkflowDefinition));
        
        // Act
        Optional<WorkflowDefinition> result = workflowService.getWorkflowDefinition("test-workflow", "1.0.0");
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals("test-workflow", result.get().getName());
        assertEquals("1.0.0", result.get().getVersion());
        verify(workflowDefinitionRepository).findByNameAndVersion("test-workflow", "1.0.0");
    }
    
    @Test
    void createWorkflowDefinition_WithNoVersion_ShouldGenerateVersionAndSave() {
        // Arrange
        WorkflowDefinition newWorkflow = new WorkflowDefinition();
        newWorkflow.setName("new-workflow");
        newWorkflow.setDescription("New workflow");
        newWorkflow.setTasks(new ArrayList<>());
        
        when(workflowVersioning.generateNextVersion("new-workflow")).thenReturn("1.0.0");
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class))).thenReturn(newWorkflow);
        
        // Act
        WorkflowDefinition result = workflowService.createWorkflowDefinition(newWorkflow);
        
        // Assert
        assertEquals("new-workflow", result.getName());
        assertEquals("1.0.0", result.getVersion());
        assertNotNull(result.getCreatedAt());
        verify(workflowVersioning).generateNextVersion("new-workflow");
        verify(workflowDefinitionRepository).save(any(WorkflowDefinition.class));
    }
    
    @Test
    void updateWorkflowDefinition_ShouldCreateNewVersion() {
        // Arrange
        WorkflowDefinition updatedWorkflow = new WorkflowDefinition();
        updatedWorkflow.setDescription("Updated description");
        updatedWorkflow.setTasks(new ArrayList<>());
        updatedWorkflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.PARALLEL);
        
        when(workflowDefinitionRepository.findById(1L)).thenReturn(Optional.of(sampleWorkflowDefinition));
        when(workflowVersioning.generateNextVersion("test-workflow")).thenReturn("1.0.1");
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class))).thenAnswer(i -> i.getArguments()[0]);
        
        // Act
        WorkflowDefinition result = workflowService.updateWorkflowDefinition(1L, updatedWorkflow);
        
        // Assert
        assertEquals("test-workflow", result.getName());
        assertEquals("1.0.1", result.getVersion());
        assertEquals("Updated description", result.getDescription());
        assertEquals(WorkflowDefinition.ExecutionStrategyType.PARALLEL, result.getStrategyType());
        assertNotNull(result.getCreatedAt());
        verify(workflowDefinitionRepository).findById(1L);
        verify(workflowVersioning).generateNextVersion("test-workflow");
        verify(workflowDefinitionRepository).save(any(WorkflowDefinition.class));
    }
    
    @Test
    void deleteWorkflowDefinition_ShouldDeleteWorkflow() {
        // Arrange - Nothing needed
        
        // Act
        workflowService.deleteWorkflowDefinition(1L);
        
        // Assert
        verify(workflowDefinitionRepository).deleteById(1L);
    }
}
