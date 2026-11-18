package com.serviceplus.form.validation.pattern.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.serviceplus.form.validation.pattern.enums.SeriesType;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SequenceConfigDTO {

    /**
     * Series type enum: NUMERIC, ALPHABETIC, ALPHANUMERIC
     */
    private SeriesType seriesType = SeriesType.NUMERIC;

    // 1. Numeric Series Settings
    private Integer digits = 6;
    private Long minValue = 1L;
    private Long maxValue = 999999L;
    private Long startingValue = 1L;
    private Long increment = 1L;
    private Boolean padding = true;
    private String paddingChar = "0";

    // 2. Alphabetic Series Settings
    private Integer alphabeticLength = 2;
    private String startingAlphabeticValue = "AA";
    private Integer alphabeticIncrement = 1;

    // 3. Alphanumeric Series Settings
    private Integer totalLength = 6;
    private String startingPattern = "A00001";
    private Long incrementStep = 1L;
    private Integer alphabeticPartLength;
    private Integer numericPartLength;

    public SequenceConfigDTO() {
    }

    public SeriesType getSeriesType() {
        return seriesType;
    }

    public void setSeriesType(SeriesType seriesType) {
        this.seriesType = seriesType;
    }

    public void setSeriesType(String seriesType) {
        this.seriesType = SeriesType.fromString(seriesType);
    }

    public Integer getDigits() {
        return digits;
    }

    public void setDigits(Integer digits) {
        this.digits = digits;
    }

    public Long getMinValue() {
        return minValue;
    }

    public void setMinValue(Long minValue) {
        this.minValue = minValue;
    }

    public Long getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Long maxValue) {
        this.maxValue = maxValue;
    }

    public Long getStartingValue() {
        return startingValue;
    }

    public void setStartingValue(Long startingValue) {
        this.startingValue = startingValue;
    }

    public Long getIncrement() {
        return increment;
    }

    public void setIncrement(Long increment) {
        this.increment = increment;
    }

    public Boolean getPadding() {
        return padding;
    }

    public void setPadding(Boolean padding) {
        this.padding = padding;
    }

    public String getPaddingChar() {
        return paddingChar;
    }

    public void setPaddingChar(String paddingChar) {
        this.paddingChar = paddingChar;
    }

    public Integer getAlphabeticLength() {
        return alphabeticLength;
    }

    public void setAlphabeticLength(Integer alphabeticLength) {
        this.alphabeticLength = alphabeticLength;
    }

    public String getStartingAlphabeticValue() {
        return startingAlphabeticValue;
    }

    public void setStartingAlphabeticValue(String startingAlphabeticValue) {
        this.startingAlphabeticValue = startingAlphabeticValue;
    }

    public Integer getAlphabeticIncrement() {
        return alphabeticIncrement;
    }

    public void setAlphabeticIncrement(Integer alphabeticIncrement) {
        this.alphabeticIncrement = alphabeticIncrement;
    }

    public Integer getTotalLength() {
        return totalLength;
    }

    public void setTotalLength(Integer totalLength) {
        this.totalLength = totalLength;
    }

    public String getStartingPattern() {
        return startingPattern;
    }

    public void setStartingPattern(String startingPattern) {
        this.startingPattern = startingPattern;
    }

    public Long getIncrementStep() {
        return incrementStep;
    }

    public void setIncrementStep(Long incrementStep) {
        this.incrementStep = incrementStep;
    }

    public Integer getAlphabeticPartLength() {
        return alphabeticPartLength;
    }

    public void setAlphabeticPartLength(Integer alphabeticPartLength) {
        this.alphabeticPartLength = alphabeticPartLength;
    }

    public Integer getNumericPartLength() {
        return numericPartLength;
    }

    public void setNumericPartLength(Integer numericPartLength) {
        this.numericPartLength = numericPartLength;
    }
}
