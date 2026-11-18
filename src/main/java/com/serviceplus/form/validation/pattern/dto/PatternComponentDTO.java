package com.serviceplus.form.validation.pattern.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.serviceplus.form.validation.pattern.enums.ComponentType;
import com.serviceplus.form.validation.pattern.enums.TransformationType;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatternComponentDTO {

    private Integer sequence;
    private ComponentType type = ComponentType.VARIABLE; // VARIABLE, SEPARATOR, STATIC
    private String token; // e.g. "{SERVICE_CODE}", "{SPGD_ENTITY}", "-", "{SEQ}", "{SERVICE_ATTRIBUTE}", "{CHECKSUM}"
    private String value; // static text or separator character
    private Boolean mandatory = true;
    private TransformationType transformation = TransformationType.NONE; // UPPERCASE, LOWERCASE, NONE
    private Integer length;
    private String padding;
    private String defaultValue;
    private String mappedAttribute; // e.g. "s1$123$367" for dynamic field mapping from application_attr_data

    public PatternComponentDTO() {
    }

    public PatternComponentDTO(Integer sequence, String type, String token, String value) {
        this.sequence = sequence;
        this.type = ComponentType.fromString(type);
        this.token = token;
        this.value = value;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public ComponentType getType() {
        return type;
    }

    public void setType(ComponentType type) {
        this.type = type;
    }

    public void setType(String type) {
        this.type = ComponentType.fromString(type);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Boolean getMandatory() {
        return mandatory;
    }

    public void setMandatory(Boolean mandatory) {
        this.mandatory = mandatory;
    }

    public TransformationType getTransformation() {
        return transformation;
    }

    public void setTransformation(TransformationType transformation) {
        this.transformation = transformation;
    }

    public void setTransformation(String transformation) {
        this.transformation = TransformationType.fromString(transformation);
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public String getPadding() {
        return padding;
    }

    public void setPadding(String padding) {
        this.padding = padding;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getMappedAttribute() {
        return mappedAttribute;
    }

    public void setMappedAttribute(String mappedAttribute) {
        this.mappedAttribute = mappedAttribute;
    }
}
