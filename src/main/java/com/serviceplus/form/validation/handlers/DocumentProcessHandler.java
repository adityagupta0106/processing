package com.serviceplus.form.validation.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.reflect.TypeToken;
import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.DocumentGeneration.DocumentProcessService;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.BulkApplicationItem;
import com.serviceplus.form.validation.dto.DocumentGenerationRequest;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.enums.DocumentMode;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.service.RedisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.EntityResponse;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_DOCUMENT_GENERATION;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

@Service
@SanitizeRequest
public class DocumentProcessHandler implements ApplicationFlowHandler {

    private final DocumentProcessService documentProcessService;
    private final ApplicationFlowRouterRepository applicationFlowRouterRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    public DocumentProcessHandler(DocumentProcessService documentProcessService,
                                  ApplicationFlowRouterRepository applicationFlowRouterRepository,
                                  RedisService redisService,
                                  ObjectMapper objectMapper) {
        this.documentProcessService = documentProcessService;
        this.applicationFlowRouterRepository = applicationFlowRouterRepository;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getActivityType() {
        return ACTIVITY_DOCUMENT_GENERATION;
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

        return request.bodyToMono(DocumentGenerationRequest.class)
                .defaultIfEmpty(new DocumentGenerationRequest())
                .flatMap(body -> {

                    String parentTxnId = request.queryParam("parentTxnId")
                            .filter(id -> !id.isBlank())
                            .orElse(null);

                    if (parentTxnId != null) {
                        return redisService.<List<BulkApplicationItem>>fetch(parentTxnId, new TypeToken<List<BulkApplicationItem>>() {}.getType())
                                .flatMap(bulkItems -> {
                                    if (bulkItems != null && !bulkItems.isEmpty()) {
                                        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
                                        String tenantId = user != null ? user.getTenantId() : null;

                                        return Flux.fromIterable(bulkItems)
                                                .concatMap(item -> {
                                                    String appId = item.getApplicationId();
                                                    String childTxnId = item.getTxnId();

                                                    DocumentGenerationRequest appBody = new DocumentGenerationRequest();
                                                    appBody.setMode(body.getMode());
                                                    appBody.setMergedUploadSection(body.getMergedUploadSection());

                                                    if (body.getBulkRequest() != null && body.getBulkRequest().containsKey(appId)) {
                                                        appBody.setDocumentSections(body.getBulkRequest().get(appId));
                                                    } else {
                                                        appBody.setDocumentSections(body.getDocumentSections());
                                                    }

                                                    DocumentMode appMode;
                                                    if (DocumentMode.MERGE.name().equalsIgnoreCase(appBody.getMode())
                                                            || (appBody.getMergedUploadSection() != null && !appBody.getMergedUploadSection().isEmpty())) {
                                                        appMode = DocumentMode.MERGE;
                                                    } else if (appBody.getDocumentSections() != null && !appBody.getDocumentSections().isEmpty()) {
                                                        appMode = DocumentMode.SUBMIT;
                                                    } else {
                                                        appMode = DocumentMode.FETCH;
                                                    }

                                                    return applicationFlowRouterRepository
                                                            .findByApplicationIdAndTxnIdAndCompletedAndTaskIdAndServiceIdAndTenantId(
                                                                    appId,
                                                                    childTxnId,
                                                                    0,
                                                                    service.getTaskId(),
                                                                    service.getServiceId(),
                                                                    tenantId
                                                            )
                                                            .switchIfEmpty(Mono.error(new SPRuntimeError("Flow status not found for applicationId: " + appId, HttpStatus.BAD_REQUEST, childTxnId)))
                                                            .flatMap(childFlow -> documentProcessService.process(
                                                                    appId,
                                                                    request,
                                                                    statusKey,
                                                                    childTxnId,
                                                                    fetch,
                                                                    childFlow,
                                                                    service,
                                                                    fromDraft,
                                                                    appBody,
                                                                    appMode
                                                            ))
                                                            .flatMap(this::captureResponseBody);
                                                })
                                                .collectList()
                                                .flatMap(applicationResponses -> {
                                                    Map<String, Object> finalResponse = new LinkedHashMap<>();
                                                    finalResponse.put("bulkResponse", true);
                                                    finalResponse.put("parentTxnId", parentTxnId);
                                                    finalResponse.put("applications", applicationResponses);
                                                    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(finalResponse);
                                                });
                                    }
                                    return executeSingleProcess(applicationId, request, statusKey, txnId, fetch, flow, service, fromDraft, body);
                                })
                                .switchIfEmpty(Mono.defer(() -> executeSingleProcess(applicationId, request, statusKey, txnId, fetch, flow, service, fromDraft, body)));
                    }

                    return executeSingleProcess(applicationId, request, statusKey, txnId, fetch, flow, service, fromDraft, body);
                })
                .switchIfEmpty(
                        fetch(applicationId, request, statusKey, txnId, fetch, flow, service, fromDraft)
                );
    }

    private Mono<ServerResponse> executeSingleProcess(String applicationId,
                                                     ServerRequest request,
                                                     String statusKey,
                                                     String txnId,
                                                     Mono<TempTransactionLogs> fetch,
                                                     ApplicationFlowStatusEntity flow,
                                                     ServiceMeta service,
                                                     boolean fromDraft,
                                                     DocumentGenerationRequest body) {
        DocumentMode mode;
        if (DocumentMode.MERGE.name().equalsIgnoreCase(body.getMode())
                || (body.getMergedUploadSection() != null && !body.getMergedUploadSection().isEmpty())) {
            mode = DocumentMode.MERGE;
        } else if (body.getDocumentSections() != null && !body.getDocumentSections().isEmpty()) {
            mode = DocumentMode.SUBMIT;
        } else {
            mode = DocumentMode.FETCH;
        }

        return documentProcessService.process(
                applicationId,
                request,
                statusKey,
                txnId,
                fetch,
                flow,
                service,
                fromDraft,
                body,
                mode
        );
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

        String parentTxnId = request.queryParam("parentTxnId")
                .filter(id -> !id.isBlank())
                .orElse(null);

        if (parentTxnId != null) {
            return redisService.<List<BulkApplicationItem>>fetch(parentTxnId, new TypeToken<List<BulkApplicationItem>>() {}.getType())
                    .flatMap(bulkItems -> {
                        if (bulkItems != null && !bulkItems.isEmpty()) {
                            UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
                            String tenantId = user != null ? user.getTenantId() : null;

                            return Flux.fromIterable(bulkItems)
                                    .concatMap(item -> {
                                        String appId = item.getApplicationId();
                                        String childTxnId = item.getTxnId();

                                        return applicationFlowRouterRepository
                                                .findByApplicationIdAndTxnIdAndCompletedAndTaskIdAndServiceIdAndTenantId(
                                                        appId,
                                                        childTxnId,
                                                        0,
                                                        service.getTaskId(),
                                                        service.getServiceId(),
                                                        tenantId
                                                )
                                                .switchIfEmpty(Mono.error(new SPRuntimeError("Flow status not found for applicationId: " + appId, HttpStatus.BAD_REQUEST, childTxnId)))
                                                .flatMap(childFlow -> documentProcessService.process(
                                                        appId,
                                                        request,
                                                        statusKey,
                                                        childTxnId,
                                                        fetch,
                                                        childFlow,
                                                        service,
                                                        fromDraft,
                                                        null,
                                                        DocumentMode.FETCH
                                                ))
                                                .flatMap(this::captureResponseBody);
                                    })
                                    .collectList()
                                    .flatMap(applicationResponses -> {
                                        Map<String, Object> finalResponse = new LinkedHashMap<>();
                                        finalResponse.put("bulkResponse", true);
                                        finalResponse.put("parentTxnId", parentTxnId);
                                        finalResponse.put("applications", applicationResponses);
                                        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(finalResponse);
                                    });
                        }
                        return documentProcessService.process(applicationId, request, statusKey, txnId, fetch, flow, service, fromDraft, null, DocumentMode.FETCH);
                    })
                    .switchIfEmpty(Mono.defer(() -> documentProcessService.process(applicationId, request, statusKey, txnId, fetch, flow, service, fromDraft, null, DocumentMode.FETCH)));
        }

        return documentProcessService.process(applicationId, request, statusKey, txnId, fetch, flow, service, fromDraft, null, DocumentMode.FETCH);
    }

    private Mono<Map<String, Object>> captureResponseBody(ServerResponse serverResponse) {
        if (serverResponse instanceof EntityResponse<?> entityResponse) {
            Object entity = entityResponse.entity();
            if (entity == null) {
                return Mono.empty();
            }
            try {
                String json = objectMapper.writeValueAsString(entity);
                Map<String, Object> response = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
                });
                return Mono.just(response);
            } catch (JsonProcessingException e) {
                return Mono.error(e);
            }
        }
        return Mono.error(new IllegalStateException("Workflow response does not contain an entity body"));
    }
}
