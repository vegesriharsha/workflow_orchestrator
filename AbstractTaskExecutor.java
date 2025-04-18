package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for task executors
 * Provides common functionality and template method pattern
 */
@Slf4j
public abstract class AbstractTaskExecutor implements TaskExecutor {
    
    /**
     * Concrete execute method implementing the template pattern
     * 
     * @param taskDefinition the task definition
     * @param context the execution context containing input variables
     * @return Map of output values
     * @throws TaskExecutionException if execution fails
     */
    @Override
    public final Map<String, Object> execute(TaskDefinition taskDefinition, ExecutionContext context) 
            throws TaskExecutionException {
        
        try {
            // Pre-execution phase
            validateTaskConfig(taskDefinition);
            preProcessContext(context);
            
            // Execute task
            log.debug("Executing task: {}, type: {}", taskDefinition.getName(), getTaskType());
            Map<String, Object> result = doExecute(taskDefinition, context);
            
            // Post-execution phase
            result = postProcessResult(result, context);
            
            log.debug("Task execution completed: {}, type: {}", taskDefinition.getName(), getTaskType());
            return result;
            
        } catch (Exception e) {
            log.error("Error executing task: {}, type: {}", 
                    taskDefinition.getName(), getTaskType(), e);
            
            if (e instanceof TaskExecutionException) {
                throw (TaskExecutionException) e;
            } else {
                throw new TaskExecutionException("Task execution failed: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Template method to be implemented by concrete task executors
     * 
     * @param taskDefinition the task definition
     * @param context the execution context containing input variables
     * @return Map of output values
     * @throws Exception if execution fails
     */
    protected abstract Map<String, Object> doExecute(TaskDefinition taskDefinition, ExecutionContext context) 
            throws Exception;
    
    /**
     * Hook method to validate task configuration
     * Can be overridden by concrete implementations
     * 
     * @param taskDefinition the task definition
     * @throws IllegalArgumentException if configuration is invalid
     */
    protected void validateTaskConfig(TaskDefinition taskDefinition) throws IllegalArgumentException {
        // Default implementation - can be overridden
    }
    
    /**
     * Hook method to pre-process context before execution
     * Can be overridden by concrete implementations
     * 
     * @param context the execution context
     */
    protected void preProcessContext(ExecutionContext context) {
        // Default implementation - can be overridden
    }
    
    /**
     * Hook method to post-process result after execution
     * Can be overridden by concrete implementations
     * 
     * @param result the result from doExecute
     * @param context the execution context
     * @return the processed result
     */
    protected Map<String, Object> postProcessResult(Map<String, Object> result, ExecutionContext context) {
        return result != null ? result : new HashMap<>();
    }
    
    /**
     * Create a standard success result
     * 
     * @param data the result data
     * @return the success result map
     */
    protected Map<String, Object> createSuccessResult(Object data) {
        return prepareResult(true, data, null);
    }
    
    /**
     * Create a standard failure result
     * 
     * @param errorMessage the error message
     * @return the failure result map
     */
    protected Map<String, Object> createFailureResult(String errorMessage) {
        return prepareResult(false, null, errorMessage);
    }
    
    /**
     * Process all strings in the configuration with variables from context
     * 
     * @param config the configuration map
     * @param context the execution context
     * @return a new map with processed strings
     */
    protected Map<String, String> processConfigVariables(Map<String, String> config, ExecutionContext context) {
        Map<String, String> processedConfig = new HashMap<>();
        
        if (config != null && context != null) {
            for (Map.Entry<String, String> entry : config.entrySet()) {
                String value = entry.getValue();
                if (value != null) {
                    value = processVariables(value, context);
                }
                processedConfig.put(entry.getKey(), value);
            }
        }
        
        return processedConfig;
    }
    
    /**
     * Extract a JSON object from a string and put it in the context
     * 
     * @param jsonString the JSON string
     * @param variableName the variable name to store in context
     * @param context the execution context
     * @throws com.fasterxml.jackson.core.JsonProcessingException if JSON parsing fails
     */
    protected void extractJsonToContext(String jsonString, String variableName, ExecutionContext context) 
            throws com.fasterxml.jackson.core.JsonProcessingException {
        
        if (jsonString != null && !jsonString.isEmpty() && context != null) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Object jsonObject = mapper.readValue(jsonString, Object.class);
            context.setVariable(variableName, jsonObject);
        }
    }
}
