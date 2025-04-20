package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Task executor for HTTP requests using Spring WebFlux
 * Supports GET, POST, PUT, DELETE, PATCH methods
 */
@Slf4j
@Component
public class HttpTaskExecutor extends AbstractTaskExecutor {
    
    private static final String TASK_TYPE = "http";
    
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    
    public HttpTaskExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        
        // Configure WebClient with increased memory buffer size for larger payloads
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)) // 2MB buffer
                .build();
        
        // Configure HTTP client with timeout
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60)); // 60 second timeout
        
        this.webClient = WebClient.builder()
                .exchangeStrategies(exchangeStrategies)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
    
    /**
     * For testing purposes to allow providing a mock WebClient
     */
    protected WebClient getWebClient() {
        return this.webClient;
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
        Integer timeoutSeconds = config.containsKey("timeoutSeconds") ? 
                Integer.parseInt(config.get("timeoutSeconds")) : 60;
        
        // Execute HTTP request
        Map<String, Object> result = executeHttpRequest(method, url, requestBody, headersJson, context, timeoutSeconds);
        
        return result;
    }
    
    private Map<String, Object> executeHttpRequest(String method, String url, String requestBody, 
                                                 String headersJson, ExecutionContext context, 
                                                 Integer timeoutSeconds) throws Exception {
        
        // Parse HTTP method
        HttpMethod httpMethod;
        try {
            httpMethod = HttpMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        
        // Parse headers
        Map<String, String> headerMap = new HashMap<>();
        if (headersJson != null && !headersJson.isEmpty()) {
            try {
                headerMap = objectMapper.readValue(headersJson, Map.class);
                
                // Process variables in header values
                for (Map.Entry<String, String> entry : new HashMap<>(headerMap).entrySet()) {
                    headerMap.put(entry.getKey(), processVariables(entry.getValue(), context));
                }
            } catch (Exception e) {
                log.warn("Failed to parse headers JSON: {}", e.getMessage());
            }
        }
        
        // Prepare the request
        WebClient.RequestBodySpec requestSpec = getWebClient()
                .method(httpMethod)
                .uri(url);
        
        // Add headers
        Consumer<HttpHeaders> headersConsumer = headers -> {
            for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                headers.add(entry.getKey(), entry.getValue());
            }
            
            // Add default headers if not explicitly set
            if (!headers.containsKey(HttpHeaders.CONTENT_TYPE) && 
                    (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT || httpMethod == HttpMethod.PATCH)) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
            
            if (!headers.containsKey(HttpHeaders.ACCEPT)) {
                headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            }
        };
        
        requestSpec = requestSpec.headers(headersConsumer);
        
        // Add request body if applicable
        WebClient.RequestHeadersSpec<?> headersSpec;
        if (requestBody != null && !requestBody.isEmpty() && 
                (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT || httpMethod == HttpMethod.PATCH)) {
            headersSpec = requestSpec.body(BodyInserters.fromValue(requestBody));
        } else {
            headersSpec = requestSpec;
        }
        
        try {
            // Execute request with timeout
            return headersSpec
                    .exchangeToMono(response -> {
                        // Collect response headers
                        HttpHeaders responseHeaders = response.headers().asHttpHeaders();
                        Map<String, List<String>> headersMultiMap = responseHeaders.toSingleValueMap().entrySet()
                                .stream()
                                .collect(Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue())));
                        
                        // Create result map
                        Map<String, Object> result = new HashMap<>();
                        result.put("statusCode", response.statusCode().value());
                        result.put("success", response.statusCode().is2xxSuccessful());
                        result.put("responseHeaders", headersMultiMap);
                        
                        if (!response.statusCode().is2xxSuccessful()) {
                            result.put("error", "HTTP error: " + response.statusCode().value());
                        }
                        
                        // Get response body
                        return response.bodyToMono(String.class)
                                .map(body -> {
                                    result.put("responseBody", body);
                                    
                                    // Try to parse JSON if applicable
                                    if (body != null && !body.isEmpty() && 
                                            responseHeaders.getContentType() != null && 
                                            responseHeaders.getContentType().includes(MediaType.APPLICATION_JSON)) {
                                        try {
                                            JsonNode jsonNode = objectMapper.readTree(body);
                                            context.setVariable("parsedResponse", jsonNode);
                                            result.put("parsedResponse", jsonNode);
                                        } catch (Exception e) {
                                            log.warn("Failed to parse response as JSON: {}", e.getMessage());
                                        }
                                    }
                                    
                                    return result;
                                })
                                .switchIfEmpty(Mono.just(result));
                    })
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
                    
        } catch (WebClientResponseException e) {
            // Handle WebClient-specific errors
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("statusCode", e.getStatusCode().value());
            errorResult.put("error", "HTTP error: " + e.getStatusCode().value());
            errorResult.put("responseBody", e.getResponseBodyAsString());
            
            // Parse headers
            Map<String, List<String>> headersMultiMap = e.getHeaders().toSingleValueMap().entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, h -> List.of(h.getValue())));
            errorResult.put("responseHeaders", headersMultiMap);
            
            return errorResult;
        } catch (Exception e) {
            // Handle any other exceptions
            log.error("Error executing HTTP request: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            
            throw new TaskExecutionException("Error executing HTTP request: " + e.getMessage(), e);
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
