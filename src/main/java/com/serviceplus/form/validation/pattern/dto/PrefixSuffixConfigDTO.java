package com.serviceplus.form.validation.pattern.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.serviceplus.form.validation.pattern.enums.SourceMode;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrefixSuffixConfigDTO {

    /**
     * Source mode: STATIC, DYNAMIC, SYSTEM_VARIABLE, NONE
     */
    private SourceMode sourceMode = SourceMode.STATIC;

    private String staticValue;

    private String mappedAttribute; // e.g. "s1$123$367" for dynamic field mapping

    private String systemVariable;

    public PrefixSuffixConfigDTO() {
    }

    public PrefixSuffixConfigDTO(String sourceMode, String staticValue, String systemVariable) {
        this.sourceMode = SourceMode.fromString(sourceMode);
        this.staticValue = staticValue;
        this.systemVariable = systemVariable;
    }

    public SourceMode getSourceMode() {
        return sourceMode;
    }

    public void setSourceMode(SourceMode sourceMode) {
        this.sourceMode = sourceMode;
    }

    public void setSourceMode(String sourceMode) {
        this.sourceMode = SourceMode.fromString(sourceMode);
    }

    public String getStaticValue() {
        return staticValue;
    }

    public void setStaticValue(String staticValue) {
        this.staticValue = staticValue;
    }

    public String getMappedAttribute() {
        return mappedAttribute;
    }

    public void setMappedAttribute(String mappedAttribute) {
        this.mappedAttribute = mappedAttribute;
    }

    public String getSystemVariable() {
        return systemVariable;
    }

    public void setSystemVariable(String systemVariable) {
        this.systemVariable = systemVariable;
    }
}
