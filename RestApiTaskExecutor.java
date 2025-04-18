package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Task executor for REST API calls
 * Supports GET, POST, PUT, DELETE, PATCH methods with JSON payloads
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestApiTaskExecutor extends AbstractTaskExecutor {
    
    private static final String TASK_TYPE = "rest-api";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }
    
    @Override
    protected void validateTaskConfig(TaskDefinition taskDefinition) {
        validateTaskConfig(taskDefinition, "url", "method");
    }
    
    @Override
    protected void preProcessContext(ExecutionContext context) {
        // Add default headers if not provided
        if (!context.hasVariable("headers")) {
            Map<String, String> defaultHeaders = new HashMap<>();
            defaultHeaders.put("Content-Type", "application/json");
            defaultHeaders.put("Accept", "application/json");
            context.setVariable("headers", defaultHeaders);
        }
    }
    
    @Override
    protected Map<String, Object> doExecute(TaskDefinition taskDefinition, ExecutionContext context) 
            throws Exception {
        
        // Get and process configuration
        Map<String, String> config = processConfigVariables(taskDefinition.getConfiguration(), context);
        
        // Extract required parameters
        String url = getRequiredConfig(config, "url");
        String method = getRequiredConfig(config, "method").toUpperCase();
        String requestBody = config.get("requestBody");
        
        // Create headers
        HttpHeaders headers = createHeaders(context);
        
        // Execute request based on method
        ResponseEntity<String> response = executeRequest(url, method, requestBody, headers);
        
        // Process response
        return processResponse(response, context);
    }
    
    /**
     * Create HTTP headers from context
     * 
     * @param context the execution context
     * @return HTTP headers
     */
    private HttpHeaders createHeaders(ExecutionContext context) {
        HttpHeaders headers = new HttpHeaders();
        
        // Add default content type
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Add headers from context
        Object headersObj = context.getVariable("headers");
        if (headersObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> headersMap = (Map<String, String>) headersObj;
            
            for (Map.Entry<String, String> entry : headersMap.entrySet()) {
                headers.add(entry.getKey(), entry.getValue());
            }
        }
        
        return headers;
    }
    
    /**
     * Execute HTTP request
     * 
     * @param url the URL
     * @param method the HTTP method
     * @param requestBody the request body (optional)
     * @param headers the HTTP headers
     * @return response entity
     */
    private ResponseEntity<String> executeRequest(String url, String method, String requestBody, HttpHeaders headers) {
        HttpMethod httpMethod = HttpMethod.valueOf(method);
        
        // Create request entity with body if applicable
        HttpEntity<String> requestEntity;
        if (requestBody != null && !requestBody.isEmpty() &&
                (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT || httpMethod == HttpMethod.PATCH)) {
            requestEntity = new HttpEntity<>(requestBody, headers);
        } else {
            requestEntity = new HttpEntity<>(headers);
        }
        
        try {
            return restTemplate.exchange(url, httpMethod, requestEntity, String.class);
        } catch (HttpStatusCodeException e) {
            // Capture the response body even when status code is an error
            return ResponseEntity.status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsString());
        }
    }
    
    /**
     * Process HTTP response
     * 
     * @param response the response entity
     * @param context the execution context
     * @return result map
     */
    private Map<String, Object> processResponse(ResponseEntity<String> response, ExecutionContext context) 
            throws JsonProcessingException {
        
        // Extract response details
        int statusCode = response.getStatusCodeValue();
        String responseBody = response.getBody();
        HttpHeaders responseHeaders = response.getHeaders();
        
        // Create result map
        Map<String, Object> result = new HashMap<>();
        result.put("statusCode", statusCode);
        result.put("responseBody", responseBody);
        
        // Convert headers to map
        Map<String, String> headersMap = new HashMap<>();
        responseHeaders.forEach((name, values) -> {
            if (!values.isEmpty()) {
                headersMap.put(name, String.join(", ", values));
            }
        });
        result.put("responseHeaders", headersMap);
        
        // Determine success
        boolean isSuccess = statusCode >= 200 && statusCode < 300;
        result.put("success", isSuccess);
        
        if (!isSuccess) {
            result.put("errorMessage", "HTTP error: " + statusCode);
        }
        
        // Parse JSON response if applicable
        if (responseBody != null && !responseBody.isEmpty() && 
                responseHeaders.getContentType() != null && 
                responseHeaders.getContentType().includes(MediaType.APPLICATION_JSON)) {
            try {
                extractJsonToContext(responseBody, "parsedResponse", context);
                Object parsedResponse = context.getVariable("parsedResponse");
                result.put("parsedResponse", parsedResponse);
            } catch (Exception e) {
                log.warn("Failed to parse JSON response: {}", e.getMessage());
            }
        }
        
        return result;
    }
    
    @Override
    protected Map<String, Object> postProcessResult(Map<String, Object> result, ExecutionContext context) {
        // Add execution timestamp
        result.put("executionTimestamp", System.currentTimeMillis());
        
        // Log response summary
        Integer statusCode = (Integer) result.get("statusCode");
        Boolean success = (Boolean) result.get("success");
        
        log.info("REST API response: statusCode={}, success={}", statusCode, success);
        
        return result;
    }
}
