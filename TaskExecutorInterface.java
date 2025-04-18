package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;

import java.util.Map;

/**
 * Interface for all task executors
 * Different task types will have different implementations
 */
public interface TaskExecutor {
    
    /**
     * Execute a task based on the definition and context
     * 
     * @param taskDefinition the task definition
     * @param context the execution context containing input variables
     * @return Map of output values
     * @throws TaskExecutionException if execution fails
     */
    Map<String, Object> execute(TaskDefinition taskDefinition, ExecutionContext context) throws TaskExecutionException;
    
    /**
     * Get the task type that this executor handles
     * 
     * @return the task type string
     */
    String getTaskType();
    
    /**
     * Helper method to get required configuration parameter
     * 
     * @param config the configuration map
     * @param key the parameter key
     * @return the parameter value
     * @throws IllegalArgumentException if parameter is missing
     */
    default String getRequiredConfig(Map<String, String> config, String key) {
        String value = config.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Required configuration parameter missing: " + key);
        }
        return value;
    }
    
    /**
     * Helper method to get optional configuration parameter
     * 
     * @param config the configuration map
     * @param key the parameter key
     * @param defaultValue the default value if parameter is missing
     * @return the parameter value or default if missing
     */
    default String getOptionalConfig(Map<String, String> config, String key, String defaultValue) {
        String value = config.get(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
    
    /**
     * Helper method to process variables in a string
     * Replaces ${varName} with values from the context
     * 
     * @param input the input string
     * @param context the execution context
     * @return the processed string
     */
    default String processVariables(String input, ExecutionContext context) {
        if (input == null || input.isEmpty() || context == null) {
            return input;
        }
        
        String result = input;
        Map<String, Object> variables = context.getAllVariables();
        
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            Object value = entry.getValue();
            
            if (value != null && result.contains(placeholder)) {
                result = result.replace(placeholder, value.toString());
            }
        }
        
        return result;
    }
    
    /**
     * Helper method to validate task configuration
     * 
     * @param taskDefinition the task definition
     * @param requiredParams array of required parameter keys
     * @throws IllegalArgumentException if any required parameter is missing
     */
    default void validateTaskConfig(TaskDefinition taskDefinition, String... requiredParams) {
        Map<String, String> config = taskDefinition.getConfiguration();
        
        if (config == null) {
            throw new IllegalArgumentException("Task configuration is null");
        }
        
        for (String param : requiredParams) {
            if (!config.containsKey(param) || config.get(param) == null || config.get(param).isEmpty()) {
                throw new IllegalArgumentException("Required task configuration parameter missing: " + param);
            }
        }
    }
    
    /**
     * Helper method to prepare a standard result map
     * 
     * @param success whether the task succeeded
     * @param data the result data
     * @param errorMessage optional error message
     * @return the result map
     */
    default Map<String, Object> prepareResult(boolean success, Object data, String errorMessage) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", success);
        
        if (data != null) {
            if (data instanceof Map) {
                // If data is already a map, merge it with the result
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = (Map<String, Object>) data;
                result.putAll(dataMap);
            } else {
                // Otherwise, add it as "data"
                result.put("data", data);
            }
        }
        
        if (!success && errorMessage != null && !errorMessage.isEmpty()) {
            result.put("errorMessage", errorMessage);
        }
        
        return result;
    }
}
