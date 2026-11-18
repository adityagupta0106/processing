package com.serviceplus.form.validation.service;

import com.google.gson.reflect.TypeToken;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.ApplicationPayment;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.enums.PaymentRevalidationMode;
import com.serviceplus.form.validation.flow.EventDecider;
import com.serviceplus.form.validation.repository.*;
import com.serviceplus.payment.adapter.api.PaymentOperation;
import com.serviceplus.payment.adapter.api.PaymentRequest;
import com.serviceplus.payment.adapter.api.PaymentStatus;
import com.serviceplus.payment.adapter.api.PaymentWebhookRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static com.serviceplus.form.validation.utility.ApplicationConstants.*;
import static com.serviceplus.form.validation.utility.Utility.*;
import static java.util.Objects.isNull;

@Service
public class PaymentProcessingService {

    private static final Logger logger = LoggerFactory.getLogger("applicationFlowLogger");

    private final ReactiveApiClient reactiveApiClient;
    private final ApplicationPaymentRepository applicationPaymentRepository;
    private final ApplicationDetailsRepository applicationDetailsRepository;
    private final ApplicationDataRepository applicationDataRepository;
    private final RedisService redisService;
    private final EventDecider eventDecider;
    private final ApplicationFlowRouterRepository applicationFlowRouterRepository;
    private final ProcessingTxnRepository processingTxnRepository;
    private final TransactionalDBExecutor transactionalDBExecutor;

    @Value("${payment.success.callback.path}")
    private String payment_success_callback_path;

    public PaymentProcessingService(ReactiveApiClient reactiveApiClient, ApplicationPaymentRepository applicationPaymentRepository, ApplicationDetailsRepository applicationDetailsRepository, ApplicationDataRepository applicationDataRepository, RedisService redisService, EventDecider eventDecider, ApplicationFlowRouterRepository applicationFlowRouterRepository, ProcessingTxnRepository processingTxnRepository, TransactionalDBExecutor transactionalDBExecutor) {
        this.reactiveApiClient = reactiveApiClient;
        this.applicationPaymentRepository = applicationPaymentRepository;
        this.applicationDetailsRepository = applicationDetailsRepository;
        this.applicationDataRepository = applicationDataRepository;
        this.redisService = redisService;
        this.eventDecider = eventDecider;
        this.applicationFlowRouterRepository = applicationFlowRouterRepository;
        this.processingTxnRepository = processingTxnRepository;
        this.transactionalDBExecutor = transactionalDBExecutor;
    }

