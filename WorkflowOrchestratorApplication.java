package com.example.workfloworchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class WorkflowOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowOrchestratorApplication.class, args);
    }
}
