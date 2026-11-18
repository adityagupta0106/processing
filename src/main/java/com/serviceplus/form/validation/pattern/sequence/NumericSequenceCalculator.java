package com.serviceplus.form.validation.pattern.sequence;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.pattern.dto.SequenceConfigDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

public class NumericSequenceCalculator {

    private static final Logger log = LogManager.getLogger("applicationFlowLogger");

    public static String formatSequence(Long value, SequenceConfigDTO config) {
        if (value == null) {
            value = config.getStartingValue() != null ? config.getStartingValue() : 1L;
        }
        int digits = config.getDigits() != null ? config.getDigits() : 6;
        boolean padding = config.getPadding() == null || config.getPadding();
        if (padding) {
            String format = "%0".concat(String.valueOf(digits)).concat("d");
            return String.format(format, value);
        } else {
            return String.valueOf(value);
        }
    }

    public static Long getNextValue(Long currentValue, SequenceConfigDTO config, String patternId, String scopeKey) {
        return getNextValue(currentValue, config, patternId, scopeKey, null);
    }

    public static Long getNextValue(Long currentValue, SequenceConfigDTO config, String patternId, String scopeKey, String txnId) {
        long min = config.getMinValue() != null ? config.getMinValue() : 1L;
        long max = config.getMaxValue() != null ? config.getMaxValue() : (long) Math.pow(10, config.getDigits() != null ? config.getDigits() : 6) - 1;
        long increment = config.getIncrement() != null && config.getIncrement() > 0 ? config.getIncrement() : 1L;

        log.info("NumericSequenceCalculator: currentValue={}, min={}, max={}, increment={}, patternId='{}', scopeKey='{}'",
                currentValue, min, max, increment, patternId, scopeKey);

        if (currentValue == null || currentValue < min) {
            long starting = config.getStartingValue() != null ? config.getStartingValue() : min;
            if (starting > max) {
                log.error("Initial numeric sequence starting value {} exceeds maximum {} for pattern '{}', scopeKey '{}'",
                        starting, max, patternId, scopeKey);
                SPRuntimeError err = new SPRuntimeError(
                        "Initial sequence value ".concat(String.valueOf(starting))
                                .concat(" exceeds maximum allowed value ")
                                .concat(String.valueOf(max)),
                        HttpStatus.CONFLICT,
                        txnId);
                Map<String, Object> data = new HashMap<>();
                data.put("patternId", patternId);
                data.put("scopeKey", scopeKey);
                data.put("errorCode", "PATTERN_SEQUENCE_EXHAUSTED");
                err.setData(data);
                throw err;
            }
            log.info("NumericSequenceCalculator: Returning starting value: {} for pattern: '{}', scopeKey: '{}'",
                    starting, patternId, scopeKey);
            return starting;
        }

        long next = currentValue + increment;
        if (next > max) {
            log.error("Numeric sequence exhausted for pattern '{}', scopeKey '{}'. Current: {}, Increment: {}, Next: {}, Max: {}",
                    patternId, scopeKey, currentValue, increment, next, max);
            SPRuntimeError err = new SPRuntimeError(
                    "Numeric sequence exhausted for pattern ".concat(patternId != null ? patternId : "")
                            .concat(" and scope ").concat(scopeKey != null ? scopeKey : "")
                            .concat(". Current: ").concat(String.valueOf(currentValue))
                            .concat(", Max: ").concat(String.valueOf(max)),
                    HttpStatus.CONFLICT,
                    txnId);
            Map<String, Object> data = new HashMap<>();
            data.put("patternId", patternId);
            data.put("scopeKey", scopeKey);
            data.put("errorCode", "PATTERN_SEQUENCE_EXHAUSTED");
            err.setData(data);
            throw err;
        }
        return next;
    }

    public static Long calculateRemainingCapacity(Long currentValue, SequenceConfigDTO config) {
        long min = config.getMinValue() != null ? config.getMinValue() : 1L;
        long max = config.getMaxValue() != null ? config.getMaxValue() : (long) Math.pow(10, config.getDigits() != null ? config.getDigits() : 6) - 1;
        long increment = config.getIncrement() != null && config.getIncrement() > 0 ? config.getIncrement() : 1L;

        if (currentValue == null || currentValue < min) {
            return (max - min) / increment + 1;
        }
        if (currentValue >= max) {
            return 0L;
        }
        return (max - currentValue) / increment;
    }

    public static Double calculateConsumptionPercentage(Long currentValue, SequenceConfigDTO config) {
        long min = config.getMinValue() != null ? config.getMinValue() : 1L;
        long max = config.getMaxValue() != null ? config.getMaxValue() : (long) Math.pow(10, config.getDigits() != null ? config.getDigits() : 6) - 1;

        if (max <= min) {
            return 100.0;
        }
        if (currentValue == null || currentValue < min) {
            return 0.0;
        }
        double percentage = ((double) (currentValue - min + 1) / (max - min + 1)) * 100.0;
        return Math.min(100.0, Math.max(0.0, percentage));
    }
}