    public Mono<ServerResponse> fetchPaymentOptions(
            String applicationId,
            ServerRequest request,
            ApplicationFlowStatusEntity flow,
            String txnId) {

        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
        logger.info("Fetching payment options. applicationId={}, taskId={}, serviceId={}", applicationId, flow.getTaskId(), flow.getServiceId());

        return reactiveApiClient
                .fetchServiceMetadata(user, flow.getServiceId(), txnId)
                .flatMap(metadata -> {
                    PaymentConfigurationDTO paymentConfiguration = metadata.getPaymentConfiguration();

                    if (paymentConfiguration == null) {
                        logger.error("Payment configuration not found. applicationId={}, serviceId={}", applicationId, flow.getServiceId());
                        return Mono.error(new SPRuntimeError("Payment configuration not found", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                    }

                    PaymentConfigurationDTO.PaymentCharge paymentCharge = findTaskPaymentCharge(metadata, flow.getTaskId());

                    if (paymentCharge == null) {
                        logger.error("Payment is not configured for task. applicationId={}, taskId={}", applicationId, flow.getTaskId());
                        return Mono.error(new SPRuntimeError("Payment is not configured for this task", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                    }

                    List<PaymentConfigurationDTO.PaymentGatewayConfiguration> gatewayConfigurations = paymentConfiguration.getPaymentGatewayConfigurations();

                    if (gatewayConfigurations == null || gatewayConfigurations.isEmpty()) {
                        logger.error("No payment gateway configured. applicationId={}, serviceId={}", applicationId, flow.getServiceId());
                        return Mono.error(new SPRuntimeError("No payment gateway configured", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                    }

                    List<PaymentGatewayDetailsDTO> enabledGateways = gatewayConfigurations.stream()
                            .filter(configuration -> Boolean.TRUE.equals(configuration.getEnabled()))
                            .map(configuration -> toPaymentGatewayDetails(configuration, paymentCharge))
                            .toList();

                    if (enabledGateways.isEmpty()) {
                        logger.error("No active payment gateway configured. applicationId={}, serviceId={}", applicationId, flow.getServiceId());
                        return Mono.error(new SPRuntimeError("No active payment gateway configured", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                    }

                    BigDecimal amount = getPaymentAmount(paymentCharge);
                    logger.info("Payment options fetched successfully. applicationId={}, gatewayCount={}, amount={}", applicationId, enabledGateways.size(), amount);

                    PaymentOptionsResponse paymentOptionsResponse =
                            new PaymentOptionsResponse(
                                    Boolean.TRUE,
                                    "Payment options fetched successfully",
                                    paymentCharge.getChargeDetailId().intValue(),
                                    amount,
                                    List.of(enabledGateways.getFirst()));

                    HandlerResponse response = new HandlerResponse();
                    response.setTxnId(flow.getTxnId());
                    response.setApplicationId(applicationId);
                    response.setActivityType(flow.getActivityType());
                    response.setActivityEnd(Boolean.FALSE);
                    response.setData(Map.of("payment", paymentOptionsResponse));

                    return ServerResponse.ok().bodyValue(response);
                });
    }

    private BigDecimal getPaymentAmount(PaymentConfigurationDTO.PaymentCharge paymentCharge) {
        if (paymentCharge.getChargeTypeList() == null || paymentCharge.getChargeTypeList().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return paymentCharge.getChargeTypeList()
                .stream()
                .filter(Objects::nonNull)
                .map(PaymentConfigurationDTO.ChargeType::getAmount)
                .filter(Objects::nonNull)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PaymentGatewayDetailsDTO toPaymentGatewayDetails(
            PaymentConfigurationDTO.PaymentGatewayConfiguration configuration,
            PaymentConfigurationDTO.PaymentCharge paymentCharge) {

        PaymentGatewayDetailsDTO response = new PaymentGatewayDetailsDTO();
        PaymentGatewayDetailsDTO.PaymentGatewayDTO gateway = new PaymentGatewayDetailsDTO.PaymentGatewayDTO();

        gateway.setName(configuration.getGatewayCode());
        gateway.setId(configuration.getGatewayCode().hashCode());

        response.setGateway(List.of(gateway));
        response.setPaymentModes(paymentCharge.getPaymentModes());
        response.setCurrency(response.getCurrency());

        return response;
    }

    public Mono<ServerResponse> initiatePayment(String applicationId, ApplicationFlowStatusEntity flow, ServerRequest request,
                                   PaymentSelectionRequest selection, String txnId, Mono<TempTransactionLogs> txnLog, ServiceMeta service, boolean fromDraft) {

        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
        String tenantId = user.getTenantId();
        String taskId = flow.getTaskId();
        String processId = isNull(flow.getCurrentProcess()) ? null : flow.getCurrentProcess().getProcessId();

        logger.info("Initiating payment. applicationId={}, taskId={}, gatewayCode={}", applicationId, taskId, selection != null ? selection.getGatewayCode() : null);

        if (selection == null || selection.getGatewayCode() == null || selection.getGatewayCode().isBlank()) {
            logger.error("Payment gateway not selected. applicationId={}", applicationId);
            return Mono.error(new SPRuntimeError("Payment gateway must be selected", HttpStatus.BAD_REQUEST, flow.getTxnId()));
        }

        Mono<ApplicationPayment> existingPayment;

        if (isNull(processId)) {
            existingPayment = applicationPaymentRepository.findFirstByApplicationIdAndTaskIdAndTenantIdOrderByCrDateDesc(applicationId, taskId, tenantId);
        } else {
            existingPayment = applicationPaymentRepository.findFirstByApplicationIdAndProcessIdAndTaskIdAndTenantIdOrderByCrDateDesc(applicationId, processId, taskId, tenantId);
        }

        return existingPayment
                .flatMap(payment -> {
                    String status = payment.getStatus();

                    if (PaymentStatus.SUCCESS.name().equalsIgnoreCase(status)) {

                        logger.info("Payment already completed. applicationId={}, paymentId={}", applicationId, payment.getPaymentId());
                        return proceedToNextActivity(applicationId, flow, request, txnId, user, service, fromDraft);

                    }

                    if (PaymentStatus.INITIATED.name().equalsIgnoreCase(status) || PaymentStatus.PENDING.name().equalsIgnoreCase(status)) {
                        logger.info("Payment already in progress. applicationId={}, paymentId={}, status={}", applicationId, payment.getPaymentId(), status);

                        return Mono.error(new SPRuntimeError("Payment is already in progress", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                    }

                    logger.info("Existing payment is not active. New payment attempt will be created. applicationId={}, paymentId={}, status={}",
                            applicationId, payment.getPaymentId(), status);

                    return cancelPreviousPayment(payment)
                            .then(
                                    initiateNewPayment(
                                            applicationId,
                                            flow,
                                            request,
                                            selection,
                                            txnId,
                                            user,
                                            tenantId,
                                            processId,
                                            service));
                })
                .switchIfEmpty(
                        initiateNewPayment(
                                applicationId,
                                flow,
                                request,
                                selection,
                                txnId,
                                user,
                                tenantId,
                                processId,
                                service));
    }

    private Mono<Void> cancelPreviousPayment(ApplicationPayment payment) {
        payment.setStatus(PaymentStatus.CANCELLED.name());
        payment.setUpDate(LocalDateTime.now());

        logger.info(
                "Previous payment attempt cancelled. paymentId={}, applicationId={}",
                payment.getPaymentId(),
                payment.getApplicationId());

        return applicationPaymentRepository
                .save(payment)
                .then();
    }

    private Mono<ServerResponse> initiateNewPayment(
            String applicationId,
            ApplicationFlowStatusEntity flow,
            ServerRequest request,
            PaymentSelectionRequest selection,
            String txnId,
            UserSessionObject user,
            String tenantId,
            String processId,
            ServiceMeta service) {

        return reactiveApiClient
                .fetchServiceMetadata(user, flow.getServiceId(), txnId)
                .flatMap(metadata -> {
                    PaymentConfigurationDTO paymentConfiguration = metadata.getPaymentConfiguration();

                    if (paymentConfiguration == null) {
                        logger.error("Payment configuration not found. applicationId ={}, serviceId={}", applicationId, flow.getServiceId());
                        return Mono.error(new SPRuntimeError("Payment configuration not found", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                    }

                    PaymentConfigurationDTO.PaymentCharge paymentCharge =
                            findTaskPaymentCharge(metadata, flow.getTaskId());

                    if (paymentCharge == null) {
                        logger.error("Payment is not configured for task. applicationId ={}, taskId={}", applicationId, flow.getTaskId());
                        return Mono.error(new SPRuntimeError("Payment is not configured for this task", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                    }

                    PaymentConfigurationDTO.PaymentGatewayConfiguration gatewayConfiguration =
                            findSelectedGateway(
                                    paymentConfiguration,
                                    selection.getGatewayCode());

                    if (gatewayConfiguration == null) {

                        logger.error("Selected payment gateway is not configured. applicationId={}, gatewayCode={}", applicationId, selection.getGatewayCode());
                        return Mono.error(new SPRuntimeError("Selected payment gateway is not configured", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                    }

                    if (!Boolean.TRUE.equals(gatewayConfiguration.getEnabled())) {
                        logger.error("Selected payment gateway is disabled. applicationId={}, gatewayCode={}", applicationId, selection.getGatewayCode());
                        return Mono.error(new SPRuntimeError("Selected payment gateway is disabled", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                    }

                    return applicationDetailsRepository
                            .findByApplicationIdAndTenantId(
                                    applicationId,
                                    tenantId)
                            .switchIfEmpty(
                                    Mono.error(new SPRuntimeError("Application details not found", HttpStatus.BAD_REQUEST, flow.getTxnId()))
                            )
                            .flatMap(applicationDetails ->
                                    resolveGatewayConfiguration(applicationId, gatewayConfiguration, selection.getFormData(), user, txnId)
                            )
                            .flatMap(resolvedConfiguration -> {

                                BigDecimal amount = getPaymentAmount(paymentCharge);

                                PaymentRequest paymentRequest = new PaymentRequest();
                                paymentRequest.setTransactionId(generatePaymentId());
                                paymentRequest.setGatewayCode(selection.getGatewayCode());
                                paymentRequest.setEnvironment(getSetupEnvironment(request.exchange().getRequest()));
                                paymentRequest.setServiceId(String.valueOf(flow.getServiceId()));
                                paymentRequest.setApplicationId(applicationId);
                                paymentRequest.setGatewayConfiguration(resolvedConfiguration);
                                paymentRequest.setSuccessUrl(getCallBackUrl(getHeaderValue(HOST_HEADER,request.exchange().getRequest()),payment_success_callback_path));
                                paymentRequest.setAmount(amount);

                                logger.info("Calling Payment Service. applicationId={}, gatewayCode={}, amount={}", applicationId, selection.getGatewayCode(), paymentRequest.getAmount());

                                return reactiveApiClient
                                        .initiatePayment(paymentRequest, user, txnId)
                                        .flatMap(response ->
                                                savePayment(
                                                        tenantId,
                                                        applicationId,
                                                        flow,
                                                        processId,
                                                        response,
                                                        paymentRequest,
                                                        gatewayConfiguration)
                                                        .flatMap(savedPayment -> {
                                                            logger.info(
                                                                    "Payment initiated. applicationId={}, paymentId={}, status={}",
                                                                    applicationId,
                                                                    savedPayment.getPaymentId(),
                                                                    savedPayment.getStatus());
                                                            HandlerResponse handlerResponse = new HandlerResponse();
                                                            handlerResponse.setTxnId(flow.getTxnId());
                                                            handlerResponse.setApplicationId(applicationId);
                                                            handlerResponse.setActivityType(flow.getActivityType());
                                                            handlerResponse.setActivityEnd(Boolean.FALSE);
                                                            handlerResponse.setWorkflowElementData(
                                                                    service.getSelectedWorkflowElementData());
                                                            handlerResponse.setData(Map.of("payment", response));
                                                            return redisService.add(
                                                                            paymentRequest,
                                                                            "PAYMENT_CALLBACK:" + paymentRequest.getTransactionId(),
                                                                            Boolean.TRUE,
                                                                            60)
                                                                    .then(ServerResponse.ok().bodyValue(handlerResponse));
                                                        }));
                            });
                });
    }

    public Mono<ServerResponse> handlePaymentWebhook(
            String applicationId,
            ApplicationFlowStatusEntity flow,
            ServerRequest request,
            PaymentSelectionRequest selection,
            String txnId,
            Mono<TempTransactionLogs> txnLog,
            ServiceMeta service,
            boolean fromDraft) {

        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());

        String tenantId = user.getTenantId();
        String taskId = flow.getTaskId();
        String processId = isNull(flow.getCurrentProcess()) ? null : flow.getCurrentProcess().getProcessId();

        logger.info("Processing payment webhook. applicationId={}, taskId={}", applicationId, taskId);

        Mono<ApplicationPayment> existingPayment;

        if (isNull(processId)) {
            existingPayment = applicationPaymentRepository.findFirstByApplicationIdAndTaskIdAndTenantIdOrderByCrDateDesc(applicationId, taskId, tenantId);
        } else {
            existingPayment = applicationPaymentRepository.findFirstByApplicationIdAndProcessIdAndTaskIdAndTenantIdOrderByCrDateDesc(applicationId, processId, taskId, tenantId);
        }

        return existingPayment
                .switchIfEmpty(
                        Mono.error(new SPRuntimeError("Payment not found", HttpStatus.BAD_REQUEST, flow.getTxnId()))
                )
                .flatMap(payment -> {

                    String paymentId = payment.getPaymentId();

                    if (paymentId == null || paymentId.isBlank()) {

                        logger.error("Payment ID not found for webhook. applicationId={}", applicationId);
                        return Mono.error(new SPRuntimeError("Payment ID not found", HttpStatus.BAD_REQUEST, flow.getTxnId()));

                    }

                    String redisKey = "PAYMENT_CALLBACK:" + paymentId;

                    logger.info(
                            "Fetching cached payment request. applicationId={}, paymentId={}",
                            applicationId,
                            paymentId);

                    return redisService
                            .fetch(redisKey, PaymentRequest.class)
                            .cast(PaymentRequest.class)
                            .switchIfEmpty(
                                    Mono.error(new SPRuntimeError("Payment request not found", HttpStatus.BAD_REQUEST, flow.getTxnId()))
                            )
                            .flatMap(paymentRequest -> {

                                PaymentWebhookRequest webhookRequest = new PaymentWebhookRequest();

                                webhookRequest.setTransactionId(paymentRequest.getTransactionId());
                                webhookRequest.setGatewayCode(paymentRequest.getGatewayCode());
                                webhookRequest.setEnvironment(paymentRequest.getEnvironment());
                                webhookRequest.setMerchantId(paymentRequest.getMerchantId());
                                webhookRequest.setAmount(paymentRequest.getAmount());
                                webhookRequest.setCurrency(paymentRequest.getCurrency());
                                webhookRequest.setServiceId(paymentRequest.getServiceId());
                                webhookRequest.setApplicationId(paymentRequest.getApplicationId());
                                webhookRequest.setSuccessUrl(paymentRequest.getSuccessUrl());
                                webhookRequest.setFailureUrl(paymentRequest.getFailureUrl());
                                webhookRequest.setCustomer(paymentRequest.getCustomer());
                                webhookRequest.setGatewayConfiguration(paymentRequest.getGatewayConfiguration());

                                webhookRequest.setRawBody(selection.getRawBody());
                                webhookRequest.setHeaders(selection.getHeaders());
                                webhookRequest.setQueryParameters(selection.getQueryParams());

                                logger.info("Calling Payment Service for webhook. paymentId={}, gatewayCode={}", paymentId, webhookRequest.getGatewayCode());

                                return reactiveApiClient
                                        .handlePaymentWebhook(
                                                webhookRequest,
                                                user,
                                                txnId)
                                        .flatMap(response ->
                                                handleWebhookResponse(
                                                        applicationId,
                                                        flow,
                                                        request,
                                                        payment,
                                                        response,
                                                        txnId,
                                                        user,
                                                        txnLog,
                                                        service,
                                                        fromDraft));
                            });
                });
    }


    private Mono<ServerResponse> handleWebhookResponse(String applicationId, ApplicationFlowStatusEntity flow, ServerRequest request, ApplicationPayment payment, PaymentInitiateResponse response, String txnId, UserSessionObject user, Mono<TempTransactionLogs> txnLog, ServiceMeta service, boolean fromDraft) {

        if (response == null || response.getStatus() == null) {
            logger.error("Invalid payment webhook response. applicationId={}, paymentId={}", applicationId, payment.getPaymentId());
            return Mono.error(new SPRuntimeError("Invalid payment response", HttpStatus.BAD_GATEWAY, txnId));
        }

        PaymentStatus status = response.getStatus();
        LocalDateTime now = LocalDateTime.now();
        logger.info("Payment webhook response received. applicationId={}, paymentId={}, status={}", applicationId, payment.getPaymentId(), status);
        payment.setStatus(status.name());
        payment.setGatewayTransactionId(response.getGatewayTransactionId());

        if (response.getAmount() != null) {
            payment.setAmount(response.getAmount());
        }
        if (response.getCurrency() != null) {
            payment.setCurrency(response.getCurrency());
        }
        payment.setUpDate(now);

        if (PaymentStatus.SUCCESS.equals(status)) {

            payment.setCompletedDate(now);

            return applicationPaymentRepository.save(payment)
                    .doOnSuccess(saved -> logger.info(
                            "Payment successful. applicationId={}, paymentId={}",
                            applicationId,
                            saved.getPaymentId()))
                    .then(proceedToNextActivity(
                            applicationId,
                            flow,
                            request,
                            txnId,
                            user,
                            service,
                            fromDraft));
        }
        if (PaymentStatus.PENDING.equals(status) || PaymentStatus.INITIATED.equals(status)) {

            return applicationPaymentRepository.save(payment)
                    .doOnSuccess(saved -> logger.info(
                            "Payment still pending. applicationId={}, paymentId={}, status={}",
                            applicationId,
                            saved.getPaymentId(),
                            saved.getStatus()))
                    .then(Mono.error(new SPRuntimeError(
                            "Payment is already in progress",
                            HttpStatus.BAD_REQUEST,
                            flow.getTxnId())));
        }
        if (PaymentStatus.FAILED.equals(status) || PaymentStatus.CANCELLED.equals(status)) {
            return applicationPaymentRepository.save(payment)
                    .doOnSuccess(saved -> logger.info(
                            "Payment failed. applicationId={}, paymentId={}, status={}",
                            applicationId,
                            saved.getPaymentId(),
                            saved.getStatus()))
                    .then(fetchPaymentOptions(
                            applicationId,
                            request,
                            flow,
                            txnId));
        }

        logger.error("Unhandled payment status. applicationId={}, paymentId={}, status={}", applicationId, payment.getPaymentId(), status);

        return Mono.error(new SPRuntimeError("Unsupported payment status", HttpStatus.BAD_GATEWAY, txnId));
    }


    private Mono<Map<String, Object>> resolveGatewayConfiguration(
            String applicationId,
            PaymentConfigurationDTO.PaymentGatewayConfiguration gatewayConfiguration,
            Map<String, Object> requestFormData,
            UserSessionObject user, String txnId) {

        List<PaymentConfigurationDTO.GatewayMapping> mappings = gatewayConfiguration.getMappings();

        if (mappings == null || mappings.isEmpty()) {
            logger.error("No gateway mappings configured. applicationId={}, gatewayCode={}", applicationId, gatewayConfiguration.getGatewayCode());
            return Mono.just(new HashMap<>());
        }

        return Flux.fromIterable(mappings)
                .filter(mapping -> PaymentOperation.INITIATE.name().equalsIgnoreCase(mapping.getMode()))
                .flatMap(mapping -> {
                    if ("STATIC".equalsIgnoreCase(mapping.getSourceType())) {
                        return Mono.just(Map.entry(mapping.getNodeCode(), mapping.getSourceValue()));
                    }

                    if ("DYNAMIC".equalsIgnoreCase(mapping.getSourceType())) {
                        String sourceValue = mapping.getSourceValue();

                        if (requestFormData != null && requestFormData.containsKey(sourceValue)) {
                            Object value = requestFormData.get(sourceValue);

                            if (value == null) {
                                logger.error("Payment field is null. applicationId={}, field={}", applicationId, sourceValue);
                                return Mono.error(new SPRuntimeError("Required payment field is null: " + sourceValue, HttpStatus.BAD_REQUEST, txnId));
                            }

                            return Mono.just(Map.entry(mapping.getNodeCode(), value));
                        }

                        return resolveApplicationAttribute(applicationId, sourceValue, user)
                                .switchIfEmpty(Mono.error(new SPRuntimeError("Required payment field not found: " + sourceValue, HttpStatus.BAD_REQUEST, txnId)))
                                .map(value -> Map.entry(mapping.getNodeCode(), value));
                    }

                    logger.error("Unsupported payment mapping source type. applicationId={}, sourceType={}", applicationId, mapping.getSourceType());
                    return Mono.error(new SPRuntimeError(
                            "Unsupported payment mapping source type: " + mapping.getSourceType(),
                            HttpStatus.BAD_REQUEST,
                            txnId));
                })
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private Mono<Object> resolveApplicationAttribute(
            String applicationId,
            String attributeKey,
            UserSessionObject user) {

        if (applicationId == null || attributeKey == null || attributeKey.isBlank()) {
            return Mono.empty();
        }

        logger.info(
                "Resolving application attribute. applicationId={}, attributeKey={}",
                applicationId,
                attributeKey);

        return applicationDataRepository
                .findByApplicationIdAndTenantId(
                        applicationId,
                        user.getTenantId())
                .flatMap(applicationData -> {
                    applicationData.convertAttrValToObject();
                    return Mono.justOrEmpty(
                            extractCurrentAttributeValue(
                                    applicationData.getAttrValObject(),
                                    attributeKey));
                })
                .doOnNext(value ->
                        logger.info(
                                "Application attribute resolved. applicationId={}, attributeKey={}",
                                applicationId,
                                attributeKey))
                .doOnError(error ->
                        logger.error(
                                "Failed to resolve application attribute. applicationId={}, attributeKey={}",
                                applicationId,
                                attributeKey,
                                error));
    }

    private Object extractCurrentAttributeValue(FormData formData, String attributeKey) {
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

        return attribute.getVersions().get("v" + current);
    }

    private PaymentConfigurationDTO.PaymentCharge findTaskPaymentCharge(ServiceJSONDTO metadata, String taskId) {
        logger.info("Finding payment charge for taskId={}", taskId);
        if (metadata == null || metadata.getProcessFlowMap() == null || metadata.getProcessFlowMap().getData() == null) {
            logger.info("Payment process flow metadata not available. taskId={}", taskId);
            return null;
        }

        for (ServiceProcessFlowDTO.Data flowNode : metadata.getProcessFlowMap().getData()) {
            if (flowNode.getNode() == null) {
                continue;
            }

            String nodeId = flowNode.getNode().getId();

            if (!taskId.equals(nodeId)) {
                continue;
            }

            logger.info("Payment task node found. taskId={}", taskId);

            TaskPaymentMappingDTO payment = flowNode.getNode().getPayment();

            if (payment == null || !Boolean.TRUE.equals(payment.getEnabled())) {
                logger.info("Payment is not enabled for task. taskId={}", taskId);
                return null;
            }

            Long chargeId = payment.getChargeId();

            if (chargeId == null || metadata.getPaymentConfiguration() == null || metadata.getPaymentConfiguration().getPaymentCharges() == null) {
                logger.error("Payment charge configuration not found. taskId={}, chargeId={}", taskId, chargeId);
                return null;
            }

            for (PaymentConfigurationDTO.PaymentCharge charge : metadata.getPaymentConfiguration().getPaymentCharges()) {
                if (charge.getChargeDetailId() != null && chargeId.equals(charge.getChargeDetailId())) {
                    logger.info("Payment charge found. taskId={}, chargeDetailId={}", taskId, chargeId);
                    return charge;
                }
            }

            logger.error("Payment charge not found for chargeDetailId. taskId={}, chargeDetailId={}", taskId, chargeId);
            return null;
        }

        logger.info("Payment configuration not found for task. taskId={}", taskId);
        return null;
    }

    private PaymentConfigurationDTO.PaymentGatewayConfiguration findSelectedGateway(
            PaymentConfigurationDTO paymentConfiguration,
            String gatewayCode) {

        if (paymentConfiguration == null || paymentConfiguration.getPaymentGatewayConfigurations() == null || gatewayCode == null) {
            return null;
        }

        return paymentConfiguration.getPaymentGatewayConfigurations()
                .stream()
                .filter(configuration -> gatewayCode.equalsIgnoreCase(configuration.getGatewayCode()))
                .findFirst()
                .orElse(null);
    }

    private Mono<ApplicationPayment> savePayment(
            String tenantId,
            String applicationId,
            ApplicationFlowStatusEntity flow,
            String processId,
            PaymentInitiateResponse response,
            PaymentRequest request,
            PaymentConfigurationDTO.PaymentGatewayConfiguration gatewayConfiguration) {
        ApplicationPayment payment = new ApplicationPayment();
        payment.setPaymentId(request.getTransactionId());
        payment.setGatewayTransactionId(response.getGatewayTransactionId());
        payment.setTenantId(tenantId);
        payment.setApplicationId(applicationId);
        payment.setServiceId(flow.getServiceId());
        payment.setProcessId(processId);
        payment.setTaskId(flow.getTaskId());
        payment.setStatus(response.getStatus() != null ? response.getStatus().name() : null);
        payment.setAmount(response.getAmount());
        payment.setCurrency(response.getCurrency());
        payment.setGatewayCode(request.getGatewayCode());
        payment.setEnvironment(request.getEnvironment());
        payment.setNewEntity(true);
        PaymentConfigurationDTO.AdditionalData additionalData =
                gatewayConfiguration != null ? gatewayConfiguration.getAdditionalData() : null;
        if (additionalData != null && Boolean.TRUE.equals(additionalData.getEnableRevalidation())) {
            Map<String, Object> revalidationConfiguration = new HashMap<>();
            revalidationConfiguration.put("enabled", true);
            revalidationConfiguration.put("refundAllowed", additionalData.getRefundAllowed());
            revalidationConfiguration.put("modes", additionalData.getRevalidationModes());
            revalidationConfiguration.put("periods", additionalData.getRevalidationPeriods());
            payment.setRevalidationConfiguration(entityToString(revalidationConfiguration));
        } else {
            payment.setRevalidationConfiguration(null);
        }
        LocalDateTime now = LocalDateTime.now();
        payment.setCrDate(now);
        payment.setUpDate(now);
        return applicationPaymentRepository.save(payment)
                .doOnSuccess(saved -> logger.info(
                        "Payment record saved. applicationId={}, paymentId={},txnId ={}, status={}, gatewayCode={}",
                        applicationId,
                        saved.getPaymentId(),
                        request.getTransactionId(),
                        saved.getStatus(),
                        saved.getGatewayCode()));
    }

    public Mono<?> fetchPayment(String applicationId, String taskId, ServerRequest request, String txnId, ApplicationFlowStatusEntity flow,
                                ServiceMeta service, boolean fromDraft) {

        UserSessionObject userSessionDetails = getUserSessionDetails(request.exchange().getRequest());
        String tenantId = userSessionDetails.getTenantId();
        String processId = isNull(flow.getCurrentProcess()) ? null : flow.getCurrentProcess().getProcessId();

        Mono<ApplicationPayment> payment;

        if (isNull(processId)) {
            payment = applicationPaymentRepository.findFirstByApplicationIdAndTaskIdAndTenantIdOrderByCrDateDesc(applicationId, taskId, tenantId);
        } else {
            payment = applicationPaymentRepository.findFirstByApplicationIdAndProcessIdAndTaskIdAndTenantIdOrderByCrDateDesc(applicationId, processId, taskId, tenantId);
        }

        return payment
                .flatMap(applicationPayment -> {
                    String status = applicationPayment.getStatus();

                    logger.info("Payment found. applicationId={}, paymentId={}, status={}", applicationId, applicationPayment.getPaymentId(), status);

                    if (PaymentStatus.SUCCESS.name().equalsIgnoreCase(status)) {

                        logger.info("Payment already completed. applicationId= {}, paymentId={}", applicationId, applicationPayment.getPaymentId());
                        return proceedToNextActivity(applicationId, flow, request, txnId, userSessionDetails, service, fromDraft);
                    }

                    if (PaymentStatus.INITIATED.name().equalsIgnoreCase(status) || PaymentStatus.PENDING.name().equalsIgnoreCase(status)) {

                        logger.info("Payment requires status verification. applicationId={}, paymentId={}, status={}", applicationId, applicationPayment.getPaymentId(), status);
                        return Mono.error(new SPRuntimeError("Payment is already in progress", HttpStatus.BAD_REQUEST, flow.getTxnId()));

                    }

                    return fetchPaymentOptions(applicationId, request, flow, txnId);
                })
                .switchIfEmpty(
                        Mono.defer(() -> {
                            logger.info("No payment found. applicationId={}, taskId={}", applicationId, taskId);

                            return fetchPaymentOptions(applicationId, request, flow, txnId);
                        }));
    }

    private String getStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal getBigDecimalValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }

        return new BigDecimal(String.valueOf(value));
    }

    private Mono<ServerResponse> proceedToNextActivity(
            String applicationId,
            ApplicationFlowStatusEntity flow,
            ServerRequest request,
            String txnId,
            UserSessionObject user,
            ServiceMeta service,
            boolean fromDraft) {

        return applicationFlowRouterRepository
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
                                        Mono.error(new SPRuntimeError("Processing transaction not found.", HttpStatus.INTERNAL_SERVER_ERROR, flow.getTxnId()))
                                )
                                .flatMap(txnLog -> {

                                    logger.info("Payment successful. Proceeding to next workflow activity. applicationId={}, paymentId={}, dataId={}", applicationId, flow.getTxnId(), fsFlow.getDataId());

                                    return eventDecider.proceedToNext(
                                            fsFlow.getDataId(),
                                            service,
                                            user,
                                            txnLog,
                                            applicationId,
                                            "",
                                            flow.getActivityType(),
                                            request,
                                            flow);
                                }));
    }


    public Mono<PaymentRevalidationResponse> revalidatePayment(ApplicationPayment payment, String originalStatus) {
        if (payment == null || payment.getPaymentId() == null || payment.getPaymentId().isBlank()) {
            logger.error("Payment revalidation skipped because paymentId is missing");
            return Mono.just(
                    new PaymentRevalidationResponse(
                            false,
                            "Payment ID is required",
                            null));
        }

        String paymentId = payment.getPaymentId();
        String redisKey = "PAYMENT_CALLBACK:" + paymentId;
        String txnId = payment.getPaymentId();

        logger.info(
                "Starting payment revalidation. paymentId={}, gatewayCode={}, status={}",
                paymentId,
                payment.getGatewayCode(),
                originalStatus);

        return redisService
                .fetch(redisKey, PaymentRequest.class)
                .cast(PaymentRequest.class)
                .flatMap(paymentRequest -> {
                    logger.info(
                            "Payment request found in cache. paymentId={}",
                            paymentId);
                    return queryPayment(
                            payment,
                            paymentRequest,
                            txnId);
                })
                .switchIfEmpty(
                        Mono.defer(() -> {
                            logger.info(
                                    "Payment request not found in cache. Rebuilding payment request. paymentId={}",
                                    paymentId);

                            return rebuildPaymentRequest(
                                    payment,
                                    txnId)
                                    .flatMap(paymentRequest ->
                                            queryPayment(
                                                    payment,
                                                    paymentRequest,
                                                    txnId));
                        }))
                .doOnSuccess(response ->
                        logger.info(
                                "Payment revalidation completed. paymentId={}, status={}",
                                paymentId,
                                response == null ? null : response.getStatus()))
                .onErrorResume(error -> {
                    logger.error(
                            "Payment revalidation failed. paymentId={}, revertingStatus={}",
                            paymentId,
                            originalStatus,
                            error);

                    return revertPaymentStatus(paymentId, originalStatus)
                            .thenReturn(
                                    new PaymentRevalidationResponse(
                                            false,
                                            "Payment revalidation failed",
                                            originalStatus));
                });
    }

    private Mono<Void> revertPaymentStatus(
            String paymentId,
            String originalStatus) {

        String paymentUpdate = """
        UPDATE %s
        SET status = '%s',
            up_date = CURRENT_TIMESTAMP
        WHERE payment_id = :paymentId
          AND status = '%s'
        """;

        String paymentSql = String.format(
                paymentUpdate,
                SP_SCHEMA_NAME.concat(".application_payment"),
                originalStatus,
                PaymentStatus.REVALIDATION_IN_PROGRESS.name());

        return transactionalDBExecutor
                .executeRawSql(
                        paymentSql,
                        Map.of("paymentId", paymentId),
                        "")
                .doOnNext(rows ->
                        logger.info(
                                "Payment status reverted after revalidation failure. paymentId={}, status={}, rows={}",
                                paymentId,
                                originalStatus,
                                rows))
                .then();
    }

    private Mono<PaymentRevalidationResponse> queryPayment(
            ApplicationPayment payment,
            PaymentRequest paymentRequest,
            String txnId) {
        return reactiveApiClient
                .queryPayment(paymentRequest, txnId)
                .flatMap(response ->
                        updatePaymentStatus(payment, response)
                                .thenReturn(
                                        new PaymentRevalidationResponse(
                                                true,
                                                "Payment revalidated successfully",
                                                response == null || response.getStatus() == null
                                                        ? null
                                                        : response.getStatus().name())));
    }

    private Mono<Void> updatePaymentStatus(ApplicationPayment payment, PaymentInitiateResponse response) {

        if (response == null || response.getStatus() == null) {
            logger.error("Invalid payment query response. paymentId={}", payment == null ? null : payment.getPaymentId());
            return Mono.empty();
        }

        PaymentStatus status = response.getStatus();
        LocalDateTime now = LocalDateTime.now();

        if(PaymentStatus.UNKNOWN.equals(status)){
            logger.error("Invalid payment query status code. paymentId={}", payment == null ? null : payment.getPaymentId());
            return Mono.error(new SPRuntimeError("Unable to process your request",HttpStatus.INTERNAL_SERVER_ERROR,null));
        }

        payment.setStatus(status.name());
        payment.setGatewayTransactionId(response.getGatewayTransactionId());

        if (response.getAmount() != null) {
            payment.setAmount(response.getAmount());
        }

        if (response.getCurrency() != null) {
            payment.setCurrency(response.getCurrency());
        }

        payment.setUpDate(now);

        if (PaymentStatus.SUCCESS.equals(status) || PaymentStatus.FAILED.equals(status) || PaymentStatus.CANCELLED.equals(status)) {
            payment.setCompletedDate(now);
        }

        logger.info("Updating payment after query. paymentId={}, status={}, gatewayTransactionId={}", payment.getPaymentId(), status, response.getGatewayTransactionId());

        return applicationPaymentRepository
                .save(payment)
                .doOnSuccess(saved ->
                        logger.info(
                                "Payment status updated. paymentId={}, status={}",
                                payment.getPaymentId(),
                                status))
                .then();
    }

    private Mono<PaymentRequest> rebuildPaymentRequest(
            ApplicationPayment payment,
            String txnId) {

        UserSessionObject user = new UserSessionObject();
        user.setTenantId(payment.getTenantId());

        return reactiveApiClient
                .fetchServiceMetadata(
                        user,
                        payment.getServiceId(),
                        txnId)
                .flatMap(metadata -> {

                    PaymentConfigurationDTO paymentConfiguration = metadata.getPaymentConfiguration();

                    if (paymentConfiguration == null) {
                        return Mono.error(new SPRuntimeError("Payment configuration not found", HttpStatus.BAD_REQUEST, txnId));
                    }

                    PaymentConfigurationDTO.PaymentGatewayConfiguration gatewayConfiguration = findSelectedGateway(paymentConfiguration, payment.getGatewayCode());

                    if (gatewayConfiguration == null) {
                        return Mono.error(new SPRuntimeError("Payment gateway configuration not found: " + payment.getGatewayCode(), HttpStatus.BAD_REQUEST, txnId));
                    }

                    return resolveQueryGatewayConfiguration(
                                payment.getApplicationId(),
                                gatewayConfiguration,
                                user,
                                txnId
                            )
                            .map(resolvedConfiguration -> {

                                PaymentRequest paymentRequest = new PaymentRequest();

                                paymentRequest.setTransactionId(payment.getPaymentId());
                                paymentRequest.setGatewayCode(payment.getGatewayCode());
                                paymentRequest.setEnvironment(payment.getEnvironment());
                                paymentRequest.setOrderId(payment.getApplicationId());
                                paymentRequest.setServiceId(String.valueOf(payment.getServiceId()));
                                paymentRequest.setApplicationId(payment.getApplicationId());
                                paymentRequest.setGatewayConfiguration(resolvedConfiguration);
                                paymentRequest.setAmount(payment.getAmount());
                                paymentRequest.setCurrency(payment.getCurrency());
                                paymentRequest.setSuccessUrl(payment_success_callback_path);

                                logger.info("Payment request rebuilt. paymentId={}, gatewayCode={}, environment={}", payment.getPaymentId(), payment.getGatewayCode(), payment.getEnvironment());

                                return paymentRequest;
                            });
                });
    }

    private Mono<Map<String, Object>> resolveQueryGatewayConfiguration(String applicationId, PaymentConfigurationDTO.PaymentGatewayConfiguration gatewayConfiguration,
                                                                       UserSessionObject user, String txnId) {

        List<PaymentConfigurationDTO.GatewayMapping> mappings = gatewayConfiguration.getMappings();

        if (mappings == null || mappings.isEmpty()) {
            return Mono.just(new HashMap<>());
        }

        return Flux.fromIterable(mappings)
                .filter(mapping ->
                        PaymentOperation.QUERY.name().equalsIgnoreCase(mapping.getMode()))
                .flatMap(mapping -> {

                    if ("STATIC".equalsIgnoreCase(mapping.getSourceType())) {

                        return Mono.just(Map.entry(mapping.getNodeCode(), mapping.getSourceValue()));
                    }

                    if ("DYNAMIC".equalsIgnoreCase(mapping.getSourceType())) {

                        return resolveApplicationAttribute(
                                applicationId,
                                mapping.getSourceValue(),
                                user)
                                .switchIfEmpty(
                                        Mono.error(new SPRuntimeError("Required payment query field not found: " + mapping.getSourceValue(), HttpStatus.BAD_REQUEST, txnId))
                                )
                                .map(value ->
                                        Map.entry(mapping.getNodeCode(), value)
                                );
                    }

                    return Mono.error(
                            new SPRuntimeError("Unsupported payment mapping source type: " + mapping.getSourceType(), HttpStatus.BAD_REQUEST, txnId)
                    );
                })
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public Mono<PaymentRevalidationResponse> revalidatePaymentById(ServerRequest request) {
        Optional<String> paymentIdOptional = request.queryParam("paymentId");

        if (paymentIdOptional.isEmpty() || paymentIdOptional.get().isBlank()) {
            return Mono.error(new SPRuntimeError("Payment ID is required", HttpStatus.BAD_REQUEST, null));
        }

        String paymentId = paymentIdOptional.get();
        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
        String tenantId = user.getTenantId();
        Long userId = user.getUserID();

        return applicationPaymentRepository
                .findById(paymentId)
                .switchIfEmpty(
                        Mono.error(new SPRuntimeError("Payment not found", HttpStatus.BAD_REQUEST, paymentId))
                )
                .flatMap(payment ->
                        applicationDetailsRepository
                                .findByApplicationIdAndTenantIdAndBeneficiaryId(
                                        payment.getApplicationId(),
                                        tenantId,
                                        userId)
                                .switchIfEmpty(
                                        Mono.error(
                                                new SPRuntimeError(
                                                        "Application not found or access denied",
                                                        HttpStatus.UNAUTHORIZED,
                                                        paymentId)))
                                .thenReturn(payment))
                .flatMap(payment -> {

                    if (!PaymentStatus.INITIATED.name().equals(payment.getStatus()) && !PaymentStatus.PENDING.name().equals(payment.getStatus())) {
                        return Mono.error(new SPRuntimeError("Payment cannot be revalidated in current status: "
                                + payment.getStatus(), HttpStatus.BAD_REQUEST, null));
                    }

                    Integer revalidationDays = getPayeeRevalidationDays(payment);

                    if (revalidationDays == null) {
                        return Mono.error(new SPRuntimeError("Payment revalidation is not enabled for PAYEE mode", HttpStatus.BAD_REQUEST, paymentId));
                    }

                    if (isRevalidationExpired(payment.getCrDate(), revalidationDays)) {
                        return Mono.error(new SPRuntimeError("Payment revalidation period has expired", HttpStatus.BAD_REQUEST, paymentId));
                    }

                    String originalStatus = payment.getStatus();

                    return claimPaymentForRevalidation(payment)
                            .flatMap(claimed -> {
                                if (!claimed) {
                                    return Mono.error(new SPRuntimeError("Payment is already being revalidated or status has changed", HttpStatus.CONFLICT, paymentId));
                                }

                                payment.setStatus(PaymentStatus.REVALIDATION_IN_PROGRESS.name());

                                return revalidatePayment(payment, originalStatus);
                            });
                });
    }

    private Integer getPayeeRevalidationDays(ApplicationPayment payment) {
        return getPayeeRevalidationDays(payment.getRevalidationConfiguration(), payment.getPaymentId());
    }

    private Integer getPayeeRevalidationDays(PaymentRevalidationApplicationResponse payment) {
        return getPayeeRevalidationDays(payment.getRevalidationConfiguration(), payment.getPaymentId());
    }

    @SuppressWarnings("unchecked")
    private Integer getPayeeRevalidationDays(String revalidationConfiguration, String paymentId) {
        if (revalidationConfiguration == null || revalidationConfiguration.isBlank()) return null;

        try {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> configuration = (Map<String, Object>) stringToEntityUsingType(revalidationConfiguration, type);

            if (!Boolean.TRUE.equals(configuration.get("enabled"))) return null;

            Object modesObject = configuration.get("modes");
            if (!(modesObject instanceof List<?> modes) || modes.stream().noneMatch(mode ->
                    PaymentRevalidationMode.PAYEE.name().equalsIgnoreCase(String.valueOf(mode)))) return null;

            Object periodsObject = configuration.get("periods");
            if (!(periodsObject instanceof Map<?, ?> periods)) return null;

            Object days = periods.get(PaymentRevalidationMode.PAYEE.name());
            if (!(days instanceof Number revalidationDays) || revalidationDays.intValue() <= 0) return null;

            return revalidationDays.intValue();
        } catch (Exception e) {
            logger.error("Failed to parse payment PAYEE revalidation configuration. PaymentId={}", paymentId, e);
            return null;
        }
    }

    private boolean isRevalidationExpired(LocalDateTime createdDate, Integer revalidationDays) {
        if (createdDate == null) {
            return false;
        }

        LocalDateTime expiryTime = createdDate.plusDays(revalidationDays);

        boolean expired = LocalDateTime.now().isAfter(expiryTime);

        if (expired) {
            logger.info("Payment revalidation period expired. revalidationDays={}, expiryTime={}", revalidationDays, expiryTime);
        }

        return expired;
    }


    private Mono<Boolean> claimPaymentForRevalidation(
            ApplicationPayment payment) {

        String paymentUpdate = """
        UPDATE %s
        SET status = '%s',
            up_date = CURRENT_TIMESTAMP
        WHERE payment_id = :paymentId
          AND status IN ('%s', '%s')
        """;

        String paymentSql = String.format(
                paymentUpdate,
                SP_SCHEMA_NAME.concat(".application_payment"),
                PaymentStatus.REVALIDATION_IN_PROGRESS.name(),
                PaymentStatus.INITIATED.name(),
                PaymentStatus.PENDING.name());

        return transactionalDBExecutor
                .executeRawSql(
                        paymentSql,
                        Map.of("paymentId", payment.getPaymentId()),
                        "")
                .map(rows -> {
                    logger.info(
                            "Payment revalidation claim completed. PaymentId={}, rows={}",
                            payment.getPaymentId(),
                            rows);
                    return rows > 0;
                });
    }

    public Mono<List<PaymentRevalidationApplicationResponse>> getApplicationsAvailableForRevalidation(ServerRequest request) {
        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
        String tenantId = user.getTenantId();
        Long userId = user.getUserID();

        int page;
        int size;

        try {
            page = Integer.parseInt(request.queryParam("page").orElse("0"));
            size = Integer.parseInt(request.queryParam("size").orElse("50"));
        } catch (NumberFormatException e) {
            return Mono.error(new SPRuntimeError("Page and size must be valid integers", HttpStatus.BAD_REQUEST, null));
        }

        if (page < 0) {
            return Mono.error(new SPRuntimeError("Page must be greater than or equal to 0", HttpStatus.BAD_REQUEST, null));
        }

        if (size < 1 || size > 100){
            return Mono.error(new SPRuntimeError("Page size must be between 1 and 100", HttpStatus.BAD_REQUEST, null));
        }

        long offset = (long) page * size;

        logger.info("Fetching applications available for payment revalidation. tenantId={}, userId={}, page={}, size={}", tenantId, userId, page, size);

        return applicationPaymentRepository.findApplicationsAvailableForRevalidation(
                        tenantId,
                        List.of(PaymentStatus.INITIATED.name(), PaymentStatus.PENDING.name()),
                        userId,
                        size,
                        offset)
                .filter(payment -> {
                    Integer revalidationDays = getPayeeRevalidationDays(payment);
                    return revalidationDays == null || !isRevalidationExpired(payment.getCreatedAt(), revalidationDays);
                })
                .collectList()
                .doOnSuccess(applications -> logger.info(
                        "Payment revalidation applications fetched. tenantId={}, userId={}, page={}, size={}, count={}",
                        tenantId, userId, page, size, applications.size()));
    }
}


