package com.serviceplus.form.validation.dto;

import java.io.Serializable;

public class ApplicationCountRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer serviceId;
    private String tenantId;

    public ApplicationCountRequestDTO() {
    }

    public ApplicationCountRequestDTO(Integer serviceId, String tenantId) {
        this.serviceId = serviceId;
        this.tenantId = tenantId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
