package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.*;
import com.serviceplus.form.validation.flow.EventDecider;
import com.serviceplus.form.validation.repository.*;
import com.serviceplus.payment.adapter.api.RedirectResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import reactor.core.publisher.Mono;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.serviceplus.form.validation.utility.ApplicationConstants.*;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static java.util.Objects.isNull;

@Service
public class EsignProcessingService {

    private static final Logger logger = LogManager.getLogger("applicationFlowLogger");

    private final ApplicationDocumentLogRepository documentLogRepository;
    private final ApplicationDocumentSubmissionRepository documentSubmissionRepository;
    private final ApplicationFlowRouterRepository flowRouterRepository;
    private final ProcessingTxnRepository processingTxnRepository;
    private final ApplicationDetailsRepository applicationDetailsRepository;
    private final ApplicationDataRepository applicationDataRepository;
    private final RedisService redisService;
    private final EventDecider eventDecider;
    private final TransactionalDBExecutor transactionalDBExecutor;
    private final ReactiveApiClient reactiveApiClient;

    public EsignProcessingService(
            ApplicationDocumentLogRepository documentLogRepository,
            ApplicationDocumentSubmissionRepository documentSubmissionRepository,
            ApplicationFlowRouterRepository flowRouterRepository,
            ProcessingTxnRepository processingTxnRepository,
            ApplicationDetailsRepository applicationDetailsRepository,
            ApplicationDataRepository applicationDataRepository,
            RedisService redisService,
            EventDecider eventDecider,
            TransactionalDBExecutor transactionalDBExecutor,
            ReactiveApiClient reactiveApiClient) {
        this.documentLogRepository = documentLogRepository;
        this.documentSubmissionRepository = documentSubmissionRepository;
        this.flowRouterRepository = flowRouterRepository;
        this.processingTxnRepository = processingTxnRepository;
        this.applicationDetailsRepository = applicationDetailsRepository;
        this.applicationDataRepository = applicationDataRepository;
        this.redisService = redisService;
        this.eventDecider = eventDecider;
        this.transactionalDBExecutor = transactionalDBExecutor;
        this.reactiveApiClient = reactiveApiClient;
    }

    /**
     * Fetch eSign activity state and documents.
     * Follows Payment fetchPayment pattern:
     * - Checks if eSign is already complete (document with status 'S' exists).
     *   If complete -> proceeds to next activity via proceedToNextActivity.
     * - If not complete -> returns available documents to sign (or auto-generates application form if empty).
     */
    public Mono<ServerResponse> fetchEsign(
            String applicationId,
            String taskId,
            ServerRequest request,
            String txnId,
            ApplicationFlowStatusEntity flow,
            ServiceMeta service,
            boolean fromDraft) {

        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
        String tenantId = (user != null && user.getTenantId() != null) ? user.getTenantId() : "default";

        logger.info("Fetching eSign activity documents. applicationId={}, taskId={}, txnId={}",
                applicationId, taskId, txnId);

        return documentSubmissionRepository
                .findByApplicationIdAndTaskIdAndStatusOrderByCreatedOnDesc(applicationId, taskId, "S")
                .next()
                .flatMap(signedDoc -> {
                    logger.info("eSign already completed (in submission table). applicationId={}, taskId={}. Proceeding to next activity.",
                            applicationId, taskId);
                    return proceedToNextActivity(applicationId, flow, request, txnId, user, service, fromDraft);
                })
                .switchIfEmpty(
                        documentLogRepository
                                .findByApplicationIdAndTaskIdAndServiceIdAndTenantId(
                                        applicationId, taskId, flow.getServiceId(), tenantId)
                                .collectList()
                                .flatMap(documents -> {
                                    boolean alreadySigned = documents.stream()
                                            .anyMatch(doc -> "S".equalsIgnoreCase(doc.getStatus()));

                                    if (alreadySigned) {
                                        logger.info("eSign already completed (in log table). applicationId={}, taskId={}. Proceeding to next activity.",
                                                applicationId, taskId);
                                        return proceedToNextActivity(applicationId, flow, request, txnId, user, service, fromDraft);
                                    }

                                    if (documents.isEmpty()) {
                                        return autoGenerateApplicationFormDocument(user, flow, service, txnId, tenantId)
                                                .map(List::of)
                                                .flatMap(generatedDocs -> buildFetchHandlerResponse(applicationId, flow, generatedDocs));
                                    }

                                    return buildFetchHandlerResponse(applicationId, flow, documents);
                                })
                );
    }

