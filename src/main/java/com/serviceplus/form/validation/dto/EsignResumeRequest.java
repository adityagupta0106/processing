package com.serviceplus.form.validation.dto;

import java.io.Serializable;

public class EsignResumeRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String applicationId;
    private String txnId;
    private Integer serviceId;
    private String taskId;
    private String documentId;
    private String originalUploadId;
    private String signedUploadId;
    private String signerName;
    private String status; // SUCCESS / FAILED
    private String errorCode;
    private String errorMessage;

    public EsignResumeRequest() {
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
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

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getOriginalUploadId() {
        return originalUploadId;
    }

    public void setOriginalUploadId(String originalUploadId) {
        this.originalUploadId = originalUploadId;
    }

    public String getSignedUploadId() {
        return signedUploadId;
    }

    public void setSignedUploadId(String signedUploadId) {
        this.signedUploadId = signedUploadId;
    }

    public String getSignerName() {
        return signerName;
    }

    public void setSignerName(String signerName) {
        this.signerName = signerName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
