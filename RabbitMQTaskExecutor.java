package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.messaging.RabbitMQSender;
import com.example.workfloworchestrator.messaging.TaskMessage;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Task executor that delegates execution to external services via RabbitMQ
 * Sends task messages and handles responses asynchronously
 */
@Slf4j
@Component
public class RabbitMQTaskExecutor extends AbstractTaskExecutor {
    
    private static final String TASK_TYPE = "rabbitmq";
    
    private final RabbitMQSender rabbitMQSender;
    private final Map<String, CompletableFuture<Map<String, Object>>> pendingResponses = new ConcurrentHashMap<>();
    
    public RabbitMQTaskExecutor(RabbitMQSender rabbitMQSender) {
        this.rabbitMQSender = rabbitMQSender;
    }
    
    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }
    
    @Override
    protected void validateTaskConfig(TaskDefinition taskDefinition) {
        validateTaskConfig(taskDefinition, "exchange", "routingKey");
    }
    
    @Override
    protected Map<String, Object> doExecute(TaskDefinition taskDefinition, ExecutionContext context) 
            throws Exception {
        
        // Get processed configuration
        Map<String, String> config = processConfigVariables(taskDefinition.getConfiguration(), context);
        
        // Extract configuration parameters
        String exchange = getRequiredConfig(config, "exchange");
        String routingKey = getRequiredConfig(config, "routingKey");
        
        // Default timeout (in seconds)
        int timeoutSeconds = Integer.parseInt(getOptionalConfig(config, "timeoutSeconds", "60"));
        
        // Create correlation ID for tracking the message
        String correlationId = UUID.randomUUID().toString();
        
        // Create message payload from context variables
        Map<String, String> payload = new HashMap<>();
        for (Map.Entry<String, Object> entry : context.getAllVariables().entrySet()) {
            if (entry.getValue() != null) {
                payload.put(entry.getKey(), entry.getValue().toString());
            }
        }
        
        // Create task message
        TaskMessage taskMessage = new TaskMessage();
        taskMessage.setCorrelationId(correlationId);
        taskMessage.setTaskType(getTaskType());
        taskMessage.setInputs(payload);
        taskMessage.setConfiguration(config);
        
        // Create future for handling response
        CompletableFuture<Map<String, Object>> responseFuture = new CompletableFuture<>();
        pendingResponses.put(correlationId, responseFuture);
        
        try {
            // Send message
            log.debug("Sending RabbitMQ task message with correlationId: {}", correlationId);
            rabbitMQSender.sendMessage(exchange, routingKey, taskMessage);
            
            // Wait for response with timeout
            return responseFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TaskExecutionException("RabbitMQ task timed out after " + timeoutSeconds + " seconds");
        } finally {
            // Clean up regardless of success or failure
            pendingResponses.remove(correlationId);
        }
    }
    
    /**
     * Handle response received from RabbitMQ
     * Completes the corresponding future to resume task execution
     * 
     * @param correlationId the correlation ID of the message
     * @param response the response data
     */
    public void handleResponse(String correlationId, Map<String, Object> response) {
        CompletableFuture<Map<String, Object>> future = pendingResponses.get(correlationId);
        
        if (future != null) {
            log.debug("Received RabbitMQ response for correlationId: {}", correlationId);
            future.complete(response);
        } else {
            log.warn("Received response for unknown correlation ID: {}", correlationId);
        }
    }
    
    @Override
    protected Map<String, Object> postProcessResult(Map<String, Object> result, ExecutionContext context) {
        // Add execution metadata if not present
        if (result != null && !result.containsKey("executedVia")) {
            result.put("executedVia", "rabbitmq");
        }
        
        return super.postProcessResult(result, context);
    }
}
