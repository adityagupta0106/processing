package com.serviceplus.form.validation.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.HandlerResponse;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.flow.EventDecider;
import com.serviceplus.form.validation.flow.EventRouter;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;
import com.serviceplus.form.validation.service.ReactiveApiClient;
import com.serviceplus.form.validation.service.TransactionGeneration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_BULK_STATUS_KEY;
import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_STATUS_KEY;
import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_PREVIEW_FORM;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

@Service
@SanitizeRequest
public class PreviewFormHandler implements ApplicationFlowHandler {

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    private final ReactiveApiClient reactiveApiClient;
    private final EventDecider eventDecider;
    private final TransactionGeneration transactionGeneration;
    private final EventRouter applicationFlowRouter;
    private final ApplicationFlowRouterRepository applicationFlowRouterRepository;
    private final ProcessingTxnRepository processingTxnRepository;
    private final ObjectMapper objectMapper;

    public PreviewFormHandler(ReactiveApiClient reactiveApiClient,
                              EventDecider eventDecider,
                              TransactionGeneration transactionGeneration,
                              @Lazy EventRouter applicationFlowRouter,
                              ApplicationFlowRouterRepository applicationFlowRouterRepository,
                              ProcessingTxnRepository processingTxnRepository,
                              ObjectMapper objectMapper) {
        this.reactiveApiClient = reactiveApiClient;
        this.eventDecider = eventDecider;
        this.transactionGeneration = transactionGeneration;
        this.applicationFlowRouter = applicationFlowRouter;
        this.applicationFlowRouterRepository = applicationFlowRouterRepository;
        this.processingTxnRepository = processingTxnRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getActivityType() {
        return ACTIVITY_PREVIEW_FORM;
    }

    @Override
    public Mono<ServerResponse> fetch(String applicationId,
                                      ServerRequest request,
                                      String statusKey,
                                      String txnId,
                                      Mono<TempTransactionLogs> fetch,
                                      ApplicationFlowStatusEntity flow,
                                      ServiceMeta service,
                                      boolean fromDraft) {

        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
        String resolvedTxnId = (flow != null && flow.getTxnId() != null && !flow.getTxnId().isBlank())
                ? flow.getTxnId() : txnId;

        applicationFlowLogs.info("PreviewFormHandler fetch called | applicationId: {} | txnId: {}", applicationId, resolvedTxnId);

        return resolveDataAndFormId(applicationId, flow, service)
                .flatMap(ids -> {
                    String dataId = ids.get("dataId");
                    String formId = ids.get("formId");

                    return reactiveApiClient.fetchFormPreviewHtml(formId, dataId, applicationId, service, resolvedTxnId, user)
                            .flatMap(previewHtml -> {
                                String cleanHtml = cleanHtmlString(previewHtml);
                                HandlerResponse hr = new HandlerResponse();
                                hr.setTxnId(resolvedTxnId);
                                hr.setApplicationId(applicationId);
                                hr.setActivityType(ACTIVITY_PREVIEW_FORM);

                                Map<String, Object> data = new HashMap<>();
                                data.put("previewHtml", cleanHtml);
                                data.put("formId", formId);
                                data.put("dataId", dataId);
                                data.put("allowEdit", true);
                                hr.setData(data);

                                return ServerResponse.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(hr);
                            });
                });
    }

    @Override
    public Mono<ServerResponse> process(String applicationId,
                                        ServerRequest request,
                                        String statusKey,
                                        String txnId,
                                        Mono<TempTransactionLogs> fetch,
                                        ApplicationFlowStatusEntity flow,
                                        ServiceMeta service,
                                        boolean fromDraft) {

        String resolvedTxnId = (flow != null && flow.getTxnId() != null && !flow.getTxnId().isBlank())
                ? flow.getTxnId() : txnId;

        return request.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .defaultIfEmpty(Collections.emptyMap())
                .onErrorResume(e -> Mono.just(Collections.emptyMap()))
                .flatMap(body -> {
                    String queryAction = request.queryParam("action").orElse(null);
                    String bodyAction = body.containsKey("action") && body.get("action") != null
                            ? String.valueOf(body.get("action")) : null;
                    String action = queryAction != null && !queryAction.isBlank() ? queryAction : bodyAction;

                    boolean isEdit = "EDIT".equalsIgnoreCase(action);
                    boolean isProceed = "PROCEED".equalsIgnoreCase(action)
                            || "CONFIRM".equalsIgnoreCase(action)
                            || "SUBMIT".equalsIgnoreCase(action);

                    if (isEdit) {
                        applicationFlowLogs.info("PreviewFormHandler: Edit requested | applicationId: {} | txnId: {}", applicationId, resolvedTxnId);
                        return transactionGeneration.editApplication(service, applicationId, resolvedTxnId, request.exchange().getRequest())
                                .flatMap(processingTxn ->
                                        applicationFlowRouter.route(null, applicationId, request, processingTxn.getTxnId(), service, true)
                                );
                    } else if (isProceed) {
                        applicationFlowLogs.info("PreviewFormHandler: Proceed requested | applicationId: {} | txnId: {}", applicationId, resolvedTxnId);
                        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());

                        return resolveDataAndFormId(applicationId, flow, service)
                                .flatMap(ids -> {
                                    String dataId = ids.get("dataId");

                                    if (flow != null) {
                                        flow.setCompleted(1);
                                        flow.setDataId(dataId);
                                        flow.setLastUpdate(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
                                    }

                                    return processingTxnRepository.findById(resolvedTxnId)
                                            .switchIfEmpty(Mono.error(new SPRuntimeError("Processing transaction not found.", HttpStatus.INTERNAL_SERVER_ERROR, resolvedTxnId)))
                                            .flatMap(txnLog -> eventDecider.proceedToNext(
                                                    dataId,
                                                    service,
                                                    user,
                                                    txnLog,
                                                    applicationId,
                                                    "",
                                                    ACTIVITY_PREVIEW_FORM,
                                                    request,
                                                    flow
                                            ));
                                });
                    } else {
                        // Initial forward after form submission or fetch request
                        return fetch(applicationId, request, statusKey, txnId, fetch, flow, service, fromDraft);
                    }
                });
    }

    private Mono<Map<String, String>> resolveDataAndFormId(String applicationId,
                                                           ApplicationFlowStatusEntity flow,
                                                           ServiceMeta service) {
        String flowDataId = flow != null ? flow.getDataId() : null;
        String flowFormId = flow != null ? flow.getFormId() : null;
        String serviceFormId = service != null ? service.getFormId() : null;

        if (flowDataId != null && !flowDataId.isBlank()) {
            Map<String, String> result = new HashMap<>();
            result.put("dataId", flowDataId);
            result.put("formId", flowFormId != null && !flowFormId.isBlank() ? flowFormId : serviceFormId);
            return Mono.just(result);
        }

        String taskId = flow != null ? flow.getTaskId() : (service != null ? service.getTaskId() : null);

        return applicationFlowRouterRepository
                .findFirstByApplicationIdAndTaskIdAndActivityTypeInAndCompletedOrderByIdDesc(
                        applicationId,
                        taskId,
                        List.of(ACTIVITY_FORM_STATUS_KEY, ACTIVITY_FORM_BULK_STATUS_KEY),
                        1
                )
                .map(fsFlow -> {
                    Map<String, String> result = new HashMap<>();
                    result.put("dataId", fsFlow.getDataId());
                    String resolvedFormId = fsFlow.getFormId() != null && !fsFlow.getFormId().isBlank()
                            ? fsFlow.getFormId()
                            : (flowFormId != null && !flowFormId.isBlank() ? flowFormId : serviceFormId);
                    result.put("formId", resolvedFormId);
                    return result;
                })
                .defaultIfEmpty(Map.of(
                        "dataId", "",
                        "formId", flowFormId != null && !flowFormId.isBlank() ? flowFormId : (serviceFormId != null ? serviceFormId : "")
                ));
    }

    private String cleanHtmlString(String html) {
        if (html == null) return "";
        return html.replace("\r", "")
                   .replace("\n", " ")
                   .replaceAll("\\s+", " ")
                   .replaceAll(">\\s+<", "><")
                   .replace("<style> ", "<style>")
                   .replace(" </style>", "</style>")
                   .trim();
    }
}
