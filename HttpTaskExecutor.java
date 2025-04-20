package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Task executor for HTTP requests
 * Supports GET, POST, PUT, DELETE, PATCH methods
 */
@Slf4j
@Component
public class HttpTaskExecutor extends AbstractTaskExecutor {
    
    private static final String TASK_TYPE = "http";
    
    private final ObjectMapper objectMapper;
    
    public HttpTaskExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * For testing purposes to allow mocking
     */
    protected CloseableHttpClient createHttpClient() {
        return HttpClients.createDefault();
    }
    
    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }
    
    @Override
    protected void validateTaskConfig(TaskDefinition taskDefinition) {
        validateTaskConfig(taskDefinition, "url", "method");
    }
    
    @Override
    protected Map<String, Object> doExecute(TaskDefinition taskDefinition, ExecutionContext context) throws Exception {
        Map<String, String> config = processConfigVariables(taskDefinition.getConfiguration(), context);
        
        // Extract required parameters
        String url = getRequiredConfig(config, "url");
        String method = getRequiredConfig(config, "method");
        
        // Optional configurations
        String requestBody = config.get("requestBody");
        String headersJson = config.get("headers");
        
        // Execute HTTP request
        Map<String, Object> result = executeHttpRequest(method, url, requestBody, headersJson, context);
        
        return result;
    }
    
    private Map<String, Object> executeHttpRequest(String method, String url, String requestBody, 
                                                  String headersJson, ExecutionContext context) throws IOException {
        
        try (CloseableHttpClient httpClient = createHttpClient()) {
            // Create HTTP request based on method
            HttpUriRequest request = createHttpRequest(method, url, requestBody);
            
            // Add headers if provided
            if (headersJson != null && !headersJson.isEmpty()) {
                Map<String, String> headers = objectMapper.readValue(headersJson, Map.class);
                
                // Process variables in header values
                Map<String, String> processedHeaders = new HashMap<>();
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    processedHeaders.put(entry.getKey(), processVariables(entry.getValue(), context));
                }
                
                // Add headers to request
                for (Map.Entry<String, String> header : processedHeaders.entrySet()) {
                    request.setHeader(header.getKey(), header.getValue());
                }
            }
            
            // Add default Content-Type header for POST, PUT, PATCH if not set
            if ((request instanceof HttpEntityEnclosingRequest) && request.getHeader("Content-Type") == null) {
                request.setHeader("Content-Type", "application/json");
            }
            
            // Add default Accept header if not set
            if (request.getHeader("Accept") == null) {
                request.setHeader("Accept", "application/json");
            }
            
            // Execute request
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                // Read response
                String responseBody = EntityUtils.toString(response.getEntity());
                int statusCode = response.getCode();
                
                // Prepare result
                Map<String, Object> result = new HashMap<>();
                result.put("statusCode", statusCode);
                result.put("responseBody", responseBody);
                
                // Add response headers to result
                Map<String, String> responseHeaders = new HashMap<>();
                response.getHeaders().forEach(header -> 
                    responseHeaders.put(header.getName(), header.getValue()));
                result.put("responseHeaders", responseHeaders);
                
                // Check if response is successful
                boolean isSuccess = statusCode >= 200 && statusCode < 300;
                result.put("success", isSuccess);
                
                if (!isSuccess) {
                    result.put("error", "HTTP error: " + statusCode);
                }
                
                // Try to parse JSON response if applicable
                if (responseBody != null && !responseBody.isEmpty() && 
                        response.getHeader("Content-Type") != null && 
                        response.getHeader("Content-Type").getValue().contains("application/json")) {
                    try {
                        Object parsedJson = objectMapper.readValue(responseBody, Object.class);
                        context.setVariable("parsedResponse", parsedJson);
                        result.put("parsedResponse", parsedJson);
                    } catch (Exception e) {
                        log.warn("Failed to parse response as JSON: {}", e.getMessage());
                    }
                }
                
                return result;
            }
        }
    }
    
    private HttpUriRequest createHttpRequest(String method, String url, String requestBody) {
        switch (method.toUpperCase()) {
            case "GET":
                return new HttpGet(url);
                
            case "POST":
                HttpPost httpPost = new HttpPost(url);
                if (requestBody != null && !requestBody.isEmpty()) {
                    httpPost.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
                }
                return httpPost;
                
            case "PUT":
                HttpPut httpPut = new HttpPut(url);
                if (requestBody != null && !requestBody.isEmpty()) {
                    httpPut.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
                }
                return httpPut;
                
            case "DELETE":
                return new HttpDelete(url);
                
            case "PATCH":
                HttpPatch httpPatch = new HttpPatch(url);
                if (requestBody != null && !requestBody.isEmpty()) {
                    httpPatch.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
                }
                return httpPatch;
                
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
    }
    
    @Override
    protected Map<String, Object> postProcessResult(Map<String, Object> result, ExecutionContext context) {
        // Add execution timestamp
        result.put("executionTimestamp", System.currentTimeMillis());
        
        // Log response summary
        if (result != null) {
            Integer statusCode = (Integer) result.get("statusCode");
            Boolean success = (Boolean) result.get("success");
            
            if (success != null && success) {
                log.info("HTTP request succeeded with status code: {}", statusCode);
            } else {
                log.warn("HTTP request failed with status code: {}", statusCode);
            }
        }
        
        return result;
    }
}
