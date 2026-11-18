package com.serviceplus.form.validation.Helpers;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.serviceplus.form.validation.dto.DesignatedOfficerDetails;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.pattern.dto.PatternConfigurationDTO;
import com.serviceplus.form.validation.pattern.dto.PatternGenerationRequestDTO;
import com.serviceplus.form.validation.pattern.dto.PatternGenerationResultDTO;
import com.serviceplus.form.validation.pattern.service.PatternGenerationService;

@Component
public class SystemAttributeHelper {

    private static final Logger log = LoggerFactory.getLogger(SystemAttributeHelper.class);

    private final PatternGenerationService patternGenerationService;

    @Autowired
    public SystemAttributeHelper(PatternGenerationService patternGenerationService) {
        this.patternGenerationService = patternGenerationService;
    }

    public SystemAttributeHelper() {
        this.patternGenerationService = null;
    }

    /**
     * Resolves configured required patterns into a map of patternId -> generated pattern value.
     * Each required pattern is resolved independently and keyed by its patternId.
     *
     * @param service service metadata containing patternConfigurations
     * @param applicationDetails application details
     * @param requiredPatterns list of pattern IDs to resolve
     * @param generationPurpose purpose of generation (e.g. "DOCUMENT", "NOTIFICATION")
     * @param contextAttrs runtime context attributes (e.g. form and system attributes)
     * @return map of patternId -> generated pattern value
     */
    public Map<String, String> resolveRequiredPatterns(ServiceMeta service,
                                                       ApplicationDetails applicationDetails,
                                                       List<String> requiredPatterns,
                                                       String generationPurpose,
                                                       Map<String, Object> contextAttrs) {
        Map<String, String> resolvedMap = new HashMap<>();
        if (requiredPatterns == null || requiredPatterns.isEmpty() || service == null || service.getPatternConfigurations() == null) {
            return resolvedMap;
        }

        // Index configured patterns by patternId
        Map<String, PatternConfigurationDTO> patternConfigMap = new HashMap<>();
        for (PatternConfigurationDTO config : service.getPatternConfigurations()) {
            if (config != null && config.getPatternId() != null && !config.getPatternId().isBlank()) {
                patternConfigMap.put(config.getPatternId().trim(), config);
            }
        }

        // Deduplicate pattern IDs while preserving configuration order
        Set<String> uniquePatternIds = new LinkedHashSet<>();
        for (String pid : requiredPatterns) {
            if (pid != null && !pid.isBlank()) {
                uniquePatternIds.add(pid.trim());
            }
        }

        log.info("SystemAttributeHelper: Resolving {} required pattern(s) {} for purpose: '{}', applicationId: '{}'",
                uniquePatternIds.size(), uniquePatternIds, generationPurpose,
                applicationDetails != null ? applicationDetails.getApplicationId() : "null");

        for (String patternId : uniquePatternIds) {
            PatternConfigurationDTO config = patternConfigMap.get(patternId);
            if (config == null) {
                log.warn("Pattern configuration not found in service metadata for patternId: '{}'", patternId);
                continue;
            }

            String generated = generatePattern(config, service, applicationDetails, contextAttrs, generationPurpose);
            log.info("SystemAttributeHelper: Successfully generated pattern value '{}' for patternId '{}'", generated, patternId);
            resolvedMap.put(patternId, generated);
        }

        return resolvedMap;
    }

    private String generatePattern(PatternConfigurationDTO pattern,
                                   ServiceMeta service,
                                   ApplicationDetails applicationDetails,
                                   Map<String, Object> contextAttrs,
                                   String generationPurpose) {
        if (patternGenerationService == null) {
            log.warn("PatternGenerationService is null. Cannot generate pattern for patternId: {}", pattern.getPatternId());
            return "";
        }
        try {
            PatternGenerationRequestDTO req = new PatternGenerationRequestDTO();
            req.setPatternId(pattern.getPatternId());
            String tenantId = applicationDetails != null && applicationDetails.getTenantId() != null
                    ? applicationDetails.getTenantId() : "DEFAULT";
            req.setTenantId(tenantId);
            req.setServiceId(service != null ? service.getServiceId() : null);
            if (service != null && service.getServiceName() != null) {
                req.setServiceCode(service.getServiceName());
            }
            req.setApplicationId(applicationDetails != null ? applicationDetails.getApplicationId() : null);
            String purpose = generationPurpose != null && !generationPurpose.isBlank() ? generationPurpose : "DOCUMENT";
            req.setGenerationPurpose(purpose);
            req.setFormAttributes(contextAttrs != null ? new HashMap<>(contextAttrs) : new HashMap<>());
            req.setSystemVariables(contextAttrs != null ? new HashMap<>(contextAttrs) : new HashMap<>());
            String appId = applicationDetails != null && applicationDetails.getApplicationId() != null
                    ? applicationDetails.getApplicationId() : "APP";
            req.setIdempotencyKey(purpose.concat("_").concat(appId).concat("_").concat(pattern.getPatternId()));

            CompletableFuture<PatternGenerationResultDTO> future = CompletableFuture.supplyAsync(() ->
                    patternGenerationService.generate(pattern, req).block()
            );
            PatternGenerationResultDTO result = future.get(10, TimeUnit.SECONDS);
            if (result != null && result.getGeneratedValue() != null) {
                return result.getGeneratedValue();
            }
        } catch (Exception ex) {
            log.error("Failed to generate pattern for patternId '{}': {}", pattern.getPatternId(), ex.getMessage());
        }
        return "";
    }

