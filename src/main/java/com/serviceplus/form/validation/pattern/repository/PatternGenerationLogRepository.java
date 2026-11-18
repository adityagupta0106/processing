package com.serviceplus.form.validation.pattern.repository;

import com.serviceplus.form.validation.pattern.entity.PatternGenerationLogEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface PatternGenerationLogRepository extends ReactiveCrudRepository<PatternGenerationLogEntity, String> {

    Mono<PatternGenerationLogEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Mono<PatternGenerationLogEntity> findByTenantIdAndGeneratedValue(String tenantId, String generatedValue);

    Mono<PatternGenerationLogEntity> findByApplicationIdAndGenerationPurposeAndStatus(String applicationId, String generationPurpose, String status);

    Flux<PatternGenerationLogEntity> findAllByPatternIdAndTenantId(String patternId, String tenantId);

    Flux<PatternGenerationLogEntity> findAllByApplicationIdAndTenantId(String applicationId, String tenantId);

    Mono<Long> countByPatternIdAndTenantIdAndStatus(String patternId, String tenantId, String status);

    Mono<Long> countByScopeKeyAndTenantId(String scopeKey, String tenantId);
}
