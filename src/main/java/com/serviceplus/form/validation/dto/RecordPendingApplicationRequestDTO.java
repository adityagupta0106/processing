package com.serviceplus.form.validation.dto;

import java.io.Serializable;

public class RecordPendingApplicationRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String applicationId;
    private Integer serviceId;
    private Integer submissionLocationId;
    private Integer sduLocationId;
    private String slaStartPolicy;
    private Long citizenUserId;
    private String acknowledgementNumber;

    public RecordPendingApplicationRequestDTO() {
    }

    public RecordPendingApplicationRequestDTO(String applicationId, Integer serviceId, Integer submissionLocationId,
                                             Integer sduLocationId, String slaStartPolicy, Long citizenUserId,
                                             String acknowledgementNumber) {
        this.applicationId = applicationId;
        this.serviceId = serviceId;
        this.submissionLocationId = submissionLocationId;
        this.sduLocationId = sduLocationId;
        this.slaStartPolicy = slaStartPolicy;
        this.citizenUserId = citizenUserId;
        this.acknowledgementNumber = acknowledgementNumber;
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

    public Integer getSubmissionLocationId() {
        return submissionLocationId;
    }

    public void setSubmissionLocationId(Integer submissionLocationId) {
        this.submissionLocationId = submissionLocationId;
    }

    public Integer getSduLocationId() {
        return sduLocationId;
    }

    public void setSduLocationId(Integer sduLocationId) {
        this.sduLocationId = sduLocationId;
    }

    public String getSlaStartPolicy() {
        return slaStartPolicy;
    }

    public void setSlaStartPolicy(String slaStartPolicy) {
        this.slaStartPolicy = slaStartPolicy;
    }

    public Long getCitizenUserId() {
        return citizenUserId;
    }

    public void setCitizenUserId(Long citizenUserId) {
        this.citizenUserId = citizenUserId;
    }

    public String getAcknowledgementNumber() {
        return acknowledgementNumber;
    }

    public void setAcknowledgementNumber(String acknowledgementNumber) {
        this.acknowledgementNumber = acknowledgementNumber;
    }
}
