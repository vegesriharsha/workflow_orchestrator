package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HttpTaskExecutorWebFluxTest {

    private MockWebServer mockWebServer;
    private HttpTaskExecutor httpTaskExecutor;
    private WebClient webClient;
    private ObjectMapper objectMapper;
    private TaskDefinition taskDefinition;
    private ExecutionContext context;

    @BeforeEach
    public void setup() throws IOException {
        // Setup ObjectMapper
        objectMapper = new ObjectMapper();
        
        // Setup MockWebServer
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        // Get mock server URL
        String baseUrl = mockWebServer.url("/").toString();
        
        // Setup WebClient pointing to mock server
        webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        
        // Setup HttpTaskExecutor with spy to inject our WebClient
        httpTaskExecutor = spy(new HttpTaskExecutor(objectMapper));
        doReturn(webClient).when(httpTaskExecutor).getWebClient();
        
        // Setup TaskDefinition
        taskDefinition = new TaskDefinition();
        taskDefinition.setId(1L);
        taskDefinition.setName("HTTP Task");
        taskDefinition.setType("http");
        taskDefinition.setConfiguration(new HashMap<>());
        
        // Setup ExecutionContext
        context = new ExecutionContext();
        context.setVariable("param1", "value1");
        context.setVariable("param2", "value2");
    }

    @AfterEach
    public void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    public void testGetRequest_ShouldExecuteSuccessfully() throws Exception {
        // Arrange
        String responseBody = "{\"data\":\"test\",\"success\":true}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(responseBody));
        
        Map<String, String> config = new HashMap<>();
        config.put("url", mockWebServer.url("/api/test").toString());
        config.put("method", "GET");
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("/api/test", recordedRequest.getPath());
        assertEquals("GET", recordedRequest.getMethod());
        
        assertTrue((Boolean) result.get("success"));
        assertEquals(200, result.get("statusCode"));
        assertEquals(responseBody, result.get("responseBody"));
        assertNotNull(result.get("parsedResponse"));
        assertNotNull(result.get("executionTimestamp"));
    }

    @Test
    public void testPostRequest_ShouldSendRequestBody() throws Exception {
        // Arrange
        String requestBody = "{\"param\":\"${param1}\"}";
        String expectedRequestBody = "{\"param\":\"value1\"}";
        String responseBody = "{\"result\":\"success\"}";
        
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(responseBody));
        
        Map<String, String> config = new HashMap<>();
        config.put("url", mockWebServer.url("/api/resource").toString());
        config.put("method", "POST");
        config.put("requestBody", requestBody);
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("/api/resource", recordedRequest.getPath());
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, recordedRequest.getHeader(HttpHeaders.CONTENT_TYPE));
        assertEquals(expectedRequestBody, recordedRequest.getBody().readUtf8());
        
        assertTrue((Boolean) result.get("success"));
        assertEquals(201, result.get("statusCode"));
        assertEquals(responseBody, result.get("responseBody"));
    }

    @Test
    public void testPutRequest_ShouldSendRequestBody() throws Exception {
        // Arrange
        String requestBody = "{\"param\":\"${param1}\",\"other\":\"${param2}\"}";
        String expectedRequestBody = "{\"param\":\"value1\",\"other\":\"value2\"}";
        String responseBody = "{\"updated\":true}";
        
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(responseBody));
        
        Map<String, String> config = new HashMap<>();
        config.put("url", mockWebServer.url("/api/resource/1").toString());
        config.put("method", "PUT");
        config.put("requestBody", requestBody);
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("/api/resource/1", recordedRequest.getPath());
        assertEquals("PUT", recordedRequest.getMethod());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, recordedRequest.getHeader(HttpHeaders.CONTENT_TYPE));
        assertEquals(expectedRequestBody, recordedRequest.getBody().readUtf8());
        
        assertTrue((Boolean) result.get("success"));
        assertEquals(200, result.get("statusCode"));
        assertEquals(responseBody, result.get("responseBody"));
    }

    @Test
    public void testDeleteRequest_ShouldExecuteSuccessfully() throws Exception {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(204));
        
        Map<String, String> config = new HashMap<>();
        config.put("url", mockWebServer.url("/api/resource/1").toString());
        config.put("method", "DELETE");
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("/api/resource/1", recordedRequest.getPath());
        assertEquals("DELETE", recordedRequest.getMethod());
        
        assertTrue((Boolean) result.get("success"));
        assertEquals(204, result.get("statusCode"));
    }

    @Test
    public void testPatchRequest_ShouldSendRequestBody() throws Exception {
        // Arrange
        String requestBody = "{\"status\":\"${param1}\"}";
        String expectedRequestBody = "{\"status\":\"value1\"}";
        String responseBody = "{\"patched\":true}";
        
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(responseBody));
        
        Map<String, String> config = new HashMap<>();
        config.put("url", mockWebServer.url("/api/resource/1").toString());
        config.put("method", "PATCH");
        config.put("requestBody", requestBody);
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("/api/resource/1", recordedRequest.getPath());
        assertEquals("PATCH", recordedRequest.getMethod());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, recordedRequest.getHeader(HttpHeaders.CONTENT_TYPE));
        assertEquals(expectedRequestBody, recordedRequest.getBody().readUtf8());
        
        assertTrue((Boolean) result.get("success"));
        assertEquals(200, result.get("statusCode"));
        assertEquals(responseBody, result.get("responseBody"));
    }

    @Test
    public void testHeaders_ShouldSendCustomHeaders() throws Exception {
        // Arrange
        String headersJson = "{\"Authorization\":\"Bearer token\",\"X-Custom-Header\":\"${param1}\"}";
        
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{}"));
        
        Map<String, String> config = new HashMap<>();
        config.put("url", mockWebServer.url("/api/resource").toString());
        config.put("method", "GET");
        config.put("headers", headersJson);
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("Bearer token", recordedRequest.getHeader("Authorization"));
        assertEquals("value1", recordedRequest.getHeader("X-Custom-Header"));
        
        assertTrue((Boolean) result.get("success"));
        assertEquals(200, result.get("statusCode"));
    }

    @Test
    public void testErrorResponse_ShouldHandleErrorCodes() throws Exception {
        // Arrange
        String errorResponseBody = "{\"error\":\"Not found\"}";
        
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(errorResponseBody));
        
        Map<String, String> config = new HashMap<>();
        config.put("url", mockWebServer.url("/api/resource/999").toString());
        config.put("method", "GET");
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        
        assertFalse((Boolean) result.get("success"));
        assertEquals(404, result.get("statusCode"));
        assertEquals("HTTP error: 404", result.get("error"));
        assertEquals(errorResponseBody, result.get("responseBody"));
    }

    @Test
    public void testMissingRequiredConfig_ShouldThrowException() {
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
    public void testInvalidHttpMethod_ShouldThrowException() {
        // Arrange
        Map<String, String> config = new HashMap<>();
        config.put("url", "https://example.com/api/resource");
        config.put("method", "INVALID");
        taskDefinition.setConfiguration(config);
        
        // Act & Assert
        TaskExecutionException exception = assertThrows(TaskExecutionException.class, () -> {
            httpTaskExecutor.execute(taskDefinition, context);
        });
        
        assertTrue(exception.getMessage().contains("Error executing HTTP task"));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertTrue(exception.getCause().getMessage().contains("Unsupported HTTP method"));
    }

    @Test
    public void testUrlVariableSubstitution_ShouldReplaceVariables() throws Exception {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{}"));
        
        Map<String, String> config = new HashMap<>();
        config.put("url", mockWebServer.url("/api/${param1}/${param2}").toString());
        config.put("method", "GET");
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("/api/value1/value2", recordedRequest.getPath());
        
        assertTrue((Boolean) result.get("success"));
        assertEquals(200, result.get("statusCode"));
    }

    @Test
    public void testParsedJsonResponse_ShouldBeAddedToContext() throws Exception {
        // Arrange
        String responseBody = "{\"id\":123,\"name\":\"test\",\"items\":[1,2,3]}";
        
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(responseBody));
        
        Map<String, String> config = new HashMap<>();
        config.put("url", mockWebServer.url("/api/resource").toString());
        config.put("method", "GET");
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        assertTrue((Boolean) result.get("success"));
        assertEquals(200, result.get("statusCode"));
        assertNotNull(result.get("parsedResponse"));
        
        // Verify context was updated
        assertTrue(context.hasVariable("parsedResponse"));
        Object parsedResponse = context.getVariable("parsedResponse");
        assertNotNull(parsedResponse);
        assertTrue(parsedResponse instanceof JsonNode);
        
        JsonNode jsonNode = (JsonNode) parsedResponse;
        assertEquals(123, jsonNode.get("id").asInt());
        assertEquals("test", jsonNode.get("name").asText());
        assertTrue(jsonNode.get("items").isArray());
        assertEquals(3, jsonNode.get("items").size());
    }

    @Test
    public void testCustomTimeout_ShouldUseConfiguredTimeout() throws Exception {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{}"));
        
        Map<String, String> config = new HashMap<>();
        config.put("url", mockWebServer.url("/api/resource").toString());
        config.put("method", "GET");
        config.put("timeoutSeconds", "30");  // Custom timeout
        taskDefinition.setConfiguration(config);
        
        // We can't easily test the actual timeout behavior with MockWebServer
        // but we can verify the configuration is correctly processed
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        assertTrue((Boolean) result.get("success"));
        assertEquals(200, result.get("statusCode"));
    }

    @Test
    public void testNonJsonResponse_ShouldNotAttemptToParse() throws Exception {
        // Arrange
        String responseBody = "Plain text response";
        
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .setBody(responseBody));
        
        Map<String, String> config = new HashMap<>();
        config.put("url", mockWebServer.url("/api/resource").toString());
        config.put("method", "GET");
        taskDefinition.setConfiguration(config);
        
        // Act
        Map<String, Object> result = httpTaskExecutor.execute(taskDefinition, context);
        
        // Assert
        assertTrue((Boolean) result.get("success"));
        assertEquals(200, result.get("statusCode"));
        assertEquals(responseBody, result.get("responseBody"));
        
        // Verify parsedResponse is not in result or context
        assertFalse(result.containsKey("parsedResponse"));
        assertFalse(context.hasVariable("parsedResponse"));
    }
}
