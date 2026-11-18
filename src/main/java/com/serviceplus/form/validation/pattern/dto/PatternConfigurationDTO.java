package com.serviceplus.form.validation.pattern.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatternConfigurationDTO {

    private String patternId;

    /**
     * Flag indicating if this pattern is designated as the Application Reference Number (ARN) for the service.
     * Enforced rule: only one pattern per service may be designated as the ARN pattern.
     */
    private Boolean isArnPattern = false;

    private Integer serviceId;

    /**
     * Ordered list of pattern components - the authoritative node through which the pattern is rendered
     */
    private List<PatternComponentDTO> components = new ArrayList<>();

    private PrefixSuffixConfigDTO prefixConfig = new PrefixSuffixConfigDTO();
    private PrefixSuffixConfigDTO suffixConfig = new PrefixSuffixConfigDTO();

    private SequenceConfigDTO sequenceConfig = new SequenceConfigDTO();
    private ResetConfigDTO resetConfig = new ResetConfigDTO();
    private ChecksumConfigDTO checksumConfig = new ChecksumConfigDTO();

    /**
     * Exhaustion policy: THROW_ERROR, BLOCK
     */
    private String exhaustionPolicy = "THROW_ERROR";

    private Boolean idempotencyEnabled = true;

    public PatternConfigurationDTO() {
    }

    public String getPatternId() {
        return patternId;
    }

    public void setPatternId(String patternId) {
        this.patternId = patternId;
    }

    public Boolean getIsArnPattern() {
        return isArnPattern;
    }

    public void setIsArnPattern(Boolean isArnPattern) {
        this.isArnPattern = isArnPattern;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public List<PatternComponentDTO> getComponents() {
        return components;
    }

    public void setComponents(List<PatternComponentDTO> components) {
        this.components = components;
    }

    public PrefixSuffixConfigDTO getPrefixConfig() {
        return prefixConfig;
    }

    public void setPrefixConfig(PrefixSuffixConfigDTO prefixConfig) {
        this.prefixConfig = prefixConfig;
    }

    public PrefixSuffixConfigDTO getSuffixConfig() {
        return suffixConfig;
    }

    public void setSuffixConfig(PrefixSuffixConfigDTO suffixConfig) {
        this.suffixConfig = suffixConfig;
    }

    public SequenceConfigDTO getSequenceConfig() {
        return sequenceConfig;
    }

    public void setSequenceConfig(SequenceConfigDTO sequenceConfig) {
        this.sequenceConfig = sequenceConfig;
    }

    public ResetConfigDTO getResetConfig() {
        return resetConfig;
    }

    public void setResetConfig(ResetConfigDTO resetConfig) {
        this.resetConfig = resetConfig;
    }

    public ChecksumConfigDTO getChecksumConfig() {
        return checksumConfig;
    }

    public void setChecksumConfig(ChecksumConfigDTO checksumConfig) {
        this.checksumConfig = checksumConfig;
    }

    public String getExhaustionPolicy() {
        return exhaustionPolicy;
    }

    public void setExhaustionPolicy(String exhaustionPolicy) {
        this.exhaustionPolicy = exhaustionPolicy;
    }

    public Boolean getIdempotencyEnabled() {
        return idempotencyEnabled;
    }

    public void setIdempotencyEnabled(Boolean idempotencyEnabled) {
        this.idempotencyEnabled = idempotencyEnabled;
    }
}
