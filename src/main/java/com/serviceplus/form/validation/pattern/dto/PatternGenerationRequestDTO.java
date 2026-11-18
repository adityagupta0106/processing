package com.serviceplus.form.validation.pattern.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatternGenerationRequestDTO {

    private String patternId;
    private String tenantId;
    private Integer serviceId;
    private String serviceCode;
    private String applicationId;
    private String documentId;
    private String txnId;
    private String generationPurpose; // e.g. "ARN", "CERTIFICATE", "ACKNOWLEDGMENT", "REGISTRATION", "RECEIPT"
    private String idempotencyKey;
    private Map<String, Object> formAttributes = new HashMap<>();
    private Map<String, Object> systemVariables = new HashMap<>();
    private LocalDateTime generationDate;
    private String generatedBy;

    public PatternGenerationRequestDTO() {
    }

    public static PatternGenerationRequestDTO forArn(String patternId, String tenantId, Integer serviceId, String serviceCode,
                                                    String applicationId, String txnId, Map<String, Object> formAttributes,
                                                    Map<String, Object> systemVariables, String userId) {
        PatternGenerationRequestDTO req = new PatternGenerationRequestDTO();
        req.setPatternId(patternId);
        req.setTenantId(tenantId);
        req.setServiceId(serviceId);
        req.setServiceCode(serviceCode);
        req.setApplicationId(applicationId);
        req.setTxnId(txnId);
        req.setGenerationPurpose("ARN");
        req.setIdempotencyKey(txnId != null ? "ARN_".concat(applicationId).concat("_").concat(txnId) : "ARN_".concat(applicationId));
        req.setFormAttributes(formAttributes != null ? formAttributes : new HashMap<>());
        req.setSystemVariables(systemVariables != null ? systemVariables : new HashMap<>());
        req.setGeneratedBy(userId);
        req.setGenerationDate(LocalDateTime.now());
        return req;
    }

    public String getPatternId() {
        return patternId;
    }

    public void setPatternId(String patternId) {
        this.patternId = patternId;
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

    public String getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
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

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
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

    public Map<String, Object> getFormAttributes() {
        return formAttributes;
    }

    public void setFormAttributes(Map<String, Object> formAttributes) {
        this.formAttributes = formAttributes;
    }

    public Map<String, Object> getSystemVariables() {
        return systemVariables;
    }

    public void setSystemVariables(Map<String, Object> systemVariables) {
        this.systemVariables = systemVariables;
    }

    public LocalDateTime getGenerationDate() {
        return generationDate;
    }

    public void setGenerationDate(LocalDateTime generationDate) {
        this.generationDate = generationDate;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(String generatedBy) {
        this.generatedBy = generatedBy;
    }
}
