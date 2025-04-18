package com.example.workfloworchestrator.config;

import com.example.workfloworchestrator.event.BaseEvent;
import com.example.workfloworchestrator.event.WorkflowEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for event handling
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class EventConfig {
    
    @Value("${workflow.events.async-executor-pool-size:5}")
    private int asyncEventExecutorPoolSize;
    
    /**
     * Configure the ApplicationEventMulticaster to use an asynchronous executor
     * This ensures that event handling doesn't block the main workflow execution
     */
    @Bean
    public ApplicationEventMulticaster applicationEventMulticaster() {
        SimpleApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();
        eventMulticaster.setTaskExecutor(asyncEventExecutor());
        
        return eventMulticaster;
    }
    
    /**
     * Configure the task executor for asynchronous event handling
     */
    @Bean(name = "asyncEventExecutor")
    public Executor asyncEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncEventExecutorPoolSize);
        executor.setMaxPoolSize(asyncEventExecutorPoolSize * 2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("event-executor-");
        executor.initialize();
        
        return executor;
    }
    
    /**
     * Example bean that could be used to integrate with external monitoring
     * systems like Prometheus, DataDog, etc.
     */
    @Bean
    public ExternalEventPublisher externalEventPublisher(
            @Value("${workflow.events.external-integration-enabled:false}") boolean enabled,
            @Value("${workflow.events.external-endpoint:}") String externalEndpoint) {
        
        return new ExternalEventPublisher(enabled, externalEndpoint);
    }
    
    /**
     * Example class for publishing events to external systems
     * This is just a stub - real implementations would connect to specific systems
     */
    @Slf4j
    public static class ExternalEventPublisher {
        
        private final boolean enabled;
        private final String endpoint;
        
        public ExternalEventPublisher(boolean enabled, String endpoint) {
            this.enabled = enabled;
            this.endpoint = endpoint;
            
            if (enabled) {
                log.info("External event publishing enabled, endpoint: {}", endpoint);
            } else {
                log.info("External event publishing disabled");
            }
        }
        
        /**
         * Publish an event to external system
         */
        public void publishEvent(BaseEvent event) {
            if (!enabled) {
                return;
            }
            
            // This is just a stub implementation
            // Real implementation would send event to external system
            log.debug("Would publish event to external system: {}, eventId: {}", 
                    event.getEventTypeString(), event.getEventId());
            
            // Example for different event types
            if (event instanceof WorkflowEvent) {
                WorkflowEvent workflowEvent = (WorkflowEvent) event;
                log.debug("Workflow event details: workflowId: {}, correlationId: {}", 
                        workflowEvent.getWorkflowExecutionId(),
                        workflowEvent.getCorrelationId());
            }
        }
    }
}
