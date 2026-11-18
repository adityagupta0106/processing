package com.serviceplus.form.validation.pattern.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpgdMappingDTO {

    /**
     * Mapped Form Attribute or System Variable (e.g. LGD_DISTRICT, district, state)
     */
    private String formAttribute;

    /**
     * Geographic Entity Type: STATE, DISTRICT, SUBDISTRICT, BLOCK, VILLAGE
     */
    private String entityType = "DISTRICT";

    /**
     * Optional fallback code if attribute is empty and fallback is configured
     */
    private String fallbackCode;

    public SpgdMappingDTO() {
    }

    public SpgdMappingDTO(String formAttribute, String entityType, String fallbackCode) {
        this.formAttribute = formAttribute;
        this.entityType = entityType;
        this.fallbackCode = fallbackCode;
    }

    public String getFormAttribute() {
        return formAttribute;
    }

    public void setFormAttribute(String formAttribute) {
        this.formAttribute = formAttribute;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getFallbackCode() {
        return fallbackCode;
    }

    public void setFallbackCode(String fallbackCode) {
        this.fallbackCode = fallbackCode;
    }
}