    /**
     * Primary method: maps standard system attributes and resolves configured required patterns into systemAttrMap.
     */
    public Map<String, Object> systemAttrMap(Map<String, Object> systemAttrMap,
                                             ApplicationDetails applicationDetails,
                                             ServiceMeta service,
                                             String documentTinyUrl,
                                             List<String> requiredPatterns,
                                             String generationPurpose) {
        if (systemAttrMap == null) {
            systemAttrMap = new HashMap<>();
        }
        if (applicationDetails != null) {
            systemAttrMap.put("Sys_1", applicationDetails.getApplyDate() != null ? applicationDetails.getApplyDate().toLocalDate().toString() : "");
            systemAttrMap.put("Sys_3", applicationDetails.getReferenceNo());
            systemAttrMap.put("Sys_1340", applicationDetails.getApplyDate() != null ? applicationDetails.getApplyDate().toLocalDate().toString() : "");
            systemAttrMap.put("Sys_2025", applicationDetails.getServiceName());
            systemAttrMap.put("Sys_2040", applicationDetails.getApplicationId());
            systemAttrMap.put("Sys_2034", applicationDetails.getBeneficiaryName());
            systemAttrMap.put("Sys_2081", applicationDetails.getActionTinyUrl());
            if (applicationDetails.getDueDate() != null) {
                systemAttrMap.put("Sys_17", applicationDetails.getDueDate().toString());
            }
            if (applicationDetails.getAppliedLocationName() != null) {
                systemAttrMap.put("Sys_2035", applicationDetails.getAppliedLocationName());
            }
            if (!systemAttrMap.containsKey("Sys_2029")) {
                systemAttrMap.put("Sys_2029", "Online");
            }
        }
        if (documentTinyUrl != null && !documentTinyUrl.isBlank()) {
            systemAttrMap.put("Sys_2077", documentTinyUrl);
        }
        if (service != null) {
            systemAttrMap.put("Sys_2098", service.getDepartmentName());
            if (service.getDesignatedOfficerDetails() != null) {
                DesignatedOfficerDetails doDetails = service.getDesignatedOfficerDetails();
                if (doDetails.getName() != null && !doDetails.getName().isBlank()) {
                    systemAttrMap.put("Sys_1335", doDetails.getName());
                }
                if (doDetails.getEmail() != null && !doDetails.getEmail().isBlank()) {
                    systemAttrMap.put("Sys_1338", doDetails.getEmail());
                }
                if (doDetails.getAddress() != null && !doDetails.getAddress().isBlank()) {
                    systemAttrMap.put("Sys_1336", doDetails.getAddress());
                }
                if (doDetails.getPhone() != null && !doDetails.getPhone().isBlank()) {
                    systemAttrMap.put("Sys_1339", doDetails.getPhone());
                }
            }
        }

        if (requiredPatterns != null && !requiredPatterns.isEmpty()) {
            Map<String, String> patternMap = resolveRequiredPatterns(
                    service, applicationDetails, requiredPatterns, generationPurpose, systemAttrMap);
            systemAttrMap.putAll(patternMap);
            log.info("SystemAttributeHelper: Populated systemAttrMap with pattern keys: {}", patternMap.keySet());
        }

        return systemAttrMap;
    }

    /**
     * Document generation specific overload.
     */
    public Map<String, Object> systemAttrMapForDocument(Map<String, Object> systemAttrMap,
                                                        ApplicationDetails applicationDetails,
                                                        ServiceMeta service,
                                                        String documentTinyUrl,
                                                        List<String> requiredPatterns) {
        return systemAttrMap(systemAttrMap, applicationDetails, service, documentTinyUrl, requiredPatterns, "DOCUMENT");
    }

    /**
     * Notification generation specific overload.
     */
    public Map<String, Object> systemAttrMapForNotification(Map<String, Object> systemAttrMap,
                                                            ApplicationDetails applicationDetails,
                                                            ServiceMeta service,
                                                            List<String> requiredPatterns) {
        return systemAttrMap(systemAttrMap, applicationDetails, service, null, requiredPatterns, "NOTIFICATION");
    }

    /**
     * Backward-compatible 4-argument method.
     * Note: sys_2090 is completely removed and never mapped.
     */
    public Map<String, Object> systemAttrMap(Map<String, Object> systemAttrMap,
                                             ApplicationDetails applicationDetails,
                                             ServiceMeta service,
                                             String documentTinyUrl) {
        return systemAttrMap(systemAttrMap, applicationDetails, service, documentTinyUrl, null, null);
    }

    /**
     * Overload accepting requiredPatterns and defaulting to DOCUMENT purpose.
     */
    public Map<String, Object> systemAttrMap(Map<String, Object> systemAttrMap,
                                             ApplicationDetails applicationDetails,
                                             ServiceMeta service,
                                             String documentTinyUrl,
                                             List<String> requiredPatterns) {
        return systemAttrMap(systemAttrMap, applicationDetails, service, documentTinyUrl, requiredPatterns, "DOCUMENT");
    }

}
