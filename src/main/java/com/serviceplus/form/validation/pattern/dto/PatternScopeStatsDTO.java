package com.serviceplus.form.validation.pattern.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatternScopeStatsDTO {

    private String canonicalScopeKey;
    private Map<String, String> resetAttributes;
    private String initialSequenceValue;
    private String currentSequenceValue;
    private Long minConfiguredValue;
    private Long maxConfiguredValue;
    private Long increment;
    private Integer sequenceLength;
    private Long totalSuccessful = 0L;
    private Long totalAllocated = 0L;
    private Long totalFailed = 0L;
    private Long remainingCapacity;
    private Double consumptionPercentage;
    private LocalDateTime scopeInitializedAt;
    private LocalDateTime lastGenerationAt;
    private LocalDateTime lastResetAt;
    private Integer resetCount = 0;
    private Boolean isExhausted = false;

    public PatternScopeStatsDTO() {
    }

    public String getCanonicalScopeKey() {
        return canonicalScopeKey;
    }

    public void setCanonicalScopeKey(String canonicalScopeKey) {
        this.canonicalScopeKey = canonicalScopeKey;
    }

    public Map<String, String> getResetAttributes() {
        return resetAttributes;
    }

    public void setResetAttributes(Map<String, String> resetAttributes) {
        this.resetAttributes = resetAttributes;
    }

    public String getInitialSequenceValue() {
        return initialSequenceValue;
    }

    public void setInitialSequenceValue(String initialSequenceValue) {
        this.initialSequenceValue = initialSequenceValue;
    }

    public String getCurrentSequenceValue() {
        return currentSequenceValue;
    }

    public void setCurrentSequenceValue(String currentSequenceValue) {
        this.currentSequenceValue = currentSequenceValue;
    }

    public Long getMinConfiguredValue() {
        return minConfiguredValue;
    }

    public void setMinConfiguredValue(Long minConfiguredValue) {
        this.minConfiguredValue = minConfiguredValue;
    }

    public Long getMaxConfiguredValue() {
        return maxConfiguredValue;
    }

    public void setMaxConfiguredValue(Long maxConfiguredValue) {
        this.maxConfiguredValue = maxConfiguredValue;
    }

    public Long getIncrement() {
        return increment;
    }

    public void setIncrement(Long increment) {
        this.increment = increment;
    }

    public Integer getSequenceLength() {
        return sequenceLength;
    }

    public void setSequenceLength(Integer sequenceLength) {
        this.sequenceLength = sequenceLength;
    }

    public Long getTotalSuccessful() {
        return totalSuccessful;
    }

    public void setTotalSuccessful(Long totalSuccessful) {
        this.totalSuccessful = totalSuccessful;
    }

    public Long getTotalAllocated() {
        return totalAllocated;
    }

    public void setTotalAllocated(Long totalAllocated) {
        this.totalAllocated = totalAllocated;
    }

    public Long getTotalFailed() {
        return totalFailed;
    }

    public void setTotalFailed(Long totalFailed) {
        this.totalFailed = totalFailed;
    }

    public Long getRemainingCapacity() {
        return remainingCapacity;
    }

    public void setRemainingCapacity(Long remainingCapacity) {
        this.remainingCapacity = remainingCapacity;
    }

    public Double getConsumptionPercentage() {
        return consumptionPercentage;
    }

    public void setConsumptionPercentage(Double consumptionPercentage) {
        this.consumptionPercentage = consumptionPercentage;
    }

    public LocalDateTime getScopeInitializedAt() {
        return scopeInitializedAt;
    }

    public void setScopeInitializedAt(LocalDateTime scopeInitializedAt) {
        this.scopeInitializedAt = scopeInitializedAt;
    }

    public LocalDateTime getLastGenerationAt() {
        return lastGenerationAt;
    }

    public void setLastGenerationAt(LocalDateTime lastGenerationAt) {
        this.lastGenerationAt = lastGenerationAt;
    }

    public LocalDateTime getLastResetAt() {
        return lastResetAt;
    }

    public void setLastResetAt(LocalDateTime lastResetAt) {
        this.lastResetAt = lastResetAt;
    }

    public Integer getResetCount() {
        return resetCount;
    }

    public void setResetCount(Integer resetCount) {
        this.resetCount = resetCount;
    }

    public Boolean getIsExhausted() {
        return isExhausted;
    }

    public void setIsExhausted(Boolean isExhausted) {
        this.isExhausted = isExhausted;
    }
}
