package com.serviceplus.form.validation.pattern.sequence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.pattern.dto.PatternConfigurationDTO;
import com.serviceplus.form.validation.pattern.dto.PatternGenerationRequestDTO;
import com.serviceplus.form.validation.pattern.dto.ResetAttributeDTO;
import com.serviceplus.form.validation.pattern.dto.ResetConfigDTO;
import com.serviceplus.form.validation.pattern.dto.SequenceConfigDTO;
import com.serviceplus.form.validation.pattern.entity.PatternGenerationLogEntity;
import com.serviceplus.form.validation.pattern.entity.PatternSequenceEntity;
import com.serviceplus.form.validation.pattern.enums.SeriesType;
import com.serviceplus.form.validation.pattern.repository.PatternGenerationLogRepository;
import com.serviceplus.form.validation.pattern.repository.PatternSequenceRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;

@Component
public class SequenceManager {

    private static final Logger log = LogManager.getLogger("applicationFlowLogger");

    private final PatternSequenceRepository sequenceRepository;
    private final PatternGenerationLogRepository generationLogRepository;
    private final R2dbcEntityTemplate entityTemplate;
    private final TransactionalOperator transactionalOperator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SequenceManager(PatternSequenceRepository sequenceRepository,
                           PatternGenerationLogRepository generationLogRepository,
                           R2dbcEntityTemplate entityTemplate,
                           TransactionalOperator transactionalOperator) {
        this.sequenceRepository = sequenceRepository;
        this.generationLogRepository = generationLogRepository;
        this.entityTemplate = entityTemplate;
        this.transactionalOperator = transactionalOperator;
    }

    public static final int DEFAULT_PATTERN_VERSION = 1;

    /**
     * Builds canonical composite reset scope key.
     * All selected reset attributes are sorted alphabetically by attribute identifier to guarantee
     * that UI display order, sort direction, and drag-and-drop order have ZERO impact on scope key.
     */
    public String buildCanonicalScopeKey(PatternConfigurationDTO config, Map<String, String> resolvedAttributeValues, String tenantId, Integer serviceId) {
        return buildCanonicalScopeKey(config, resolvedAttributeValues, tenantId, serviceId, null);
    }

    public String buildCanonicalScopeKey(PatternConfigurationDTO config, Map<String, String> resolvedAttributeValues, String tenantId, Integer serviceId, String txnId) {
        log.info("Building canonical scope key for pattern: {}, tenant: {}, service: {}, txnId: {}",
                config.getPatternId(), tenantId, serviceId, txnId);

        ResetConfigDTO resetConfig = config.getResetConfig();
        if (resetConfig == null || resetConfig.getResetEnabled() == null || !resetConfig.getResetEnabled() || resetConfig.getResetAttributes() == null || resetConfig.getResetAttributes().isEmpty()) {
            String defaultScope = String.format("T:%s:S:%s:P:%s:v%d:DEFAULT", tenantId, serviceId, config.getPatternId(), DEFAULT_PATTERN_VERSION);
            log.info("Reset is not enabled or no reset attributes configured for pattern: {}. Using default scope: {}", config.getPatternId(), defaultScope);
            return defaultScope;
        }

        List<ResetAttributeDTO> resetAttrs = new ArrayList<>(resetConfig.getResetAttributes());
        // Sort attribute keys alphabetically for canonical consistency independent of UI order
        resetAttrs.sort((r1, r2) -> {
            String k1 = (r1 != null && r1.getMappedAttribute() != null && !r1.getMappedAttribute().isBlank())
                    ? r1.getMappedAttribute().trim()
                    : (r1 != null && r1.getToken() != null ? r1.getToken().trim().toUpperCase() : "");
            String k2 = (r2 != null && r2.getMappedAttribute() != null && !r2.getMappedAttribute().isBlank())
                    ? r2.getMappedAttribute().trim()
                    : (r2 != null && r2.getToken() != null ? r2.getToken().trim().toUpperCase() : "");
            return k1.compareTo(k2);
        });

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("T:%s:S:%s:P:%s:v%d:", tenantId, serviceId, config.getPatternId(), DEFAULT_PATTERN_VERSION));

