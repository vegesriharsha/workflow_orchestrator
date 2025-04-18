package com.example.workfloworchestrator.config;

import com.example.workfloworchestrator.engine.executor.TaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for task executors
 * Maps task types to their corresponding executors
 */
@Configuration
public class TaskExecutorConfig {
    
    /**
     * Create a map of task types to task executors
     * 
     * @param executors list of task executor instances
     * @return map of task types to executors
     */
    @Bean
    public Map<String, TaskExecutor> taskExecutors(List<TaskExecutor> executors) {
        Map<String, TaskExecutor> executorMap = new HashMap<>();
        
        // Register each executor by its task type
        for (TaskExecutor executor : executors) {
            executorMap.put(executor.getTaskType(), executor);
        }
        
        return executorMap;
    }
}
