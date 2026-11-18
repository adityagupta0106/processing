package com.serviceplus.form.validation.handlers;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_STATUS_KEY;
import static com.serviceplus.form.validation.utility.ApplicationConstants.OFFICIAL_TASK_FLAG;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.Utility.decryptWorkflowKey;
import static com.serviceplus.form.validation.utility.Utility.entityToString;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static com.serviceplus.form.validation.utility.Utility.isEmpty;
import static com.serviceplus.form.validation.utility.Utility.populateActionAndLocation;
import static com.serviceplus.form.validation.utility.Utility.stringToEntity;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.HandlerResponse;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;
import com.serviceplus.form.validation.service.EditFormDetailsService;
import com.serviceplus.form.validation.service.FormService;
import com.serviceplus.form.validation.service.PreProcessingFacade;
import com.serviceplus.form.validation.service.ReactiveApiClient;

import reactor.core.publisher.Mono;

@Service
@SanitizeRequest
public class FormSubmissionHandler implements ApplicationFlowHandler {

    @Autowired
    private FormService formService;

    @Autowired
    private ProcessingTxnRepository txnRepository;

    @Autowired
    private ReactiveApiClient reactiveApiClient;

    @Autowired
    private PreProcessingFacade preProcessingFacade;

    @Autowired
    private ApplicationDetailsRepository applicationDetailsRepository;

    @Autowired
    private EditFormDetailsService editFormDetailsService;
    
    @Override
    public String getActivityType() {
        return ACTIVITY_FORM_STATUS_KEY;
    }

	@SuppressWarnings("unchecked")
	@Override
	public Mono<ServerResponse> process(String applicationId, ServerRequest request, String statusKey, String txnId,
			Mono<TempTransactionLogs> tempLog, ApplicationFlowStatusEntity flowStatus, ServiceMeta service,
			boolean fromDraft) {

		Optional<String> applyKeyOpt = request.queryParam("serviceKey");
		Optional<String> appIdOpt = request.queryParam("appId");
		Optional<String> serviceIdOpt = request.queryParam("serviceId");

		if (isEmpty(txnId) || applyKeyOpt.isEmpty() || serviceIdOpt.isEmpty()) {
			return Mono.error(new SPRuntimeError("Parameters missing", HttpStatus.BAD_REQUEST, txnId));
		}

		if (appIdOpt.isEmpty() && service.getTaskType().equals(OFFICIAL_TASK_FLAG)) {
			return Mono.error(new SPRuntimeError("Issue while processing the request [SUB - 008]",
					HttpStatus.INTERNAL_SERVER_ERROR, txnId));
		}

		final boolean newEntityFlag = appIdOpt.isEmpty();
		String applId = appIdOpt.orElse(createUniqueId());

		Mono<String> bodyMono = request.bodyToMono(String.class).cache();

		return bodyMono.hasElement().flatMap(hasBody -> {

			if (hasBody) {
				return bodyMono.flatMap(body -> {

					Map<String, Object> requestBody = (Map<String, Object>) stringToEntity(body, Map.class);

					String workflowKey = (String) requestBody.remove("workflowKey");

					Object editPermissionPayload = null;

					if (!newEntityFlag) {

						editPermissionPayload = requestBody.remove("editPermission");

						if (editPermissionPayload == null) {
							editPermissionPayload = requestBody.remove("editPermissionData");
						}

						if (editPermissionPayload == null && requestBody.containsKey("specific")) {

							Map<String, Object> rootPermission = new HashMap<>();

							for (String field : List.of("selectedHolderId", "selectedFormId",
									"specific")) {

								if (requestBody.containsKey(field)) {
									rootPermission.put(field, requestBody.remove(field));
								}
							}

							editPermissionPayload = rootPermission;
						}
					}

					final Object finalEditPermissionPayload = editPermissionPayload;

					Mono<Boolean> priorityMono;

					if (requestBody.containsKey("isPriority")) {

						Boolean isPriority = (Boolean) requestBody.remove("isPriority");
						priorityMono = Mono.just(Objects.requireNonNullElse(isPriority, Boolean.FALSE));

					} else {

						priorityMono = applicationDetailsRepository.findByApplicationId(applicationId)
								.map(ApplicationDetails::getIsPriority)
								.map(priority -> Objects.requireNonNullElse(priority, Boolean.FALSE))
								.defaultIfEmpty(Boolean.FALSE);
					}

					return priorityMono.flatMap(isPriority -> {
						service.setIsPriority(isPriority);

						if (!isEmpty(workflowKey)) {
							service.setWorkflowElementData(decryptWorkflowKey(workflowKey));
						}

						if (!newEntityFlag && finalEditPermissionPayload != null) {

							List<ServiceProcessFlowDTO.Data.EditPermissionData> validatedPermissions = editFormDetailsService
									.validateSubmittedPermissions(finalEditPermissionPayload,
											service.getWorkflowElementData(), txnId);

							service.setPendingEditPermissionData(validatedPermissions);

						} else {
							service.setPendingEditPermissionData(Collections.emptyList());
						}

						String formData = entityToString(requestBody);

						return reactiveApiClient
								.fetchServiceMetadata(getUserSessionDetails(request.exchange().getRequest()),
										service.getServiceId(), txnId)
								.flatMap(metadataService -> {

									populateActionAndLocation(metadataService, service);

									return formService.applicationSubmission(request.exchange().getRequest(), txnId,
											formData, applId, fromDraft, request, flowStatus, serviceIdOpt.get(),
											service, newEntityFlag);
								});
					});
				});

			} else {
				return fetch(applicationId, request, statusKey, txnId, tempLog, flowStatus, service, fromDraft);
			}
		});
	}
     @Override
    public Mono<ServerResponse> fetch(String applicationId, ServerRequest request, String statusKey, String txnId, Mono<TempTransactionLogs> fetch, ApplicationFlowStatusEntity flow, ServiceMeta service, boolean fromDraft) {

        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());

