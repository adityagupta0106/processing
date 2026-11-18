package com.serviceplus.form.validation.pattern.service;

import com.serviceplus.form.validation.dto.FormData;
import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationData;
import com.serviceplus.form.validation.pattern.dto.PatternComponentDTO;
import com.serviceplus.form.validation.pattern.dto.PatternConfigurationDTO;
import com.serviceplus.form.validation.pattern.dto.PatternGenerationRequestDTO;
import com.serviceplus.form.validation.pattern.dto.PatternGenerationResultDTO;
import com.serviceplus.form.validation.pattern.dto.ResetAttributeDTO;
import com.serviceplus.form.validation.pattern.dto.SequenceConfigDTO;
import com.serviceplus.form.validation.pattern.enums.SeriesType;
import com.serviceplus.form.validation.pattern.renderer.PatternRenderer;
import com.serviceplus.form.validation.pattern.renderer.PlaceholderResolver;
import com.serviceplus.form.validation.pattern.repository.PatternGenerationLogRepository;
import com.serviceplus.form.validation.pattern.sequence.AlphabeticSequenceCalculator;
import com.serviceplus.form.validation.pattern.sequence.AlphanumericSequenceCalculator;
import com.serviceplus.form.validation.pattern.sequence.NumericSequenceCalculator;
import com.serviceplus.form.validation.pattern.sequence.SequenceManager;
import com.serviceplus.form.validation.pattern.validator.PatternConfigurationValidator;
import com.serviceplus.form.validation.repository.ApplicationDataRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PatternGenerationServiceImpl implements PatternGenerationService {

    private static final Logger log = LogManager.getLogger("applicationFlowLogger");

    private final SequenceManager sequenceManager;
    private final PatternGenerationLogRepository generationLogRepository;
    private final ApplicationDataRepository applicationDataRepository;

    public PatternGenerationServiceImpl(SequenceManager sequenceManager,
                                        PatternGenerationLogRepository generationLogRepository,
                                        ApplicationDataRepository applicationDataRepository) {
        this.sequenceManager = sequenceManager;
        this.generationLogRepository = generationLogRepository;
        this.applicationDataRepository = applicationDataRepository;
    }

    @Override
    public Mono<PatternGenerationResultDTO> generate(PatternConfigurationDTO config, PatternGenerationRequestDTO request) {
        log.info("Starting pattern generation for patternId: '{}', tenantId: '{}', serviceId: {}, applicationId: '{}', purpose: '{}', idempotencyKey: '{}', txnId: '{}'",
                config.getPatternId(), request.getTenantId(), request.getServiceId(), request.getApplicationId(),
                request.getGenerationPurpose(), request.getIdempotencyKey(), request.getTxnId());

        // 1. Validate configuration
        PatternConfigurationValidator.validate(config, request.getTxnId());

        // 2. Check idempotency
        return generationLogRepository.findByTenantIdAndIdempotencyKey(request.getTenantId(), request.getIdempotencyKey())
                .flatMap(existingLog -> {
                    log.info("Idempotent request match found for idempotencyKey: '{}'. Returning cached generated value: '{}', patternId: '{}'",
                            request.getIdempotencyKey(), existingLog.getGeneratedValue(), existingLog.getPatternId());
                    PatternGenerationResultDTO result = new PatternGenerationResultDTO(
                            existingLog.getPatternId(),
                            existingLog.getPatternVersion(),
                            existingLog.getGeneratedValue(),
                            existingLog.getSequenceRepresentation(),
                            null,
                            existingLog.getScopeKey(),
                            existingLog.getGeneratedAt(),
                            existingLog.getGenerationPurpose(),
                            existingLog.getStatus()
                    );
                    result.setIdempotent(true);
                    return Mono.just(result);
                })
                .switchIfEmpty(
                        Mono.defer(() -> resolveDataAndExecute(config, request))
                );
    }

    /**
     * Resolves mapped dynamic fields from application_attr_data (if mappedAttribute is configured)
     * and proceeds with sequence allocation and identifier rendering.
     */
    private Mono<PatternGenerationResultDTO> resolveDataAndExecute(PatternConfigurationDTO config, PatternGenerationRequestDTO request) {
        Map<String, Object> formAttrs = request.getFormAttributes() != null ? new HashMap<>(request.getFormAttributes()) : new HashMap<>();

        // Identify any mapped attributes that need to be fetched from application_attr_data
        List<String> neededMappedAttrs = new ArrayList<>();
        if (config.getComponents() != null) {
            for (PatternComponentDTO comp : config.getComponents()) {
                if (comp.getMappedAttribute() != null && !comp.getMappedAttribute().isBlank()
                        && !formAttrs.containsKey(comp.getMappedAttribute())) {
                    neededMappedAttrs.add(comp.getMappedAttribute());
                }
            }
        }
        if (config.getPrefixConfig() != null && config.getPrefixConfig().getMappedAttribute() != null
                && !config.getPrefixConfig().getMappedAttribute().isBlank()
                && !formAttrs.containsKey(config.getPrefixConfig().getMappedAttribute())) {
            neededMappedAttrs.add(config.getPrefixConfig().getMappedAttribute());
        }
        if (config.getResetConfig() != null && config.getResetConfig().getResetAttributes() != null) {
            for (ResetAttributeDTO resetAttr : config.getResetConfig().getResetAttributes()) {
                if (resetAttr != null) {
                    if (resetAttr.getMappedAttribute() != null && !resetAttr.getMappedAttribute().isBlank()
                            && !formAttrs.containsKey(resetAttr.getMappedAttribute())
                            && !neededMappedAttrs.contains(resetAttr.getMappedAttribute())) {
                        neededMappedAttrs.add(resetAttr.getMappedAttribute());
                    }
                    String token = resetAttr.getToken();
                    if (token != null && !token.isBlank() && !token.startsWith("{")
                            && !formAttrs.containsKey(token)
                            && !neededMappedAttrs.contains(token)) {
                        neededMappedAttrs.add(token);
                    }
                }
            }
        }

        if (!neededMappedAttrs.isEmpty() && request.getApplicationId() != null && request.getTenantId() != null) {
            log.info("Fetching dynamic attributes {} from application_attr_data for applicationId: '{}', tenantId: '{}'",
                    neededMappedAttrs, request.getApplicationId(), request.getTenantId());

            return applicationDataRepository.findByApplicationIdAndTenantId(request.getApplicationId(), request.getTenantId())
                    .doOnNext(ApplicationData::convertAttrValToObject)
                    .flatMap(appDataEntity -> {
                        FormData formData = appDataEntity.getAttrValObject();
                        if (formData != null) {
                            for (String attrKey : neededMappedAttrs) {
                                Object val = PlaceholderResolver.extractCurrentAttributeValue(formData, attrKey);
                                if (val != null) {
                                    formAttrs.put(attrKey, val);
                                    log.info("Extracted attribute '{}' from application_attr_data: value='{}'", attrKey, val);
                                } else {
                                    log.warn("Attribute '{}' not found or empty in application_attr_data for applicationId: '{}'",
                                            attrKey, request.getApplicationId());
                                }
                            }
                        }
                        request.setFormAttributes(formAttrs);
                        return executeGeneration(config, request);
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("No application_attr_data record found for applicationId: '{}', tenantId: '{}'. Proceeding with context attributes.",
                                request.getApplicationId(), request.getTenantId());
                        request.setFormAttributes(formAttrs);
                        return executeGeneration(config, request);
                    }));
        }

        log.info("No external attribute lookups required for pattern: '{}'. Proceeding directly to generation.", config.getPatternId());
        request.setFormAttributes(formAttrs);
        return executeGeneration(config, request);
    }

    private Mono<PatternGenerationResultDTO> executeGeneration(PatternConfigurationDTO config, PatternGenerationRequestDTO request) {
        LocalDateTime genDate = request.getGenerationDate() != null ? request.getGenerationDate() : LocalDateTime.now();

        // 3. Resolve non-sequence placeholders
        Map<String, String> resolvedPlaceholders = PlaceholderResolver.resolvePlaceholders(
                config, request.getFormAttributes(), request.getSystemVariables(), genDate);
        log.info("Resolved placeholders for pattern: '{}' -> {}", config.getPatternId(), resolvedPlaceholders);

        // 4. Extract reset attributes
        Map<String, String> resetAttributeValues = PlaceholderResolver.extractResetAttributeValues(config, resolvedPlaceholders);
        log.info("Extracted reset attribute values for pattern: '{}' -> {}", config.getPatternId(), resetAttributeValues);

        // 5. Derive canonical composite scope key (independent of UI ordering)
        String scopeKey = sequenceManager.buildCanonicalScopeKey(config, resetAttributeValues, request.getTenantId(), request.getServiceId(), request.getTxnId());
        log.info("Derived canonical composite scopeKey: '{}' for patternId: '{}'", scopeKey, config.getPatternId());

        // 6. Atomically allocate next sequence value
        return sequenceManager.allocateNextSequence(config, scopeKey, request.getTenantId(), request.getServiceId(), resetAttributeValues, request.getTxnId())
                .flatMap(allocation -> {
                    log.info("Allocated sequence value: '{}' (Numeric: {}, Alpha: {}) for pattern: '{}', scopeKey: '{}'",
                            allocation.getFormattedSequence(), allocation.getNumericValue(), allocation.getAlphaIndex(),
                            config.getPatternId(), scopeKey);

                    // 7. Render final identifier through components array with checksum prior to checksum token
                    String finalIdentifier = PatternRenderer.render(config, resolvedPlaceholders, allocation.getFormattedSequence(), request.getTxnId());
                    log.info("Successfully rendered pattern identifier: '{}' for patternId: '{}', scopeKey: '{}'",
                            finalIdentifier, config.getPatternId(), scopeKey);

                    // 8. Persist authoritative generation log and update statistics
                    return sequenceManager.recordSuccessfulGeneration(config, request, finalIdentifier, allocation)
                            .map(savedLog -> {
                                log.info("Pattern generation completed successfully. PatternId: '{}', GeneratedValue: '{}', Purpose: '{}', LogId: '{}'",
                                        config.getPatternId(), finalIdentifier, request.getGenerationPurpose(), savedLog.getId());
                                return new PatternGenerationResultDTO(
                                        config.getPatternId(),
                                        SequenceManager.DEFAULT_PATTERN_VERSION,
                                        finalIdentifier,
                                        allocation.getFormattedSequence(),
                                        allocation.getNumericValue(),
                                        scopeKey,
                                        savedLog.getGeneratedAt(),
                                        request.getGenerationPurpose(),
                                        savedLog.getStatus()
                                );
                            })
                            .doOnError(ex -> {
                                log.error("Generation recording failed for value: '{}' in pattern: '{}', scopeKey: '{}'. Recording failure.",
                                        finalIdentifier, config.getPatternId(), scopeKey, ex);
                                sequenceManager.recordGenerationFailure(config.getPatternId(), request.getTenantId(), scopeKey).subscribe();
                            });
                });
    }

    @Override
    public Mono<PatternGenerationResultDTO> generateArn(ServiceJSONDTO service, UserSessionObject user, String applicationId,
                                                        String txnId, Map<String, Object> formAttributes, Map<String, Object> systemVariables) {
        log.info("Checking configured ARN pattern for serviceId: {}, applicationId: '{}', txnId: '{}'",
                service != null ? service.getServiceId() : null, applicationId, txnId);

        if (service == null) {
            log.warn("ServiceJSONDTO is null. Cannot generate ARN pattern.");
            return Mono.empty();
        }

        List<PatternConfigurationDTO> patterns = service.getPatternConfigurations();
        if (patterns == null || patterns.isEmpty()) {
            log.info("No pattern configurations found in ServiceJSONDTO for serviceId: {}. Retaining default ARN generation behavior.", service.getServiceId());
            return Mono.empty();
        }

        // Find the pattern designated as ARN pattern
        PatternConfigurationDTO arnPattern = patterns.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsArnPattern()))
                .findFirst()
                .orElse(null);

        if (arnPattern == null) {
            log.info("No active pattern marked with isArnPattern=true for serviceId: {}. Retaining default ARN generation behavior.", service.getServiceId());
            return Mono.empty();
        }

        log.info("Found active ARN pattern: '{}' for serviceId: {}. Proceeding with pattern-based ARN generation.",
                arnPattern.getPatternId(), service.getServiceId());

        PatternGenerationRequestDTO request = PatternGenerationRequestDTO.forArn(
                arnPattern.getPatternId(),
                user.getTenantId(),
                service.getServiceId(),
                service.getServiceName(),
                applicationId,
                txnId,
                formAttributes,
                systemVariables,
                String.valueOf(user.getUserID())
        );

        return generate(arnPattern, request)
                .doOnSuccess(res -> {
                    if (res != null) {
                        log.info("Successfully generated pattern-based ARN: '{}' for applicationId: '{}'",
                                res.getGeneratedValue(), applicationId);
                    }
                });
    }

    @Override
    public Mono<PatternGenerationResultDTO> regenerate(PatternConfigurationDTO config, PatternGenerationRequestDTO request, String previousIdentifier) {
        log.info("Regenerating identifier for patternId: '{}', previousIdentifier: '{}', applicationId: '{}'",
                config.getPatternId(), previousIdentifier, request.getApplicationId());

        return generate(config, request)
                .flatMap(newResult -> {
                    if (previousIdentifier != null && !previousIdentifier.isBlank()) {
                        log.info("Superseding previous generation log with value: '{}' by new value: '{}'",
                                previousIdentifier, newResult.getGeneratedValue());
                        return generationLogRepository.findByTenantIdAndGeneratedValue(request.getTenantId(), previousIdentifier)
                                .flatMap(oldLog -> {
                                    oldLog.setStatus("SUPERSEDED");
                                    oldLog.setSupersededBy(newResult.getGeneratedValue());
                                    oldLog.setNewEntity(false);
                                    return generationLogRepository.save(oldLog)
                                            .doOnSuccess(s -> log.info("Successfully marked log for previous identifier '{}' as SUPERSEDED", previousIdentifier));
                                })
                                .thenReturn(newResult);
                    }
                    return Mono.just(newResult);
                });
    }

    @Override
    public Mono<PatternGenerationResultDTO> preview(PatternConfigurationDTO config, PatternGenerationRequestDTO request) {
        log.info("Generating preview for patternId: '{}', tenantId: '{}'", config.getPatternId(), request.getTenantId());

        PatternConfigurationValidator.validate(config, request.getTxnId());
        LocalDateTime genDate = request.getGenerationDate() != null ? request.getGenerationDate() : LocalDateTime.now();

        Map<String, String> resolvedPlaceholders = PlaceholderResolver.resolvePlaceholders(
                config, request.getFormAttributes(), request.getSystemVariables(), genDate);

        SequenceConfigDTO seqConfig = config.getSequenceConfig() != null ? config.getSequenceConfig() : new SequenceConfigDTO();
        SeriesType seriesType = seqConfig.getSeriesType() != null ? seqConfig.getSeriesType() : SeriesType.NUMERIC;
        String sampleSeq;

        if (seriesType == SeriesType.ALPHABETIC) {
            sampleSeq = AlphabeticSequenceCalculator.formatSequence(null, seqConfig);
        } else if (seriesType == SeriesType.ALPHANUMERIC) {
            sampleSeq = AlphanumericSequenceCalculator.formatSequence(null, null, seqConfig);
        } else {
            sampleSeq = NumericSequenceCalculator.formatSequence(null, seqConfig);
        }

        String previewIdentifier = PatternRenderer.render(config, resolvedPlaceholders, sampleSeq, request.getTxnId());

        Map<String, String> resetValues = PlaceholderResolver.extractResetAttributeValues(config, resolvedPlaceholders);
        String previewScope = sequenceManager.buildCanonicalScopeKey(config, resetValues, request.getTenantId(), request.getServiceId(), request.getTxnId());
        log.info("Generated preview identifier: '{}' for patternId: '{}', sampleSeq: '{}', previewScope: '{}'",
                previewIdentifier, config.getPatternId(), sampleSeq, previewScope);

        PatternGenerationResultDTO result = new PatternGenerationResultDTO(
                config.getPatternId(),
                SequenceManager.DEFAULT_PATTERN_VERSION,
                previewIdentifier,
                sampleSeq,
                1L,
                previewScope,
                LocalDateTime.now(),
                request.getGenerationPurpose(),
                "PREVIEW"
        );
        return Mono.just(result);
    }
}
