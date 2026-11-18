package com.serviceplus.form.validation.handlers;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.enums.DigiLockerOperation;
import com.serviceplus.form.validation.flow.EventDecider;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.ApplicationDocumentSubmissionRepository;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;
import com.serviceplus.form.validation.service.ActivityMapService;
import com.serviceplus.form.validation.service.ReactiveApiClient;
import com.serviceplus.form.validation.utility.ApplicationConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.serviceplus.form.validation.utility.ApplicationConstants.*;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static com.serviceplus.form.validation.utility.Utility.isEmpty;

@Service
@SanitizeRequest
public class DigiLockerHandler implements ApplicationFlowHandler {

	private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

	private final ReactiveApiClient reactiveApiClient;
	private final EventDecider eventDecider;
	private final ProcessingTxnRepository processingTxnRepository;
	private final ApplicationDocumentSubmissionRepository documentSubmissionRepository;
	private final ApplicationDetailsRepository applicationDetailsRepository;
	private final ActivityMapService activityMapService;

	public DigiLockerHandler(
			ReactiveApiClient reactiveApiClient,
			@Lazy EventDecider eventDecider,
			ProcessingTxnRepository processingTxnRepository,
			ApplicationDocumentSubmissionRepository documentSubmissionRepository,
			ApplicationDetailsRepository applicationDetailsRepository,
			ActivityMapService activityMapService) {
		this.reactiveApiClient = reactiveApiClient;
		this.eventDecider = eventDecider;
		this.processingTxnRepository = processingTxnRepository;
		this.documentSubmissionRepository = documentSubmissionRepository;
		this.applicationDetailsRepository = applicationDetailsRepository;
		this.activityMapService = activityMapService;
	}

	@Override
	public String getActivityType() {
		return ACTIVITY_DIGILOCKER;
	}

	@Override
	public List<String> getActivityTypes() {
		return List.of(
				ACTIVITY_DIGILOCKER,
				ACTIVITY_DIGILOCKER_EKYC,
				"EKYC",
				ACTIVITY_DIGILOCKER_PUSH,
				"PUSH_DOCUMENT_TO_DIGILOCKER",
				"PD"
		);
	}

	@Override
	public Mono<ServerResponse> process(String applicationId, ServerRequest request, String statusKey, String txnId,
			Mono<TempTransactionLogs> tempLog, ApplicationFlowStatusEntity flowStatus, ServiceMeta service,
			boolean fromDraft) {

		UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
		if (user == null) {
			return Mono.error(new SPRuntimeError("User session details are missing", HttpStatus.UNAUTHORIZED, txnId));
		}

		DigiLockerOperation operation = resolveOperation(statusKey, request, flowStatus, service);

		applicationFlowLogs.info("DigiLockerHandler process invoked | appId={}, txnId={}, statusKey={}, op={}",
				applicationId, txnId, statusKey, operation);

		if (operation == DigiLockerOperation.PUSH_DOCUMENT) {
			return pushDocumentToDigiLocker(applicationId, request, txnId, tempLog, flowStatus, service, fromDraft, user);
		} else {
			return processEKYC(applicationId, request, txnId, tempLog, flowStatus, service, fromDraft, user);
		}
	}

	@Override
	public Mono<ServerResponse> fetch(String applicationId, ServerRequest request, String statusKey, String txnId,
			Mono<TempTransactionLogs> tempLog, ApplicationFlowStatusEntity flowStatus, ServiceMeta service,
			boolean fromDraft) {

		UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
		if (user == null) {
			return Mono.error(new SPRuntimeError("User session details are missing", HttpStatus.UNAUTHORIZED, txnId));
		}

		DigiLockerOperation operation = resolveOperation(statusKey, request, flowStatus, service);

		if (operation == DigiLockerOperation.PUSH_DOCUMENT) {
			return pushDocumentToDigiLocker(applicationId, request, txnId, tempLog, flowStatus, service, fromDraft, user);
		}

		return reactiveApiClient.getDigiLockerIdentity(applicationId, user, txnId).flatMap(identity -> {
			HandlerResponse hr = new HandlerResponse();
			hr.setTxnId(txnId);
			hr.setApplicationId(applicationId);
			hr.setActivityType(flowStatus != null && flowStatus.getActivityType() != null
					? flowStatus.getActivityType()
					: ACTIVITY_DIGILOCKER_EKYC);
			hr.setActivityEnd(false);

			if (identity.isLinked()) {
				hr.setData(Map.of(
						"linked", true,
						"digilockerId", identity.getDigilockerId() != null ? identity.getDigilockerId() : "",
						"referenceKey", identity.getReferenceKey() != null ? identity.getReferenceKey() : ""
				));
				return ServerResponse.ok().bodyValue(hr);
			} else {
				return reactiveApiClient.initiateDigiLockerAuth(applicationId, user, txnId).flatMap(authResp -> {
					hr.setData(Map.of(
							"linked", false,
							"authUrl", authResp.getAuthUrl() != null ? authResp.getAuthUrl() : ""
					));
					return ServerResponse.ok().bodyValue(hr);
				}).onErrorResume(ex -> {
					hr.setData(Map.of("linked", false, "error", ex.getMessage()));
					return ServerResponse.ok().bodyValue(hr);
				});
			}
		});
	}

