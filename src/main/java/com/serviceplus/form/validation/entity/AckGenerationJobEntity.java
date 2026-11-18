package com.serviceplus.form.validation.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

@Table(name = "ack_generation_job", schema = SP_SCHEMA_NAME)
public class AckGenerationJobEntity implements Persistable<String> {

    @Id
    private String id;

    private String txnId;

    private String applicationId;

    private Integer serviceId;

    private String taskId;

    private String referenceId;

    private String documentName;

    private Long outputFormatId;

    private String category;

    private String status;

    private String uploadId;

    private Integer retryCount = 0;

    private String errorMessage;

    private String tenantId;

    private Long crBy;

    private LocalDateTime crOn;

    private LocalDateTime upOn;

    @Transient
    private boolean isNew = true;

    public AckGenerationJobEntity() {
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
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public Long getOutputFormatId() {
        return outputFormatId;
    }

    public void setOutputFormatId(Long outputFormatId) {
        this.outputFormatId = outputFormatId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

	public Long getCrBy() {
		return crBy;
	}

	public void setCrBy(Long crBy) {
		this.crBy = crBy;
	}

	public LocalDateTime getCrOn() {
		return crOn;
	}

	public void setCrOn(LocalDateTime crOn) {
		this.crOn = crOn;
	}

	public LocalDateTime getUpOn() {
		return upOn;
	}

	public void setUpOn(LocalDateTime upOn) {
		this.upOn = upOn;
	}

    
}