    private Mono<ServerResponse> buildFetchHandlerResponse(
            String applicationId,
            ApplicationFlowStatusEntity flow,
            List<ApplicationDocumentLogEntity> documents) {

        Map<String, Object> esignData = new LinkedHashMap<>();
        esignData.put("applicationId", applicationId);
        esignData.put("taskId", flow.getTaskId());
        esignData.put("serviceId", flow.getServiceId());
        esignData.put("activityType", ACTIVITY_ESIGN);
        esignData.put("documents", documents);

        HandlerResponse response = new HandlerResponse();
        response.setTxnId(flow.getTxnId());
        response.setApplicationId(applicationId);
        response.setActivityType(flow.getActivityType());
        response.setActivityEnd(Boolean.FALSE);
        response.setData(Map.of("esign", esignData));

        return ServerResponse.ok().bodyValue(response);
    }

    /**
     * 1. Initiate Flow:
     * Frontend calls /action (mode = "initiate").
     * - Generates eSign initiation URL via reactiveApiClient.initiateEsign.
     * - Saves initiation request details in DocumentLog table (ApplicationDocumentLogEntity).
     * - Returns response JSON structure matching Payment Return Initiate response structure
     *   (with RedirectResponse object containing url, method, parameters).
     */
    public Mono<ServerResponse> initiateEsign(
            String applicationId,
            ApplicationFlowStatusEntity flow,
            ServerRequest request,
            EsignInitiateRequest selection,
            String txnId,
            Mono<TempTransactionLogs> txnLog,
            ServiceMeta service,
            boolean fromDraft) {

        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
        if (user == null) {
            return Mono.error(new SPRuntimeError("User session not found", HttpStatus.UNAUTHORIZED, txnId));
        }

        String tenantId = user.getTenantId() != null ? user.getTenantId() : "default";
        String taskId = flow.getTaskId();
        String processId = isNull(flow.getCurrentProcess()) ? null : flow.getCurrentProcess().getProcessId();

        logger.info("Initiating eSign. applicationId={}, taskId={}, serviceId={}",
                applicationId, taskId, flow.getServiceId());

        return documentSubmissionRepository
                .findByApplicationIdAndTaskIdAndStatusOrderByCreatedOnDesc(applicationId, taskId, "S")
                .next()
                .flatMap(signedDoc -> {
                    logger.info("eSign already completed. Proceeding to next activity. applicationId={}, taskId={}",
                            applicationId, taskId);
                    return proceedToNextActivity(applicationId, flow, request, txnId, user, service, fromDraft);
                })
                .switchIfEmpty(
                        documentLogRepository
                                .findByApplicationIdAndTaskIdAndServiceIdAndTenantId(
                                        applicationId, taskId, flow.getServiceId(), tenantId)
                                .collectList()
                                .flatMap(documents -> {

                                    Mono<ApplicationDocumentLogEntity> targetDocMono;
                                    if (selection.getDocumentId() != null && !selection.getDocumentId().isBlank()) {
                                        targetDocMono = documentLogRepository.findByUploadId(selection.getDocumentId())
                                                .switchIfEmpty(
                                                        Mono.defer(() -> {
                                                            ApplicationDocumentLogEntity fallback = new ApplicationDocumentLogEntity();
                                                            fallback.setUploadId(selection.getDocumentId());
                                                            fallback.setReferenceId("APP_FORM");
                                                            fallback.setDocumentName("Application Form");
                                                            return Mono.just(fallback);
                                                        })
                                                );
                                    } else {
                                        Optional<ApplicationDocumentLogEntity> existingDoc = documents.stream()
                                                .filter(doc -> doc.getUploadId() != null && !doc.getUploadId().isBlank())
                                                .findFirst();

                                        if (existingDoc.isPresent()) {
                                            targetDocMono = Mono.just(existingDoc.get());
                                        } else {
                                            targetDocMono = autoGenerateApplicationFormDocument(user, flow, service, txnId, tenantId);
                                        }
                                    }

                                    return targetDocMono.flatMap(targetDoc -> {
                                        String docUploadId = targetDoc.getUploadId();
                                        String docRefId = targetDoc.getReferenceId() != null ? targetDoc.getReferenceId() : "APP_FORM";
                                        String docName = targetDoc.getDocumentName() != null ? targetDoc.getDocumentName() : "Application Form";

                                        selection.setApplicationId(applicationId);
                                        selection.setServiceId(flow.getServiceId());
                                        selection.setTaskId(taskId);
                                        selection.setDocumentId(docUploadId);

                                        // Save initiation request details in DocumentLog table
                                        ApplicationDocumentLogEntity initLog = new ApplicationDocumentLogEntity();
                                        initLog.setNewEntity(true);
                                        initLog.setId(createUniqueId());
                                        initLog.setApplicationId(applicationId);
                                        initLog.setTxnId(flow.getTxnId());
                                        initLog.setProcessId(processId);
                                        initLog.setServiceId(flow.getServiceId());
                                        initLog.setTaskId(taskId);
                                        initLog.setReferenceId(docRefId);
                                        initLog.setDocumentName(docName);
                                        initLog.setSourceType(targetDoc.getSourceType() != null ? targetDoc.getSourceType() : "APPLICATION_FORM");
                                        initLog.setUploadId(docUploadId);
                                        initLog.setStatus("INITIATED");
                                        initLog.setCreatedBy(user.getUserID() != null ? user.getUserID().intValue() : null);
                                        initLog.setCreatedOn(LocalDateTime.now());
                                        initLog.setTenantId(tenantId);

                                        return documentLogRepository.save(initLog)
                                                .flatMap(savedLog -> resolveSignerNameAndInitiate(
                                                        selection, user, applicationId, tenantId, flow, service));
                                    });
                                })
                );
    }

