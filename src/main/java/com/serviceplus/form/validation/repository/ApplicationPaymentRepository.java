package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.dto.PaymentRevalidationApplicationResponse;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.ApplicationPayment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ApplicationPaymentRepository extends ReactiveCrudRepository<ApplicationPayment, String> {

    Mono<ApplicationPayment> findByTenantIdAndApplicationIdAndTaskId(
            String tenantId,
            String applicationId,
            String taskId);

    Mono<ApplicationPayment> findByTenantIdAndApplicationIdAndProcessIdAndTaskId(
            String tenantId,
            String applicationId,
            String processId,
            String taskId);

    Mono<ApplicationPayment> findByApplicationIdAndTaskIdAndTenantId(
            String applicationId,
            String taskId,
            String tenantId);

    Mono<ApplicationPayment> findByApplicationIdAndProcessIdAndTaskIdAndTenantId(
            String applicationId,
            String processId,
            String taskId,
            String tenantId);

    Mono<ApplicationPayment> findFirstByApplicationIdAndProcessIdAndTaskIdAndTenantIdOrderByCrDateDesc(String applicationId, String processId, String taskId, String tenantId);

    Mono<ApplicationPayment> findFirstByApplicationIdAndTaskIdAndTenantIdOrderByCrDateDesc(String applicationId, String taskId, String tenantId);

    Flux<ApplicationPayment> findByStatusInAndCrDateBeforeOrderByCrDateAsc(List<String> name, LocalDateTime cutoff, PageRequest of);

    Flux<ApplicationPayment> findByTenantIdAndStatusInOrderByCrDateDesc(String tenantId, List<String> name, PageRequest of);

    Flux<ApplicationPayment> findByTenantIdAndStatusInAndRevalidationConfigurationIsNotNullOrderByCrDateDesc(String tenantId, List<String> name, PageRequest of);

    @Query("""
    SELECT
         p.application_id AS application_id,
         p.payment_id AS payment_id,
         p.service_id AS service_id,
         p.task_id AS task_id,
         p.status AS status,
         p.amount AS amount,
         p.currency AS currency,
         p.gateway_code AS gateway_code,
         p.cr_date AS created_at,
         p.revalidation_configuration AS revalidation_configuration,
         a.service_name AS service_name,
         a.reference_no AS reference_no
    FROM schm_sp.application_payment p
    INNER JOIN schm_sp.application_details a
        ON a.application_id = p.application_id
        AND a.tenant_id = p.tenant_id
    WHERE p.tenant_id = :tenantId
        AND p.status IN (:statuses)
        AND p.revalidation_configuration IS NOT NULL
        AND a.beneficiary_id = :userId
    ORDER BY p.cr_date DESC
    LIMIT :limit OFFSET :offset
    """)
    Flux<PaymentRevalidationApplicationResponse> findApplicationsAvailableForRevalidation(String tenantId, List<String> statuses, Long userId, int limit, long offset);
}
