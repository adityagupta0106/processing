package com.serviceplus.form.validation.pattern.renderer;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.pattern.dto.PatternComponentDTO;
import com.serviceplus.form.validation.pattern.dto.PatternConfigurationDTO;
import com.serviceplus.form.validation.pattern.enums.ComponentType;
import com.serviceplus.form.validation.pattern.enums.StandardPlaceholder;
import com.serviceplus.form.validation.pattern.enums.TransformationType;
import com.serviceplus.form.validation.pattern.sequence.ChecksumCalculator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatternRenderer {

    private static final Logger log = LogManager.getLogger("applicationFlowLogger");

    /**
     * Renders the complete identifier strictly through the "components" node (sorted by sequence).
     * Checksum is computed over the entire identifier (all values prior to and following the checksum attribute),
     * and inserted at the exact configured sequence position where {CHECKSUM} was placed.
     */
    public static String render(PatternConfigurationDTO config, Map<String, String> resolvedPlaceholders, String formattedSequence) {
        return render(config, resolvedPlaceholders, formattedSequence, null);
    }

    public static String render(PatternConfigurationDTO config, Map<String, String> resolvedPlaceholders, String formattedSequence, String txnId) {
        List<PatternComponentDTO> components = config.getComponents();
        if (components == null || components.isEmpty()) {
            log.warn("No components found in pattern configuration: '{}'. Returning raw formattedSequence: '{}'",
                    config.getPatternId(), formattedSequence);
            return formattedSequence;
        }
        return renderFromComponents(config, components, resolvedPlaceholders, formattedSequence, txnId);
    }

    private static String renderFromComponents(PatternConfigurationDTO config, List<PatternComponentDTO> components,
                                               Map<String, String> resolvedPlaceholders, String formattedSequence, String txnId) {
        // Sort components strictly by sequence
        List<PatternComponentDTO> sortedComponents = new java.util.ArrayList<>(components);
        sortedComponents.sort(Comparator.comparing(PatternComponentDTO::getSequence, Comparator.nullsLast(Comparator.naturalOrder())));

        log.info("Rendering pattern: '{}' with {} components. FormattedSequence: '{}', txnId: '{}'",
                config.getPatternId(), sortedComponents.size(), formattedSequence, txnId);

        StringBuilder currentText = new StringBuilder();
        int checksumInsertionIndex = -1;
        PatternComponentDTO checksumComponent = null;

        for (PatternComponentDTO comp : sortedComponents) {
            String token = comp.getToken();
            ComponentType compType = comp.getType();

            if (StandardPlaceholder.CHECKSUM.getToken().equalsIgnoreCase(token)) {
                // Record the exact position where {CHECKSUM} was dropped so it can be calculated over all components (before and after)
                checksumInsertionIndex = currentText.length();
                checksumComponent = comp;
                log.info("Encountered {CHECKSUM} component at character position {}. Checksum will be computed over full identifier after loop.",
                        checksumInsertionIndex);
                continue;
            }

            String partValue;
            if (compType == ComponentType.SEPARATOR) {
                partValue = comp.getValue() != null ? comp.getValue() : (token != null ? token : "");
            } else if (compType == ComponentType.STATIC) {
                partValue = comp.getValue() != null ? comp.getValue() : "";
            } else if (StandardPlaceholder.SEQ.getToken().equalsIgnoreCase(token)) {
                partValue = formattedSequence;
            } else {
                // Try resolving by mappedAttribute first, then by token
                if (comp.getMappedAttribute() != null && !comp.getMappedAttribute().isBlank()
                        && resolvedPlaceholders.containsKey(comp.getMappedAttribute())) {
                    partValue = resolvedPlaceholders.get(comp.getMappedAttribute());
                } else if (token != null && resolvedPlaceholders.containsKey(token)) {
                    partValue = resolvedPlaceholders.get(token);
                } else {
                    partValue = comp.getDefaultValue() != null ? comp.getDefaultValue() : "";
                }
            }

            if (Boolean.TRUE.equals(comp.getMandatory()) && (partValue == null || partValue.isBlank())) {
                String missingAttr = comp.getMappedAttribute() != null && !comp.getMappedAttribute().isBlank() ? comp.getMappedAttribute() : token;
                log.error("Mandatory component '{}' is missing or empty in runtime context for pattern '{}', txnId: '{}'",
                        missingAttr, config.getPatternId(), txnId);
                SPRuntimeError err = new SPRuntimeError(
                        "Mandatory component '".concat(token != null ? token : "UNKNOWN").concat("' is missing or empty in runtime context"),
                        HttpStatus.BAD_REQUEST, txnId);
                Map<String, Object> data = new HashMap<>();
                data.put("patternId", config.getPatternId());
                data.put("attributeName", missingAttr);
                data.put("errorCode", "PATTERN_MANDATORY_ATTRIBUTE_MISSING");
                err.setData(data);
                throw err;
            }

            if (comp.getTransformation() == TransformationType.UPPERCASE && partValue != null) {
                partValue = partValue.toUpperCase();
            } else if (comp.getTransformation() == TransformationType.LOWERCASE && partValue != null) {
                partValue = partValue.toLowerCase();
            }

            log.info("Rendered component [seq={}, type={}, token='{}', mappedAttribute='{}'] -> value='{}'",
                    comp.getSequence(), compType, token, comp.getMappedAttribute(), partValue);

            if (partValue != null) {
                currentText.append(partValue);
            }
        }

        // Post-loop checksum calculation: computes checksum over all values (before AND after checksum position)
        if (checksumInsertionIndex >= 0 && checksumComponent != null) {
            String fullTextWithoutChecksum = currentText.toString();
            String algorithm = (config.getChecksumConfig() != null && config.getChecksumConfig().getAlgorithm() != null)
                    ? config.getChecksumConfig().getAlgorithm() : "LUHN";
            String checksumDigit = ChecksumCalculator.computeChecksum(fullTextWithoutChecksum, algorithm);

            if (checksumComponent.getTransformation() == TransformationType.UPPERCASE && checksumDigit != null) {
                checksumDigit = checksumDigit.toUpperCase();
            } else if (checksumComponent.getTransformation() == TransformationType.LOWERCASE && checksumDigit != null) {
                checksumDigit = checksumDigit.toLowerCase();
            }

            log.info("Computed checksum digit '{}' using algorithm '{}' for complete identifier text '{}'. Inserting at position {}",
                    checksumDigit, algorithm, fullTextWithoutChecksum, checksumInsertionIndex);
            currentText.insert(checksumInsertionIndex, checksumDigit);
        }

        String finalResult = currentText.toString();
        log.info("Finished rendering pattern: '{}' -> finalIdentifier: '{}'", config.getPatternId(), finalResult);
        return finalResult;
    }
}
