package com.bank.withdrawal;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What it does: Configures Temporal client stubs and starts the Temporal Worker.
 * Why it exists: Connects the Spring Boot application to the Temporal cluster and registers workflows/activities.
 * Technology used: Temporal SDK (WorkflowServiceStubs, WorkflowClient, WorkerFactory, Worker).
 * Communication: Connects to Temporal cluster on localhost:7233, registers WithdrawalWorkflowImpl and WithdrawalActivityImpl.
 */
@Configuration
public class TemporalConfig {

    public static final String TASK_QUEUE = "WITHDRAWAL_TASK_QUEUE";

    @Value("${temporal.target:127.0.0.1:7233}")
    private String temporalTarget;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(temporalTarget)
                        .build()
        );
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(serviceStubs);
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient workflowClient, WithdrawalActivityImpl activityImpl) {
        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
        Worker worker = factory.newWorker(TASK_QUEUE);

        // Register the workflow implementation class
        worker.registerWorkflowImplementationTypes(WithdrawalWorkflowImpl.class);

        // Register the activity implementation instance
        worker.registerActivitiesImplementations(activityImpl);

        // Start listening to the task queue
        factory.start();
        System.out.println("[TemporalConfig] Temporal Worker started on task queue: " + TASK_QUEUE);

        return factory;
    }
}

