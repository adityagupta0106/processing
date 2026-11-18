package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_STATUS_KEY;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.LabelValue;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.EditFormDetails;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import com.serviceplus.form.validation.repository.EditFormDetailsRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class EditFormDetailsService {

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    private final EditFormDetailsRepository editFormDetailsRepository;
    private final ApplicationFlowRouterRepository applicationFlowRouterRepository;
    private final CurrentProcessRepository currentProcessRepository;
    private final ObjectMapper objectMapper;

    public EditFormDetailsService(
            EditFormDetailsRepository editFormDetailsRepository,
            ApplicationFlowRouterRepository applicationFlowRouterRepository,
            CurrentProcessRepository currentProcessRepository,
            ObjectMapper objectMapper) {
        this.editFormDetailsRepository = editFormDetailsRepository;
        this.applicationFlowRouterRepository = applicationFlowRouterRepository;
        this.currentProcessRepository = currentProcessRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates the permission selection against the signed workflow metadata.
     * The payload may be an editPermission object, a list of objects, or a DTO
     * containing only `specific` when exactly one configured selection exists.
     */
    public List<ServiceProcessFlowDTO.Data.EditPermissionData> validateSubmittedPermissions(
            Object payload,
            ServiceProcessFlowDTO.Data.WorkflowElementData trustedWorkflow,
            String txnId) {

        if (payload == null) {
            return Collections.emptyList();
        }

        List<Object> candidates = unwrapPayload(payload);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<ServiceProcessFlowDTO.Data.EditPermissionData> configured =
                trustedWorkflow == null || trustedWorkflow.getEditPermission() == null
                        ? Collections.emptyList()
                        : trustedWorkflow.getEditPermission();

        List<ServiceProcessFlowDTO.Data.EditPermissionData> validated = new ArrayList<>();

        if (configured.isEmpty()) {
            for (Object candidateValue : candidates) {
                ServiceProcessFlowDTO.Data.EditPermissionData candidate = toEditPermission(candidateValue, txnId);
                if (candidate.getSpecific() != null) {
                    validated.add(candidate);
                }
            }
            return validated;
        }

        for (Object candidateValue : candidates) {
            ServiceProcessFlowDTO.Data.EditPermissionData candidate = toEditPermission(candidateValue, txnId);

            String selectedHolderId = candidate.getSelectedHolderId();
            String selectedFormId = candidate.getSelectedFormId();

            List<ServiceProcessFlowDTO.Data.EditPermissionData> matching = configured.stream()
                    .filter(Objects::nonNull)
                    .filter(config -> isBlank(selectedHolderId)
                            || Objects.equals(selectedHolderId, config.getSelectedHolderId()))
                    .filter(config -> isBlank(selectedFormId)
                            || Objects.equals(selectedFormId, config.getSelectedFormId()))
                    .toList();

            ServiceProcessFlowDTO.Data.EditPermissionData trusted = matching.isEmpty() ? candidate : matching.getFirst();
            if (candidate.getSpecific() == null) {
                throw badRequest("Edit permission specific data is required.", txnId);
            }


            ServiceProcessFlowDTO.Data.EditPermissionData normalized =
                    new ServiceProcessFlowDTO.Data.EditPermissionData();
            normalized.setSelectedHolderId(trusted.getSelectedHolderId());
            normalized.setSelectedFormId(trusted.getSelectedFormId());
            normalized.setLabel(candidate.getLabel() != null ? candidate.getLabel() : trusted.getLabel());
            normalized.setSortOrder(candidate.getSortOrder() != null ? candidate.getSortOrder() : trusted.getSortOrder());
            normalized.setModifyChoice(candidate.getModifyChoice() != null ? candidate.getModifyChoice() : trusted.getModifyChoice());
            normalized.setPlacement(candidate.getPlacement() != null ? candidate.getPlacement() : trusted.getPlacement());
            normalized.setSpecific(candidate.getSpecific());
            validated.add(normalized);
        }

        return validated;
    }

    private List<Object> unwrapPayload(Object payload) {
        if (payload instanceof Map<?, ?> map && map.containsKey("editPermission")) {
            return unwrapPayload(map.get("editPermission"));
        }
        if (payload instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (payload instanceof Map<?, ?>) {
            return List.of(payload);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private ServiceProcessFlowDTO.Data.EditPermissionData toEditPermission(Object value, String txnId) {
        if (value instanceof ServiceProcessFlowDTO.Data.EditPermissionData dto) {
            return dto;
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw badRequest("Invalid edit permission request body.", txnId);
        }

        Map<String, Object> map = (Map<String, Object>) rawMap;
        ServiceProcessFlowDTO.Data.EditPermissionData dto = new ServiceProcessFlowDTO.Data.EditPermissionData();
        dto.setSelectedHolderId(asString(map.get("selectedHolderId")));
        dto.setSelectedFormId(asString(map.get("selectedFormId")));
        dto.setLabel(asString(map.get("label")));
        dto.setSpecific(toSpecific(map.get("specific"), txnId));
        if (map.get("sortOrder") instanceof Number num) {
            dto.setSortOrder(num.intValue());
        }
        if (map.get("modifyChoice") instanceof Number num) {
            dto.setModifyChoice(num.intValue());
        }
        dto.setPlacement(asString(map.get("placement")));
        return dto;
    }

    @SuppressWarnings("unchecked")
    private ServiceProcessFlowDTO.Data.EditPermissionData.Specific toSpecific(Object value, String txnId) {
        if (value instanceof ServiceProcessFlowDTO.Data.EditPermissionData.Specific specific) {
            return specific;
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw badRequest("Invalid edit permission specific data.", txnId);
        }

        Map<String, Object> map = (Map<String, Object>) rawMap;
        ServiceProcessFlowDTO.Data.EditPermissionData.Specific specific =
                new ServiceProcessFlowDTO.Data.EditPermissionData.Specific();
        specific.setAllowed(toLabelValues(map.get("allowed"), txnId));
        specific.setRestricted(toLabelValues(map.get("restricted"), txnId));
        specific.setHide(toLabelValues(map.get("hide"), txnId));
        return specific;
    }

    private List<LabelValue> toLabelValues(Object value, String txnId) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (!(value instanceof List<?> values)) {
            throw badRequest("Edit permission values must be arrays.", txnId);
        }

        List<LabelValue> result = new ArrayList<>();
        for (Object item : values) {
            if (item instanceof LabelValue labelValue) {
                normalizeLabelValue(labelValue);
                result.add(labelValue);
            } else if (item instanceof String stringValue) {
                LabelValue labelValue = new LabelValue();
                labelValue.setLabel(stringValue);
                labelValue.setValue(stringValue);
                result.add(labelValue);
            } else if (item instanceof Map<?, ?> itemMap) {
                try {
                    LabelValue labelValue = objectMapper.convertValue(itemMap, LabelValue.class);
                    normalizeLabelValue(labelValue);
                    result.add(labelValue);
                } catch (IllegalArgumentException ex) {
                    throw badRequest("Invalid edit permission value.", txnId);
                }
            } else {
                throw badRequest("Invalid edit permission value.", txnId);
            }
        }
        return result;
    }

    private void normalizeLabelValue(LabelValue labelValue) {
        if (labelValue != null) {
            if (labelValue.getLabel() == null && labelValue.getValue() != null) {
                labelValue.setLabel(labelValue.getValue().toString());
            } else if (labelValue.getValue() == null && labelValue.getLabel() != null) {
                labelValue.setValue(labelValue.getLabel());
            }
        }
    }

    /** Saves only after Form Designer confirms that the form data was saved. */
    public Mono<Void> saveSubmittedPermissions(
            String applicationId,
            Integer serviceId,
            String tenantId,
            List<ServiceProcessFlowDTO.Data.EditPermissionData> permissions,
            String txnId) {

        if (applicationId == null || applicationId.isBlank()
                || permissions == null || permissions.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(permissions)
                .concatMap(permission -> editFormDetailsRepository
                        .findFirstByApplicationIdAndServiceIdAndTenantIdAndHolderIdAndFormId(
                                applicationId,
                                serviceId,
                                tenantId,
                                permission.getSelectedHolderId(),
                                permission.getSelectedFormId())
                        .flatMap(existing -> populateRow(existing, applicationId, serviceId, tenantId, permission, txnId))
                        .switchIfEmpty(Mono.defer(() -> {
                            EditFormDetails row = new EditFormDetails();
                            row.setId(createUniqueId());
                            row.setNewEntity(true);
                            return populateRow(row, applicationId, serviceId, tenantId, permission, txnId);
                        }))
                        .flatMap(row -> editFormDetailsRepository.save(row)))
                .then();
    }

    private Mono<EditFormDetails> populateRow(
            EditFormDetails row,
            String applicationId,
            Integer serviceId,
            String tenantId,
            ServiceProcessFlowDTO.Data.EditPermissionData permission,
            String txnId) {
        try {
            ServiceProcessFlowDTO.Data.EditPermissionData.Specific specific = permission.getSpecific();
            row.setApplicationId(applicationId);
            row.setServiceId(serviceId);
            row.setTenantId(tenantId);
            row.setHolderId(permission.getSelectedHolderId());
            row.setFormId(permission.getSelectedFormId());
            row.setAllowed(objectMapper.writeValueAsString(toIds(specific.getAllowed())));
            row.setRestricted(objectMapper.writeValueAsString(toIds(specific.getRestricted())));
            row.setHide(objectMapper.writeValueAsString(toIds(specific.getHide())));
            row.setSpecific(objectMapper.writeValueAsString(specific));
            
            OffsetDateTime now = OffsetDateTime.now();

            if (row.getCrDate() == null) {
                row.setCrDate(now);
            }

            row.setUpDate(now);
            
            return Mono.just(row);
        } catch (JsonProcessingException e) {
            return Mono.error(new SPRuntimeError(
                    "Unable to serialize edit form configuration.",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    txnId));
        }
    }

    /**
     * Reattaches persisted permission settings to the trusted workflow DTO when
     * a draft/global form is rendered.
     */
    public Mono<ServiceProcessFlowDTO.Data.WorkflowElementData> enrichGlobalWorkflowElementData(
            ServiceProcessFlowDTO.Data.WorkflowElementData workflowElementData,
            String applicationId,
            Integer serviceId,
            String tenantId,
            String txnId) {

        if (workflowElementData == null
                || !Boolean.TRUE.equals(workflowElementData.getIsGlobal())
                || applicationId == null || applicationId.isBlank()
                || workflowElementData.getEditPermission() == null
                || workflowElementData.getEditPermission().isEmpty()) {
            return Mono.justOrEmpty(workflowElementData);
        }

        // Only enrich targets configured in the trusted workflow metadata. This avoids
        // attaching another form's saved permissions to an unrelated global task.
        return Flux.fromIterable(workflowElementData.getEditPermission())
                .filter(Objects::nonNull)
                .filter(permission -> !isBlank(permission.getSelectedHolderId())
                        && !isBlank(permission.getSelectedFormId()))
                .concatMap(permission -> editFormDetailsRepository
                        .findFirstByApplicationIdAndServiceIdAndTenantIdAndHolderIdAndFormId(
                                applicationId,
                                serviceId,
                                tenantId,
                                permission.getSelectedHolderId(),
                                permission.getSelectedFormId())
                        .doOnNext(row -> permission.setSpecific(parseStoredSpecific(row, txnId)))
                        .then())
                .then(Mono.fromSupplier(() -> workflowElementData));
    }

    /**
     * Retrieves saved edit permissions for rendering draft / global form if applicationId and formId match.
     * Returns:
     * {
     *   "allowed": ["..."],
     *   "restricted": ["..."],
     *   "hide": ["..."]
     * }
     */
    public Mono<Map<String, Object>> getSavedEditPermission(String applicationId, String formId) {
        if (isBlank(applicationId) || isBlank(formId)) {
            return Mono.empty();
        }

        return editFormDetailsRepository
                .findFirstByApplicationIdAndFormIdOrderByUpDateDesc(applicationId, formId)
                .map(row -> {
                    Map<String, Object> permissionMap = new LinkedHashMap<>();
                    permissionMap.put("allowed", parseStoredIdList(row.getAllowed(), row.getSpecific(), "allowed"));
                    permissionMap.put("restricted", parseStoredIdList(row.getRestricted(), row.getSpecific(), "restricted"));
                    permissionMap.put("hide", parseStoredIdList(row.getHide(), row.getSpecific(), "hide"));
                    return permissionMap;
                });
    }

    private List<String> parseStoredIdList(String columnValue, String specificJson, String key) {
        if (columnValue != null && !columnValue.isBlank()) {
            try {
                return objectMapper.readValue(columnValue, new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
            }
        }
        if (specificJson != null && !specificJson.isBlank()) {
            try {
                Map<String, Object> specificMap = objectMapper.readValue(specificJson, Map.class);
                Object val = specificMap.get(key);
                if (val instanceof List<?> list) {
                    List<String> ids = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof String str) {
                            ids.add(str);
                        } else if (item instanceof Map<?, ?> m && m.containsKey("value")) {
                            ids.add(Objects.toString(m.get("value"), ""));
                        }
                    }
                    return ids;
                }
            } catch (Exception ignored) {
            }
        }
        return Collections.emptyList();
    }

    private ServiceProcessFlowDTO.Data.EditPermissionData.Specific parseStoredSpecific(
            EditFormDetails row, String txnId) {
        try {
            if (row.getSpecific() != null && !row.getSpecific().isBlank()) {
                return objectMapper.readValue(
                        row.getSpecific(),
                        ServiceProcessFlowDTO.Data.EditPermissionData.Specific.class);
            }

            ServiceProcessFlowDTO.Data.EditPermissionData.Specific specific =
                    new ServiceProcessFlowDTO.Data.EditPermissionData.Specific();
            specific.setAllowed(parseStoredLabelValues(row.getAllowed(), txnId));
            specific.setRestricted(parseStoredLabelValues(row.getRestricted(), txnId));
            specific.setHide(parseStoredLabelValues(row.getHide(), txnId));
            return specific;
        } catch (Exception e) {
            throw new SPRuntimeError(
                    "Unable to read saved edit form configuration.",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    txnId);
        }
    }


    private List<LabelValue> parseStoredLabelValues(String value, String txnId) throws JsonProcessingException {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        List<Object> rawValues = objectMapper.readValue(
                value,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Object.class));
        return toLabelValues(rawValues, "");
    }

    private List<String> toIds(List<LabelValue> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> value.getValue() == null ? value.getLabel() : value.getValue().toString())
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    /**
     * For each newly-created, global form task, create an incomplete FS flow (completed = 0)
     * in schm_sp.application_flow_status so that the next global form task can be initialized.
     */
    public Mono<Void> createGlobalTaskFlowStatuses(
            List<CurrentProcess> processes,
            String tenantId,
            String txnId) {

        if (processes == null || processes.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(processes)
                .filter(process -> Boolean.TRUE.equals(process.getIsGlobal()))
                .filter(process -> process.getFormId() != null && !process.getFormId().isBlank())
                .filter(process -> "N".equals(process.getActionTaken()))
                .concatMap(process -> createFlowForGlobalProcess(process, tenantId, txnId))
                .then();
    }

    private Mono<ApplicationFlowStatusEntity> createFlowForGlobalProcess(
            CurrentProcess generatedProcess,
            String tenantId,
            String parentTxnId) {

        applicationFlowLogs.info(
                "Checking global task flow status for applicationId: {}, taskId: {}, formId: {}",
                generatedProcess.getApplicationId(),
                generatedProcess.getCurrentTask(),
                generatedProcess.getFormId());

        return applicationFlowRouterRepository
                .findFirstByApplicationIdAndCompletedAndTaskIdAndServiceIdAndTenantIdAndActivityTypeOrderByIdDesc(
                        generatedProcess.getApplicationId(),
                        0,
                        generatedProcess.getCurrentTask(),
                        generatedProcess.getServiceId(),
                        tenantId,
                        ACTIVITY_FORM_STATUS_KEY)
                .doOnNext(existing -> applicationFlowLogs.info(
                        "Incomplete flow status already exists for applicationId: {}, taskId: {}, txnId: {}",
                        existing.getApplicationId(),
                        existing.getTaskId(),
                        existing.getTxnId()))
                .switchIfEmpty(Mono.defer(() -> createIncompleteFormFlow(generatedProcess, tenantId, parentTxnId)));
    }

    private Mono<ApplicationFlowStatusEntity> createIncompleteFormFlow(
            CurrentProcess generatedProcess,
            String tenantId,
            String parentTxnId) {

        String appId = generatedProcess.getApplicationId();
        String taskId = generatedProcess.getCurrentTask();
        String formId = generatedProcess.getFormId();
        Integer serviceId = generatedProcess.getServiceId();

        applicationFlowLogs.info(
                "ParentTxnId: {} | Initializing incomplete global form flow. appId: {}, taskId: {}, formId: {}, serviceId: {}, tenantId: {}",
                parentTxnId, appId, taskId, formId, serviceId, tenantId);

        ApplicationFlowStatusEntity flow = new ApplicationFlowStatusEntity();
        flow.setId(createUniqueId());
        flow.setApplicationId(appId);
        flow.setActivityType(ACTIVITY_FORM_STATUS_KEY);
        flow.setTxnId(createUniqueId());
        flow.setCompleted(0);
        flow.setFormId(formId);
        flow.setServiceId(serviceId);
        flow.setTaskId(taskId);
        flow.setTenantId(tenantId);
        flow.setLastUpdate(LocalDateTime.now());
        flow.setNewEntity(true);

        return applicationFlowRouterRepository
                .findFirstByApplicationIdAndCompletedAndFormIdAndServiceIdAndTenantIdAndActivityTypeOrderByIdDesc(
                        appId,
                        1,
                        formId,
                        serviceId,
                        tenantId,
                        ACTIVITY_FORM_STATUS_KEY)
                .doOnNext(previousFlow -> applicationFlowLogs.info(
                        "ParentTxnId: {} | Found previous completed flow by formId: {}. txnId: {}, dataId: {} for appId: {}",
                        parentTxnId, formId, previousFlow.getTxnId(), previousFlow.getDataId(), appId))
                .map(previousFlow -> Objects.toString(previousFlow.getDataId(), ""))
                .filter(dataId -> !dataId.isBlank())
                .defaultIfEmpty("")
                .flatMap(dataId -> {
                    if (dataId.isBlank()) {
                        flow.setDataId(null);
                        applicationFlowLogs.info(
                                "ParentTxnId: {} | No previous completed flow/dataId found to carry over. Setting dataId=null for appId: {}, taskId: {}, formId: {}",
                                parentTxnId, appId, taskId, formId);
                    } else {
                        flow.setDataId(dataId);
                        applicationFlowLogs.info(
                                "ParentTxnId: {} | Carried over previous dataId: {} for appId: {}, taskId: {}, formId: {}",
                                parentTxnId, dataId, appId, taskId, formId);
                    }

                    applicationFlowLogs.info(
                            "ParentTxnId: {} | Persisting incomplete global application_flow_status: appId={}, taskId={}, formId={}, newTxnId={}, dataId={}",
                            parentTxnId,
                            flow.getApplicationId(),
                            flow.getTaskId(),
                            flow.getFormId(),
                            flow.getTxnId(),
                            flow.getDataId());

                    return applicationFlowRouterRepository.save(flow)
                            .doOnSuccess(saved -> applicationFlowLogs.info(
                                    "ParentTxnId: {} | Successfully persisted incomplete global flow. id={}, txnId={}, appId={}, taskId={}, formId={}, dataId={}",
                                    parentTxnId,
                                    saved.getId(),
                                    saved.getTxnId(),
                                    saved.getApplicationId(),
                                    saved.getTaskId(),
                                    saved.getFormId(),
                                    saved.getDataId()))
                            .doOnError(err -> applicationFlowLogs.error(
                                    "ParentTxnId: {} | Failed to persist incomplete global flow for appId={}, taskId={}, formId={}. Error: {}",
                                    parentTxnId,
                                    flow.getApplicationId(),
                                    flow.getTaskId(),
                                    flow.getFormId(),
                                    err.getMessage(),
                                    err));
                });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static SPRuntimeError badRequest(String message, String txnId) {
        return new SPRuntimeError(message, HttpStatus.BAD_REQUEST, txnId);
    }
}
