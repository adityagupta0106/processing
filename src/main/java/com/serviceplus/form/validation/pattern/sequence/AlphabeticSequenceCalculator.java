package com.serviceplus.form.validation.pattern.sequence;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.pattern.dto.SequenceConfigDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

public class AlphabeticSequenceCalculator {

    private static final Logger log = LogManager.getLogger("applicationFlowLogger");

    public static long stringToIndex(String text, int length) {
        if (text == null || text.isBlank()) {
            return 0L;
        }
        text = text.trim().toUpperCase();
        long index = 0L;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 'A' || c > 'Z') {
                throw new IllegalArgumentException("Invalid alphabetic character: ".concat(String.valueOf(c)));
            }
            index = index * 26 + (c - 'A');
        }
        return index;
    }

    public static String indexToString(long index, int length) {
        char[] chars = new char[length];
        for (int i = length - 1; i >= 0; i--) {
            chars[i] = (char) ('A' + (index % 26));
            index /= 26;
        }
        return new String(chars);
    }

    public static long getMaxIndex(int length) {
        long max = 1L;
        for (int i = 0; i < length; i++) {
            max *= 26;
        }
        return max - 1;
    }

    public static Long getNextIndex(Long currentIndex, SequenceConfigDTO config, String patternId, String scopeKey) {
        return getNextIndex(currentIndex, config, patternId, scopeKey, null);
    }

    public static Long getNextIndex(Long currentIndex, SequenceConfigDTO config, String patternId, String scopeKey, String txnId) {
        int length = config.getAlphabeticLength() != null && config.getAlphabeticLength() > 0 ? config.getAlphabeticLength() : 2;
        long maxIndex = getMaxIndex(length);
        int increment = config.getAlphabeticIncrement() != null && config.getAlphabeticIncrement() > 0 ? config.getAlphabeticIncrement() : 1;

        log.info("AlphabeticSequenceCalculator: currentIndex={}, length={}, maxIndex={}, increment={}, patternId='{}', scopeKey='{}'",
                currentIndex, length, maxIndex, increment, patternId, scopeKey);

        if (currentIndex == null || currentIndex < 0) {
            String starting = config.getStartingAlphabeticValue();
            long startingIndex = (starting != null && !starting.isBlank()) ? stringToIndex(starting, length) : 0L;
            if (startingIndex > maxIndex) {
                log.error("Initial alphabetic value '{}' (index {}) exceeds maximum allowed index {} for pattern '{}', scopeKey '{}'",
                        starting, startingIndex, maxIndex, patternId, scopeKey);
                SPRuntimeError err = new SPRuntimeError("Initial alphabetic value exceeds maximum allowed sequence", HttpStatus.CONFLICT, txnId);
                Map<String, Object> data = new HashMap<>();
                data.put("patternId", patternId);
                data.put("errorCode", "PATTERN_SEQUENCE_EXHAUSTED");
                err.setData(data);
                throw err;
            }
            log.info("AlphabeticSequenceCalculator: Returning starting index: {} ({}) for pattern: '{}', scopeKey: '{}'",
                    startingIndex, indexToString(startingIndex, length), patternId, scopeKey);
            return startingIndex;
        }

        long next = currentIndex + increment;
        if (next > maxIndex) {
            log.error("Alphabetic sequence exhausted for pattern '{}', scopeKey '{}'. Current: {} (index {}), Next: {}, Max: {} (index {})",
                    patternId, scopeKey, indexToString(currentIndex, length), currentIndex, next, indexToString(maxIndex, length), maxIndex);
            SPRuntimeError err = new SPRuntimeError(
                    "Alphabetic sequence exhausted for pattern ".concat(patternId != null ? patternId : "")
                            .concat(" and scope ").concat(scopeKey != null ? scopeKey : "")
                            .concat(". Current: ").concat(indexToString(currentIndex, length))
                            .concat(", Max: ").concat(indexToString(maxIndex, length)),
                    HttpStatus.CONFLICT,
                    txnId);
            Map<String, Object> data = new HashMap<>();
            data.put("patternId", patternId);
            data.put("errorCode", "PATTERN_SEQUENCE_EXHAUSTED");
            err.setData(data);
            throw err;
        }
        return next;
    }

    public static String formatSequence(Long index, SequenceConfigDTO config) {
        int length = config.getAlphabeticLength() != null && config.getAlphabeticLength() > 0 ? config.getAlphabeticLength() : 2;
        if (index == null || index < 0) {
            String starting = config.getStartingAlphabeticValue();
            index = (starting != null && !starting.isBlank()) ? stringToIndex(starting, length) : 0L;
        }
        return indexToString(index, length);
    }

    public static Long calculateRemainingCapacity(Long currentIndex, SequenceConfigDTO config) {
        int length = config.getAlphabeticLength() != null && config.getAlphabeticLength() > 0 ? config.getAlphabeticLength() : 2;
        long max = getMaxIndex(length);
        int increment = config.getAlphabeticIncrement() != null && config.getAlphabeticIncrement() > 0 ? config.getAlphabeticIncrement() : 1;

        if (currentIndex == null || currentIndex < 0) {
            return (max / increment) + 1;
        }
        if (currentIndex >= max) {
            return 0L;
        }
        return (max - currentIndex) / increment;
    }

    public static Double calculateConsumptionPercentage(Long currentIndex, SequenceConfigDTO config) {
        int length = config.getAlphabeticLength() != null && config.getAlphabeticLength() > 0 ? config.getAlphabeticLength() : 2;
        long max = getMaxIndex(length);
        if (max <= 0) {
            return 100.0;
        }
        if (currentIndex == null || currentIndex < 0) {
            return 0.0;
        }
        double percentage = ((double) (currentIndex + 1) / (max + 1)) * 100.0;
        return Math.min(100.0, Math.max(0.0, percentage));
    }
}