    private Mono<ServerResponse> resolveSignerNameAndInitiate(
            EsignInitiateRequest selection,
            UserSessionObject user,
            String applicationId,
            String tenantId,
            ApplicationFlowStatusEntity flow,
            ServiceMeta service) {

        return applicationDetailsRepository
                .findByApplicationIdAndTenantId(applicationId, tenantId)
                .defaultIfEmpty(new ApplicationDetails())
                .flatMap(details -> {
                    if (details.getBeneficiaryName() != null && !details.getBeneficiaryName().isBlank()) {
                        selection.setSignerName(details.getBeneficiaryName());
                    } else if (user.getUserName() != null) {
                        selection.setSignerName(user.getUserName());
                    }
                    return executeInitiateEsign(selection, user, flow, service);
                });
    }

    private Mono<ServerResponse> executeInitiateEsign(
            EsignInitiateRequest initiateRequest,
            UserSessionObject user,
            ApplicationFlowStatusEntity flow,
            ServiceMeta service) {

        return reactiveApiClient
                .initiateEsign(initiateRequest, user)
                .flatMap(response -> {
                    String espTxnId = response.getTxnId();

                    // Construct RedirectResponse matching Payment Module
                    RedirectResponse redirect = new RedirectResponse();
                    redirect.setRequired(true);
                    redirect.setUrl(response.getGatewayUrl());
                    redirect.setMethod(response.getHttpMethod() != null ? response.getHttpMethod() : "POST");

                    Map<String, String> parameters = new LinkedHashMap<>();
                    if (response.geteSignRequest() != null) {
                        parameters.put("eSignRequest", response.geteSignRequest());
                    }
                    if (response.getAspTxnID() != null) {
                        parameters.put("aspTxnID", response.getAspTxnID());
                    }
                    redirect.setParameters(parameters);

                    response.setRedirect(redirect);
                    response.setPaymentId(espTxnId);
                    response.setGatewayTransactionId(espTxnId);
                    response.setStatus("INITIATED");

                    logger.info("eSign initiated successfully. applicationId={}, espTxnId={}, url={}",
                            initiateRequest.getApplicationId(), espTxnId, response.getGatewayUrl());

                    HandlerResponse handlerResponse = new HandlerResponse();
                    handlerResponse.setTxnId(flow.getTxnId());
                    handlerResponse.setApplicationId(initiateRequest.getApplicationId());
                    handlerResponse.setActivityType(flow.getActivityType());
                    handlerResponse.setActivityEnd(Boolean.FALSE);
                    handlerResponse.setWorkflowElementData(
                            service != null ? service.getSelectedWorkflowElementData() : null);

                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("esign", response);
                    data.put("payment", response);
                    data.put("redirect", redirect);
                    handlerResponse.setData(data);

                    return redisService.add(
                                    initiateRequest,
                                    "ESIGN_CALLBACK:" + espTxnId,
                                    Boolean.TRUE,
                                    60)
                            .then(redisService.add(
                                    initiateRequest,
                                    "ESIGN_CALLBACK:" + flow.getTxnId(),
                                    Boolean.TRUE,
                                    60))
                            .then(ServerResponse.ok().bodyValue(handlerResponse));
                });
    }

