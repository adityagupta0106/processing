package com.serviceplus.form.validation.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

@Table(name = "application_payment", schema = SP_SCHEMA_NAME)
public class ApplicationPayment  implements Persistable<String> {

    @Id
    private String paymentId;
    private String gatewayTransactionId;
    private String gatewayCode;
    private String environment;
    private String revalidationConfiguration;
    private String applicationId;
    private Integer serviceId;
    private String processId;
    private String taskId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime crDate;
    private LocalDateTime upDate;
    private LocalDateTime completedDate;
    private String tenantId;

    @Transient
    private boolean newEntity = false;

    @Override
    public String getId() {
        return this.paymentId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return this.newEntity;
    }

    public String getGatewayCode() {
        return gatewayCode;
    }

    public void setGatewayCode(String gatewayCode) {
        this.gatewayCode = gatewayCode;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public void setGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isNewEntity() {
        return newEntity;
    }

    public void setNewEntity(boolean newEntity) {
        this.newEntity = newEntity;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public LocalDateTime getCrDate() {
        return crDate;
    }

    public void setCrDate(LocalDateTime crDate) {
        this.crDate = crDate;
    }

    public LocalDateTime getUpDate() {
        return upDate;
    }

    public void setUpDate(LocalDateTime upDate) {
        this.upDate = upDate;
    }

    public LocalDateTime getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(LocalDateTime completedDate) {
        this.completedDate = completedDate;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getRevalidationConfiguration() {
        return revalidationConfiguration;
    }

    public void setRevalidationConfiguration(String revalidationConfiguration) {
        this.revalidationConfiguration = revalidationConfiguration;
    }
}
