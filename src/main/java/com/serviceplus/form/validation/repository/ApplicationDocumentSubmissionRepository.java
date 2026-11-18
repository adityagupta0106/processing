package com.serviceplus.form.validation.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.serviceplus.form.validation.entity.ApplicationDocumentSubmissionEntity;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ApplicationDocumentSubmissionRepository extends ReactiveCrudRepository<ApplicationDocumentSubmissionEntity, String> {

    Flux<ApplicationDocumentSubmissionEntity> findByApplicationId(String applicationId);

    Flux<ApplicationDocumentSubmissionEntity> findByTxnId(String txnId);

    Mono<ApplicationDocumentSubmissionEntity> findFirstByApplicationIdAndReferenceId(
            String applicationId,
            String referenceId
    );

    Mono<ApplicationDocumentSubmissionEntity> findFirstByApplicationIdAndTaskIdAndReferenceIdAndStatusOrderByCreatedOnDesc(String applicationId, String linkedTaskId, String linkedReferenceId, String p);

    Flux<ApplicationDocumentSubmissionEntity> findByApplicationIdAndTxnIdAndTaskIdAndStatus(String applicationId, String txnId, String taskId, String p);

    Flux<ApplicationDocumentSubmissionEntity> findByApplicationIdAndTaskIdAndStatusOrderByCreatedOnDesc(String applicationId, String taskId, String p);
}
