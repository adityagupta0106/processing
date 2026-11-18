package com.serviceplus.form.validation.pattern.validator;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.pattern.dto.PatternComponentDTO;
import com.serviceplus.form.validation.pattern.dto.PatternConfigurationDTO;
import com.serviceplus.form.validation.pattern.dto.ResetAttributeDTO;
import com.serviceplus.form.validation.pattern.dto.ResetConfigDTO;
import com.serviceplus.form.validation.pattern.dto.SequenceConfigDTO;
import com.serviceplus.form.validation.pattern.enums.ComponentType;
import com.serviceplus.form.validation.pattern.enums.SeparatorType;
import com.serviceplus.form.validation.pattern.enums.SeriesType;
import com.serviceplus.form.validation.pattern.enums.StandardPlaceholder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatternConfigurationValidator {

    private static final Logger log = LogManager.getLogger("applicationFlowLogger");

    public static void validate(PatternConfigurationDTO config) {
        validate(config, null);
    }

    public static void validate(PatternConfigurationDTO config, String txnId) {
        log.info("Validating pattern configuration for patternId: '{}', txnId: '{}'",
                config != null ? config.getPatternId() : "null", txnId);

        if (config == null) {
            throw error("Pattern configuration cannot be null", "UNKNOWN", txnId);
        }

        String patternId = config.getPatternId();
        if (patternId == null || patternId.trim().isEmpty()) {
            throw error("Pattern ID is required and cannot be empty", "UNKNOWN", txnId);
        }

        // Validate Components node (Primary authoritative definition)
        List<PatternComponentDTO> components = config.getComponents();
        if (components == null || components.isEmpty()) {
            throw error("Pattern 'components' node is required and must not be empty", patternId, txnId);
        }

        boolean hasSeqComponent = false;
        for (PatternComponentDTO comp : components) {
            if (comp.getType() == ComponentType.SEPARATOR) {
                String sep = comp.getValue() != null ? comp.getValue() : comp.getToken();
                if (!SeparatorType.isSupported(sep)) {
                    throw error(
                            "Unsupported separator '".concat(sep != null ? sep : "null").concat("'. Supported: -, /, _, \\"),
                            patternId, txnId);
                }
            } else if (StandardPlaceholder.SEQ.getToken().equalsIgnoreCase(comp.getToken())) {
                hasSeqComponent = true;
            } else if (comp.getToken() != null && comp.getToken().startsWith("{") && !StandardPlaceholder.isSupported(comp.getToken())) {
                throw error(
                        "Unsupported component placeholder token: ".concat(comp.getToken()), patternId, txnId);
            }
        }
        if (!hasSeqComponent) {
            throw error("Pattern components must include the {SEQ} sequence component", patternId, txnId);
        }

        // Validate Sequence Settings
        SequenceConfigDTO seq = config.getSequenceConfig();
        if (seq == null) {
            throw error("Sequence configuration must be specified", patternId, txnId);
        }

        SeriesType seriesType = seq.getSeriesType() != null ? seq.getSeriesType() : SeriesType.NUMERIC;

        switch (seriesType) {
            case NUMERIC -> {
                if (seq.getDigits() == null || seq.getDigits() < 1) {
                    throw error("Numeric sequence digits must be greater than 0", patternId, txnId);
                }
                long maxAllowed = (long) Math.pow(10, seq.getDigits()) - 1;
                if (seq.getMinValue() != null && seq.getMinValue() < 0) {
                    throw error("Numeric minimum value cannot be negative", patternId, txnId);
                }
                if (seq.getMaxValue() != null) {
                    if (seq.getMaxValue() > maxAllowed) {
                        throw error(
                                "Configured maximum value ".concat(String.valueOf(seq.getMaxValue()))
                                        .concat(" exceeds allowed limit (").concat(String.valueOf(maxAllowed))
                                        .concat(") for ").concat(String.valueOf(seq.getDigits())).concat(" digits"),
                                patternId, txnId);
                    }
                    if (seq.getMinValue() != null && seq.getMinValue() >= seq.getMaxValue()) {
                        throw error("Numeric minimum value must be strictly less than maximum value", patternId, txnId);
                    }
                }
                if (seq.getIncrement() != null && seq.getIncrement() < 1) {
                    throw error("Sequence increment must be a positive integer", patternId, txnId);
                }
            }
            case ALPHABETIC -> {
                if (seq.getAlphabeticLength() == null || seq.getAlphabeticLength() < 1) {
                    throw error("Alphabetic length must be greater than 0", patternId, txnId);
                }
                if (seq.getAlphabeticIncrement() != null && seq.getAlphabeticIncrement() < 1) {
                    throw error("Alphabetic increment must be a positive integer", patternId, txnId);
                }
                if (seq.getStartingAlphabeticValue() != null && !seq.getStartingAlphabeticValue().isBlank()) {
                    String starting = seq.getStartingAlphabeticValue().trim();
                    if (starting.length() != seq.getAlphabeticLength()) {
                        throw error("Starting alphabetic value length must match configured alphabeticLength", patternId, txnId);
                    }
                    if (!starting.matches("^[A-Za-z]+$")) {
                        throw error("Starting alphabetic value must contain only letters A-Z", patternId, txnId);
                    }
                }
            }
            case ALPHANUMERIC -> {
                if (seq.getTotalLength() != null && seq.getTotalLength() < 2) {
                    throw error("Alphanumeric totalLength must be at least 2", patternId, txnId);
                }
                if (seq.getStartingPattern() != null && !seq.getStartingPattern().isBlank()) {
                    String startPat = seq.getStartingPattern().trim();
                    if (seq.getTotalLength() != null && startPat.length() != seq.getTotalLength()) {
                        throw error(
                                "Starting pattern length (".concat(String.valueOf(startPat.length()))
                                        .concat(") must match totalLength (").concat(String.valueOf(seq.getTotalLength())).concat(")"),
                                patternId, txnId);
                    }
                }
                if (seq.getIncrementStep() != null && seq.getIncrementStep() < 1) {
                    throw error("Alphanumeric incrementStep must be a positive integer", patternId, txnId);
                }
                if (seq.getNumericPartLength() != null && seq.getAlphabeticPartLength() != null && seq.getTotalLength() != null) {
                    if (seq.getNumericPartLength() + seq.getAlphabeticPartLength() != seq.getTotalLength()) {
                        throw error(
                                "Sum of numericPartLength (".concat(String.valueOf(seq.getNumericPartLength()))
                                        .concat(") and alphabeticPartLength (").concat(String.valueOf(seq.getAlphabeticPartLength()))
                                        .concat(") must equal totalLength (").concat(String.valueOf(seq.getTotalLength())).concat(")"),
                                patternId, txnId);
                    }
                }
            }
        }

        // Validate Reset Settings
        ResetConfigDTO reset = config.getResetConfig();
        if (reset != null && Boolean.TRUE.equals(reset.getResetEnabled())) {
            List<ResetAttributeDTO> resetAttrs = reset.getResetAttributes();
            if (resetAttrs == null || resetAttrs.isEmpty()) {
                throw error("At least one reset attribute must be configured when sequence reset logic is enabled", patternId, txnId);
            }
            if (resetAttrs.size() > 3) {
                throw error("Maximum 3 reset attributes can be configured", patternId, txnId);
            }
        }

        log.info("Pattern configuration successfully validated for patternId: '{}'", patternId);
    }

    private static SPRuntimeError error(String message, String patternId, String txnId) {
        log.error("Pattern configuration validation failed for patternId '{}', txnId '{}': {}", patternId, txnId, message);
        SPRuntimeError err = new SPRuntimeError(message, HttpStatus.BAD_REQUEST, txnId);
        Map<String, Object> data = new HashMap<>();
        data.put("patternId", patternId);
        data.put("errorCode", "PATTERN_INVALID_CONFIG");
        err.setData(data);
        return err;
    }
}
