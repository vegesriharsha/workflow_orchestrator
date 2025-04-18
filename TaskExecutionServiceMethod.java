// Additional methods to be added to TaskExecutionService.java

@Transactional
public TaskExecution saveTaskExecution(TaskExecution taskExecution) {
    return taskExecutionRepository.save(taskExecution);
}

@Transactional
public UserReviewPoint createUserReviewPoint(Long taskExecutionId) {
    TaskExecution taskExecution = getTaskExecution(taskExecutionId);
    
    // Create review point
    UserReviewPoint reviewPoint = new UserReviewPoint();
    reviewPoint.setTaskExecutionId(taskExecutionId);
    reviewPoint.setCreatedAt(LocalDateTime.now());
    
    // Save through the related workflow execution
    WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(
            taskExecution.getWorkflowExecutionId());
    workflowExecution.getReviewPoints().add(reviewPoint);
    workflowExecutionService.save(workflowExecution);
    
    // Publish event
    eventPublisherService.publishUserReviewRequestedEvent(workflowExecution, reviewPoint);
    
    return reviewPoint;
}
