package com.serviceplus.form.validation.pattern.sequence;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.pattern.dto.SequenceConfigDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

public class AlphanumericSequenceCalculator {

    private static final Logger log = LogManager.getLogger("applicationFlowLogger");

    public static class AlphanumericState {
        private final Long alphaIndex;
        private final Long numericValue;

        public AlphanumericState(Long alphaIndex, Long numericValue) {
            this.alphaIndex = alphaIndex;
            this.numericValue = numericValue;
        }

        public Long getAlphaIndex() {
            return alphaIndex;
        }

        public Long getNumericValue() {
            return numericValue;
        }
    }

    public static int getAlphaLength(SequenceConfigDTO config) {
        if (config.getAlphabeticPartLength() != null && config.getAlphabeticPartLength() > 0) {
            return config.getAlphabeticPartLength();
        }
        String pattern = config.getStartingPattern();
        if (pattern != null && !pattern.isBlank()) {
            int len = 0;
            for (char c : pattern.toCharArray()) {
                if (Character.isLetter(c)) {
                    len++;
                }
            }
            if (len > 0) {
                return len;
            }
        }
        return 1;
    }

    public static int getNumericLength(SequenceConfigDTO config) {
        if (config.getNumericPartLength() != null && config.getNumericPartLength() > 0) {
            return config.getNumericPartLength();
        }
        String pattern = config.getStartingPattern();
        if (pattern != null && !pattern.isBlank()) {
            int len = 0;
            for (char c : pattern.toCharArray()) {
                if (Character.isDigit(c)) {
                    len++;
                }
            }
            if (len > 0) {
                return len;
            }
        }
        if (config.getTotalLength() != null && config.getTotalLength() > 0) {
            int alphaLen = getAlphaLength(config);
            return Math.max(1, config.getTotalLength() - alphaLen);
        }
        return 5;
    }

    public static boolean isNumericFirst(SequenceConfigDTO config) {
        String pattern = config.getStartingPattern();
        return pattern != null && !pattern.isBlank() && Character.isDigit(pattern.trim().charAt(0));
    }

    public static AlphanumericState parseInitialState(SequenceConfigDTO config) {
        int alphaLen = getAlphaLength(config);

        String startingPattern = config.getStartingPattern();
        String startAlpha = "A";
        Long startNum = config.getStartingValue() != null ? config.getStartingValue() : 1L;

        if (startingPattern != null && !startingPattern.isBlank()) {
            startingPattern = startingPattern.trim();
            StringBuilder alphaChars = new StringBuilder();
            StringBuilder numChars = new StringBuilder();
            for (char c : startingPattern.toCharArray()) {
                if (Character.isLetter(c)) {
                    alphaChars.append(c);
                } else if (Character.isDigit(c)) {
                    numChars.append(c);
                }
            }
            if (alphaChars.length() > 0) {
                startAlpha = alphaChars.toString();
            }
            if (numChars.length() > 0) {
                try {
                    startNum = Long.parseLong(numChars.toString());
                } catch (Exception ignored) {
                }
            }
        } else if (config.getStartingAlphabeticValue() != null && !config.getStartingAlphabeticValue().isBlank()) {
            startAlpha = config.getStartingAlphabeticValue().trim();
        }

        long alphaIndex = AlphabeticSequenceCalculator.stringToIndex(startAlpha, alphaLen);
        return new AlphanumericState(alphaIndex, startNum);
    }

    public static AlphanumericState getNextState(Long currentAlphaIndex, Long currentNumericValue,
                                                SequenceConfigDTO config, String patternId, String scopeKey) {
        return getNextState(currentAlphaIndex, currentNumericValue, config, patternId, scopeKey, null);
    }

