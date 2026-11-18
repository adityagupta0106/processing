package com.serviceplus.form.validation.pattern.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAdapter(ResetConfigDTO.GsonAdapter.class)
public class ResetConfigDTO {

    private Boolean resetEnabled = false;

    /**
     * Periodic reset frequency: NEVER, YEARLY, FINANCIAL_YEAR, MONTHLY, DAILY
     */
    private String resetFrequency = "NEVER";

    /**
     * Selected reset attributes (maximum 3).
     * Supported values: SPGD_ENTITY, FINANCIAL_YEAR, CALENDAR_YEAR, SERVICE_CODE, SERVICE_ATTRIBUTE, etc.
     * Can contain string tokens or objects with token and mappedAttribute.
     */
    private List<ResetAttributeDTO> resetAttributes = new ArrayList<>();

    public ResetConfigDTO() {
    }

    public Boolean getResetEnabled() {
        return resetEnabled;
    }

    public void setResetEnabled(Boolean resetEnabled) {
        this.resetEnabled = resetEnabled;
    }

    public String getResetFrequency() {
        return resetFrequency;
    }

    public void setResetFrequency(String resetFrequency) {
        this.resetFrequency = resetFrequency;
    }

    public List<ResetAttributeDTO> getResetAttributes() {
        return resetAttributes;
    }

    public void setResetAttributes(List<?> resetAttributes) {
        if (resetAttributes == null) {
            this.resetAttributes = new ArrayList<>();
            return;
        }
        List<ResetAttributeDTO> list = new ArrayList<>();
        for (Object item : resetAttributes) {
            if (item != null) {
                ResetAttributeDTO dto = ResetAttributeDTO.fromValue(item);
                if (dto != null) {
                    list.add(dto);
                }
            }
        }
        this.resetAttributes = list;
    }

    public static class GsonAdapter implements JsonDeserializer<ResetConfigDTO>, JsonSerializer<ResetConfigDTO> {
        @Override
        public ResetConfigDTO deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                try {
                    JsonElement parsed = JsonParser.parseString(json.getAsString());
                    return deserialize(parsed, typeOfT, context);
                } catch (Exception e) {
                    return new ResetConfigDTO();
                }
            }
            if (!json.isJsonObject()) {
                return new ResetConfigDTO();
            }
            JsonObject obj = json.getAsJsonObject();
            ResetConfigDTO dto = new ResetConfigDTO();
            if (obj.has("resetEnabled") && !obj.get("resetEnabled").isJsonNull()) {
                dto.setResetEnabled(obj.get("resetEnabled").getAsBoolean());
            }
            if (obj.has("resetFrequency") && !obj.get("resetFrequency").isJsonNull()) {
                dto.setResetFrequency(obj.get("resetFrequency").getAsString());
            }
            if (obj.has("resetAttributes") && !obj.get("resetAttributes").isJsonNull()) {
                JsonElement attrsElem = obj.get("resetAttributes");
                List<ResetAttributeDTO> list = new ArrayList<>();
                if (attrsElem.isJsonArray()) {
                    for (JsonElement item : attrsElem.getAsJsonArray()) {
                        if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                            list.add(ResetAttributeDTO.fromValue(item.getAsString()));
                        } else if (item.isJsonObject()) {
                            JsonObject itemObj = item.getAsJsonObject();
                            String token = itemObj.has("token") && !itemObj.get("token").isJsonNull() ? itemObj.get("token").getAsString() : null;
                            String mapped = itemObj.has("mappedAttribute") && !itemObj.get("mappedAttribute").isJsonNull() ? itemObj.get("mappedAttribute").getAsString() : null;
                            list.add(ResetAttributeDTO.fromValue(new ResetAttributeDTO(token, mapped)));
                        }
                    }
                } else if (attrsElem.isJsonPrimitive() && attrsElem.getAsJsonPrimitive().isString()) {
                    list.add(ResetAttributeDTO.fromValue(attrsElem.getAsString()));
                }
                dto.setResetAttributes(list);
            }
            return dto;
        }

        @Override
        public JsonElement serialize(ResetConfigDTO src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) {
                return JsonNull.INSTANCE;
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("resetEnabled", src.getResetEnabled());
            obj.addProperty("resetFrequency", src.getResetFrequency());
            JsonArray arr = new JsonArray();
            if (src.getResetAttributes() != null) {
                for (ResetAttributeDTO attr : src.getResetAttributes()) {
                    if (attr != null) {
                        JsonObject itemObj = new JsonObject();
                        itemObj.addProperty("token", attr.getToken());
                        if (attr.getMappedAttribute() != null) {
                            itemObj.addProperty("mappedAttribute", attr.getMappedAttribute());
                        }
                        arr.add(itemObj);
                    }
                }
            }
            obj.add("resetAttributes", arr);
            return obj;
        }
    }
}