    /**
     * 2. Webhook Flow:
     * Frontend callback calls /action (mode = "webhook").
     * - Extracts eSign 2.1 response XML.
     * - Parses XML according to CCA eSign 2.1 specifications (status, errCode, errMsg, resCode, txn).
     * - If failed: logs error and returns fetchEsign so user can re-try (matching Payment failure pattern).
     * - If successful:
     *   - Calls reactiveApiClient.processEsignResponse to create and upload signed PDF.
     *   - Saves signed document details in DocumentSubmissionEntity (ApplicationDocumentSubmissionEntity).
     *   - Also updates/saves DocumentLog (ApplicationDocumentLogEntity with status 'S').
     *   - Invokes EventDecider to continue workflow exactly as implemented in Payment Handler.
     */
    public Mono<ServerResponse> handleEsignWebhook(
            String applicationId,
            ApplicationFlowStatusEntity flow,
            ServerRequest request,
            EsignInitiateRequest selection,
            String txnId,
            Mono<TempTransactionLogs> txnLog,
            ServiceMeta service,
            boolean fromDraft) {

        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
        String tenantId = (user != null && user.getTenantId() != null) ? user.getTenantId() : "default";
        String taskId = flow.getTaskId();
        String processId = isNull(flow.getCurrentProcess()) ? null : flow.getCurrentProcess().getProcessId();

        logger.info("Processing eSign webhook. applicationId={}, taskId={}, txnId={}",
                applicationId, taskId, txnId);

        String xml = extractResponseXml(selection);

        if (xml == null || xml.isBlank()) {
            logger.error("eSign response XML is missing from webhook request. applicationId={}", applicationId);
            return Mono.error(new SPRuntimeError("eSign response XML is missing", HttpStatus.BAD_REQUEST, flow.getTxnId()));
        }

        Map<String, String> parsed = parseEsignResponseXml(xml);
        String status = parsed.getOrDefault("status", "");
        String errCode = parsed.getOrDefault("errCode", "");
        String errMsg = parsed.getOrDefault("errMsg", "");
        String resCode = parsed.getOrDefault("resCode", "");
        String espTxnId = parsed.getOrDefault("txn", selection.getEspTxnID() != null ? selection.getEspTxnID() : txnId);

        logger.info("eSign webhook parsed: status={}, resCode={}, errCode={}, errMsg={}, txn={}",
                status, resCode, errCode, errMsg, espTxnId);

        // Verification of eSign failure according to eSign 2.1 specifications
        if ("0".equals(status) || (!"NA".equalsIgnoreCase(errCode) && !errCode.isBlank() && !"0".equals(errCode))) {
            logger.warn("eSign failed at provider. applicationId={}, errCode={}, errMsg={}",
                    applicationId, errCode, errMsg);
            // Revert and return fetch response so user can re-attempt, matching Payment module
            return fetchEsign(applicationId, taskId, request, txnId, flow, service, fromDraft)
                    .flatMap(fetchResponse -> ServerResponse.ok().bodyValue(fetchResponse));
        }

        // eSign was successful at ESP (status = "1")
        EsignCompleteRequest completeRequest = new EsignCompleteRequest();
        completeRequest.setApplicationId(applicationId);
        completeRequest.setTaskId(taskId);
        completeRequest.setServiceId(flow.getServiceId());
        completeRequest.seteSignResponse(xml);
        completeRequest.setEspTxnID(espTxnId);
        completeRequest.setTxnId(espTxnId);

        logger.info("Calling Integrated Plugins to attach signature to document. applicationId={}, espTxnId={}",
                applicationId, espTxnId);

        return reactiveApiClient
                .processEsignResponse(completeRequest, user)
                .flatMap(result -> {
                    if (result == null || !result.isSuccess()) {
                        String error = (result != null && result.getErrorMessage() != null)
                                ? result.getErrorMessage() : "Failed to attach eSign signature";
                        logger.error("eSign signature attachment failed. applicationId={}, error={}", applicationId, error);
                        return fetchEsign(applicationId, taskId, request, txnId, flow, service, fromDraft);
                    }

                    String signedUploadId = result.getSignedUploadId();
                    String origUploadId = result.getOriginalUploadId();
                    String refId = result.getReferenceId() != null ? result.getReferenceId() : "APP_FORM";
                    String docName = result.getDocumentName() != null ? result.getDocumentName() : "Application Form (Signed)";

                    logger.info("Signature embedded successfully. signedUploadId={}, applicationId={}",
                            signedUploadId, applicationId);

                    // 1. Save in DocumentSubmissionEntity
                    ApplicationDocumentSubmissionEntity submissionEntity = new ApplicationDocumentSubmissionEntity();
                    submissionEntity.setNew(true);
                    submissionEntity.setId(createUniqueId());
                    submissionEntity.setApplicationId(applicationId);
                    submissionEntity.setTxnId(flow.getTxnId());
                    submissionEntity.setProcessId(processId);
                    submissionEntity.setServiceId(flow.getServiceId());
                    submissionEntity.setTaskId(taskId);
                    submissionEntity.setReferenceId(refId);
                    submissionEntity.setDocumentName(docName);
                    submissionEntity.setUploadId(origUploadId);
                    submissionEntity.setSignedUploadId(signedUploadId);
                    submissionEntity.setStatus("S");
                    submissionEntity.setMerged(false);
                    submissionEntity.setCreatedBy(user != null && user.getUserID() != null ? user.getUserID() : 0L);
                    submissionEntity.setCreatedOn(LocalDateTime.now());
                    submissionEntity.setTenantId(tenantId);

                    // 2. Save in DocumentLogEntity
                    ApplicationDocumentLogEntity signedDocLog = new ApplicationDocumentLogEntity();
                    signedDocLog.setNewEntity(true);
                    signedDocLog.setId(createUniqueId());
                    signedDocLog.setApplicationId(applicationId);
                    signedDocLog.setTxnId(flow.getTxnId());
                    signedDocLog.setProcessId(processId);
                    signedDocLog.setServiceId(flow.getServiceId());
                    signedDocLog.setTaskId(taskId);
                    signedDocLog.setReferenceId(refId);
                    signedDocLog.setDocumentName(docName);
                    signedDocLog.setSourceType("APPLICATION_FORM");
                    signedDocLog.setUploadId(signedUploadId);
                    signedDocLog.setStatus("S");
                    signedDocLog.setCreatedBy(user != null && user.getUserID() != null ? user.getUserID().intValue() : null);
                    signedDocLog.setCreatedOn(LocalDateTime.now());
                    signedDocLog.setTenantId(tenantId);

                    return documentSubmissionRepository
                            .save(submissionEntity)
                            .then(documentLogRepository.save(signedDocLog))
                            .then(proceedToNextActivity(applicationId, flow, request, txnId, user, service, fromDraft));
                });
    }

