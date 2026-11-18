package com.serviceplus.form.validation.utility;

import com.serviceplus.form.validation.dto.ApiActionDefinition;
import com.serviceplus.form.validation.dto.ApiActionDefinition.ApiAuthorizationDefinition;
import com.serviceplus.form.validation.dto.ApiActionDefinition.ApiBodyDefinition;
import com.serviceplus.form.validation.dto.ApiActionDefinition.ApiDefinition;
import com.serviceplus.form.validation.dto.ApiActionDefinition.ApiKeyDefinition;
import com.serviceplus.form.validation.dto.ApiActionDefinition.ApiKeyValueDefinition;
import com.serviceplus.form.validation.dto.ApiActionDefinition.ApiRequestDefinition;
import com.serviceplus.form.validation.dto.ApiActionDefinition.BasicDefinition;
import com.serviceplus.form.validation.dto.ApiActionDefinition.BearerDefinition;
import com.serviceplus.form.validation.dto.ApiActionDefinition.DataMappingDefinition;
import com.serviceplus.form.validation.dto.WebServiceDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WebServiceDefinitionMapper {

    private WebServiceDefinitionMapper() {
        // Private constructor for utility class
    }

    /**
     * Maps WebServiceDetails.Definition to ApiActionDefinition.
     *
     * @param definition WebServiceDetails.Definition source
     * @return ApiActionDefinition target
     */
    public static ApiActionDefinition toApiActionDefinition(WebServiceDetails.Definition definition) {
        if (definition == null) {
            return null;
        }

        ApiActionDefinition action = new ApiActionDefinition();
        action.setId(definition.getId());
        action.setActionType("API");
        action.setDescription(definition.getDescription());

        ApiDefinition apiDefinition = new ApiDefinition();
        apiDefinition.setActiveSection("request");
        apiDefinition.setRequest(mapRequest(definition));
        apiDefinition.setDataMapping(mapDataMappings(definition.getDataMapping()));

        action.setApiDefinition(apiDefinition);
        return action;
    }

    private static ApiRequestDefinition mapRequest(WebServiceDetails.Definition definition) {
        ApiRequestDefinition request = new ApiRequestDefinition();
        request.setCallType(definition.getCallType());
        request.setStoreResponse(definition.getStoreResponse());
        request.setServerValidation(definition.getServerValidation());
        request.setEndpoint(definition.getEndpoint());
        request.setMethod(definition.getMethod());
        request.setHeaders(mapHeaders(definition.getHeaders()));
        request.setParams(mapParams(definition.getParams()));
        request.setBody(mapBody(definition.getBody()));
        request.setAuthorization(mapAuthorization(definition.getAuthorization()));
        return request;
    }

    private static List<ApiKeyValueDefinition> mapHeaders(List<WebServiceDetails.Header> headers) {
        if (headers == null) {
            return new ArrayList<>();
        }
        return headers.stream()
                .filter(h -> h != null)
                .map(h -> {
                    ApiKeyValueDefinition kv = new ApiKeyValueDefinition();
                    kv.setLabel(h.getLabel());
                    kv.setValue(h.getValue());
                    kv.setFieldValue(h.getFieldValue());
                    return kv;
                })
                .collect(Collectors.toList());
    }

    private static List<ApiKeyValueDefinition> mapParams(List<WebServiceDetails.Param> params) {
        if (params == null) {
            return new ArrayList<>();
        }
        return params.stream()
                .filter(p -> p != null)
                .map(p -> {
                    ApiKeyValueDefinition kv = new ApiKeyValueDefinition();
                    kv.setLabel(p.getLabel());
                    kv.setValue(p.getValue());
                    kv.setFieldValue(p.getFieldValue());
                    return kv;
                })
                .collect(Collectors.toList());
    }

    private static ApiBodyDefinition mapBody(WebServiceDetails.Body body) {
        if (body == null) {
            return null;
        }
        ApiBodyDefinition bodyDef = new ApiBodyDefinition();
        bodyDef.setType(body.getType());
        bodyDef.setRaw(body.getRaw());
        bodyDef.setRawFormat(body.getRawFormat());

        if (body.getFormData() != null) {
            bodyDef.setFormData(body.getFormData().stream()
                    .filter(fd -> fd != null)
                    .map(fd -> {
                        ApiKeyValueDefinition kv = new ApiKeyValueDefinition();
                        kv.setLabel(fd.getLabel());
                        kv.setValue(fd.getValue());
                        kv.setFieldValue(fd.getFieldValue());
                        return kv;
                    })
                    .collect(Collectors.toList()));
        } else {
            bodyDef.setFormData(new ArrayList<>());
        }

        if (body.getUrlEncoded() != null) {
            bodyDef.setUrlEncoded(body.getUrlEncoded().stream()
                    .filter(ue -> ue != null)
                    .map(ue -> {
                        ApiKeyValueDefinition kv = new ApiKeyValueDefinition();
                        kv.setLabel(ue.getLabel());
                        kv.setValue(ue.getValue());
                        kv.setFieldValue(ue.getFieldValue());
                        return kv;
                    })
                    .collect(Collectors.toList()));
        } else {
            bodyDef.setUrlEncoded(new ArrayList<>());
        }

        if (body.getRawJson() != null) {
            bodyDef.setRawJson(body.getRawJson().stream()
                    .filter(rj -> rj != null)
                    .map(rj -> {
                        ApiKeyValueDefinition kv = new ApiKeyValueDefinition();
                        kv.setLabel(rj.getLabel());
                        kv.setValue(rj.getValue());
                        kv.setFieldValue(rj.getFieldValue());
                        return kv;
                    })
                    .collect(Collectors.toList()));
        } else {
            bodyDef.setRawJson(new ArrayList<>());
        }

        return bodyDef;
    }

    private static ApiAuthorizationDefinition mapAuthorization(WebServiceDetails.Authorization auth) {
        if (auth == null) {
            return null;
        }
        ApiAuthorizationDefinition authDef = new ApiAuthorizationDefinition();
        authDef.setType(auth.getType());

        if (auth.getApiKey() != null) {
            ApiKeyDefinition apiKey = new ApiKeyDefinition();
            apiKey.setKey(auth.getApiKey().getKey());
            apiKey.setValue(auth.getApiKey().getValue());
            apiKey.setAddTo(auth.getApiKey().getAddTo());
            authDef.setApiKey(apiKey);
        }

        if (auth.getBearer() != null) {
            BearerDefinition bearer = new BearerDefinition();
            bearer.setToken(auth.getBearer().getToken());
            authDef.setBearer(bearer);
        }

        if (auth.getBasic() != null) {
            BasicDefinition basic = new BasicDefinition();
            basic.setUsername(auth.getBasic().getUsername());
            basic.setPassword(auth.getBasic().getPassword());
            authDef.setBasic(basic);
        }

        return authDef;
    }

    private static List<DataMappingDefinition> mapDataMappings(List<WebServiceDetails.DataMapping> dataMappings) {
        if (dataMappings == null) {
            return new ArrayList<>();
        }
        return dataMappings.stream()
                .filter(dm -> dm != null)
                .map(dm -> {
                    DataMappingDefinition mapping = new DataMappingDefinition();
                    mapping.setTargetType(dm.getTargetType());
                    mapping.setLabel(dm.getLabel());
                    mapping.setDataType(dm.getDataType());
                    mapping.setExplicitKeys(dm.getExplicitKeys());
                    mapping.setValueFormat(dm.getValueFormat());
                    mapping.setLabelName(dm.getLabelName());
                    mapping.setMapWith(dm.getMapWith());
                    return mapping;
                })
                .collect(Collectors.toList());
    }
}
