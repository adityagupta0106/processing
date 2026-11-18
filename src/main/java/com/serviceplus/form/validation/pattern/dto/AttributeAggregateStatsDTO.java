package com.serviceplus.form.validation.pattern.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttributeAggregateStatsDTO {

    private String attributeName;
    private String attributeValue;
    private Long totalGenerations = 0L;
    private Long totalAllocated = 0L;
    private Long distinctScopes = 0L;
    private Integer resetCount = 0;

    public AttributeAggregateStatsDTO() {
    }

    public AttributeAggregateStatsDTO(String attributeName, String attributeValue, Long totalGenerations, Long totalAllocated, Long distinctScopes, Integer resetCount) {
        this.attributeName = attributeName;
        this.attributeValue = attributeValue;
        this.totalGenerations = totalGenerations;
        this.totalAllocated = totalAllocated;
        this.distinctScopes = distinctScopes;
        this.resetCount = resetCount;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getAttributeValue() {
        return attributeValue;
    }

    public void setAttributeValue(String attributeValue) {
        this.attributeValue = attributeValue;
    }

    public Long getTotalGenerations() {
        return totalGenerations;
    }

    public void setTotalGenerations(Long totalGenerations) {
        this.totalGenerations = totalGenerations;
    }

    public Long getTotalAllocated() {
        return totalAllocated;
    }

    public void setTotalAllocated(Long totalAllocated) {
        this.totalAllocated = totalAllocated;
    }

    public Long getDistinctScopes() {
        return distinctScopes;
    }

    public void setDistinctScopes(Long distinctScopes) {
        this.distinctScopes = distinctScopes;
    }

    public Integer getResetCount() {
        return resetCount;
    }

    public void setResetCount(Integer resetCount) {
        this.resetCount = resetCount;
    }
}
