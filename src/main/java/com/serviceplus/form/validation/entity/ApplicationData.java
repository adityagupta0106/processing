package com.serviceplus.form.validation.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.dto.FormData;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Date;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;
import static com.serviceplus.form.validation.utility.Utility.entityToString;
import static com.serviceplus.form.validation.utility.Utility.stringToEntity;

@Table(name = "application_attr_data", schema = SP_SCHEMA_NAME)
public class ApplicationData implements Persistable<String> {

    @Id
    private String applicationId;

    private Integer baseServiceId;

    private Integer serviceId;

    @Column("attr_val")
    private String attrVal;

    @Transient
    private FormData attrValObject;

    private LocalDateTime uDate;

    private String tenantId;

    @Transient
    private boolean newEntity = false;

    public ApplicationData() {
    }

    @Override
    public String getId() {
        return this.applicationId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return this.newEntity;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public Integer getBaseServiceId() {
        return baseServiceId;
    }

    public void setBaseServiceId(Integer baseServiceId) {
        this.baseServiceId = baseServiceId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getAttrVal() {
        return attrVal;
    }

    public void setAttrVal(String attrVal) {
        this.attrVal = attrVal;
    }

    public FormData getAttrValObject() {
        return attrValObject;
    }

    public void setAttrValObject(FormData attrValObject) {
        this.attrValObject = attrValObject;
    }

    public LocalDateTime getuDate() {
        return uDate;
    }

    public void setuDate(LocalDateTime uDate) {
        this.uDate = uDate;
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

    public void convertAttrValToObject() {
        if (getAttrVal() == null || getAttrVal().isBlank()) {
            setAttrValObject(null);
            return;
        }
        setAttrValObject((FormData) stringToEntity(getAttrVal(),FormData.class));
    }

    public void convertAttrValToJson() {
        if (getAttrValObject() == null) {
            setAttrVal(null);
            return;
        }
        setAttrVal(entityToString(getAttrValObject()));
    }
}