    /**
     * Advances workflow to next activity using EventDecider.
     * Matches PaymentProcessingService.proceedToNextActivity exactly.
     */
    private Mono<ServerResponse> proceedToNextActivity(
            String applicationId,
            ApplicationFlowStatusEntity flow,
            ServerRequest request,
            String txnId,
            UserSessionObject user,
            ServiceMeta service,
            boolean fromDraft) {

        return flowRouterRepository
                .findFirstByApplicationIdAndTaskIdAndActivityTypeInAndCompletedOrderByIdDesc(
                        flow.getApplicationId(),
                        flow.getTaskId(),
                        List.of(
                                ACTIVITY_FORM_STATUS_KEY,
                                ACTIVITY_FORM_BULK_STATUS_KEY),
                        1)
                .switchIfEmpty(
                        Mono.error(
                                new SPRuntimeError("Completed Form Submission activity not found.", HttpStatus.BAD_REQUEST, flow.getTxnId())
                        )
                )
                .flatMap(fsFlow ->
                        processingTxnRepository
                                .findById(flow.getTxnId())
                                .switchIfEmpty(
                                        Mono.defer(() -> {
                                            ProcessingTxn newTxn = new ProcessingTxn();
                                            newTxn.setTxnId(flow.getTxnId());
                                            newTxn.setNewEntity(true);
                                            newTxn.setStartTime(LocalDateTime.now());
                                            return processingTxnRepository.save(newTxn);
                                        })
                                )
                                .flatMap(txnLog -> {
                                    logger.info("eSign successful. Proceeding to next workflow activity. applicationId={}, txnId={}, dataId={}",
                                            applicationId, flow.getTxnId(), fsFlow.getDataId());

                                    ServiceMeta effectiveService = (service != null) ? service : new ServiceMeta();
                                    if (effectiveService.getServiceId() == null) {
                                        effectiveService.setServiceId(flow.getServiceId());
                                    }
                                    if (effectiveService.getTaskId() == null) {
                                        effectiveService.setTaskId(flow.getTaskId());
                                    }

                                    return eventDecider.proceedToNext(
                                            fsFlow.getDataId(),
                                            effectiveService,
                                            user,
                                            txnLog,
                                            applicationId,
                                            "",
                                            flow.getActivityType(),
                                            request,
                                            flow);
                                }));
    }

