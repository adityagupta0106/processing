package com.serviceplus.form.validation.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;

import reactor.core.publisher.Mono;

@Repository
public interface ApplicationFlowRouterRepository  extends ReactiveCrudRepository<ApplicationFlowStatusEntity, String> {

    Mono<ApplicationFlowStatusEntity> findByApplicationIdAndCompletedAndTaskIdAndServiceIdAndTenantId(String applicationId,Integer completed,String taskId,Integer serviceId,String tenantId);

    Mono<ApplicationFlowStatusEntity> findByApplicationIdAndTxnIdAndCompletedAndTaskIdAndServiceIdAndTenantId(String applicationId,String txnId,Integer completed,String taskId,Integer serviceId,String tenantId);

    Mono<ApplicationFlowStatusEntity> findFirstByApplicationIdAndCompletedAndTaskIdAndServiceIdAndTenantIdAndActivityTypeOrderByIdDesc(
            String applicationId,
            Integer completed,
            String taskId,
            Integer serviceId,
            String tenantId,
            String activityType
    );

    Mono<ApplicationFlowStatusEntity> findByApplicationIdAndCompletedAndServiceIdAndTenantId(String applicationId,Integer completed,Integer serviceId,String tenantId);


    Mono<ApplicationFlowStatusEntity> findFirstByApplicationIdAndTaskIdAndActivityTypeAndCompletedOrderByIdDesc(String applicationId, String taskId, String type, int completed);

    Mono<ApplicationFlowStatusEntity> findFirstByApplicationIdAndTaskIdAndServiceIdAndTenantIdAndActivityTypeOrderByIdDesc(String appId, String taskId, Integer serviceId, String tenantId, String activityFormStatusKey);

    Mono<ApplicationFlowStatusEntity> findFirstByApplicationIdAndTaskIdAndServiceIdAndTenantIdAndActivityTypeAndLastUpdateAfterOrderByIdDesc(String appId, String taskId, Integer serviceId, String tenantId, String activityFormStatusKey, LocalDateTime initiatedOn);

	Mono<ApplicationFlowStatusEntity> findFirstByApplicationIdAndTaskIdAndActivityTypeInAndCompletedOrderByIdDesc(
			String applicationId, String taskId, List<String> activityType, Integer completed);
	
    Mono<ApplicationFlowStatusEntity> findFirstByApplicationIdAndTaskIdAndServiceIdAndTenantIdAndActivityTypeAndCompletedAndLastUpdateAfterOrderByIdDesc(
            String applicationId,
            String taskId,
            Integer serviceId,
            String tenantId,
            String activityType,
            Integer completed,
            LocalDateTime lastUpdateAfter);
    Mono<ApplicationFlowStatusEntity> findFirstByApplicationIdAndFormIdAndServiceIdAndTenantIdAndActivityTypeAndCompletedOrderByLastUpdateDesc(
            String applicationId,
            String formId,
            Integer serviceId,
            String tenantId,
            String activityType,
            Integer completed);

    Mono<ApplicationFlowStatusEntity> findFirstByApplicationIdAndCompletedAndFormIdAndServiceIdAndTenantIdAndActivityTypeOrderByIdDesc(
            String applicationId,
            Integer completed,
            String formId,
            Integer serviceId,
            String tenantId,
            String activityType);


	Mono<ApplicationFlowStatusEntity> findFirstByApplicationIdAndTaskIdAndServiceIdAndTenantIdAndActivityTypeAndLastUpdateAfterAndDataIdIsNotNullOrderByIdDesc(
			String appId, String taskId, Integer serviceId, String tenantId, String activityFormStatusKey,
			LocalDateTime initiatedOn);

    Mono<ApplicationFlowStatusEntity> findByTxnIdAndCompletedAndTaskIdAndServiceIdAndTenantId(String txnId, int i, String taskId, Integer serviceId, String tenantId);
}
