package com.serviceplus.form.validation.pattern.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import com.serviceplus.form.validation.pattern.enums.StandardPlaceholder;

import java.lang.reflect.Type;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAdapter(ResetAttributeDTO.GsonAdapter.class)
public class ResetAttributeDTO {

    private String token;
    private String mappedAttribute;

    public ResetAttributeDTO() {
    }

    public ResetAttributeDTO(String token) {
        this.token = token;
        normalize(this);
    }

    public ResetAttributeDTO(String token, String mappedAttribute) {
        this.token = token;
        this.mappedAttribute = mappedAttribute;
        normalize(this);
    }

    @JsonCreator
    public static ResetAttributeDTO fromValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ResetAttributeDTO dto) {
            normalize(dto);
            return dto;
        }
        if (value instanceof java.util.Map<?, ?> map) {
            ResetAttributeDTO dto = new ResetAttributeDTO();
            Object token = map.get("token");
            if (token != null) {
                dto.setToken(String.valueOf(token));
            }
            Object mapped = map.get("mappedAttribute");
            if (mapped != null) {
                dto.setMappedAttribute(String.valueOf(mapped));
            }
            normalize(dto);
            return dto;
        }
        if (value instanceof String s) {
            String str = s.trim();
            // Check if string is serialized JSON like {"token": "{FY}", "mappedAttribute": ""}
            if (str.startsWith("{") && (str.contains("\"token\"") || str.contains("'token'") || str.contains("mappedAttribute"))) {
                ResetAttributeDTO dto = parseJsonString(str);
                normalize(dto);
                return dto;
            }
            // If it's a dynamic form attribute path like "s_1$2076915554479902720$sp217300"
            if (str.contains("$") && !str.contains("{")) {
                ResetAttributeDTO dto = new ResetAttributeDTO(StandardPlaceholder.SERVICE_ATTRIBUTE.getToken(), str);
                return dto;
            }
            ResetAttributeDTO dto = new ResetAttributeDTO(str);
            normalize(dto);
            return dto;
        }
        ResetAttributeDTO dto = new ResetAttributeDTO(String.valueOf(value));
        normalize(dto);
        return dto;
    }

    private static ResetAttributeDTO parseJsonString(String json) {
        ResetAttributeDTO dto = new ResetAttributeDTO();
        Matcher mToken = Pattern.compile("[\"']token[\"']\\s*:\\s*[\"']([^\"']*)[\"']").matcher(json);
        if (mToken.find()) {
            dto.setToken(mToken.group(1));
        }
        Matcher mMapped = Pattern.compile("[\"']mappedAttribute[\"']\\s*:\\s*[\"']([^\"']*)[\"']").matcher(json);
        if (mMapped.find()) {
            String mapped = mMapped.group(1);
            if (mapped != null && !mapped.isBlank()) {
                dto.setMappedAttribute(mapped.trim());
            }
        }
        return dto;
    }

    /**
     * Enforces normalization rules:
     * 1. Standardizes token names (e.g. FY -> {FY}, FINANCIAL_YEAR -> {FY}).
     * 2. Enforces strict rule: Only SPGD_ENTITY and SERVICE_ATTRIBUTE can have a mappedAttribute.
     *    For all other tokens ({FY}, {YYYY}, {SERVICE_CODE}, etc.), mappedAttribute is stripped to null.
     */
    public static void normalize(ResetAttributeDTO dto) {
        if (dto == null) {
            return;
        }
        String t = dto.getToken();
        if (t != null) {
            String clean = t.replaceAll("[{}]", "").trim();
            if ("FY".equalsIgnoreCase(clean) || "FINANCIAL_YEAR".equalsIgnoreCase(clean)) {
                dto.setToken(StandardPlaceholder.FY.getToken());
            } else if ("YYYY".equalsIgnoreCase(clean) || "CALENDAR_YEAR".equalsIgnoreCase(clean)) {
                dto.setToken(StandardPlaceholder.YYYY.getToken());
            } else if ("SERVICE_CODE".equalsIgnoreCase(clean)) {
                dto.setToken(StandardPlaceholder.SERVICE_CODE.getToken());
            } else if ("SPGD_ENTITY".equalsIgnoreCase(clean)) {
                dto.setToken(StandardPlaceholder.SPGD_ENTITY.getToken());
            } else if ("SERVICE_ATTRIBUTE".equalsIgnoreCase(clean) || "SERVICE_ATTR".equalsIgnoreCase(clean)) {
                dto.setToken(StandardPlaceholder.SERVICE_ATTRIBUTE.getToken());
            } else {
                dto.setToken(t.trim());
            }
        }

        // Rule: Only SPGD_ENTITY and SERVICE_ATTRIBUTE can have a mappedAttribute
        String currentToken = dto.getToken() != null ? dto.getToken().replaceAll("[{}]", "").trim().toUpperCase() : "";
        boolean canHaveMapped = "SPGD_ENTITY".equals(currentToken) || "SERVICE_ATTRIBUTE".equals(currentToken) || "SERVICE_ATTR".equals(currentToken);
        if (!canHaveMapped) {
            dto.setMappedAttribute(null);
        } else if (dto.getMappedAttribute() != null && dto.getMappedAttribute().isBlank()) {
            dto.setMappedAttribute(null);
        }
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getMappedAttribute() {
        return mappedAttribute;
    }

    public void setMappedAttribute(String mappedAttribute) {
        this.mappedAttribute = mappedAttribute;
    }

    @Override
    public String toString() {
        return token != null ? token : "";
    }

    public static class GsonAdapter implements JsonDeserializer<ResetAttributeDTO>, JsonSerializer<ResetAttributeDTO> {
        @Override
        public ResetAttributeDTO deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                return ResetAttributeDTO.fromValue(json.getAsString());
            }
            if (json.isJsonObject()) {
                JsonObject obj = json.getAsJsonObject();
                String token = obj.has("token") && !obj.get("token").isJsonNull() ? obj.get("token").getAsString() : null;
                String mapped = obj.has("mappedAttribute") && !obj.get("mappedAttribute").isJsonNull() ? obj.get("mappedAttribute").getAsString() : null;
                return ResetAttributeDTO.fromValue(new ResetAttributeDTO(token, mapped));
            }
            return ResetAttributeDTO.fromValue(json.toString());
        }

        @Override
        public JsonElement serialize(ResetAttributeDTO src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) {
                return JsonNull.INSTANCE;
            }
            JsonObject obj = new JsonObject();
            if (src.getToken() != null) {
                obj.addProperty("token", src.getToken());
            }
            if (src.getMappedAttribute() != null) {
                obj.addProperty("mappedAttribute", src.getMappedAttribute());
            }
            return obj;
        }
    }
}
