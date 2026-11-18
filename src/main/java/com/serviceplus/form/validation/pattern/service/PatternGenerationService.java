package com.serviceplus.form.validation.pattern.service;

import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.pattern.dto.PatternConfigurationDTO;
import com.serviceplus.form.validation.pattern.dto.PatternGenerationRequestDTO;
import com.serviceplus.form.validation.pattern.dto.PatternGenerationResultDTO;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface PatternGenerationService {

    Mono<PatternGenerationResultDTO> generate(PatternConfigurationDTO config, PatternGenerationRequestDTO request);

    /**
     * Generates ARN from configured ServiceJSONDTO metadata.
     * If no active ARN pattern is configured, returns Mono.empty() to allow graceful legacy fallback.
     */
    Mono<PatternGenerationResultDTO> generateArn(ServiceJSONDTO service, UserSessionObject user, String applicationId,
                                                String txnId, Map<String, Object> formAttributes, Map<String, Object> systemVariables);

    Mono<PatternGenerationResultDTO> preview(PatternConfigurationDTO config, PatternGenerationRequestDTO request);

    Mono<PatternGenerationResultDTO> regenerate(PatternConfigurationDTO config, PatternGenerationRequestDTO request, String previousIdentifier);
}