	private DigiLockerOperation resolveOperation(String statusKey, ServerRequest request,
			ApplicationFlowStatusEntity flowStatus, ServiceMeta service) {

		DigiLockerOperation op = DigiLockerOperation.fromActivity(statusKey);
		if (op != null) {
			return op;
		}

		Optional<String> activityId = request.queryParam("activityId");
		if (activityId.isPresent() && !activityId.get().isBlank()) {
			op = DigiLockerOperation.fromActivity(activityId.get());
			if (op != null) {
				return op;
			}
		}

		if (flowStatus != null && flowStatus.getActivityType() != null) {
			op = DigiLockerOperation.fromActivity(flowStatus.getActivityType());
			if (op != null) {
				return op;
			}
		}

		if (service != null && OFFICIAL_TASK_FLAG.equals(service.getTaskType())) {
			return DigiLockerOperation.PUSH_DOCUMENT;
		}

		return DigiLockerOperation.EKYC;
	}

	private Mono<ServerResponse> pushDocumentToDigiLocker(
			String applicationId, ServerRequest request, String txnId,
			Mono<TempTransactionLogs> tempLog, ApplicationFlowStatusEntity flowStatus,
			ServiceMeta service, boolean fromDraft, UserSessionObject user) {

		String resolvedTxnId = (flowStatus != null && flowStatus.getTxnId() != null) ? flowStatus.getTxnId() : txnId;

		return request.bodyToMono(DigiLockerPushDocumentRequestDTO.class)
				.defaultIfEmpty(new DigiLockerPushDocumentRequestDTO())
				.flatMap(pushReq -> {
					pushReq.setApplicationId(applicationId);
					pushReq.setUserId(user.getUserID());
					pushReq.setTenantId(user.getTenantId());

					if (isEmpty(pushReq.getAction())) {
						pushReq.setAction("A");
					}
					if (isEmpty(pushReq.getIssueDate())) {
						pushReq.setIssueDate(LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
					}
					if (isEmpty(pushReq.getTs())) {
						pushReq.setTs(Instant.now().toString());
					}
					if (isEmpty(pushReq.getDocType())) {
						pushReq.setDocType(service != null && service.getServiceName() != null ? service.getServiceName() : "CERTT");
					}

					Mono<DigiLockerPushDocumentRequestDTO> enrichUploadIdMono = (pushReq.getUploadId() != null && !pushReq.getUploadId().isBlank())
							? Mono.just(pushReq)
							: documentSubmissionRepository
									.findByApplicationIdAndTaskIdAndStatusOrderByCreatedOnDesc(applicationId, service.getTaskId(), "P")
									.next()
									.switchIfEmpty(documentSubmissionRepository.findByApplicationId(applicationId).next())
									.map(sub -> {
										String uploadId = (sub.getSignedUploadId() != null && !sub.getSignedUploadId().isBlank())
												? sub.getSignedUploadId()
												: sub.getUploadId();
										pushReq.setUploadId(uploadId);
										if (isEmpty(pushReq.getDescription())) {
											pushReq.setDescription(sub.getDocumentName());
										}
										return pushReq;
									})
									.defaultIfEmpty(pushReq);

					return enrichUploadIdMono.flatMap(withUpload -> {
						Mono<DigiLockerPushDocumentRequestDTO> enrichDigiIdMono = (withUpload.getDigilockerId() != null && !withUpload.getDigilockerId().isBlank())
								? Mono.just(withUpload)
								: reactiveApiClient.getDigiLockerIdentity(applicationId, user, resolvedTxnId)
										.map(idResp -> {
											if (idResp.getDigilockerId() != null) {
												withUpload.setDigilockerId(idResp.getDigilockerId());
											}
											return withUpload;
										})
										.defaultIfEmpty(withUpload);

						return enrichDigiIdMono.flatMap(withDigi -> {
							Mono<DigiLockerPushDocumentRequestDTO> enrichRefMono = (withDigi.getDocId() != null && !withDigi.getDocId().isBlank())
									? Mono.just(withDigi)
									: applicationDetailsRepository.findByApplicationIdAndTenantId(applicationId, user.getTenantId())
											.map(app -> {
												withDigi.setApplicationReferenceNo(app.getReferenceNo());
												withDigi.setDocId(app.getReferenceNo());
												return withDigi;
											})
											.defaultIfEmpty(withDigi);

							return enrichRefMono.flatMap(finalPushReq -> {
								applicationFlowLogs.info("TxnId : {} | Pushing document to DigiLocker | appId={}, uploadId={}, digilockerId={}",
										resolvedTxnId, applicationId, finalPushReq.getUploadId(), finalPushReq.getDigilockerId());

								Mono<DigiLockerResponseDTO> pushCallMono;
								if (isEmpty(finalPushReq.getDigilockerId())) {
									applicationFlowLogs.warn("TxnId : {} | Applicant has no linked DigiLocker account for appId={}. Skipping remote push.",
											resolvedTxnId, applicationId);
									DigiLockerResponseDTO skipResp = new DigiLockerResponseDTO();
									skipResp.setSuccess(true);
									skipResp.setMessage("DigiLocker not linked by applicant, push skipped");
									pushCallMono = Mono.just(skipResp);
								} else {
									pushCallMono = reactiveApiClient.pushDocumentToDigiLocker(finalPushReq, user, resolvedTxnId)
											.onErrorResume(ex -> {
												applicationFlowLogs.error("TxnId : {} | DigiLocker push call failed: {}", resolvedTxnId, ex.getMessage());
												DigiLockerResponseDTO errResp = new DigiLockerResponseDTO();
												errResp.setSuccess(false);
												errResp.setMessage("DigiLocker push failed: " + ex.getMessage());
												return Mono.just(errResp);
											});
								}

								return pushCallMono.flatMap(pushResp -> {
									applicationFlowLogs.info("TxnId : {} | DigiLocker push completed | success={}. Invoking EventDecider.",
											resolvedTxnId, pushResp.isSuccess());

									String currentActivityType = (flowStatus != null && flowStatus.getActivityType() != null)
											? flowStatus.getActivityType()
											: ACTIVITY_DIGILOCKER_PUSH;

									String currentDataId = (flowStatus != null) ? flowStatus.getDataId() : null;

									return processingTxnRepository.findById(resolvedTxnId)
											.switchIfEmpty(Mono.error(new SPRuntimeError("Processing transaction not found.", HttpStatus.INTERNAL_SERVER_ERROR, resolvedTxnId)))
											.flatMap(txnLog -> eventDecider.proceedToNext(
													currentDataId,
													service,
													user,
													txnLog,
													applicationId,
													null,
													currentActivityType,
													request,
													flowStatus
											));
								});
							});
						});
					});
				});
	}

	private Mono<ServerResponse> processEKYC(
			String applicationId, ServerRequest request, String txnId,
			Mono<TempTransactionLogs> tempLog, ApplicationFlowStatusEntity flowStatus,
			ServiceMeta service, boolean fromDraft, UserSessionObject user) {

		String resolvedTxnId = (flowStatus != null && flowStatus.getTxnId() != null) ? flowStatus.getTxnId() : txnId;

		return request.bodyToMono(DigiLockerEKYCRequestDTO.class)
				.defaultIfEmpty(new DigiLockerEKYCRequestDTO())
				.flatMap(ekycReq -> {
					ekycReq.setApplicationId(applicationId);
					ekycReq.setUserId(user.getUserID());
					ekycReq.setTenantId(user.getTenantId());
					if (service != null && service.getServiceId() != null) {
						ekycReq.setServiceId(service.getServiceId());
					}

					return reactiveApiClient.performDigiLockerEKYC(ekycReq, user, resolvedTxnId)
							.flatMap(response -> {
								applicationFlowLogs.info("TxnId : {} | DigiLocker eKYC result | linked={}, success={}",
										resolvedTxnId, response.isLinked(), response.isSuccess());

								if (!response.isLinked() && response.getAuthUrl() != null && !response.getAuthUrl().isBlank()) {
									HandlerResponse hr = new HandlerResponse();
									hr.setTxnId(resolvedTxnId);
									hr.setApplicationId(applicationId);
									hr.setActivityType(flowStatus != null && flowStatus.getActivityType() != null
											? flowStatus.getActivityType()
											: ACTIVITY_DIGILOCKER_EKYC);
									hr.setActivityEnd(false);
									hr.setData(Map.of("linked", false, "authUrl", response.getAuthUrl()));
									return ServerResponse.ok().bodyValue(hr);
								}

								if (response.getExtractedAttributes() != null && !response.getExtractedAttributes().isEmpty()) {
									service.setPreviousHandlerData(Map.of("ekycAttributes", response.getExtractedAttributes()));
								}

								String currentActivityType = (flowStatus != null && flowStatus.getActivityType() != null)
										? flowStatus.getActivityType()
										: ACTIVITY_DIGILOCKER_EKYC;

								String currentDataId = (flowStatus != null) ? flowStatus.getDataId() : null;

								return processingTxnRepository.findById(resolvedTxnId)
										.switchIfEmpty(Mono.error(new SPRuntimeError("Processing transaction not found.", HttpStatus.INTERNAL_SERVER_ERROR, resolvedTxnId)))
										.flatMap(txnLog -> eventDecider.proceedToNext(
												currentDataId,
												service,
												user,
												txnLog,
												applicationId,
												null,
												currentActivityType,
												request,
												flowStatus
										));
							});
				});
	}
}