    public static AlphanumericState getNextState(Long currentAlphaIndex, Long currentNumericValue,
                                                SequenceConfigDTO config, String patternId, String scopeKey, String txnId) {
        int alphaLen = getAlphaLength(config);
        int numLen = getNumericLength(config);
        long maxAlphaIndex = AlphabeticSequenceCalculator.getMaxIndex(alphaLen);
        long maxNumeric = config.getMaxValue() != null ? config.getMaxValue() : (long) Math.pow(10, numLen) - 1;
        long minNumeric = config.getMinValue() != null ? config.getMinValue() : 1L;
        long step = config.getIncrementStep() != null && config.getIncrementStep() > 0
                ? config.getIncrementStep()
                : (config.getIncrement() != null && config.getIncrement() > 0 ? config.getIncrement() : 1L);

        log.info("AlphanumericSequenceCalculator: currentAlphaIndex={}, currentNumericValue={}, step={}, maxNumeric={}, maxAlphaIndex={}, patternId='{}', scopeKey='{}'",
                currentAlphaIndex, currentNumericValue, step, maxNumeric, maxAlphaIndex, patternId, scopeKey);

        if (currentAlphaIndex == null || currentNumericValue == null) {
            AlphanumericState initial = parseInitialState(config);
            if (initial.getAlphaIndex() > maxAlphaIndex || initial.getNumericValue() > maxNumeric) {
                log.error("Initial alphanumeric state (alphaIndex={}, numValue={}) exceeds bounds (maxAlphaIndex={}, maxNumeric={}) for pattern '{}', scopeKey '{}'",
                        initial.getAlphaIndex(), initial.getNumericValue(), maxAlphaIndex, maxNumeric, patternId, scopeKey);
                SPRuntimeError err = new SPRuntimeError("Initial alphanumeric state exceeds configured bounds", HttpStatus.CONFLICT, txnId);
                Map<String, Object> data = new HashMap<>();
                data.put("patternId", patternId);
                data.put("scopeKey", scopeKey);
                data.put("errorCode", "PATTERN_SEQUENCE_EXHAUSTED");
                err.setData(data);
                throw err;
            }
            log.info("AlphanumericSequenceCalculator: Returning initial state (alphaIndex={}, numValue={}) for pattern '{}', scopeKey '{}'",
                    initial.getAlphaIndex(), initial.getNumericValue(), patternId, scopeKey);
            return initial;
        }

        long nextNum = currentNumericValue + step;
        long nextAlpha = currentAlphaIndex;

        if (nextNum > maxNumeric) {
            // Odometer rollover: numeric resets to minNumeric and alphabetic advances
            nextNum = minNumeric;
            nextAlpha = currentAlphaIndex + 1;
            log.info("Alphanumeric odometer rollover triggered for pattern '{}', scopeKey '{}': num reset to {}, alpha advanced to {}",
                    patternId, scopeKey, nextNum, nextAlpha);
            if (nextAlpha > maxAlphaIndex) {
                log.error("Alphanumeric sequence completely exhausted for pattern '{}', scopeKey '{}'. nextAlpha: {} > maxAlphaIndex: {}",
                        patternId, scopeKey, nextAlpha, maxAlphaIndex);
                SPRuntimeError err = new SPRuntimeError(
                        "Alphanumeric sequence completely exhausted for pattern "
                                .concat(patternId != null ? patternId : "")
                                .concat(" and scope ")
                                .concat(scopeKey != null ? scopeKey : ""),
                        HttpStatus.CONFLICT, txnId);
                Map<String, Object> data = new HashMap<>();
                data.put("patternId", patternId);
                data.put("scopeKey", scopeKey);
                data.put("errorCode", "PATTERN_SEQUENCE_EXHAUSTED");
                err.setData(data);
                throw err;
            }
        }

        return new AlphanumericState(nextAlpha, nextNum);
    }

    public static String formatSequence(Long alphaIndex, Long numericValue, SequenceConfigDTO config) {
        int alphaLen = getAlphaLength(config);
        int numLen = getNumericLength(config);

        if (alphaIndex == null || numericValue == null) {
            AlphanumericState initial = parseInitialState(config);
            alphaIndex = initial.getAlphaIndex();
            numericValue = initial.getNumericValue();
        }

        String alphaStr = AlphabeticSequenceCalculator.indexToString(alphaIndex, alphaLen);
        String numFormat = "%0".concat(String.valueOf(numLen)).concat("d");
        String numStr = String.format(numFormat, numericValue);

        if (isNumericFirst(config)) {
            return numStr.concat(alphaStr);
        } else {
            return alphaStr.concat(numStr);
        }
    }

    public static Long calculateRemainingCapacity(Long currentAlphaIndex, Long currentNumericValue, SequenceConfigDTO config) {
        int alphaLen = getAlphaLength(config);
        int numLen = getNumericLength(config);
        long maxAlphaIndex = AlphabeticSequenceCalculator.getMaxIndex(alphaLen);
        long maxNumeric = config.getMaxValue() != null ? config.getMaxValue() : (long) Math.pow(10, numLen) - 1;
        long minNumeric = config.getMinValue() != null ? config.getMinValue() : 1L;
        long step = config.getIncrementStep() != null && config.getIncrementStep() > 0
                ? config.getIncrementStep()
                : (config.getIncrement() != null && config.getIncrement() > 0 ? config.getIncrement() : 1L);

        if (currentAlphaIndex == null || currentNumericValue == null) {
            AlphanumericState initial = parseInitialState(config);
            currentAlphaIndex = initial.getAlphaIndex();
            currentNumericValue = initial.getNumericValue();
        }

        long totalNumericSteps = (maxNumeric - minNumeric) / step + 1;
        long remainingNumericCurrentCycle = Math.max(0L, (maxNumeric - currentNumericValue) / step);
        long remainingAlphaCycles = Math.max(0L, maxAlphaIndex - currentAlphaIndex);
        return remainingNumericCurrentCycle + (remainingAlphaCycles * totalNumericSteps);
    }

    public static Double calculateConsumptionPercentage(Long currentAlphaIndex, Long currentNumericValue, SequenceConfigDTO config) {
        int alphaLen = getAlphaLength(config);
        int numLen = getNumericLength(config);
        long maxAlphaIndex = AlphabeticSequenceCalculator.getMaxIndex(alphaLen);
        long maxNumeric = config.getMaxValue() != null ? config.getMaxValue() : (long) Math.pow(10, numLen) - 1;
        long minNumeric = config.getMinValue() != null ? config.getMinValue() : 1L;
        long step = config.getIncrementStep() != null && config.getIncrementStep() > 0
                ? config.getIncrementStep()
                : (config.getIncrement() != null && config.getIncrement() > 0 ? config.getIncrement() : 1L);

        long numSteps = (maxNumeric - minNumeric) / step + 1;
        long alphaSteps = maxAlphaIndex + 1;
        long totalCapacity = numSteps * alphaSteps;

        if (totalCapacity <= 0) {
            return 100.0;
        }

        Long remaining = calculateRemainingCapacity(currentAlphaIndex, currentNumericValue, config);
        long consumed = totalCapacity - remaining;
        double percentage = ((double) consumed / totalCapacity) * 100.0;
        return Math.min(100.0, Math.max(0.0, percentage));
    }
}
