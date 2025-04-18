# Workflow Orchestrator

A robust, flexible workflow orchestrator built with Spring Boot, Java 21, Gradle, and RabbitMQ.

## Overview

This workflow orchestrator provides a comprehensive framework for defining, executing, and managing complex business workflows. It enables the orchestration of different types of tasks with various execution strategies, supports human review points, and offers flexible retry and error handling capabilities.

## Architecture

![Workflow Orchestrator Architecture](https://i.imgur.com/placeholder_for_architecture_diagram.png)

### Key Components

#### 1. Workflow Definition Model

The workflow definition model consists of a hierarchical structure:

- **WorkflowDefinition**: Defines a workflow with its metadata and collection of tasks
- **TaskDefinition**: Defines individual tasks within a workflow with configuration and execution parameters
- **ExecutionStrategy**: Determines how tasks are executed (sequentially, in parallel, or conditionally)

```java
// Sample workflow definition structure
WorkflowDefinition workflow = new WorkflowDefinition();
workflow.setName("order-processing");
workflow.setDescription("Process customer orders");
workflow.setStrategyType(ExecutionStrategyType.SEQUENTIAL);
```

#### 2. Workflow Engine

The engine is the core component that orchestrates workflow execution:

- Manages workflow state transitions
- Coordinates task execution based on the selected strategy
- Handles errors and failure scenarios
- Provides mechanisms to pause, resume, and cancel workflows

```java
// Executing a workflow
workflowEngine.executeWorkflow(workflowExecution.getId());

// Executing a subset of tasks
workflowEngine.executeTaskSubset(workflowExecutionId, taskIds);
```

#### 3. Task Execution Framework

The task execution framework provides a pluggable mechanism for executing different types of tasks:

- **TaskExecutor Interface**: Common interface for all task executors
- **ExecutionContext**: Passes data between tasks in a workflow
- **Task Types**: HTTP, Database, RabbitMQ, and Custom task executors

```java
// Task executor interface
public interface TaskExecutor {
    Map<String, Object> execute(TaskDefinition taskDefinition, ExecutionContext context) throws TaskExecutionException;
    String getTaskType();
}
```

#### 4. Execution Strategies

Multiple execution strategies are supported:

- **SequentialExecutionStrategy**: Executes tasks one after another
- **ParallelExecutionStrategy**: Executes independent tasks concurrently
- **ConditionalExecutionStrategy**: Determines task execution based on conditions

#### 5. State Management

Workflows and tasks have well-defined states:

- **WorkflowStatus**: CREATED, RUNNING, PAUSED, AWAITING_USER_REVIEW, COMPLETED, FAILED, CANCELLED
- **TaskStatus**: PENDING, RUNNING, COMPLETED, FAILED, SKIPPED, CANCELLED, AWAITING_RETRY

#### 6. User Review Points

The orchestrator supports human-in-the-loop workflows:

- Workflow can pause at designated points for user review
- Users can approve, reject, or request a restart of tasks
- Review decisions influence the workflow path

```java
// Task definition with user review
taskDefinition.setRequireUserReview(true);

// Submitting a user review
userReviewService.submitUserReview(
    reviewPointId, 
    ReviewDecision.APPROVE,
    "john.doe", 
    "Looks good, approved."
);
```

#### 7. Messaging Integration

RabbitMQ integration for:

- Asynchronous task execution
- Communication with external systems
- Event publishing

```java
// Send a task message
rabbitMQSender.sendTaskMessage(taskMessage);

// Listener for task results
@RabbitListener(queues = RabbitMQConfig.WORKFLOW_RESULT_QUEUE)
public void receiveTaskResult(TaskMessage resultMessage) { ... }
```

#### 8. REST API

A comprehensive REST API for:

- Creating and managing workflow definitions
- Starting and controlling workflow executions
- Handling user review points

## Key Programming Concepts

### 1. Domain-Driven Design

The codebase follows Domain-Driven Design principles:

- **Aggregates**: WorkflowDefinition and WorkflowExecution are aggregate roots
- **Value Objects**: TaskDefinition, TaskExecution, etc.
- **Repository Pattern**: Separate repositories for workflow definitions and executions
- **Services**: Domain services for workflow and task operations

### 2. Asynchronous Programming

Extensive use of CompletableFuture for asynchronous operations:

```java
CompletableFuture<WorkflowStatus> futureStatus = strategy.execute(workflowExecution);

futureStatus.thenAccept(status -> {
    // Handle workflow completion
    workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, status);
});
```

### 3. Strategy Pattern

The Strategy pattern is used for execution strategies:

```java
public interface ExecutionStrategy {
    CompletableFuture<WorkflowStatus> execute(WorkflowExecution workflowExecution);
    CompletableFuture<WorkflowStatus> executeSubset(WorkflowExecution workflowExecution, List<Long> taskIds);
}
```

### 4. Observer Pattern

Event publishing follows the Observer pattern:

```java
// Publishing events
eventPublisherService.publishWorkflowStartedEvent(workflowExecution);
eventPublisherService.publishTaskCompletedEvent(taskExecution);
```

### 5. Factory Pattern

Task executors are created based on task type:

```java
private TaskExecutor getTaskExecutor(String taskType) {
    return Optional.ofNullable(taskExecutors.get(taskType))
            .orElseThrow(() -> new TaskExecutionException("No executor found for task type: " + taskType));
}
```

### 6. Dependency Injection

Spring's dependency injection is used throughout the codebase:

```java
@RequiredArgsConstructor
public class WorkflowEngine {
    private final WorkflowExecutionService workflowExecutionService;
    private final TaskExecutionService taskExecutionService;
    private final EventPublisherService eventPublisherService;
    // ...
}
```

### 7. Transactional Boundaries

Spring's @Transactional annotation is used to maintain data consistency:

```java
@Transactional
public WorkflowExecution startWorkflow(String workflowName, String version, Map<String, String> variables) {
    // ...
}
```

### 8. Retry with Exponential Backoff

Sophisticated retry mechanism with exponential backoff:

```java
public long calculateExponentialBackoff(int retryCount) {
    double exponentialPart = Math.pow(multiplier, retryCount);
    long delay = (long) (initialIntervalMs * exponentialPart);
    
    // Add jitter to avoid thundering herd
    double randomFactor = 1.0 + Math.random() * 0.25;
    delay = (long) (delay * randomFactor);
    
    return Math.min(delay, maxIntervalMs);
}
```

### 9. Semantic Versioning

Workflow definitions follow semantic versioning:

```java
public String generateNextVersion(String workflowName) {
    // Logic to generate next version number (e.g., 1.0.0 to 1.0.1)
}
```

## Getting Started

### Prerequisites

- JDK 21 or higher
- PostgreSQL database
- RabbitMQ server

### Building the Project

```bash
./gradlew clean build
```

### Configuration

Configure the application in `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/workflow_db
    username: postgres
    password: postgres
  
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

workflow:
  task:
    execution:
      thread-pool-size: 10
    retry:
      max-attempts: 3
      initial-interval: 1000
      multiplier: 2.0
```

### Running the Application

```bash
./gradlew bootRun
```

## Usage Examples

### Creating a Workflow Definition

```java
WorkflowDefinition workflow = new WorkflowDefinition();
workflow.setName("document-approval");
workflow.setDescription("Document approval workflow");
workflow.setStrategyType(ExecutionStrategyType.SEQUENTIAL);

TaskDefinition submitTask = new TaskDefinition();
submitTask.setName("submit-document");
submitTask.setType("http");
submitTask.setExecutionOrder(0);
submitTask.getConfiguration().put("url", "https://api.example.com/documents");
submitTask.getConfiguration().put("method", "POST");

TaskDefinition reviewTask = new TaskDefinition();
reviewTask.setName("review-document");
reviewTask.setType("custom");
reviewTask.setExecutionOrder(1);
reviewTask.setRequireUserReview(true);

workflow.getTasks().add(submitTask);
workflow.getTasks().add(reviewTask);

workflowService.createWorkflowDefinition(workflow);
```

### Starting a Workflow Execution

```java
Map<String, String> variables = new HashMap<>();
variables.put("documentId", "12345");
variables.put("requestor", "john.doe");

workflowExecutionService.startWorkflow("document-approval", null, variables);
```

### Submitting a User Review

```java
userReviewService.submitUserReview(
    reviewPointId,
    UserReviewPoint.ReviewDecision.APPROVE,
    "jane.doe",
    "Document approved after review"
);
```

## Extending the System

### Adding a New Task Executor

1. Create a new implementation of the TaskExecutor interface:

```java
@Component
public class EmailTaskExecutor implements TaskExecutor {
    
    private static final String TASK_TYPE = "email";
    
    @Override
    public Map<String, Object> execute(TaskDefinition taskDefinition, ExecutionContext context) throws TaskExecutionException {
        // Send email implementation
        String to = taskDefinition.getConfiguration().get("to");
        String subject = taskDefinition.getConfiguration().get("subject");
        String body = taskDefinition.getConfiguration().get("body");
        
        // Process variables
        to = processVariables(to, context);
        subject = processVariables(subject, context);
        body = processVariables(body, context);
        
        // Send email logic
        // ...
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("sentTo", to);
        return result;
    }
    
    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }
    
    // Helper methods
    // ...
}
```

### Implementing a New Execution Strategy

Create a new implementation of the ExecutionStrategy interface:

```java
@Component
public class PrioritizedExecutionStrategy implements ExecutionStrategy {
    
    // Implementation
    // ...
    
    @Override
    public CompletableFuture<WorkflowStatus> execute(WorkflowExecution workflowExecution) {
        // Custom execution logic based on task priorities
        // ...
    }
    
    @Override
    public CompletableFuture<WorkflowStatus> executeSubset(WorkflowExecution workflowExecution, List<Long> taskIds) {
        // Custom subset execution logic
        // ...
    }
}
```

## Advanced Features

### Complex Workflow Patterns

The system supports complex workflow patterns:

- **Sequential Steps**: Tasks executed in a defined order
- **Parallel Processing**: Multiple tasks executed concurrently
- **Conditional Branches**: Based on task outcomes or workflow context
- **Error Handling Paths**: Special flows for handling errors
- **User Decision Points**: Human-in-the-loop decision making

### Event Handling and Integration

The workflow orchestrator can integrate with external systems through:

- Event publishing for workflow and task state changes
- RabbitMQ messaging for asynchronous communication
- Extensible event handlers for custom integrations

### Monitoring and Management

The system provides:

- REST API for monitoring workflow status
- Detection of stuck workflows
- Retry mechanisms for failed tasks
- Historical tracking of workflow executions

## Conclusion

This workflow orchestrator demonstrates advanced Java and Spring Boot concepts while providing a flexible framework for business process automation. The modular design allows for easy extension to meet specific business requirements, and the integration capabilities enable it to work within a larger enterprise ecosystem.

## License

[MIT License](LICENSE)
