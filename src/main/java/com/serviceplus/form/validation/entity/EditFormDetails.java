package com.serviceplus.form.validation.entity;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

import java.time.OffsetDateTime;
import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "edit_form_details", schema = SP_SCHEMA_NAME)
public class EditFormDetails implements Persistable<String> {

    @Id
    private String id;

    private String applicationId;
    private Integer serviceId;
    private String holderId;
    private String formId;
    private String tenantId;

    // JSON text columns. `specific` stores the full Specific object; allowed/restricted/hide
    // are kept separately as requested for direct querying/reporting.
    private String allowed;
    private String restricted;
    private String hide;
    private String specific;
    
    private OffsetDateTime crDate;

    private OffsetDateTime upDate;

    @Transient
    private boolean newEntity = false;

    @Override
    @Transient
    public boolean isNew() {
        return newEntity;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getHolderId() {
        return holderId;
    }

    public void setHolderId(String holderId) {
        this.holderId = holderId;
    }

    public String getFormId() {
        return formId;
    }

    public void setFormId(String formId) {
        this.formId = formId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getAllowed() {
        return allowed;
    }

    public void setAllowed(String allowed) {
        this.allowed = allowed;
    }

    public String getRestricted() {
        return restricted;
    }

    public void setRestricted(String restricted) {
        this.restricted = restricted;
    }

    public String getHide() {
        return hide;
    }

    public void setHide(String hide) {
        this.hide = hide;
    }


    public String getSpecific() {
        return specific;
    }

    public void setSpecific(String specific) {
        this.specific = specific;
    }

    public void setNewEntity(boolean newEntity) {
        this.newEntity = newEntity;
    }

	public OffsetDateTime getCrDate() {
		return crDate;
	}

	public void setCrDate(OffsetDateTime crDate) {
		this.crDate = crDate;
	}

	public OffsetDateTime getUpDate() {
		return upDate;
	}

	public void setUpDate(OffsetDateTime upDate) {
		this.upDate = upDate;
	}

}
