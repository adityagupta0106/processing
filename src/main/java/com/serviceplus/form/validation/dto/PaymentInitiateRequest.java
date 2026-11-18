package com.serviceplus.form.validation.dto;

import java.math.BigDecimal;

public class PaymentInitiateRequest {

    private Integer serviceId;

    private String applicationId;

    private String taskId;

    private Integer gatewayId;

    private Integer locationId;

    private BigDecimal amount;

    private String tenantId;

    public PaymentInitiateRequest() {
    }

    public PaymentInitiateRequest(
            Integer serviceId,
            String applicationId,
            String taskId,
            Integer locationId,
            Integer gatewayId) {

        this.serviceId = serviceId;
        this.applicationId = applicationId;
        this.taskId = taskId;
        this.locationId = locationId;
        this.gatewayId = gatewayId;
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

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getLocationId() {
        return locationId;
    }

    public void setLocationId(Integer locationId) {
        this.locationId = locationId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getGatewayId() {
        return gatewayId;
    }

    public void setGatewayId(Integer gatewayId) {
        this.gatewayId = gatewayId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}