package com.serviceplus.form.validation.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.serviceplus.form.validation.entity.WorkflowWebServiceExecution;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface WorkflowWebServiceExecutionRepository
        extends ReactiveCrudRepository<WorkflowWebServiceExecution, String> {

    Flux<WorkflowWebServiceExecution> findByStatusInAndNextRetryTimeLessThanEqual(
            List<String> status,
            OffsetDateTime nextRetryTime);

    Mono<WorkflowWebServiceExecution> findByCurrentProcessId(String currentProcessId);

    Flux<WorkflowWebServiceExecution> findByApplicationId(String applicationId);

    Flux<WorkflowWebServiceExecution> findByApplicationIdIn(List<String> applicationIds);

    @Query("""
        SELECT * FROM schm_sp.workflow_webservice_execution
        WHERE (status = 'EXHAUSTED' OR (status = 'FAILED' AND attempt_count >= max_attempt))
          AND (CAST(:serviceId AS integer) IS NULL OR service_id = :serviceId OR base_service_id = :serviceId)
          AND (CAST(:taskId AS varchar) IS NULL OR current_task_id = :taskId)
    """)
    Flux<WorkflowWebServiceExecution> findFailedWithoutAttemptsLeft(
            @Param("serviceId") Integer serviceId,
            @Param("taskId") String taskId);

    @Query("""
        SELECT * FROM schm_sp.workflow_webservice_execution
        WHERE (status = 'EXHAUSTED' OR (status = 'FAILED' AND attempt_count >= max_attempt))
          AND (CAST(:serviceId AS integer) IS NULL OR service_id = :serviceId OR base_service_id = :serviceId)
          AND (CAST(:taskId AS varchar) IS NULL OR current_task_id = :taskId)
          AND application_id IN (:applicationIds)
    """)
    Flux<WorkflowWebServiceExecution> findFailedWithoutAttemptsLeftByApplicationIds(
            @Param("serviceId") Integer serviceId,
            @Param("taskId") String taskId,
            @Param("applicationIds") List<String> applicationIds);

    @Query("""
        SELECT * FROM schm_sp.workflow_webservice_execution
        WHERE status = 'FAILED' AND (attempt_count < max_attempt OR max_attempt IS NULL)
          AND (CAST(:serviceId AS integer) IS NULL OR service_id = :serviceId OR base_service_id = :serviceId)
          AND (CAST(:taskId AS varchar) IS NULL OR current_task_id = :taskId)
    """)
    Flux<WorkflowWebServiceExecution> findFailedWithAttemptsLeft(
            @Param("serviceId") Integer serviceId,
            @Param("taskId") String taskId);

    @Query("""
        SELECT * FROM schm_sp.workflow_webservice_execution
        WHERE status = 'FAILED' AND (attempt_count < max_attempt OR max_attempt IS NULL)
          AND (CAST(:serviceId AS integer) IS NULL OR service_id = :serviceId OR base_service_id = :serviceId)
          AND (CAST(:taskId AS varchar) IS NULL OR current_task_id = :taskId)
          AND application_id IN (:applicationIds)
    """)
    Flux<WorkflowWebServiceExecution> findFailedWithAttemptsLeftByApplicationIds(
            @Param("serviceId") Integer serviceId,
            @Param("taskId") String taskId,
            @Param("applicationIds") List<String> applicationIds);
}
