package com.serviceplus.form.validation.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.serviceplus.form.validation.entity.AckGenerationJobEntity;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface AckGenerationJobRepository extends ReactiveCrudRepository<AckGenerationJobEntity, String> {

    Flux<AckGenerationJobEntity> findByApplicationId(String applicationId);

    Flux<AckGenerationJobEntity> findByTxnId(String txnId);

    Flux<AckGenerationJobEntity> findByStatus(String status);

    Mono<AckGenerationJobEntity> findFirstByApplicationIdAndReferenceId(String applicationId, String referenceId);
}
