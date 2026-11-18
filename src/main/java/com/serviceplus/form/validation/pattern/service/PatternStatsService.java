package com.serviceplus.form.validation.pattern.service;

import com.serviceplus.form.validation.pattern.dto.AttributeAggregateStatsDTO;
import com.serviceplus.form.validation.pattern.dto.PatternConfigurationDTO;
import com.serviceplus.form.validation.pattern.dto.PatternScopeStatsDTO;
import com.serviceplus.form.validation.pattern.dto.SequenceConfigDTO;
import com.serviceplus.form.validation.pattern.entity.PatternSequenceEntity;
import com.serviceplus.form.validation.pattern.enums.SeriesType;
import com.serviceplus.form.validation.pattern.repository.PatternSequenceRepository;
import com.serviceplus.form.validation.pattern.sequence.AlphabeticSequenceCalculator;
import com.serviceplus.form.validation.pattern.sequence.AlphanumericSequenceCalculator;
import com.serviceplus.form.validation.pattern.sequence.NumericSequenceCalculator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class PatternStatsService {

    private static final Logger log = LogManager.getLogger("applicationFlowLogger");

    private final PatternSequenceRepository sequenceRepository;

    public PatternStatsService(PatternSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    public Mono<PatternScopeStatsDTO> getScopeStats(PatternConfigurationDTO config, String tenantId, String scopeKey) {
        log.info("Fetching scope statistics for patternId: '{}', tenantId: '{}', scopeKey: '{}'",
                config != null ? config.getPatternId() : "null", tenantId, scopeKey);

        SequenceConfigDTO seqConfig = config.getSequenceConfig() != null ? config.getSequenceConfig() : new SequenceConfigDTO();
        SeriesType seriesType = seqConfig.getSeriesType() != null ? seqConfig.getSeriesType() : SeriesType.NUMERIC;

        return sequenceRepository.findByPatternIdAndTenantIdAndScopeKey(config.getPatternId(), tenantId, scopeKey)
                .map(entity -> {
                    PatternScopeStatsDTO dto = new PatternScopeStatsDTO();
                    dto.setCanonicalScopeKey(entity.getScopeKey());
                    dto.setCurrentSequenceValue(entity.getCurrentFormattedSequence());
                    dto.setTotalSuccessful(entity.getTotalSuccessful());
                    dto.setTotalAllocated(entity.getTotalAllocated());
                    dto.setTotalFailed(entity.getTotalFailed());
                    dto.setScopeInitializedAt(entity.getCreatedOn());
                    dto.setLastGenerationAt(entity.getLastAllocatedOn());
                    dto.setLastResetAt(entity.getLastResetOn());
                    dto.setResetCount(entity.getResetCount());
                    dto.setIsExhausted(entity.getIsExhausted());

                    // Parse reset attributes directly from canonical scopeKey
                    dto.setResetAttributes(extractResetAttributesFromScopeKey(entity.getScopeKey()));

                    // Compute capacity and consumption based on series type
                    if (seriesType == SeriesType.NUMERIC) {
                        dto.setMinConfiguredValue(seqConfig.getMinValue());
                        dto.setMaxConfiguredValue(seqConfig.getMaxValue());
                        dto.setIncrement(seqConfig.getIncrement());
                        dto.setSequenceLength(seqConfig.getDigits());
                        dto.setRemainingCapacity(NumericSequenceCalculator.calculateRemainingCapacity(entity.getCurrentNumericValue(), seqConfig));
                        dto.setConsumptionPercentage(NumericSequenceCalculator.calculateConsumptionPercentage(entity.getCurrentNumericValue(), seqConfig));
                    } else if (seriesType == SeriesType.ALPHABETIC) {
                        dto.setSequenceLength(seqConfig.getAlphabeticLength());
                        dto.setRemainingCapacity(AlphabeticSequenceCalculator.calculateRemainingCapacity(entity.getCurrentAlphaValue(), seqConfig));
                        dto.setConsumptionPercentage(AlphabeticSequenceCalculator.calculateConsumptionPercentage(entity.getCurrentAlphaValue(), seqConfig));
                    } else if (seriesType == SeriesType.ALPHANUMERIC) {
                        dto.setSequenceLength(seqConfig.getTotalLength());
                        dto.setRemainingCapacity(AlphanumericSequenceCalculator.calculateRemainingCapacity(entity.getCurrentAlphaValue(), entity.getCurrentNumericValue(), seqConfig));
                        dto.setConsumptionPercentage(AlphanumericSequenceCalculator.calculateConsumptionPercentage(entity.getCurrentAlphaValue(), entity.getCurrentNumericValue(), seqConfig));
                    }

                    log.info("Computed stats for scopeKey: '{}': totalSuccessful={}, totalAllocated={}, isExhausted={}, remainingCapacity={}",
                            scopeKey, dto.getTotalSuccessful(), dto.getTotalAllocated(), dto.getIsExhausted(), dto.getRemainingCapacity());
                    return dto;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("No sequence scope record found for patternId: '{}', scopeKey: '{}'", config.getPatternId(), scopeKey);
                    return Mono.empty();
                }));
    }

    public Flux<PatternScopeStatsDTO> getAllScopeStats(PatternConfigurationDTO config, String tenantId) {
        log.info("Fetching all scope statistics for patternId: '{}', tenantId: '{}'",
                config != null ? config.getPatternId() : "null", tenantId);
        return sequenceRepository.findAllByPatternIdAndTenantId(config.getPatternId(), tenantId)
                .flatMap(entity -> getScopeStats(config, tenantId, entity.getScopeKey()));
    }

    public Flux<AttributeAggregateStatsDTO> getAggregateStatsForAttribute(String patternId, String tenantId, String attributeName) {
        log.info("Fetching aggregate stats for attribute '{}' across scopes in patternId: '{}', tenantId: '{}'",
                attributeName, patternId, tenantId);
        return sequenceRepository.findAllByPatternIdAndTenantId(patternId, tenantId)
                .collectList()
                .flatMapMany(scopes -> {
                    Map<String, AttributeAggregateStatsDTO> aggMap = new HashMap<>();
                    for (PatternSequenceEntity scope : scopes) {
                        Map<String, String> attrs = extractResetAttributesFromScopeKey(scope.getScopeKey());
                        String attrVal = attrs.get(attributeName.toUpperCase());
                        if (attrVal == null) {
                            attrVal = attrs.get(attributeName);
                        }
                        if (attrVal != null) {
                            AttributeAggregateStatsDTO agg = aggMap.computeIfAbsent(attrVal, k ->
                                    new AttributeAggregateStatsDTO(attributeName, k, 0L, 0L, 0L, 0));
                            agg.setTotalGenerations(agg.getTotalGenerations() + (scope.getTotalSuccessful() != null ? scope.getTotalSuccessful() : 0L));
                            agg.setTotalAllocated(agg.getTotalAllocated() + (scope.getTotalAllocated() != null ? scope.getTotalAllocated() : 0L));
                            agg.setDistinctScopes(agg.getDistinctScopes() + 1);
                            agg.setResetCount(agg.getResetCount() + (scope.getResetCount() != null ? scope.getResetCount() : 0));
                        }
                    }
                    log.info("Aggregated {} distinct values for attribute '{}' in patternId: '{}'",
                            aggMap.size(), attributeName, patternId);
                    return Flux.fromIterable(aggMap.values());
                });
    }
    private Map<String, String> extractResetAttributesFromScopeKey(String scopeKey) {
        Map<String, String> attrs = new HashMap<>();
        if (scopeKey == null || !scopeKey.contains(":v")) {
            return attrs;
        }
        int vIdx = scopeKey.indexOf(":v");
        int colonAfterV = scopeKey.indexOf(':', vIdx + 1);
        if (colonAfterV < 0 || colonAfterV >= scopeKey.length() - 1) {
            return attrs;
        }
        String attrPart = scopeKey.substring(colonAfterV + 1);
        if ("GLOBAL".equalsIgnoreCase(attrPart)) {
            return attrs;
        }
        String[] pairs = attrPart.split("\\|");
        for (String pair : pairs) {
            int eqIdx = pair.indexOf('=');
            if (eqIdx > 0 && eqIdx < pair.length() - 1) {
                String k = pair.substring(0, eqIdx).trim();
                String v = pair.substring(eqIdx + 1).trim();
                attrs.put(k, v);
            }
        }
        return attrs;
    }
}