        Optional<String> serviceIdOpt = request.queryParam("serviceId");

        return serviceIdOpt.map(s -> preProcessingFacade
                .prepareForm(
                        service, user, flow.getTxnId(), flow.getApplicationId()
                )
                .flatMap(context -> {
                    ServiceProcessFlowDTO.Data.WorkflowElementData workflowElementData =
                            context.service().getWorkflowElementData();

                    return editFormDetailsService.enrichGlobalWorkflowElementData(
                                    workflowElementData,
                                    applicationId,
                                    service.getServiceId(),
                                    user.getTenantId(),
                                    flow.getTxnId())
                            .then(formService.fetchFormData(
                                    flow.getDataId(),
                                    flow.getFormId(),
                                    user,
                                    flow.getTxnId(),
                                    applicationId,
                                    s,
                                    service))
                            .flatMap(response -> {
                                String formIdToMatch = flow.getFormId() != null && !flow.getFormId().isBlank()
                                        ? flow.getFormId()
                                        : service.getFormId();
                                return editFormDetailsService.getSavedEditPermission(applicationId, formIdToMatch)
                                        .defaultIfEmpty(Collections.emptyMap())
                                        .doOnNext(permissionMap -> {
                                            applyEditPermission(response, workflowElementData, permissionMap);
                                        })
                                        .thenReturn(response);
                            })
                            .map(response -> {
                                response.setWorkflowElementData(workflowElementData);
                                response.setWorkflowKey(context.workflowKey());
                                return response;
                            });

                })
                .flatMap(response ->
                        ServerResponse.ok()
                                .bodyValue(response)
                )).orElseGet(() -> Mono.error(
                        new SPRuntimeError("Key missing", HttpStatus.BAD_REQUEST, txnId
                )
        ));
     }

    private void applyEditPermission(HandlerResponse response,
                                     ServiceProcessFlowDTO.Data.WorkflowElementData workflowElementData,
                                     Map<String, Object> permissionMap) {
        if (isPermissionNotBlank(permissionMap)) {
            response.setEditPermission(permissionMap);
            if (workflowElementData != null) {
                workflowElementData.setFallBackEditPermission(permissionMap);
            }
            if (response.getData() != null) {
                response.getData().put("editPermission", permissionMap);
            }
        } else {
            response.setEditPermission(null);
            if (workflowElementData != null) {
                workflowElementData.setFallBackEditPermission(null);
            }
            if (response.getData() != null) {
                response.getData().remove("editPermission");
            }
        }
    }

    private boolean isPermissionNotBlank(Map<String, Object> permissionMap) {
        if (permissionMap == null || permissionMap.isEmpty()) {
            return false;
        }
        Object allowedObj = permissionMap.get("allowed");
        Object restrictedObj = permissionMap.get("restricted");
        Object hideObj = permissionMap.get("hide");

        boolean hasAllowed = (allowedObj instanceof java.util.Collection<?> c && !c.isEmpty());
        boolean hasRestricted = (restrictedObj instanceof java.util.Collection<?> c && !c.isEmpty());
        boolean hasHide = (hideObj instanceof java.util.Collection<?> c && !c.isEmpty());

        return hasAllowed || hasRestricted || hasHide;
    }
}
