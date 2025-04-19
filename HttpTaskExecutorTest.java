package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HttpTaskExecutorTest {
    
    @Mock
    private CloseableHttpClient httpClient;
    
    @Mock
    private CloseableHttpResponse httpResponse;
    
    @Mock
    private HttpEntity httpEntity;
    
    @InjectMocks
    private HttpTaskExecutor httpTaskExecutor;
    
    @Captor
    private ArgumentCaptor<HttpUriRequest> requestCaptor;
    
    private TaskDefinition taskDefinition;
    private ExecutionContext context;
    private ObjectMapper objectMapper = new ObjectMapper();
    
    @BeforeEach
    void setUp() throws Exception {
        // Mock HTTP client to avoid real HTTP calls
        MockitoAnnotations.openMocks(this);
        
        // Create HTTP task definition
        taskDefinition = new TaskDefinition();
        taskDefinition.setId(1L);
        taskDefinition.setName("HTTP Task");
        taskDefinition.setType("http");
        
        // Create execution context
        context = new ExecutionContext();
        context.setVariable("param1", "value1");
        context.setVariable("param2", "value2");
        
        // Reset taskDefinition configuration for each test
        taskDefinition.setConfiguration(new HashMap<>());
        
        // Mock HTTP response
        when(httpResponse.getEntity()).thenReturn(httpEntity);
        when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream("{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8)));
        when(httpResponse.getCode()).thenReturn(200);
        
        // Mock HTTP client execution
        when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(httpResponse);
        
        // Set ObjectMapper in the executor
        httpTaskExecutor = new HttpTaskExecutor(objectMapper);
        
        // Use Mockito's spy to mock the HttpClients.createDefault() call
        httpTaskExecutor = spy(httpTaskExecutor);
        doReturn(httpClient).when(httpTaskExecutor).createHttpClient();
    }
    
    @Test
    void execute_GetRequest_ShouldExecuteSuccessfully() throws Exception {
        // Arrange
        Map<String, String> config = new HashMap<>();
        config.put("url", "https://example.com/api/resource");
        config.put("method", "GET");
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        verify(httpClient).execute(requestCaptor.capture());
        HttpUriRequest request = requestCaptor.getValue();
        
        assertTrue(request instanceof HttpGet);
        assertEquals("https://example.com/api/resource", request.getRequestUri().toString());
        assertEquals(true, result.get("success"));
        assertEquals(200, result.get("statusCode"));
        assertNotNull(result.get("responseBody"));
    }
    
    @Test
    void execute_PostRequest_ShouldSendRequestBody() throws Exception {
        // Arrange
        Map<String, String> config = new HashMap<>();
        config.put("url", "https://example.com/api/resource");
        config.put("method", "POST");
        config.put("requestBody", "{\"data\":\"${param1}\"}");
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        verify(httpClient).execute(requestCaptor.capture());
        HttpUriRequest request = requestCaptor.getValue();
        
        assertTrue(request instanceof HttpPost);
        HttpPost postRequest = (HttpPost) request;
        
        assertEquals("https://example.com/api/resource", request.getRequestUri().toString());
        assertNotNull(postRequest.getEntity());
        assertEquals(true, result.get("success"));
        assertEquals(200, result.get("statusCode"));
    }
    
    @Test
    void execute_PutRequest_ShouldSendRequestBody() throws Exception {
        // Arrange
        Map<String, String> config = new HashMap<>();
        config.put("url", "https://example.com/api/resource/1");
        config.put("method", "PUT");
        config.put("requestBody", "{\"data\":\"${param1}\"}");
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        verify(httpClient).execute(requestCaptor.capture());
        HttpUriRequest request = requestCaptor.getValue();
        
        assertTrue(request instanceof HttpPut);
        HttpPut putRequest = (HttpPut) request;
        
        assertEquals("https://example.com/api/resource/1", request.getRequestUri().toString());
        assertNotNull(putRequest.getEntity());
        assertEquals(true, result.get("success"));
        assertEquals(200, result.get("statusCode"));
    }
    
    @Test
    void execute_DeleteRequest_ShouldExecuteSuccessfully() throws Exception {
        // Arrange
        Map<String, String> config = new HashMap<>();
        config.put("url", "https://example.com/api/resource/1");
        config.put("method", "DELETE");
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        verify(httpClient).execute(requestCaptor.capture());
        HttpUriRequest request = requestCaptor.getValue();
        
        assertTrue(request instanceof HttpDelete);
        assertEquals("https://example.com/api/resource/1", request.getRequestUri().toString());
        assertEquals(true, result.get("success"));
        assertEquals(200, result.get("statusCode"));
    }
    
    @Test
    void execute_WithHeaders_ShouldAddHeadersToRequest() throws Exception {
        // Arrange
        Map<String, String> config = new HashMap<>();
        config.put("url", "https://example.com/api/resource");
        config.put("method", "GET");
        config.put("headers", "{\"Authorization\":\"Bearer token\",\"X-Custom-Header\":\"${param1}\"}");
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        verify(httpClient).execute(requestCaptor.capture());
        HttpUriRequest request = requestCaptor.getValue();
        
        assertEquals("Bearer token", request.getHeader("Authorization").getValue());
        assertEquals("value1", request.getHeader("X-Custom-Header").getValue());
        assertEquals(true, result.get("success"));
    }
    
    @Test
    void execute_ErrorResponse_ShouldHandleErrorCodes() throws Exception {
        // Arrange
        Map<String, String> config = new HashMap<>();
        config.put("url", "https://example.com/api/resource");
        config.put("method", "GET");
        taskDefinition.setConfiguration(config);
        
        // Mock error response
        when(httpResponse.getCode()).thenReturn(404);
        when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream("{\"error\":\"Not found\"}".getBytes(StandardCharsets.UTF_8)));
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        verify(httpClient).execute(any(HttpUriRequest.class));
        assertEquals(false, result.get("success"));
        assertEquals(404, result.get("statusCode"));
        assertEquals("HTTP error: 404", result.get("error"));
        assertEquals("{\"error\":\"Not found\"}", result.get("responseBody"));
    }
    
    @Test
    void execute_NetworkError_ShouldHandleExceptions() throws Exception {
        // Arrange
        Map<String, String> config = new HashMap<>();
        config.put("url", "https://example.com/api/resource");
        config.put("method", "GET");
        taskDefinition.setConfiguration(config);
        
        // Mock network error
        when(httpClient.execute(any(HttpUriRequest.class))).thenThrow(new IOException("Network error"));
        
        // Act & Assert
        TaskExecutionException exception = assertThrows(TaskExecutionException.class, () -> {
            httpTaskExecutor.execute(taskDefinition, context);
        });
        
        assertTrue(exception.getMessage().contains("Error executing HTTP task"));
        assertTrue(exception.getCause() instanceof IOException);
    }
    
    @Test
    void execute_MissingRequiredConfig_ShouldThrowException() {
        // Arrange
        Map<String, String> config = new HashMap<>();
        config.put("url", "https://example.com/api/resource");
        // Missing method
        taskDefinition.setConfiguration(config);
        
        // Act & Assert
        TaskExecutionException exception = assertThrows(TaskExecutionException.class, () -> {
            httpTaskExecutor.execute(taskDefinition, context);
        });
        
        assertTrue(exception.getMessage().contains("Error executing HTTP task"));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }
    
    @Test
    void execute_VariableSubstitution_ShouldReplaceVariables() throws Exception {
        // Arrange
        Map<String, String> config = new HashMap<>();
        config.put("url", "https://example.com/api/${param1}");
        config.put("method", "GET");
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        verify(httpClient).execute(requestCaptor.capture());
        HttpUriRequest request = requestCaptor.getValue();
        
        assertEquals("https://example.com/api/value1", request.getRequestUri().toString());
        assertEquals(true, result.get("success"));
    }
}
