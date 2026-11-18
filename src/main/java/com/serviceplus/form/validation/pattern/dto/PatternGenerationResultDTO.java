package com.serviceplus.form.validation.pattern.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatternGenerationResultDTO {

    private String patternId;
    private Integer patternVersion;
    private String generatedValue;
    private String sequenceValue;
    private Long numericSequence;
    private String compositeScopeKey;
    private LocalDateTime generationTimestamp;
    private String generationPurpose;
    private String status;
    private Boolean idempotent = false;

    public PatternGenerationResultDTO() {
    }

    public PatternGenerationResultDTO(String patternId, Integer patternVersion, String generatedValue,
                                     String sequenceValue, Long numericSequence, String compositeScopeKey,
                                     LocalDateTime generationTimestamp, String generationPurpose, String status) {
        this.patternId = patternId;
        this.patternVersion = patternVersion;
        this.generatedValue = generatedValue;
        this.sequenceValue = sequenceValue;
        this.numericSequence = numericSequence;
        this.compositeScopeKey = compositeScopeKey;
        this.generationTimestamp = generationTimestamp;
        this.generationPurpose = generationPurpose;
        this.status = status;
    }

    public String getPatternId() {
        return patternId;
    }

    public void setPatternId(String patternId) {
        this.patternId = patternId;
    }

    public Integer getPatternVersion() {
        return patternVersion;
    }

    public void setPatternVersion(Integer patternVersion) {
        this.patternVersion = patternVersion;
    }

    public String getGeneratedValue() {
        return generatedValue;
    }

    public void setGeneratedValue(String generatedValue) {
        this.generatedValue = generatedValue;
    }

    public String getSequenceValue() {
        return sequenceValue;
    }

    public void setSequenceValue(String sequenceValue) {
        this.sequenceValue = sequenceValue;
    }

    public Long getNumericSequence() {
        return numericSequence;
    }

    public void setNumericSequence(Long numericSequence) {
        this.numericSequence = numericSequence;
    }

    public String getCompositeScopeKey() {
        return compositeScopeKey;
    }

    public void setCompositeScopeKey(String compositeScopeKey) {
        this.compositeScopeKey = compositeScopeKey;
    }

    public LocalDateTime getGenerationTimestamp() {
        return generationTimestamp;
    }

    public void setGenerationTimestamp(LocalDateTime generationTimestamp) {
        this.generationTimestamp = generationTimestamp;
    }

    public String getGenerationPurpose() {
        return generationPurpose;
    }

    public void setGenerationPurpose(String generationPurpose) {
        this.generationPurpose = generationPurpose;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIdempotent() {
        return idempotent;
    }

    public void setIdempotent(Boolean idempotent) {
        this.idempotent = idempotent;
    }
}