        boolean first = true;
        for (ResetAttributeDTO resetAttr : resetAttrs) {
            if (resetAttr == null) {
                continue;
            }
            String token = resetAttr.getToken();
            String mapped = resetAttr.getMappedAttribute();
            String val = null;

            if (mapped != null && !mapped.isBlank()) {
                val = resolvedAttributeValues.get(mapped);
            }
            if ((val == null || val.trim().isEmpty()) && token != null) {
                val = resolvedAttributeValues.get(token);
                if (val == null || val.trim().isEmpty()) {
                    val = resolvedAttributeValues.get(token.replaceAll("[{}]", ""));
                }
                if (val == null || val.trim().isEmpty()) {
                    val = resolvedAttributeValues.get("{".concat(token.replaceAll("[{}]", "")).concat("}"));
                }
            }
            if (val == null || val.trim().isEmpty()) {
                String missingName = (mapped != null && !mapped.isBlank()) ? mapped : (token != null ? token : "UNKNOWN");
                log.error("Mandatory reset attribute '{}' is missing or empty in runtime context for pattern '{}', txnId: '{}'",
                        missingName, config.getPatternId(), txnId);
                SPRuntimeError err = new SPRuntimeError(
                        "Mandatory reset attribute '".concat(missingName).concat("' is missing or empty in runtime context"),
                        HttpStatus.BAD_REQUEST,
                        txnId);
                Map<String, Object> data = new HashMap<>();
                data.put("patternId", config.getPatternId());
                data.put("attributeName", missingName);
                data.put("errorCode", "PATTERN_MANDATORY_ATTRIBUTE_MISSING");
                err.setData(data);
                throw err;
            }

            log.info("Reset attribute resolved: token='{}', mappedAttribute='{}', value='{}'", token, mapped, val.trim());
            if (!first) {
                sb.append("|");
            }
            String key = (mapped != null && !mapped.isBlank()) ? mapped : token.trim().toUpperCase().replaceAll("[{}]", "");
            sb.append(key).append("=").append(val.trim());
            first = false;
        }

