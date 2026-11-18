package com.serviceplus.form.validation.pattern.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

@Table(name = "pattern_sequence", schema = SP_SCHEMA_NAME)
public class PatternSequenceEntity implements Persistable<Long> {

    @Id
    private Long id;

    private String patternId;
    private Integer version;
    private String tenantId;
    private Integer serviceId;
    private String scopeKey;

    private Long currentNumericValue = 0L;
    private Long currentAlphaValue = 0L;
    private String currentFormattedSequence;

    private Long totalAllocated = 0L;
    private Long totalSuccessful = 0L;
    private Long totalFailed = 0L;

    private Boolean isExhausted = false;

    private LocalDateTime createdOn;
    private LocalDateTime lastAllocatedOn;
    private LocalDateTime lastResetOn;
    private Integer resetCount = 0;

    @Transient
    private boolean newEntity = false;

    public PatternSequenceEntity() {
    }

    public PatternSequenceEntity(String patternId, Integer version, String tenantId, Integer serviceId, String scopeKey) {
        this.patternId = patternId;
        this.version = version;
        this.tenantId = tenantId;
        this.serviceId = serviceId;
        this.scopeKey = scopeKey;
        this.createdOn = LocalDateTime.now();
        this.newEntity = true;
    }

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    @Transient
    public boolean isNew() {
        return this.newEntity || this.id == null;
    }

    public void setNewEntity(boolean newEntity) {
        this.newEntity = newEntity;
    }

    public String getPatternId() {
        return patternId;
    }

    public void setPatternId(String patternId) {
        this.patternId = patternId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getScopeKey() {
        return scopeKey;
    }

    public void setScopeKey(String scopeKey) {
        this.scopeKey = scopeKey;
    }

    public Long getCurrentNumericValue() {
        return currentNumericValue;
    }

    public void setCurrentNumericValue(Long currentNumericValue) {
        this.currentNumericValue = currentNumericValue;
    }

    public Long getCurrentAlphaValue() {
        return currentAlphaValue;
    }

    public void setCurrentAlphaValue(Long currentAlphaValue) {
        this.currentAlphaValue = currentAlphaValue;
    }

    public String getCurrentFormattedSequence() {
        return currentFormattedSequence;
    }

    public void setCurrentFormattedSequence(String currentFormattedSequence) {
        this.currentFormattedSequence = currentFormattedSequence;
    }

    public Long getTotalAllocated() {
        return totalAllocated;
    }

    public void setTotalAllocated(Long totalAllocated) {
        this.totalAllocated = totalAllocated;
    }

    public Long getTotalSuccessful() {
        return totalSuccessful;
    }

    public void setTotalSuccessful(Long totalSuccessful) {
        this.totalSuccessful = totalSuccessful;
    }

    public Long getTotalFailed() {
        return totalFailed;
    }

    public void setTotalFailed(Long totalFailed) {
        this.totalFailed = totalFailed;
    }

    public Boolean getIsExhausted() {
        return isExhausted;
    }

    public void setIsExhausted(Boolean isExhausted) {
        this.isExhausted = isExhausted;
    }

    public LocalDateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(LocalDateTime createdOn) {
        this.createdOn = createdOn;
    }

    public LocalDateTime getLastAllocatedOn() {
        return lastAllocatedOn;
    }

    public void setLastAllocatedOn(LocalDateTime lastAllocatedOn) {
        this.lastAllocatedOn = lastAllocatedOn;
    }

    public LocalDateTime getLastResetOn() {
        return lastResetOn;
    }

    public void setLastResetOn(LocalDateTime lastResetOn) {
        this.lastResetOn = lastResetOn;
    }

    public Integer getResetCount() {
        return resetCount;
    }

    public void setResetCount(Integer resetCount) {
        this.resetCount = resetCount;
    }
}