    private String extractResponseXml(EsignInitiateRequest selection) {
        if (selection == null) {
            return null;
        }
        String xml = null;
        if (selection.geteSignResponse() != null && !selection.geteSignResponse().isBlank()) {
            xml = selection.geteSignResponse();
        } else if (selection.getRawBody() != null && !selection.getRawBody().isBlank()) {
            xml = selection.getRawBody();
        } else if (selection.getQueryParams() != null && selection.getQueryParams().containsKey("eSignResponse")) {
            xml = String.valueOf(selection.getQueryParams().get("eSignResponse"));
        } else if (selection.getFormData() != null && selection.getFormData().containsKey("eSignResponse")) {
            xml = String.valueOf(selection.getFormData().get("eSignResponse"));
        } else if (selection.getQueryParams() != null && selection.getQueryParams().containsKey("msg")) {
            xml = String.valueOf(selection.getQueryParams().get("msg"));
        }

        if (xml != null && xml.startsWith("eSignResponse=")) {
            xml = URLDecoder.decode(xml.substring("eSignResponse=".length()), StandardCharsets.UTF_8);
        } else if (xml != null && (xml.contains("%3CEsignResp") || xml.contains("%3cesignresp"))) {
            xml = URLDecoder.decode(xml, StandardCharsets.UTF_8);
        }
        return xml;
    }

    private Map<String, String> parseEsignResponseXml(String xml) {
        Map<String, String> result = new LinkedHashMap<>();
        if (xml == null || xml.isBlank()) {
            return result;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setNamespaceAware(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml.trim())));
            Element root = doc.getDocumentElement();

