package com.serviceplus.form.validation.pattern.renderer;

import com.serviceplus.form.validation.dto.FormData;
import com.serviceplus.form.validation.pattern.dto.PatternComponentDTO;
import com.serviceplus.form.validation.pattern.dto.PatternConfigurationDTO;
import com.serviceplus.form.validation.pattern.dto.PrefixSuffixConfigDTO;
import com.serviceplus.form.validation.pattern.dto.ResetAttributeDTO;
import com.serviceplus.form.validation.pattern.enums.StandardPlaceholder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class PlaceholderResolver {

    private static final Logger log = LogManager.getLogger("applicationFlowLogger");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Resolves standard placeholders and dynamic mapped attributes from context and form attributes.
     */
    public static Map<String, String> resolvePlaceholders(PatternConfigurationDTO config,
                                                          Map<String, Object> formAttributes,
                                                          Map<String, Object> systemVariables,
                                                          LocalDateTime generationDate) {
        log.info("Resolving placeholders for pattern: '{}'. FormAttributes count: {}, SystemVariables count: {}",
                config != null ? config.getPatternId() : "null",
                formAttributes != null ? formAttributes.size() : 0,
                systemVariables != null ? systemVariables.size() : 0);
        if (generationDate == null) {
            generationDate = LocalDateTime.now();
        }
        if (formAttributes == null) {
            formAttributes = new HashMap<>();
        }
        if (systemVariables == null) {
            systemVariables = new HashMap<>();
        }

        Map<String, String> resolved = new HashMap<>();

        // 1. Prefix
        PrefixSuffixConfigDTO prefix = config.getPrefixConfig();
        if (prefix != null) {
            String prefixVal = "";
            if (prefix.getMappedAttribute() != null && !prefix.getMappedAttribute().isBlank()) {
                Object val = formAttributes.get(prefix.getMappedAttribute());
                if (val != null) {
                    prefixVal = String.valueOf(val).trim();
                }
            }
            if (prefixVal.isEmpty() && prefix.getStaticValue() != null) {
                prefixVal = prefix.getStaticValue().trim();
            }
            if (prefixVal.isEmpty() && prefix.getSystemVariable() != null) {
                Object val = systemVariables.get(prefix.getSystemVariable());
                if (val != null) {
                    prefixVal = String.valueOf(val).trim();
                }
            }
            resolved.put(StandardPlaceholder.PREFIX.getToken(), prefixVal);
        } else {
            resolved.put(StandardPlaceholder.PREFIX.getToken(), "");
        }

        // 2. Suffix
        PrefixSuffixConfigDTO suffix = config.getSuffixConfig();
        if (suffix != null) {
            String suffixVal = "";
            if (suffix.getMappedAttribute() != null && !suffix.getMappedAttribute().isBlank()) {
                Object val = formAttributes.get(suffix.getMappedAttribute());
                if (val != null) {
                    suffixVal = String.valueOf(val).trim();
                }
            }
            if (suffixVal.isEmpty() && suffix.getStaticValue() != null) {
                suffixVal = suffix.getStaticValue().trim();
            }
            if (suffixVal.isEmpty() && suffix.getSystemVariable() != null) {
                Object val = systemVariables.get(suffix.getSystemVariable());
                if (val != null) {
                    suffixVal = String.valueOf(val).trim();
                }
            }
            resolved.put(StandardPlaceholder.SUFFIX.getToken(), suffixVal);
        } else {
            resolved.put(StandardPlaceholder.SUFFIX.getToken(), "");
        }

        // 3. Service Code (derive from systemVariables or serviceId)
        String serviceCode = "";
        Object sc = systemVariables.get("serviceCode");
        if (sc == null) {
            sc = systemVariables.get("SERVICE_CODE");
        }
        if (sc == null && config.getServiceId() != null) {
            sc = String.valueOf(config.getServiceId());
        }
        serviceCode = sc != null ? String.valueOf(sc) : "";
        resolved.put(StandardPlaceholder.SERVICE_CODE.getToken(), serviceCode);

        // 4. Date & Time Components (Context dropped directly from UI)
        int year = generationDate.getYear();
        int month = generationDate.getMonthValue();
        int day = generationDate.getDayOfMonth();

        resolved.put(StandardPlaceholder.YYYY.getToken(), String.format("%04d", year));
        resolved.put("YYYY", String.format("%04d", year));
        resolved.put("CALENDAR_YEAR", String.format("%04d", year));
        resolved.put("{CALENDAR_YEAR}", String.format("%04d", year));
        resolved.put(StandardPlaceholder.YY.getToken(), String.format("%02d", year % 100));
        resolved.put(StandardPlaceholder.MM.getToken(), String.format("%02d", month));
        resolved.put(StandardPlaceholder.DD.getToken(), String.format("%02d", day));

        // Financial Year: India fiscal calendar runs April 1 to March 31
        String fy;
        if (month >= 4) {
            fy = String.format("%d-%02d", year, (year + 1) % 100);
        } else {
            fy = String.format("%d-%02d", year - 1, year % 100);
        }
        resolved.put(StandardPlaceholder.FY.getToken(), fy);
        resolved.put("FY", fy);
        resolved.put("FINANCIAL_YEAR", fy);
        resolved.put("{FINANCIAL_YEAR}", fy);
        resolved.put(StandardPlaceholder.HH_MM_SS.getToken(), generationDate.format(TIME_FORMATTER));
        resolved.put(StandardPlaceholder.HH.getToken(), String.format("%02d", generationDate.getHour()));
        resolved.put("HH", String.format("%02d", generationDate.getHour()));
        resolved.put(StandardPlaceholder.mm.getToken(), String.format("%02d", generationDate.getMinute()));
        resolved.put("mm", String.format("%02d", generationDate.getMinute()));
        resolved.put(StandardPlaceholder.ss.getToken(), String.format("%02d", generationDate.getSecond()));
        resolved.put("ss", String.format("%02d", generationDate.getSecond()));

        // 5. Dynamic Mapped Attributes from Components ({SPGD_ENTITY}, {SERVICE_ATTRIBUTE}, etc.)
        if (config.getComponents() != null) {
            for (PatternComponentDTO comp : config.getComponents()) {
                String token = comp.getToken();
                String mappedAttr = comp.getMappedAttribute();

                String valStr = "";
                if (mappedAttr != null && !mappedAttr.isBlank()) {
                    Object val = formAttributes.get(mappedAttr);
                    if (val != null) {
                        valStr = String.valueOf(val).trim();
                    }
                    resolved.put(mappedAttr, valStr);
                }

                if (token != null) {
                    if (valStr.isEmpty()) {
                        // Fallback check by token name without curly braces
                        String cleanToken = token.replaceAll("[{}]", "");
                        Object val = formAttributes.get(cleanToken);
                        if (val == null) {
                            val = systemVariables.get(cleanToken);
                        }
                        if (val != null) {
                            valStr = String.valueOf(val).trim();
                        }
                    }
                    if (!valStr.isEmpty() || !resolved.containsKey(token)) {
                        resolved.put(token, valStr);
                    }
                }
            }
        }

        // 6. Dynamic Mapped Attributes from Reset Config ({SPGD_ENTITY}, {SERVICE_ATTRIBUTE}, etc.)
        if (config.getResetConfig() != null && config.getResetConfig().getResetAttributes() != null) {
            for (ResetAttributeDTO resetAttr : config.getResetConfig().getResetAttributes()) {
                if (resetAttr == null) {
                    continue;
                }
                String mappedAttr = resetAttr.getMappedAttribute();
                if (mappedAttr != null && !mappedAttr.isBlank()) {
                    Object val = formAttributes.get(mappedAttr);
                    if (val != null) {
                        resolved.put(mappedAttr, String.valueOf(val).trim());
                    }
                }
            }
        }

        return resolved;
    }

    /**
     * Extracts values for reset attributes from the resolved placeholders and context.
     * Supports mapping for {SERVICE_CODE}, {SERVICE_ATTRIBUTE}, {SERVICE_ATTR}, {SPGD_ENTITY}, {FY}, {YYYY},
     * as well as direct mappedAttribute keys (e.g. "1$2085619097902649344$sp349139").
     */
    public static Map<String, String> extractResetAttributeValues(PatternConfigurationDTO config, Map<String, String> resolvedPlaceholders) {
        Map<String, String> resetValues = new HashMap<>();
        if (config.getResetConfig() == null || config.getResetConfig().getResetAttributes() == null) {
            return resetValues;
        }

        for (ResetAttributeDTO resetAttr : config.getResetConfig().getResetAttributes()) {
            if (resetAttr == null) {
                continue;
            }
            String token = resetAttr.getToken();
            String mapped = resetAttr.getMappedAttribute();
            String val = null;

            // 1. If mappedAttribute is directly configured on the reset attribute:
            if (mapped != null && !mapped.isBlank()) {
                val = resolvedPlaceholders.get(mapped);
            }

            // 2. Otherwise resolve by token / standard placeholder / components mapping
            if ((val == null || val.isBlank()) && token != null && !token.isBlank()) {
                String cleanAttr = token.trim();
                String upper = cleanAttr.toUpperCase();

                if ("SERVICE_CODE".equals(upper) || "{SERVICE_CODE}".equals(upper)) {
                    val = resolvedPlaceholders.get(StandardPlaceholder.SERVICE_CODE.getToken());
                    if (val == null || val.isBlank()) {
                        val = resolvedPlaceholders.get("SERVICE_CODE");
                    }
                } else if ("SERVICE_ATTRIBUTE".equals(upper) || "{SERVICE_ATTRIBUTE}".equals(upper)
                        || "SERVICE_ATTR".equals(upper) || "{SERVICE_ATTR}".equals(upper)) {
                    val = resolvedPlaceholders.get(StandardPlaceholder.SERVICE_ATTRIBUTE.getToken());
                    if (val == null || val.isBlank()) {
                        val = resolvedPlaceholders.get("SERVICE_ATTRIBUTE");
                    }
                    if (val == null || val.isBlank()) {
                        if (config.getComponents() != null) {
                            for (PatternComponentDTO comp : config.getComponents()) {
                                if (comp.getToken() != null && comp.getToken().toUpperCase().contains("SERVICE_ATTR")
                                        && comp.getMappedAttribute() != null) {
                                    val = resolvedPlaceholders.get(comp.getMappedAttribute());
                                    if (val != null && !val.isBlank()) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else if ("SPGD_ENTITY".equals(upper) || "{SPGD_ENTITY}".equals(upper)) {
                    val = resolvedPlaceholders.get(StandardPlaceholder.SPGD_ENTITY.getToken());
                    if (val == null || val.isBlank()) {
                        val = resolvedPlaceholders.get("SPGD_ENTITY");
                    }
                    if (val == null || val.isBlank()) {
                        if (config.getComponents() != null) {
                            for (PatternComponentDTO comp : config.getComponents()) {
                                if (comp.getToken() != null && comp.getToken().toUpperCase().contains("SPGD")
                                        && comp.getMappedAttribute() != null) {
                                    val = resolvedPlaceholders.get(comp.getMappedAttribute());
                                    if (val != null && !val.isBlank()) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else if ("FINANCIAL_YEAR".equals(upper) || "{FINANCIAL_YEAR}".equals(upper) || "FY".equals(upper) || "{FY}".equals(upper) || upper.contains("FY")) {
                    val = resolvedPlaceholders.get(StandardPlaceholder.FY.getToken());
                    if (val == null || val.isBlank()) {
                        val = resolvedPlaceholders.get("FY");
                    }
                    if (val == null || val.isBlank()) {
                        val = resolvedPlaceholders.get("FINANCIAL_YEAR");
                    }
                } else if ("CALENDAR_YEAR".equals(upper) || "{CALENDAR_YEAR}".equals(upper) || "YYYY".equals(upper) || "{YYYY}".equals(upper)) {
                    val = resolvedPlaceholders.get(StandardPlaceholder.YYYY.getToken());
                    if (val == null || val.isBlank()) {
                        val = resolvedPlaceholders.get("YYYY");
                    }
                    if (val == null || val.isBlank()) {
                        val = resolvedPlaceholders.get("CALENDAR_YEAR");
                    }
                } else {
                    val = resolvedPlaceholders.get(cleanAttr);
                    if (val == null || val.isBlank()) {
                        val = resolvedPlaceholders.get("{".concat(cleanAttr).concat("}"));
                    }
                    if (val == null || val.isBlank()) {
                        val = resolvedPlaceholders.get(cleanAttr.replaceAll("[{}]", ""));
                    }
                }
            }

            if (val != null && !val.isBlank()) {
                if (mapped != null && !mapped.isBlank()) {
                    resetValues.put(mapped, val);
                }
                if (token != null && !token.isBlank()) {
                    String cleanAttr = token.trim();
                    resetValues.put(cleanAttr, val);
                    resetValues.put(cleanAttr.replaceAll("[{}]", ""), val);
                    resetValues.put("{".concat(cleanAttr.replaceAll("[{}]", "")).concat("}"), val);
                }
            }
        }

        return resetValues;
    }

    /**
     * Helper to extract attribute value from FormData object when mapped like "taskId$formId$attributeId"
     * (e.g. "s1$123$367")
     */
    public static Object extractCurrentAttributeValue(FormData formData, String attributeKey) {
        if (formData == null || attributeKey == null) {
            return null;
        }
        String[] arr = attributeKey.split("\\$");
        if (arr.length != 3) {
            return null;
        }

        String taskId = arr[0];
        String formId = arr[1];
        String attributeId = arr[2];

        if (formData.getTasks() == null) {
            return null;
        }
        FormData.Task task = formData.getTasks().get(taskId);
        if (task == null || task.getForms() == null) {
            return null;
        }
        FormData.Form form = task.getForms().get(formId);
        if (form == null || form.getAttributes() == null) {
            return null;
        }
        FormData.Attribute attribute = form.getAttributes().get(attributeId);
        if (attribute == null || attribute.getVersions() == null || attribute.getVersions().isEmpty()) {
            return null;
        }
        Integer current = attribute.getCurrent();
        if (current == null) {
            return null;
        }
        return attribute.getVersions().get("v".concat(String.valueOf(current)));
    }
}