        String computedScopeKey = sb.toString();
        log.info("Computed canonical composite scopeKey: '{}' for pattern: '{}'", computedScopeKey, config.getPatternId());
        return computedScopeKey;
    }

    public static class SequenceAllocation {
        private final String formattedSequence;
        private final Long numericValue;
        private final Long alphaIndex;
        private final String scopeKey;
        private final boolean isNewScope;
        private final boolean isResetEvent;
        private final String resetReason;

        public SequenceAllocation(String formattedSequence, Long numericValue, Long alphaIndex, String scopeKey, boolean isNewScope, boolean isResetEvent, String resetReason) {
            this.formattedSequence = formattedSequence;
            this.numericValue = numericValue;
            this.alphaIndex = alphaIndex;
            this.scopeKey = scopeKey;
            this.isNewScope = isNewScope;
            this.isResetEvent = isResetEvent;
            this.resetReason = resetReason;
        }

        public String getFormattedSequence() {
            return formattedSequence;
        }

        public Long getNumericValue() {
            return numericValue;
        }

        public Long getAlphaIndex() {
            return alphaIndex;
        }

        public String getScopeKey() {
            return scopeKey;
        }

        public boolean isNewScope() {
            return isNewScope;
        }

        public boolean isResetEvent() {
            return isResetEvent;
        }

        public String getResetReason() {
            return resetReason;
        }
    }

    /**
     * Atomically allocates the next sequence within the scope in a failure-safe, concurrent transaction.
     */
    public Mono<SequenceAllocation> allocateNextSequence(PatternConfigurationDTO config, String scopeKey, String tenantId, Integer serviceId, Map<String, String> resetAttributeValues) {
        return allocateNextSequence(config, scopeKey, tenantId, serviceId, resetAttributeValues, null);
    }

    public Mono<SequenceAllocation> allocateNextSequence(PatternConfigurationDTO config, String scopeKey, String tenantId, Integer serviceId, Map<String, String> resetAttributeValues, String txnId) {
        log.info("Initiating sequence allocation for pattern: '{}', scopeKey: '{}', tenantId: '{}', serviceId: {}, txnId: '{}'",
                config.getPatternId(), scopeKey, tenantId, serviceId, txnId);

        SequenceConfigDTO seqConfig = config.getSequenceConfig() != null ? config.getSequenceConfig() : new SequenceConfigDTO();
        SeriesType seriesType = seqConfig.getSeriesType() != null ? seqConfig.getSeriesType() : SeriesType.NUMERIC;

        return sequenceRepository.findByPatternIdAndTenantIdAndScopeKey(config.getPatternId(), tenantId, scopeKey)
                .flatMap(existingEntity -> {
                    log.info("Found existing sequence record for scopeKey: '{}'. CurrentNumeric: {}, CurrentAlpha: {}, Formatted: {}, isExhausted: {}, totalAllocated: {}",
                            scopeKey, existingEntity.getCurrentNumericValue(), existingEntity.getCurrentAlphaValue(),
                            existingEntity.getCurrentFormattedSequence(), existingEntity.getIsExhausted(), existingEntity.getTotalAllocated());

                    // 1. Initial check: If scope is already marked as exhausted, reject immediately before generating
                    if (Boolean.TRUE.equals(existingEntity.getIsExhausted())) {
                        log.error("Scope '{}' for pattern '{}' is already marked as EXHAUSTED. Rejecting request upfront with HttpStatus 409 CONFLICT",
                                scopeKey, config.getPatternId());
                        SPRuntimeError err = new SPRuntimeError(
                                "Sequence scope is exhausted for pattern ",
                                HttpStatus.CONFLICT,
                                txnId);
                        Map<String, Object> data = new HashMap<>();
                        data.put("patternId", config.getPatternId());
                        data.put("errorCode", "PATTERN_SEQUENCE_EXHAUSTED");
                        err.setData(data);
                        return Mono.error(err);
                    }

                    // Scope already exists and is not exhausted. Calculate next value according to series type.
                    String formattedSeq;
                    Long nextNum = existingEntity.getCurrentNumericValue();
                    Long nextAlpha = existingEntity.getCurrentAlphaValue();

                    if (seriesType == SeriesType.ALPHABETIC) {
                        nextAlpha = AlphabeticSequenceCalculator.getNextIndex(existingEntity.getCurrentAlphaValue(), seqConfig, config.getPatternId(), scopeKey, txnId);
                        formattedSeq = AlphabeticSequenceCalculator.formatSequence(nextAlpha, seqConfig);
                    } else if (seriesType == SeriesType.ALPHANUMERIC) {
                        AlphanumericSequenceCalculator.AlphanumericState nextState = AlphanumericSequenceCalculator.getNextState(
                                existingEntity.getCurrentAlphaValue(), existingEntity.getCurrentNumericValue(), seqConfig, config.getPatternId(), scopeKey, txnId);
                        nextAlpha = nextState.getAlphaIndex();
                        nextNum = nextState.getNumericValue();
                        formattedSeq = AlphanumericSequenceCalculator.formatSequence(nextAlpha, nextNum, seqConfig);
                    } else {
                        // Default NUMERIC
                        nextNum = NumericSequenceCalculator.getNextValue(existingEntity.getCurrentNumericValue(), seqConfig, config.getPatternId(), scopeKey, txnId);
                        formattedSeq = NumericSequenceCalculator.formatSequence(nextNum, seqConfig);
                    }

                    // 2. Check if this next generated sequence is the last available in the sequence bounds
                    boolean isLast = checkIfExhaustedAfterAllocation(seriesType, nextNum, nextAlpha, seqConfig);
                    if (isLast) {
                        log.warn("Sequence allocation reaches maximum configured capacity for pattern: '{}', scopeKey: '{}'. Marking is_exhausted = true",
                                config.getPatternId(), scopeKey);
                    } else {
                        log.info("Sequence allocation successful for pattern: '{}', scopeKey: '{}'. NextNum: {}, NextAlpha: {}, Formatted: {}. Remaining capacity exists",
                                config.getPatternId(), scopeKey, nextNum, nextAlpha, formattedSeq);
                    }

                    existingEntity.setCurrentNumericValue(nextNum);
                    existingEntity.setCurrentAlphaValue(nextAlpha);
                    existingEntity.setCurrentFormattedSequence(formattedSeq);
                    existingEntity.setTotalAllocated(existingEntity.getTotalAllocated() != null ? existingEntity.getTotalAllocated() + 1 : 1L);
                    existingEntity.setIsExhausted(isLast);
                    existingEntity.setLastAllocatedOn(LocalDateTime.now());
                    existingEntity.setNewEntity(false);

                    final Long finalNum = nextNum;
                    final Long finalAlpha = nextAlpha;
                    final String finalSeq = formattedSeq;

                    return sequenceRepository.save(existingEntity)
                            .doOnSuccess(saved -> log.info("Successfully persisted updated sequence for pattern: '{}', scopeKey: '{}'. Value: {}, isExhausted: {}",
                                    config.getPatternId(), scopeKey, saved.getCurrentFormattedSequence(), saved.getIsExhausted()))
                            .map(saved -> new SequenceAllocation(finalSeq, finalNum, finalAlpha, scopeKey, false, false, null));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Initial scope creation: first allocation for this composite scope
                    log.info("No sequence record found for scopeKey: '{}'. Creating initial sequence scope record", scopeKey);

                    String formattedSeq;
                    Long initialNum;
                    Long initialAlpha = 0L;

                    if (seriesType == SeriesType.ALPHABETIC) {
                        initialAlpha = AlphabeticSequenceCalculator.getNextIndex(null, seqConfig, config.getPatternId(), scopeKey, txnId);
                        initialNum = 0L;
                        formattedSeq = AlphabeticSequenceCalculator.formatSequence(initialAlpha, seqConfig);
                    } else if (seriesType == SeriesType.ALPHANUMERIC) {
                        AlphanumericSequenceCalculator.AlphanumericState initState = AlphanumericSequenceCalculator.getNextState(null, null, seqConfig, config.getPatternId(), scopeKey, txnId);
                        initialAlpha = initState.getAlphaIndex();
                        initialNum = initState.getNumericValue();
                        formattedSeq = AlphanumericSequenceCalculator.formatSequence(initialAlpha, initialNum, seqConfig);
                    } else {
                        initialNum = NumericSequenceCalculator.getNextValue(null, seqConfig, config.getPatternId(), scopeKey, txnId);
                        formattedSeq = NumericSequenceCalculator.formatSequence(initialNum, seqConfig);
                    }

                    // Check if initial generated sequence is also the last available
                    boolean isLast = checkIfExhaustedAfterAllocation(seriesType, initialNum, initialAlpha, seqConfig);
                    if (isLast) {
                        log.warn("Initial sequence allocation immediately exhausts capacity for pattern: '{}', scopeKey: '{}'. Marking is_exhausted = true",
                                config.getPatternId(), scopeKey);
                    } else {
                        log.info("Initial sequence allocation for pattern: '{}', scopeKey: '{}'. InitialNum: {}, InitialAlpha: {}, Formatted: {}",
                                config.getPatternId(), scopeKey, initialNum, initialAlpha, formattedSeq);
                    }

                    PatternSequenceEntity newEntity = new PatternSequenceEntity(config.getPatternId(), DEFAULT_PATTERN_VERSION, tenantId, serviceId, scopeKey);
                    newEntity.setCurrentNumericValue(initialNum);
                    newEntity.setCurrentAlphaValue(initialAlpha);
                    newEntity.setCurrentFormattedSequence(formattedSeq);
                    newEntity.setTotalAllocated(1L);
                    newEntity.setTotalSuccessful(0L);
                    newEntity.setTotalFailed(0L);
                    newEntity.setIsExhausted(isLast);
                    newEntity.setResetCount(0); // Initial creation is NOT a reset event
                    newEntity.setLastAllocatedOn(LocalDateTime.now());

                    final Long fInitNum = initialNum;
                    final Long fInitAlpha = initialAlpha;
                    final String fFormattedSeq = formattedSeq;

                    return sequenceRepository.save(newEntity)
                            .doOnSuccess(saved -> log.info("Successfully persisted new sequence scope entity for pattern: '{}', scopeKey: '{}'. InitialValue: {}, isExhausted: {}",
                                    config.getPatternId(), scopeKey, saved.getCurrentFormattedSequence(), saved.getIsExhausted()))
                            .map(saved -> new SequenceAllocation(fFormattedSeq, fInitNum, fInitAlpha, scopeKey, true, false, null))
                            .onErrorResume(DuplicateKeyException.class, ex -> {
                                log.warn("Concurrent initial scope creation detected for scopeKey: '{}'. Retrying lookup and allocation", scopeKey);
                                return allocateNextSequence(config, scopeKey, tenantId, serviceId, resetAttributeValues, txnId);
                            });
                }))
                .as(transactionalOperator::transactional);
    }

    /**
     * Determines whether the sequence is exhausted after allocating the given values.
     * If the remaining capacity is 0, this sequence allocation consumed the last available value.
     */
    private boolean checkIfExhaustedAfterAllocation(SeriesType seriesType, Long numVal, Long alphaVal, SequenceConfigDTO seqConfig) {
        if (seriesType == SeriesType.ALPHABETIC) {
            Long remaining = AlphabeticSequenceCalculator.calculateRemainingCapacity(alphaVal, seqConfig);
            log.info("Checking alphabetic exhaustion: currentAlphaIndex={}, remainingCapacity={}", alphaVal, remaining);
            return remaining != null && remaining <= 0L;
        } else if (seriesType == SeriesType.ALPHANUMERIC) {
            Long remaining = AlphanumericSequenceCalculator.calculateRemainingCapacity(alphaVal, numVal, seqConfig);
            log.info("Checking alphanumeric exhaustion: currentAlphaIndex={}, currentNumericValue={}, remainingCapacity={}", alphaVal, numVal, remaining);
            return remaining != null && remaining <= 0L;
        } else {
            Long remaining = NumericSequenceCalculator.calculateRemainingCapacity(numVal, seqConfig);
            log.info("Checking numeric exhaustion: currentNumericValue={}, remainingCapacity={}", numVal, remaining);
            return remaining != null && remaining <= 0L;
        }
    }

    /**
     * Persists the generation log and updates statistics atomically.
     * Enforces uniqueness on the generated identifier; throws SPRuntimeError if a collision occurs.
     */
    public Mono<PatternGenerationLogEntity> recordSuccessfulGeneration(PatternConfigurationDTO config, PatternGenerationRequestDTO request,
                                                                      String generatedValue, SequenceAllocation allocation) {
        log.info("Recording successful generation in pattern_generation_log for pattern: '{}', value: '{}', scopeKey: '{}', purpose: '{}'",
                config.getPatternId(), generatedValue, allocation.getScopeKey(), request.getGenerationPurpose());

        PatternGenerationLogEntity logEntity = new PatternGenerationLogEntity(
                generateLogId(),
                config.getPatternId(),
                DEFAULT_PATTERN_VERSION,
                generatedValue,
                allocation.getFormattedSequence(),
                allocation.getScopeKey(),
                request.getTenantId(),
                request.getServiceId(),
                request.getApplicationId(),
                request.getDocumentId(),
                request.getGenerationPurpose(),
                request.getIdempotencyKey(),
                request.getGeneratedBy()
        );

        return generationLogRepository.save(logEntity)
                .doOnSuccess(saved -> log.info("Successfully saved generation log entity. LogId: '{}', GeneratedValue: '{}', ScopeKey: '{}'",
                        saved.getId(), saved.getGeneratedValue(), saved.getScopeKey()))
                .onErrorResume(DuplicateKeyException.class, ex -> {
                    log.error("Duplicate identifier collision for value: '{}' in pattern: '{}', tenant: '{}', txnId: '{}'. Throwing 409 CONFLICT",
                            generatedValue, config.getPatternId(), request.getTenantId(), request.getTxnId());
                    SPRuntimeError err = new SPRuntimeError(
                            "Identifier collision detected",
                            HttpStatus.CONFLICT,
                            request.getTxnId());
                    Map<String, Object> data = new HashMap<>();
                    data.put("patternId", config.getPatternId());
                    data.put("errorCode", "PATTERN_DUPLICATE_IDENTIFIER");
                    err.setData(data);
                    return Mono.error(err);
                })
                .flatMap(savedLog -> updateScopeStats(config, request, allocation).thenReturn(savedLog));
    }

    private Mono<Void> updateScopeStats(PatternConfigurationDTO config, PatternGenerationRequestDTO request, SequenceAllocation allocation) {
        log.info("Updating sequence success statistics in pattern_sequence for pattern: '{}', scopeKey: '{}'",
                config.getPatternId(), allocation.getScopeKey());

        return sequenceRepository.findByPatternIdAndTenantIdAndScopeKey(config.getPatternId(), request.getTenantId(), allocation.getScopeKey())
                .flatMap(seqEntity -> {
                    long newSuccessful = seqEntity.getTotalSuccessful() != null ? seqEntity.getTotalSuccessful() + 1 : 1L;
                    seqEntity.setTotalSuccessful(newSuccessful);
                    seqEntity.setNewEntity(false);
                    return sequenceRepository.save(seqEntity)
                            .doOnSuccess(saved -> log.info("Updated totalSuccessful to {} for scopeKey: '{}'", newSuccessful, allocation.getScopeKey()))
                            .then();
                });
    }

    public Mono<Void> recordGenerationFailure(String patternId, String tenantId, String scopeKey) {
        log.warn("Recording generation failure in pattern_sequence for pattern: '{}', tenantId: '{}', scopeKey: '{}'",
                patternId, tenantId, scopeKey);

        return sequenceRepository.findByPatternIdAndTenantIdAndScopeKey(patternId, tenantId, scopeKey)
                .flatMap(seq -> {
                    long newFailed = seq.getTotalFailed() != null ? seq.getTotalFailed() + 1 : 1L;
                    seq.setTotalFailed(newFailed);
                    seq.setNewEntity(false);
                    return sequenceRepository.save(seq)
                            .doOnSuccess(saved -> log.warn("Updated totalFailed to {} for scopeKey: '{}'", newFailed, scopeKey))
                            .then();
                });
    }

    private String generateLogId() {
        try {
            return createUniqueId();
        } catch (Exception ex) {
            log.warn("Failed to generate snowflake ID, falling back to UUID", ex);
            return UUID.randomUUID().toString();
        }
    }
}