            if (root != null) {
                result.put("status", root.getAttribute("status"));
                result.put("ts", root.getAttribute("ts"));
                result.put("txn", root.getAttribute("txn"));
                result.put("resCode", root.getAttribute("resCode"));
                result.put("errCode", root.getAttribute("errCode"));
                result.put("errMsg", root.getAttribute("errMsg"));

                NodeList userCertList = root.getElementsByTagName("UserX509Certificate");
                if (userCertList.getLength() > 0) {
                    result.put("userX509Certificate", userCertList.item(0).getTextContent());
                }

                NodeList docSigList = root.getElementsByTagName("DocSignature");
                if (docSigList.getLength() > 0) {
                    Element docSig = (Element) docSigList.item(0);
                    result.put("docSignature", docSig.getTextContent());
                    result.put("docSigId", docSig.getAttribute("id"));
                    result.put("sigHashAlgorithm", docSig.getAttribute("sigHashAlgorithm"));
                    result.put("docSigError", docSig.getAttribute("error"));
                }
            }
        } catch (Exception e) {
            logger.error("XML parse error on eSign response: {}. Falling back to regex extraction.", e.getMessage());
            extractAttributeRegex(xml, "status", result);
            extractAttributeRegex(xml, "txn", result);
            extractAttributeRegex(xml, "resCode", result);
            extractAttributeRegex(xml, "errCode", result);
            extractAttributeRegex(xml, "errMsg", result);
        }
        return result;
    }

    private void extractAttributeRegex(String xml, String attr, Map<String, String> map) {
        Pattern p = Pattern.compile(attr + "=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(xml);
        if (m.find()) {
            map.put(attr, m.group(1));
        }
    }

    private Mono<ApplicationDocumentLogEntity> autoGenerateApplicationFormDocument(
            UserSessionObject user,
            ApplicationFlowStatusEntity flow,
            ServiceMeta service,
            String txnId,
            String tenantId) {

        logger.info("Auto-generating Application Form document for eSign. applicationId: {}, serviceId: {}",
                flow.getApplicationId(), flow.getServiceId());

        return applicationDetailsRepository
                .findByApplicationIdAndTenantId(flow.getApplicationId(), tenantId)
                .defaultIfEmpty(new ApplicationDetails())
                .flatMap(details -> {
                    Map<String, Object> systemAttrMap = new HashMap<>();
                    if (details.getReferenceNo() != null) {
                        systemAttrMap.put("referenceNo", details.getReferenceNo());
                        systemAttrMap.put("Sys_01", details.getReferenceNo());
                    }
                    if (details.getApplyDate() != null) {
                        systemAttrMap.put("appliedDate", details.getApplyDate().toString());
                        systemAttrMap.put("Sys_02", details.getApplyDate().toString());
                    }
                    if (details.getBeneficiaryName() != null) {
                        systemAttrMap.put("applicantName", details.getBeneficiaryName());
                        systemAttrMap.put("Sys_03", details.getBeneficiaryName());
                    }
                    if (details.getServiceName() != null) {
                        systemAttrMap.put("serviceName", details.getServiceName());
                    } else if (service != null && service.getServiceName() != null) {
                        systemAttrMap.put("serviceName", service.getServiceName());
                    }

                    return reactiveApiClient
                            .generateApplicationFormDocument(
                                    user,
                                    flow.getServiceId(),
                                    flow.getApplicationId(),
                                    txnId,
                                    systemAttrMap,
                                    null)
                            .flatMap(res -> {
                                String processId = (flow.getCurrentProcess() != null)
                                        ? flow.getCurrentProcess().getProcessId() : null;

                                ApplicationDocumentLogEntity entity = new ApplicationDocumentLogEntity();
                                entity.setNewEntity(Boolean.TRUE);
                                entity.setId(createUniqueId());
                                entity.setApplicationId(flow.getApplicationId());
                                entity.setTxnId(txnId);
                                entity.setServiceId(flow.getServiceId());
                                entity.setTaskId(flow.getTaskId());
                                entity.setProcessId(processId);
                                entity.setReferenceId("APP_FORM");
                                entity.setDocumentName("Application Form");
                                entity.setSourceType("APPLICATION_FORM");
                                entity.setUploadId(res.getUploadId());
                                entity.setPreviewUrl(res.getPreviewUrl());
                                entity.setStatus("P");
                                entity.setCreatedOn(LocalDateTime.now());
                                entity.setTenantId(tenantId);

                                return documentLogRepository.save(entity)
                                        .map(saved -> {
                                            saved.setPreviewUrl(res.getPreviewUrl());
                                            return saved;
                                        });
                            });
                });
    }
}
