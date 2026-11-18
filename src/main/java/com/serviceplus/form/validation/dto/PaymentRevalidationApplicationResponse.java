package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentRevalidationApplicationResponse {

    @Column("application_id")
    private String applicationId;

    @Column("payment_id")
    private String paymentId;

    @Column("service_id")
    private Integer serviceId;

    @Column("task_id")
    private String taskId;

    @Column("status")
    private String status;

    @Column("amount")
    private BigDecimal amount;

    @Column("currency")
    private String currency;

    @Column("gateway_code")
    private String gatewayCode;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("service_name")
    private String serviceName;

    @Column("reference_no")
    private String referenceNo;

    @JsonIgnore
    @Column("revalidation_configuration")
    private String revalidationConfiguration;

    public PaymentRevalidationApplicationResponse() {
    }

    @PersistenceCreator
    public PaymentRevalidationApplicationResponse(
            String applicationId,
            String paymentId,
            Integer serviceId,
            String taskId,
            String status,
            BigDecimal amount,
            String currency,
            String gatewayCode,
            LocalDateTime createdAt,
            String serviceName,
            String referenceNo,
            String revalidationConfiguration) {
        this.applicationId = applicationId;
        this.paymentId = paymentId;
        this.serviceId = serviceId;
        this.taskId = taskId;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.gatewayCode = gatewayCode;
        this.createdAt = createdAt;
        this.serviceName = serviceName;
        this.referenceNo = referenceNo;
        this.revalidationConfiguration = revalidationConfiguration;
    }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public Integer getServiceId() { return serviceId; }
    public void setServiceId(Integer serviceId) { this.serviceId = serviceId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getGatewayCode() { return gatewayCode; }
    public void setGatewayCode(String gatewayCode) { this.gatewayCode = gatewayCode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public String getRevalidationConfiguration() { return revalidationConfiguration; }
    public void setRevalidationConfiguration(String revalidationConfiguration) { this.revalidationConfiguration = revalidationConfiguration; }
}