package com.serviceplus.form.validation.pattern.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

@Table(name = "pattern_generation_log", schema = SP_SCHEMA_NAME)
public class PatternGenerationLogEntity implements Persistable<String> {

    @Id
    private String id;

    private String patternId;
    private Integer patternVersion;
    private String generatedValue;
    private String sequenceRepresentation;
    private String scopeKey;
    private String tenantId;
    private Integer serviceId;
    private String applicationId;
    private String documentId;
    private String generationPurpose;
    private String idempotencyKey;
    private String status = "ACTIVE"; // ACTIVE, SUPERSEDED, REVOKED
    private String supersededBy;
    private String generatedBy;
    private LocalDateTime generatedAt;

    @Transient
    private boolean newEntity = false;

    public PatternGenerationLogEntity() {
    }

    public PatternGenerationLogEntity(String id, String patternId, Integer patternVersion, String generatedValue,
                                     String sequenceRepresentation, String scopeKey, String tenantId, Integer serviceId,
                                     String applicationId, String documentId, String generationPurpose, String idempotencyKey,
                                     String generatedBy) {
        this.id = id;
        this.patternId = patternId;
        this.patternVersion = patternVersion;
        this.generatedValue = generatedValue;
        this.sequenceRepresentation = sequenceRepresentation;
        this.scopeKey = scopeKey;
        this.tenantId = tenantId;
        this.serviceId = serviceId;
        this.applicationId = applicationId;
        this.documentId = documentId;
        this.generationPurpose = generationPurpose;
        this.idempotencyKey = idempotencyKey;
        this.generatedBy = generatedBy;
        this.generatedAt = LocalDateTime.now();
        this.status = "ACTIVE";
        this.newEntity = true;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    public String getSequenceRepresentation() {
        return sequenceRepresentation;
    }

    public void setSequenceRepresentation(String sequenceRepresentation) {
        this.sequenceRepresentation = sequenceRepresentation;
    }

    public String getScopeKey() {
        return scopeKey;
    }

    public void setScopeKey(String scopeKey) {
        this.scopeKey = scopeKey;
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

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getGenerationPurpose() {
        return generationPurpose;
    }

    public void setGenerationPurpose(String generationPurpose) {
        this.generationPurpose = generationPurpose;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSupersededBy() {
        return supersededBy;
    }

    public void setSupersededBy(String supersededBy) {
        this.supersededBy = supersededBy;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(String generatedBy) {
        this.generatedBy = generatedBy;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
