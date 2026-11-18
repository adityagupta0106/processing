package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.Helpers.SystemAttributeHelper;
import com.serviceplus.form.validation.dto.NotificationDetails;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.flow.EventDecider;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;

import reactor.core.publisher.Mono;

@Service
public class NotificationProcessService {

	private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

	private final ReactiveApiClient reactiveApiClient;
	private final ProcessingTxnRepository processingTxnRepository;
	private final EventDecider eventDecider;
	private final ActivityMapService activityMapService;
	private final ApplicationDetailsRepository applicationDetailsRepository;
	private final SystemAttributeHelper systemAttributeHelper;

	public NotificationProcessService(ReactiveApiClient reactiveApiClient,
			ProcessingTxnRepository processingTxnRepository, EventDecider eventDecider, ActivityMapService activityMapService, ApplicationDetailsRepository applicationDetailsRepository, SystemAttributeHelper systemAttributeHelper) {

		this.reactiveApiClient = reactiveApiClient;
		this.processingTxnRepository = processingTxnRepository;
		this.eventDecider = eventDecider;
		this.activityMapService = activityMapService;
		this.applicationDetailsRepository = applicationDetailsRepository;
		this.systemAttributeHelper = systemAttributeHelper;
	}

    public Mono<ServerResponse> process(
            String applicationId,
            ServerRequest request,
            String statusKey,
            String txnId,
            Mono<TempTransactionLogs> fetch,
            ApplicationFlowStatusEntity flow,
            ServiceMeta service,
            boolean fromDraft) {

        UserSessionObject user =
                getUserSessionDetails(request.exchange().getRequest());

        return applicationDetailsRepository
                .findByApplicationIdAndTenantId(
                        applicationId, user.getTenantId())

                .switchIfEmpty(Mono.error(
                        new SPRuntimeError("Application details not found.", HttpStatus.BAD_REQUEST, flow.getTxnId())
                ))

                .flatMap(details -> {

                    applicationFlowLogs.info(
                            "TxnId : {} | Application details loaded. ReferenceNo={}",
                            flow.getTxnId(),
                            details.getReferenceNo());

                    return activityMapService
                            .activityConfigId(
                                    service,
                                    user,
                                    applicationId,
                                    flow.getTxnId(),
                                    flow.getActivityType())

                            .switchIfEmpty(Mono.error(
                                    new SPRuntimeError("Notification activity configuration not found.", HttpStatus.BAD_REQUEST, flow.getTxnId()
                                    )
                            ))

                            .flatMap(activityConfigId -> {
                                Map<String, Object> systemAttrMap = new HashMap<>();

                                List<String> requiredPatterns = resolveNotificationPatterns(service, activityConfigId, flow);

                                systemAttributeHelper.systemAttrMap(
                                        systemAttrMap,
                                        details,
                                        service,
                                        null,
                                        requiredPatterns,
                                        "NOTIFICATION");

                                applicationFlowLogs.info(
                                        "TxnId : {} | Notification systemAttrMap prepared with requiredPatterns: {} for notificationId: {}",
                                        flow.getTxnId(),
                                        requiredPatterns,
                                        activityConfigId);

                                return reactiveApiClient.sendNotification(applicationId, service.getServiceId(), activityConfigId, flow.getTxnId(), systemAttrMap, request);
                            });
                })

                .then(
                        processingTxnRepository
                                .findById(flow.getTxnId())
                                .switchIfEmpty(Mono.error(
                                        new SPRuntimeError("Processing transaction not found.", HttpStatus.INTERNAL_SERVER_ERROR, flow.getTxnId())
                                 ))
                )

                .flatMap(txnLog -> {

                    applicationFlowLogs.info("TxnId : {} | Notification processing completed successfully. Invoking EventDecider for next workflow activity.", flow.getTxnId());

                    return eventDecider.proceedToNext(flow.getDataId(), service, user, txnLog, applicationId, null, flow.getActivityType(), request, flow);
                });
    }

    public List<String> resolveNotificationPatterns(ServiceMeta service, Long activityConfigId, ApplicationFlowStatusEntity flow) {
        if (service == null || service.getNotificationDetails() == null || service.getNotificationDetails().isEmpty()) {
            return Collections.emptyList();
        }

        String currentTaskId = service.getTaskId();

        List<NotificationDetails> notifList = service.getNotificationDetails();

        // 1. Try matching by exact notificationId (activityConfigId) AND taskId
        if (activityConfigId != null && currentTaskId != null) {
            for (NotificationDetails nd : notifList) {
                if (nd != null && activityConfigId.equals(nd.getNotificationId()) && currentTaskId.equals(nd.getTaskId())) {
                    if (nd.getRequiredPatterns() != null) {
                        return nd.getRequiredPatterns();
                    }
                }
            }
        }

        return Collections.emptyList();
    }
}
