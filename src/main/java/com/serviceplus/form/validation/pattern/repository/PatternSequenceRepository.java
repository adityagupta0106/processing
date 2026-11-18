package com.serviceplus.form.validation.pattern.repository;

import com.serviceplus.form.validation.pattern.entity.PatternSequenceEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface PatternSequenceRepository extends ReactiveCrudRepository<PatternSequenceEntity, Long> {

    Mono<PatternSequenceEntity> findByPatternIdAndTenantIdAndScopeKey(String patternId, String tenantId, String scopeKey);

    Flux<PatternSequenceEntity> findAllByPatternIdAndTenantId(String patternId, String tenantId);

    Flux<PatternSequenceEntity> findAllByTenantIdAndServiceId(String tenantId, Integer serviceId);

    @Query("SELECT * FROM schm_sp.pattern_sequence WHERE pattern_id = :patternId AND tenant_id = :tenantId AND scope_key = :scopeKey FOR UPDATE")
    Mono<PatternSequenceEntity> findByPatternIdAndTenantIdAndScopeKeyForUpdate(String patternId, String tenantId, String scopeKey);
}
