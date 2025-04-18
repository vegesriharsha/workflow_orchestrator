// Additional methods to be added to WorkflowExecutionService.java

@Transactional
public WorkflowExecution save(WorkflowExecution workflowExecution) {
    return workflowExecutionRepository.save(workflowExecution);
}

@Transactional(readOnly = true)
public List<WorkflowExecution> getAllWorkflowExecutions() {
    return workflowExecutionRepository.findAll();
}

@Transactional
public WorkflowExecution retryWorkflowExecutionSubset(Long workflowExecutionId, List<Long> taskIds) {
    WorkflowExecution workflowExecution = getWorkflowExecution(workflowExecutionId);
    
    if (workflowExecution.getStatus() == WorkflowStatus.FAILED ||
        workflowExecution.getStatus() == WorkflowStatus.PAUSED) {
        
        workflowExecution.setStatus(WorkflowStatus.RUNNING);
        workflowExecution.setRetryCount(workflowExecution.getRetryCount() + 1);
        workflowExecutionRepository.save(workflowExecution);
        
        // Execute subset of tasks
        workflowEngine.executeTaskSubset(workflowExecutionId, taskIds);
        
        eventPublisherService.publishWorkflowRetryEvent(workflowExecution);
    }
    
    return workflowExecution;
}
