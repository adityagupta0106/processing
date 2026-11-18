package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.entity.EditFormDetails;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface EditFormDetailsRepository extends ReactiveCrudRepository<EditFormDetails, String> {

    Mono<EditFormDetails> findFirstByApplicationIdAndServiceIdAndTenantIdAndHolderIdAndFormId(
            String applicationId,
            Integer serviceId,
            String tenantId,
            String holderId,
            String formId);

    Flux<EditFormDetails> findByApplicationIdAndServiceIdAndTenantIdAndFormId(
            String applicationId,
            Integer serviceId,
            String tenantId,
            String formId);

    Flux<EditFormDetails> findByApplicationIdAndServiceIdAndTenantId(
            String applicationId,
            Integer serviceId,
            String tenantId);

    Mono<EditFormDetails> findFirstByApplicationIdAndFormIdOrderByUpDateDesc(
            String applicationId,
            String formId);

}
