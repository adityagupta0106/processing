package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_STATUS_KEY;
import static com.serviceplus.form.validation.utility.ApplicationConstants.APPLICATION_SUBMISSION_TASK_FLAG;
import static com.serviceplus.form.validation.utility.ApplicationConstants.OFFICIAL_TASK_FLAG;
import static com.serviceplus.form.validation.utility.Utility.encryptServiceKeys;
import static com.serviceplus.form.validation.utility.Utility.entityToString;
import static com.serviceplus.form.validation.utility.Utility.handleWebClientError;
import static com.serviceplus.form.validation.utility.Utility.isEmpty;
import static com.serviceplus.form.validation.utility.Utility.stringToEntityUsingType;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.serviceplus.form.validation.dto.*;
import com.serviceplus.payment.adapter.api.PaymentRequest;
import com.serviceplus.payment.adapter.api.PaymentResponse;
import com.serviceplus.payment.adapter.api.PaymentWebhookRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.reflect.TypeToken;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.executor.ApiExecutor;
import com.serviceplus.form.validation.utility.Utility;

import reactor.core.publisher.Mono;

@Service
public class ReactiveApiClient {

    @Autowired
    private ApiExecutor AsynchronousApiExecutor;

    @Value("${metatdata.service}")
    private String METADATA_SERVICE;

    @Value("${guest.internal-secret}")
    private String GUEST_INTERNAL_SECRET;

    @Value("${formmgmt.service}")
    private String FORM_MANAGEMENT_SERVICE;

    @Value("${document.generation.service}")
    private String DOCUMENT_GENERATION_SERVICE;
    
    @Value("${mvel.execution.service}")
    private String MVEL_EXECUTION_SERVICE;

    @Value("${file.management.service}")
    private String FILE_MANAGEMENT_SERVICE;

    @Value("${tracking.service}")
    private String TRACKING_SERVICE;
    
    @Value("${notification.service}")
    private String NOTIFICATION_SERVICE;
    
    @Value("${spgd.service}")
    private String SPGD_SERVICE_URL;

    @Value("${payment.service}")
    private String PAYMENT_SERVICE;
    
    @Value("${plugins.service}")
    private String PLUGINS_SERVICE;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private RedisService redis;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    @SuppressWarnings("unchecked")
	public Mono<List<ServiceMeta>> fetchServiceList(UserSessionObject user) {
        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
        String url = METADATA_SERVICE.concat("apply/serviceList");

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
												                String.class,
												                HttpMethod.GET,
												                headers,
												                Collections.emptyMap(),
												                url,
												                null,
												                MediaType.APPLICATION_JSON);
        
        
        //map if only transforming to list can be synchronous
        //IF another API call or DB or any other process then can use flatmap for asynchronous call
        return callExternalEndpoint.flatMap(apiResponse -> {
				                String body = apiResponse.getBody().toString();
				
				                Type listType = new TypeToken<List<ServiceMeta>>() {}.getType();
				                List<ServiceMeta> servicesList = (List<ServiceMeta>) stringToEntityUsingType(body, listType);
				
				                return Mono.just(servicesList);
				            })
			        		;
    }


    public Mono<Map<String, Object>> resolveToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Mono.error(new SPRuntimeError("Token is required", HttpStatus.BAD_REQUEST, null));
        }
        String url = METADATA_SERVICE.concat("apply/resolve-token");
        Map<String, String> headers = Map.of();
        Map<String, Object> params = Map.of("token", token.trim());
        return AsynchronousApiExecutor.callExternalEndpoint(
                String.class,
                HttpMethod.GET,
                headers,
                params,
                url,
                null,
                MediaType.APPLICATION_JSON
        ).flatMap(response -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
                return Mono.error(new SPRuntimeError("Invalid or expired apply token", HttpStatus.BAD_REQUEST, null));
            }
            try {
                Map<String, Object> map = mapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
                if (map != null && Boolean.TRUE.equals(map.get("status"))) {
                    return Mono.just(map);
                } else {
                    String msg = (map != null && map.get("errorMessage") != null) ? map.get("errorMessage").toString() : "Service not found for token";
                    return Mono.error(new SPRuntimeError(msg, HttpStatus.BAD_REQUEST, null));
                }
            } catch (Exception e) {
                return Mono.error(new SPRuntimeError("Failed to parse token resolution response", HttpStatus.INTERNAL_SERVER_ERROR, null));
            }
        }).onErrorResume(WebClientResponseException.class, ex -> {
            if (ex.getStatusCode().equals(HttpStatus.BAD_REQUEST)) {
                return Mono.error(new SPRuntimeError("Invalid or expired apply token", HttpStatus.BAD_REQUEST, null));
            }
            return Mono.error(new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR, null));
        });
    }

    public Mono<GuestEligibilityDTO> fetchGuestEligibility(Integer serviceId) {

        String url = METADATA_SERVICE.concat("internal/guest/eligibility");

        Map<String, String> headers = Map.of(
                "X-Guest-Internal-Key",
                GUEST_INTERNAL_SECRET
        );

        Map<String, Object> params = Map.of(
                "serviceId",
                serviceId
        );

        return AsynchronousApiExecutor
                .callExternalEndpoint(
                        String.class,
                        HttpMethod.GET,
                        headers,
                        params,
                        url,
                        null,
                        MediaType.APPLICATION_JSON
                )
                .flatMap(response -> {

                    if (!response.getStatusCode().is2xxSuccessful()) {
                        return Mono.<GuestEligibilityDTO>error(
                                new SPRuntimeError(
                                        "Guest eligibility service unavailable",
                                        HttpStatus.BAD_GATEWAY,
                                        null
                                )
                        );
                    }

                    String body = response.getBody();

                    if (body == null || body.isBlank()) {
                        return Mono.<GuestEligibilityDTO>error(
                                new SPRuntimeError(
                                        "Guest application is not available",
                                        HttpStatus.FORBIDDEN,
                                        null
                                )
                        );
                    }

                    GuestEligibilityDTO eligibility =
                            (GuestEligibilityDTO) Utility.stringToEntity(
                                    body,
                                    GuestEligibilityDTO.class
                            );

                    return Mono.just(eligibility);
                })
                .onErrorResume(
                        WebClientResponseException.class,
                        ex -> {

                            if (ex.getStatusCode().value() == 403) {
                                return Mono.<GuestEligibilityDTO>error(
                                        new SPRuntimeError(
                                                "Guest application is not available",
                                                HttpStatus.FORBIDDEN,
                                                null
                                        )
                                );
                            }

                            return Mono.<GuestEligibilityDTO>error(
                                    new SPRuntimeError(
                                            "Unable to verify guest application eligibility",
                                            HttpStatus.BAD_GATEWAY,
                                            null
                                    )
                            );
                        }
                );
    }

    @SuppressWarnings("unchecked")
	public Mono<HandlerResponse> fetchFormData(String txnId, ServiceMeta service, String applicationId, UserSessionObject user) {
        String url = FORM_MANAGEMENT_SERVICE.concat("getByFormIdApplicant?");
        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        System.out.println(
        	    "fetchFormData parameters: txnId=" + txnId
        	    + ", serviceId=" + (service != null ? service.getServiceId() : null)
        	    + ", applicationId=" + applicationId
        	);
        
        Map<String, Object> params = new HashMap<>();

        params.put("txnId", txnId);
        params.put("formId", service.getFormId());
        params.put("taskId", service.getTaskId());
        params.put("serviceId", service.getServiceId());

        if (applicationId != null) {
            params.put("applId", applicationId);
        }
        
        
        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
											                String.class,
											                HttpMethod.GET,
                                                            headers,
                                                            params,
											                url,
											                null,
											                MediaType.APPLICATION_JSON);
        
       return callExternalEndpoint.flatMap(apiResponse -> {
                String body = apiResponse.getBody();
                if (body == null || body.isBlank()) {
                    return Mono.error(new SPRuntimeError("Unable to process your request", HttpStatus.FAILED_DEPENDENCY,txnId));
                }
                
                ObjectMapper mapper = new ObjectMapper();

                Map<String, Object> responseJson;
                try {
                    responseJson = mapper.readValue(body, new TypeReference<Map<String, Object>>() {});
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                    return Mono.error(new SPRuntimeError(
                        "Issue while processing the request [ERR - 002]", HttpStatus.INTERNAL_SERVER_ERROR,txnId));
                }
                HandlerResponse hr = new HandlerResponse();
                hr.setData(responseJson);
                hr.setTxnId(txnId);
                hr.setActivityType(ACTIVITY_FORM_STATUS_KEY);
                hr.setApplyLocations(service.getLocations());
                return Mono.just(hr);
            });
    }

	public Mono<ResponseEntity<String>> saveFormData(String txnId, ServiceMeta service, String appData, UserSessionObject user, String dataId,String applId,boolean draft) {
    	String url = FORM_MANAGEMENT_SERVICE.concat("addApplicationData");
        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
        dataId = isEmpty(dataId) ? "" : dataId;

        return AsynchronousApiExecutor.callExternalEndpoint(
											                String.class,
											                HttpMethod.POST,
                                                            headers,
											                Map.of(
                                                                    "txnId", txnId,
                                                                    "formId", service.getFormId(),
                                                                    "serviceId",service.getServiceId(),
                                                                    "taskId",service.getTaskId(),
                                                                    "dataId",dataId,
                                                                    "applId",applId,
                                                                    "draft", String.valueOf(draft)
                                                             ),
											                url,
											                appData,
											                MediaType.APPLICATION_JSON);
	}
    //CHECK CIRCUIT BREAKER AND ADD LOGS
	public Mono<ServiceReferenceDTO> fetchReferenceAbbrviation(
	        Integer serviceId,
	        UserSessionObject user,
	        String txnId) {

	    return fetchServiceMetadata(user, serviceId, txnId)
	            .flatMap(metadata -> {

	                String serviceAbbreviation = metadata.getServiceAbbrevation();
	                String serviceUrlHeader = metadata.getServiceUrlHeader();

	                if (serviceAbbreviation == null) {
	                    return Mono.error(new SPRuntimeError(
	                            "Unable to process your request [SUB - 007]",
	                            HttpStatus.FAILED_DEPENDENCY,
	                            txnId));
	                }

	                ServiceReferenceDTO response = new ServiceReferenceDTO();
	                response.setServiceAbbreviation(serviceAbbreviation);
	                response.setServiceUrlHeader(serviceUrlHeader);

	                return Mono.just(response);
	            });
	}

	public Mono<ServiceJSONDTO> fetchServiceMetadata(UserSessionObject user,Integer serviceId,String txnId) {

	    final String REDIS_KEY = "ServiceMetaData_" + serviceId;

	    return redis.fetch(
	                    REDIS_KEY,
	                    new TypeToken<ServiceJSONDTO>() {}.getType())
	            .cast(ServiceJSONDTO.class)
	            .switchIfEmpty(fetchAndCacheServiceMetadata(user, serviceId, txnId));
	}
	
	private Mono<ServiceJSONDTO> fetchAndCacheServiceMetadata(
	        UserSessionObject user,
	        Integer serviceId,
			String txnId) {

		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

		String url = METADATA_SERVICE.concat("apply/serviceMetaData?");

		Mono<ResponseEntity<String>> response = AsynchronousApiExecutor.callExternalEndpoint(String.class,
				HttpMethod.POST, headers, Map.of("serviceId", serviceId), url, "", MediaType.APPLICATION_JSON);

		return response.flatMap(apiResponse -> {
			ServiceJSONDTO metadata = (ServiceJSONDTO) stringToEntityUsingType(apiResponse.getBody(),
					new TypeToken<ServiceJSONDTO>() {
					}.getType());

            applicationFlowLogs.info("Cache miss for service metadata for serviceId {} txnId {} , response {}",serviceId,txnId,entityToString(metadata));

			redis.add(metadata, "ServiceMetaData_" + serviceId, true, 5*60).subscribe();

			return Mono.just(Objects.requireNonNull(metadata));

		}).onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex, txnId));
	}
	
	public Mono<ServiceMeta> fetchServiceKey(Integer baseServiceId, UserSessionObject user, String appId, String taskId,
			Integer serviceId) {

		return fetchServiceMetadata(user, serviceId, "FETCH_SERVICE_KEY").map(metadata -> {

			ServiceMeta service = new ServiceMeta();

			service.setServiceId(metadata.getServiceId());
			service.setServiceName(metadata.getServiceName());
			service.setDepartmentName(metadata.getDepartmentName());
			service.setSla(metadata.getSla());
			service.setSlaUnit(metadata.getSlaUnit());
			service.setBaseServiceId(baseServiceId);
			service.setPatternConfigurations(metadata.getPatternConfigurations());
			service.setNotificationDetails(metadata.getNotificationDetails());
			service.setDesignatedOfficerDetails(metadata.getDesignatedOfficerDetails());
			//service.setRegistrationRequired(metadata.getRegistrationRequired());
			

			if (taskId == null || taskId.isBlank()) {
				service.setTaskType(APPLICATION_SUBMISSION_TASK_FLAG);
				service.setTaskId(metadata.getApplSubmissionTaskId());
				service.setFormId(metadata.getApplFormId());
			} else {
				service.setTaskType(OFFICIAL_TASK_FLAG);
				service.setTaskId(taskId);
				service.setFormId(metadata.getTaskFormMapping().get(taskId));
			}

            List<WorkFlowDataDTO.WorkFlowAction> availableActions = metadata.getWorkFlowDetails()
                                                        .stream()
                                                        .filter(workflow ->
                                                                service.getTaskId().equals(workflow.getTaskId())
                                                        )
                                                        .filter(workflow ->
                                                                workflow.getAllowedAction() != null
                                                        )
                                                        .flatMap(workflow ->
                                                                workflow.getAllowedAction().stream()
                                                        )
                                                        .filter(Objects::nonNull)
                                                        .toList();

            service.setAvailableActions(availableActions);

			metadata.getActivityMap()
            .stream()
            .filter(a -> service.getTaskId().equals(a.getTaskId()))
            .findFirst()
            .ifPresent(activityMap -> {

                ActivityMapDTO taskActivity = new ActivityMapDTO();
                taskActivity.setData(activityMap.getData());

                service.setActivityMap(taskActivity);
                service.setServiceKey(encryptServiceKeys(service));
            });

            // Resolve Office Locations

            if(service.getTaskType().equals(APPLICATION_SUBMISSION_TASK_FLAG)) {

                metadata.getOfficeDetails()
                        .stream()
                        .filter(o -> service.getTaskId().equals(o.getTaskId()))
                        .findFirst()
                        .ifPresent(o -> {

                            if(o.getAllowedOffices() == null){
                                return;
                            }

                            List<ServiceMeta.AvailableApplyLocations> locations =
                                    o.getAllowedOffices()
                                            .stream()
                                            .map(office -> {
                                                ServiceMeta.AvailableApplyLocations location =
                                                        new ServiceMeta.AvailableApplyLocations();
                                                location.setOrgUnitCode(office.getOrgUnitCode() != null
                                                        ? office.getOrgUnitCode().longValue()
                                                        : null);
                                                location.setOrgUnitName(
                                                        office.getOrgUnitName());

                                                location.setHolderIds(new ArrayList<>());

                                                return location;

                                            })
                                            .collect(Collectors.toList());

                            service.setLocations(locations);
                        });
            }

			return service;
		});
	}
//    public Mono<ServiceMeta> fetchServiceKey(Integer baseServiceId, UserSessionObject user, String appId, String taskId, Integer serviceId) {
//        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
//        String url = METADATA_SERVICE.concat("apply/resolveForm?");
//        //CACHE AT THIS LEVEL ?????
//
//        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
//                                                                                String.class,
//                                                                                HttpMethod.POST,
//                                                                                headers,
//                                                                                Map.of("baseServiceId",baseServiceId,"taskId",taskId,"serviceId",serviceId),
//                                                                                url,
//                                                                                null,
//                                                                                MediaType.APPLICATION_JSON);
//
//
//        return callExternalEndpoint.flatMap(apiResponse -> {
//            String body = apiResponse.getBody();
//
//            Type listType = new TypeToken<ServiceMeta>() {}.getType();
//            ServiceMeta service = (ServiceMeta) stringToEntityUsingType(body, listType);
//            service.setServiceKey(encryptServiceKeys(service));
//            TaskActivity taskActivity = service.getActivityMap();
//            String key = SERVICE_ACTIVITY_REDIS_KEY_APPENDER.concat("_").concat(service.getServiceId().toString().concat("_").concat(service.getTaskId()));
//            redis.add(taskActivity,key,true,5).subscribe();
//            return Mono.just(service);
//            })
//            .onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex,"FROM FETCH SERVICE LIST"));
//
//    }

//    public Mono<ServiceWorkFlow> fetchProcessFlow(Integer baseServiceId, UserSessionObject user, String appId, String taskId, Integer serviceId,String txnId) {
//        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
//        String url = METADATA_SERVICE.concat("apply/processFlow?");
//
//        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
//                                                                String.class,
//                                                                HttpMethod.POST,
//                                                                headers,
//                                                                Map.of("serviceId",serviceId),
//                                                                url,
//                                                                null,
//                                                                MediaType.APPLICATION_JSON);
//
//
//        return callExternalEndpoint.flatMap(apiResponse -> {
//                    String body = apiResponse.getBody();
//
//                    Type listType = new TypeToken<ServiceWorkFlow>() {}.getType();
//                    ServiceWorkFlow workflow = (ServiceWorkFlow) stringToEntityUsingType(body, listType);
//                    final String REDIS_KEY =SERVICE_WORKFLOW_REDIS_KEY_APPENDER.concat("_").concat(baseServiceId.toString());
//                    redis.add(workflow,REDIS_KEY,true,5).subscribe();
//                    return Mono.just(workflow);
//                })
//                .onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex,txnId))
//                ;
//    }
	
	public Mono<ServiceJSONDTO> fetchProcessFlow(Integer baseServiceId, UserSessionObject user, String appId,
			String taskId, Integer serviceId, String txnId) {

		return fetchServiceMetadata(user, serviceId, txnId);
	}

    @SuppressWarnings("unchecked")
    public Mono<HandlerResponse> fetchApplicantData(String dataId, String formId,UserSessionObject user,String txnId,String applicationId) {
        String url = FORM_MANAGEMENT_SERVICE.concat("getApplicationData?");
        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("formId", formId);
        requestBody.put("dataId", dataId);
        requestBody.put("txnId", txnId);
        
        if(applicationId != null) {
        	requestBody.put("applId", applicationId);
        }
        
        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
                String.class,
                HttpMethod.POST,
                headers,
                Collections.emptyMap(),
                url,
                entityToString(requestBody),
                MediaType.APPLICATION_JSON);

        return callExternalEndpoint.flatMap(apiResponse -> {
            String body = apiResponse.getBody();
            if (body == null || body.isBlank()) {
                return Mono.error(new SPRuntimeError("Unable to process your request", HttpStatus.FAILED_DEPENDENCY,txnId));
            }

            ObjectMapper mapper = new ObjectMapper();

            Map<String, Object> responseJson;
            try {
                responseJson = mapper.readValue(body, new TypeReference<>() {
                });
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return Mono.error(new SPRuntimeError(
                        "Issue while processing the request [ERR - 002]", HttpStatus.INTERNAL_SERVER_ERROR,txnId));
            }

            Map<String, Object> formData = (Map<String, Object>) responseJson.get("formData");
            Map<String, Object> applicationData = (Map<String, Object>) responseJson.get("applicationData");
            Map<String, Object> actualResponse = new HashMap<>(formData);


            HandlerResponse hr = new HandlerResponse();
            responseJson.remove("applicationId");
            responseJson.remove("status");
            hr.setData(actualResponse);
            hr.setApplicationData(applicationData);
            hr.setTxnId(txnId);
            hr.setApplicationId(applicationId);
            return Mono.just(hr);
        });

    }
    public Mono<List<MvelDetailsDTO>> fetchMvelDetails(UserSessionObject user, Integer serviceId, String txnId) {
        return fetchServiceMetadata(user, serviceId, txnId).flatMap(metadata -> {
            List<MvelDetailsDTO> list =metadata.getMvelDetailsDTO();
            return Mono.just(list);
        });
    }
    public Mono<MvelExecutionResponse> executeMvel(
            Long mvelId,
            String txnId,
            String activityId,
            String triggerPoint,
            String appId,
            Integer serviceId,
            String currentProcessId,
            String formData,
            Map<String, Object> appDetails,
            Map<String, Object> serviceDetails,
            Map<String, Map<String,List<String>>> taskLocationUserHolderMap,
            List<String> nextNodeList
    ) {

        String url = MVEL_EXECUTION_SERVICE.concat("execute");

        Map<String, String> headers = new HashMap<>();
        MvelExecutionRequest request = new MvelExecutionRequest();
        request.setFunctionId(mvelId);
        request.setTxnId(txnId);
        request.setActivityId(activityId);
        request.setTriggerPoint(triggerPoint);
        request.setApplicationId(appId);
        request.setServiceId(serviceId);
        request.setCurrentProcessId(currentProcessId);
        request.setFormData(formData);
        request.setApplicationDetails(appDetails);
        request.setServiceDetails(serviceDetails);
        request.setTaskLocationUserHolderMap(taskLocationUserHolderMap);
        request.setNextNodeList(nextNodeList);
        

        return AsynchronousApiExecutor.callExternalEndpoint(
                MvelExecutionResponse.class,
                HttpMethod.POST,
                headers,
                Map.of(),
                url,
                entityToString(request),
                MediaType.APPLICATION_JSON
        )
        .map(ResponseEntity::getBody)
        .flatMap(body -> {
            try {
                MvelExecutionResponse res =
                		mapper.readValue(body, MvelExecutionResponse.class);

                if (res == null) {
                    return Mono.error(new RuntimeException("MVEL response is null"));
                }

                return Mono.just(res);

            } catch (Exception e) {
                return Mono.error(new RuntimeException("Failed to parse MVEL response", e));
            }
        })
        .onErrorResume(ex -> {
            MvelExecutionResponse errorRes = new MvelExecutionResponse();
            errorRes.setSuccess(false);
            errorRes.setError(ex.getMessage());
            return Mono.just(errorRes);
        });
    }
    
	public Mono<List<WorkflowAssignmentDTO>> fetchWorkflowAssignments(Integer serviceId, String taskId,
			String locationId, UserSessionObject user, String txnId) {

		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

		String url = METADATA_SERVICE.concat("workflow/assignment-details?");

		Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(String.class,
				HttpMethod.GET, headers, Map.of("serviceId", serviceId, "taskId", taskId, "locationId", locationId),
				url, null, MediaType.APPLICATION_JSON);

		return callExternalEndpoint.flatMap(apiResponse -> {

			String body = apiResponse.getBody();

			Type listType = new TypeToken<List<WorkflowAssignmentDTO>>() {
			}.getType();

			List<WorkflowAssignmentDTO> assignments = (List<WorkflowAssignmentDTO>) stringToEntityUsingType(body,
					listType);

			return Mono.just(assignments);

		}).onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex, txnId));
	}
	
	@SuppressWarnings("unchecked")
    public Mono<ServerSidePaginationRecord<WorkflowInboxResponse>> fetchWFPInbox(ServerHttpRequest request, UserSessionObject user) {

        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        String url = TRACKING_SERVICE.concat("/a/workflow/inbox/list");

        Map<String, Object> params = new HashMap<>();

        request.getQueryParams().forEach((key, value) -> {
            params.put(key, value.size() == 1 ? value.getFirst() : value);
        });

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
                                                                                    String.class,
                                                                                    HttpMethod.GET,
                                                                                    headers,
                                                                                    params,
                                                                                    url,
                                                                                    null,
                                                                                    MediaType.APPLICATION_JSON
                                                                            );

        return callExternalEndpoint.flatMap(apiResponse -> {

            String body = apiResponse.getBody();

            if (body == null || body.isBlank()) {
                return Mono.error(new SPRuntimeError("Unable to fetch inbox", HttpStatus.FAILED_DEPENDENCY, null));
            }

            Type listType = new TypeToken<ServerSidePaginationRecord<WorkflowInboxResponse>>() {}.getType();

            ServerSidePaginationRecord<WorkflowInboxResponse> inboxList = (ServerSidePaginationRecord<WorkflowInboxResponse>) stringToEntityUsingType(body, listType);
            return Mono.just(inboxList);
        });
    }
	//----------------------------------------------------------------------------------
	@SuppressWarnings("unchecked")
	public Mono<ServerSidePaginationRecord<WorkflowInboxResponse>> fetchSlaApplications(
	        ServerHttpRequest request, UserSessionObject user) {

	    Map<String, String> headers =
	            Map.of("USER-DETAILS", entityToString(user));

	    String url = TRACKING_SERVICE.concat("/a/sla/applications");

	    Map<String, Object> params = new HashMap<>();

	    request.getQueryParams().forEach((key, value) -> {
	        params.put(key, value.size() == 1 ? value.getFirst() : value);
	    });

	    Mono<ResponseEntity<String>> callExternalEndpoint =
	            AsynchronousApiExecutor.callExternalEndpoint(
	                    String.class,
	                    HttpMethod.GET,
	                    headers,
	                    params,
	                    url,
	                    null,
	                    MediaType.APPLICATION_JSON
	            );

	    return callExternalEndpoint.flatMap(apiResponse -> {

	        String body = apiResponse.getBody();

	        if (body == null || body.isBlank()) {
	            return Mono.error(new SPRuntimeError(
	                    "Unable to fetch SLA applications",
	                    HttpStatus.FAILED_DEPENDENCY,
	                    null
	            ));
	        }

	        Type listType = new TypeToken<
	                ServerSidePaginationRecord<WorkflowInboxResponse>>() {}.getType();

	        ServerSidePaginationRecord<WorkflowInboxResponse> inboxList =
	                (ServerSidePaginationRecord<WorkflowInboxResponse>)
	                        stringToEntityUsingType(body, listType);

	        return Mono.just(inboxList);
	    });
	}
	

    @SuppressWarnings("unchecked")
    public Mono<ServerSidePaginationRecord<WorkflowInboxResponse>> fetchWFPOutbox(ServerHttpRequest request, UserSessionObject user) {

        Map<String, String> headers = user != null ? Map.of("USER-DETAILS", entityToString(user)) : Collections.emptyMap();

        String url = TRACKING_SERVICE.concat("a/workflow/outbox/list");

        Map<String, Object> params = new HashMap<>();

        request.getQueryParams().forEach((key, value) -> {
            params.put(key, value.size() == 1 ? value.getFirst() : value);
        });

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
                                                                                    String.class,
                                                                                    HttpMethod.GET,
                                                                                    headers,
                                                                                    params,
                                                                                    url,
                                                                                    null,
                                                                                    MediaType.APPLICATION_JSON
                                                                            );

        return callExternalEndpoint.flatMap(apiResponse -> {

            String body = apiResponse.getBody();

            if (body == null || body.isBlank()) {
                return Mono.error(new SPRuntimeError("Unable to fetch outbox", HttpStatus.FAILED_DEPENDENCY, null));
            }

            Type listType = new TypeToken<ServerSidePaginationRecord<WorkflowInboxResponse>>() {}.getType();

            ServerSidePaginationRecord<WorkflowInboxResponse> outboxList = (ServerSidePaginationRecord<WorkflowInboxResponse>) stringToEntityUsingType(body, listType);
            return Mono.just(outboxList);
        });
    }

    @SuppressWarnings("unchecked")
    public Mono<ServerSidePaginationRecord<WorkflowInboxResponse>> fetchApplicantInbox(
            String requestBody,
            ServerHttpRequest request,
            UserSessionObject user) {

        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        String url = TRACKING_SERVICE.concat("/a/appl/applicant/inbox");

        Mono<ResponseEntity<String>> callExternalEndpoint =
                AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.POST,
                        headers,
                        Collections.emptyMap(),
                        url,
                        requestBody,
                        MediaType.APPLICATION_JSON
                );

        return callExternalEndpoint.flatMap(apiResponse -> {

            String body = apiResponse.getBody();

            if (body == null || body.isBlank()) {
                return Mono.error(new SPRuntimeError("Unable to fetch applicant inbox", HttpStatus.FAILED_DEPENDENCY, null));
            }

            Type responseType = new TypeToken<ServerSidePaginationRecord<ApplicationApplicantTrackingResponse.ApplicantApplicationDTO>>() {
            }.getType();

            ServerSidePaginationRecord<ApplicationApplicantTrackingResponse.ApplicantApplicationDTO> applicantInbox =
                    (ServerSidePaginationRecord<ApplicationApplicantTrackingResponse.ApplicantApplicationDTO>) stringToEntityUsingType(body, responseType);

            ServerSidePaginationRecord<WorkflowInboxResponse> response = new ServerSidePaginationRecord<>();

            List<WorkflowInboxResponse> data = applicantInbox.getData().stream().map(this::convertApplicantInbox).toList();

            response.setData(data);
            response.setTotalRecords(applicantInbox.getTotalRecords());
            response.setCurrentPage(applicantInbox.getCurrentPage());
            response.setPageSize(applicantInbox.getPageSize());
            response.setTotalPages(applicantInbox.getTotalPages());

            return Mono.just(response);

        });
    }

    private WorkflowInboxResponse convertApplicantInbox(ApplicationApplicantTrackingResponse.ApplicantApplicationDTO applicant) {

        WorkflowInboxResponse response = new WorkflowInboxResponse();

        response.setApplId(applicant.getApplId());
        response.setApplRefNo(applicant.getApplRefNo());
        response.setServiceId(applicant.getServiceId());
        response.setServiceName(applicant.getServiceName());
        response.setReceivedDate(applicant.getReceivedDate());
        response.setTaskName(applicant.getTaskName());
        response.setTaskId(applicant.getTaskId());
        response.setFormId(applicant.getFormId());

        return response;
    }

	@SuppressWarnings("unchecked")
	public Mono<ServerSidePaginationRecord<WorkflowInboxResponse>> getWFPInboxFilterApplications(
			ServerHttpRequest request, UserSessionObject user, InboxApplReqDTO inboxApplReqDTO) {

		applicationFlowLogs.info("getWFPInboxFilterApplications called | serviceId={}, taskId={}, filters={}",
				inboxApplReqDTO.getServiceId(), inboxApplReqDTO.getTaskId(), inboxApplReqDTO.getFilters());

		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

		String url = TRACKING_SERVICE.concat("/a/workflow/inbox/filter/applications");

		Map<String, Object> params = new HashMap<>();
		request.getQueryParams().forEach((key, value) -> params.put(key, value.size() == 1 ? value.getFirst() : value));

		applicationFlowLogs.info("Query params for tracking service call | url={}, params={}", url, params);

		String requestBody;
		try {
			requestBody = mapper.writeValueAsString(inboxApplReqDTO);
		} catch (JsonProcessingException e) {
			applicationFlowLogs.error("Failed to serialize InboxApplReqDTO to JSON | serviceId={}, taskId={}",
					inboxApplReqDTO.getServiceId(), inboxApplReqDTO.getTaskId(), e);
			return Mono.error(new SPRuntimeError("Invalid request payload", HttpStatus.BAD_REQUEST, null));
		}

		applicationFlowLogs.info("Serialized request body for tracking service | body={}", requestBody);

		Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(String.class,
				HttpMethod.POST, headers, params, url, requestBody, MediaType.APPLICATION_JSON);

		return callExternalEndpoint.flatMap(apiResponse -> {
			String body = apiResponse.getBody();

			applicationFlowLogs.info("Tracking service responded | status={}", apiResponse.getStatusCode());

			if (body == null || body.isBlank()) {
				applicationFlowLogs.warn("Empty/blank response body from tracking service | url={}, status={}", url,
						apiResponse.getStatusCode());
				return Mono.error(new SPRuntimeError("Unable to fetch inbox", HttpStatus.FAILED_DEPENDENCY, null));
			}

			Type listType = new TypeToken<ServerSidePaginationRecord<WorkflowInboxResponse>>() {
			}.getType();
			ServerSidePaginationRecord<WorkflowInboxResponse> inboxList = (ServerSidePaginationRecord<WorkflowInboxResponse>) stringToEntityUsingType(
					body, listType);

			applicationFlowLogs.info("Successfully parsed inbox filter response | recordCount={}, totalRecords={}",
					inboxList.getData() != null ? inboxList.getData().size() : 0, inboxList.getTotalRecords());

			return Mono.just(inboxList);
		}).doOnError(err -> applicationFlowLogs
				.error("Error occurred while calling tracking service inbox filter endpoint | url={}", url, err));
	}
	@SuppressWarnings("unchecked")
	public Mono<ServerSidePaginationRecord<WorkflowInboxResponse>> getOutboxApplications(ServerHttpRequest request, UserSessionObject user) {

		Map<String, String> headers = user != null ? Map.of("USER-DETAILS", entityToString(user)) : Collections.emptyMap();

		String base = TRACKING_SERVICE.endsWith("/") ? TRACKING_SERVICE : TRACKING_SERVICE.concat("/");
		String url = base.concat("a/outbox/applications");
		Map<String, Object> params = new HashMap<>();

		request.getQueryParams().forEach((key, value) -> {
			params.put(key, value.size() == 1 ? value.getFirst() : value);
		});

		Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(String.class,
				HttpMethod.GET, headers, params, url, null, MediaType.APPLICATION_JSON);

		return callExternalEndpoint.flatMap(apiResponse -> {

			String body = apiResponse.getBody();

			if (body == null || body.isBlank()) {
				return Mono.error(new SPRuntimeError("Unable to fetch outbox applications", HttpStatus.FAILED_DEPENDENCY, null));
			}

			Type listType = new TypeToken<ServerSidePaginationRecord<WorkflowInboxResponse>>() {
			}.getType();
			ServerSidePaginationRecord<WorkflowInboxResponse> outboxList = (ServerSidePaginationRecord<WorkflowInboxResponse>) stringToEntityUsingType(
					body, listType);

			return Mono.just(outboxList);
		});
	}

	public Mono<ServerSidePaginationRecord<WorkflowInboxResponse>> getInboxApplications(ServerHttpRequest request, UserSessionObject user) {

		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

		String url = TRACKING_SERVICE.concat("a/inbox/applications");
		Map<String, Object> params = new HashMap<>();

		request.getQueryParams().forEach((key, value) -> {
			params.put(key, value.size() == 1 ? value.getFirst() : value);
		});

		Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(String.class,
				HttpMethod.GET, headers, params, url, null, MediaType.APPLICATION_JSON);

		return callExternalEndpoint.flatMap(apiResponse -> {

			String body = apiResponse.getBody();

			if (body == null || body.isBlank()) {
				return Mono.error(new SPRuntimeError("Unable to fetch inbox", HttpStatus.FAILED_DEPENDENCY, null));
			}
			Type listType = new TypeToken<ServerSidePaginationRecord<WorkflowInboxResponse>>() {}.getType();
			ServerSidePaginationRecord<WorkflowInboxResponse> inboxList = (ServerSidePaginationRecord<WorkflowInboxResponse>) stringToEntityUsingType(body, listType);
            return Mono.just(inboxList);
		});
	}

    public Mono<Boolean> attachTrackingDocument(UserSessionObject user, TrackingDocumentAttachRequest request) {
        String base = TRACKING_SERVICE.endsWith("/") ? TRACKING_SERVICE : TRACKING_SERVICE.concat("/");
        String url = base.concat("a/application/document/attach");
        Map<String, String> headers = user != null ? Map.of("USER-DETAILS", entityToString(user)) : Collections.emptyMap();

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
                String.class,
                HttpMethod.POST,
                headers,
                Collections.emptyMap(),
                url,
                entityToString(request),
                MediaType.APPLICATION_JSON
        );

        return callExternalEndpoint
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .onErrorResume(e -> {
                    applicationFlowLogs.warn("Error calling tracking service attach document endpoint | url={}, error={}", url, e.getMessage());
                    return Mono.just(false);
                });
    }

    public Mono<FetchTaskHolders> fetchTaskHolders(Integer serviceId,
                                                   String taskId,
                                                   UserSessionObject user,
                                                   String txnId) {

        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        String url = METADATA_SERVICE.concat("workflow/fetchTaskHolders?");

        Mono<ResponseEntity<String>> callExternalEndpoint =
                AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.POST,
                        headers,
                        Map.of("serviceId", serviceId, "taskId", taskId),
                        url,
                        null,
                        MediaType.APPLICATION_JSON);

        return callExternalEndpoint.flatMap(apiResponse -> {

            String body = apiResponse.getBody();

            FetchTaskHolders response = (FetchTaskHolders) stringToEntityUsingType(body, new TypeToken<FetchTaskHolders>() {}.getType());

            return Mono.just(response);

        }).onErrorResume(WebClientResponseException.class,
                ex -> handleWebClientError(ex, txnId));
    }

    public Mono<EscalationMvelResponse> executeEscalationMvel(
            EscalationMvelRequest request) {
        String url = MVEL_EXECUTION_SERVICE.concat("execute-escalation");
        Map<String, String> headers = new HashMap<>();
        return AsynchronousApiExecutor.callExternalEndpoint(
                EscalationMvelResponse.class,
                HttpMethod.POST,
                headers,
                Map.of(),
                url,
                entityToString(request),
                MediaType.APPLICATION_JSON
        )
        .map(ResponseEntity::getBody)
        .flatMap(body -> {
            try {
                EscalationMvelResponse response =mapper.readValue(body, EscalationMvelResponse.class);
                if (response == null) {
                    return Mono.error(new RuntimeException("Escalation MVEL response is null"));
                }
                return Mono.just(response);
            } catch (Exception e) {
                return Mono.error(
                        new RuntimeException("Failed to parse Escalation MVEL response", e));
            }
        })
        .onErrorResume(ex -> {
            EscalationMvelResponse errorResponse = new EscalationMvelResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError(ex.getMessage());
            return Mono.just(errorResponse);
        });
    }

    public Mono<Map<String, Object>> fetchWorkflowAttributes(UserSessionObject user, String formId, String dataId,String txnId) {

        String url = FORM_MANAGEMENT_SERVICE.concat("b/workflow/attributes");

        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
        Map<String, Object> request = Map.of("formId", formId, "dataId", dataId);

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
                                                                    String.class,
                                                                    HttpMethod.POST,
                                                                    headers,
                                                                    Collections.emptyMap(),
                                                                    url,
                                                                    entityToString(request),
                                                                    MediaType.APPLICATION_JSON);

        return callExternalEndpoint.flatMap(apiResponse -> {

            String body = apiResponse.getBody();

            if (body == null || body.isBlank()) {
                return Mono.error(new SPRuntimeError("Unable to fetch workflow attributes", HttpStatus.FAILED_DEPENDENCY, txnId));
            }

            try {

                Map<String, Object> response = mapper.readValue(body, new TypeReference<Map<String, Object>>() {
                });

                return Mono.just(response);

            } catch (JsonProcessingException e) {

                applicationFlowLogs.error("Failed to parse workflow attributes response", e);
                return Mono.error(new SPRuntimeError("Issue while processing workflow attributes response", HttpStatus.INTERNAL_SERVER_ERROR,txnId));
            }
        });
    }

    @SuppressWarnings("unchecked")
    public Mono<GenerateDocDResDTO> generateDocument(UserSessionObject user,
                                                     Integer serviceId,
                                                     Integer outputFormatId,
                                                     String applicationId,
                                                     boolean async,
                                                     String txnId, Map<String, Object> systemAttrMap) {

        String url = DOCUMENT_GENERATION_SERVICE.concat("doc/generate");

        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        Map<String, Object> request = new HashMap<>();
        request.put("serviceId", serviceId);
        request.put("outputFormatId", outputFormatId);
        request.put("applicationIdList", List.of(applicationId));
        request.put("async", async);
        request.put("applicationDetails", systemAttrMap);

        return AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.POST,
                        headers,
                        Collections.emptyMap(),
                        url,
                        entityToString(request),
                        MediaType.APPLICATION_JSON)
                .flatMap(response -> {

                    List<GenerateDocDResDTO> result =
                            (List<GenerateDocDResDTO>) stringToEntityUsingType(response.getBody(), new TypeToken<List<GenerateDocDResDTO>>() {
                            }.getType());

                    if (result == null || result.isEmpty()) {
                        return Mono.error(new SPRuntimeError("Document generation failed.", HttpStatus.INTERNAL_SERVER_ERROR, txnId));
                    }

                    return Mono.just(result.getFirst());
                });
    }
    
    @SuppressWarnings("unchecked")
    public Mono<GenerateDocDResDTO> generateApplicationFormDocument(UserSessionObject user,
                                                                    Integer serviceId,
                                                                    String applicationId,
                                                                    String txnId,
                                                                    Map<String, Object> systemAttrMap,
                                                                    Map<String, Object> formData) {

        String url = DOCUMENT_GENERATION_SERVICE.concat("doc/generate");

        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        Map<String, Object> request = new HashMap<>();
        request.put("serviceId", serviceId);
        request.put("outputFormatId", 0L);
        request.put("isApplicationForm", true);
        request.put("applicationIdList", List.of(applicationId));
        request.put("async", false);
        request.put("applicationDetails", systemAttrMap != null ? systemAttrMap : Collections.emptyMap());
        if (formData != null) {
            request.put("formData", formData);
        }

        return AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.POST,
                        headers,
                        Collections.emptyMap(),
                        url,
                        entityToString(request),
                        MediaType.APPLICATION_JSON)
                .flatMap(response -> {
                    List<GenerateDocDResDTO> result =
                            (List<GenerateDocDResDTO>) stringToEntityUsingType(response.getBody(), new TypeToken<List<GenerateDocDResDTO>>() {
                            }.getType());

                    if (result == null || result.isEmpty()) {
                        return Mono.error(new SPRuntimeError("Application form document generation failed.", HttpStatus.INTERNAL_SERVER_ERROR, txnId));
                    }

                    return Mono.just(result.getFirst());
                });
    }

    public Mono<FileViewResponse> getFileView(UserSessionObject user,
                                              List<String> uploadIds,
                                              String txnId) {

        String url = FILE_MANAGEMENT_SERVICE.concat("b/view/batch");

        Map<String, String> headers = Map.of(
                "USER-DETAILS", entityToString(user)
        );

        CommitRequest request = new CommitRequest();
        request.setUploadIds(uploadIds);

        return AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.POST,
                        headers,
                        Collections.emptyMap(),
                        url,
                        entityToString(request),
                        MediaType.APPLICATION_JSON)
                .flatMap(response -> {

                    FileViewResponse result =  (FileViewResponse) stringToEntityUsingType(
                                                        response.getBody(),
                                                        new TypeToken<FileViewResponse>() {}.getType());

                    return Mono.just(Objects.requireNonNull(result));
                });
    }

    public Mono<CreateUploadSessionsResponse> createUploadSession(UserSessionObject user, CreateUploadSessionsRequest request, String txnId) {

        String url = FILE_MANAGEMENT_SERVICE.concat("b/upload-sessions");

        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        return AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.POST,
                        headers,
                        Collections.emptyMap(),
                        url,
                        entityToString(request),
                        MediaType.APPLICATION_JSON)
                .flatMap(response -> {

                    CreateUploadSessionsResponse result =
                            (CreateUploadSessionsResponse) stringToEntityUsingType(
                                    response.getBody(),
                                    new TypeToken<CreateUploadSessionsResponse>() {}.getType());

                    return Mono.just(Objects.requireNonNull(result));
                });
    }

    public Mono<byte[]> downloadFromPresignedUrl(String presignedUrl) {

        applicationFlowLogs.info(
                "Downloading presigned URL: {}",
                presignedUrl
        );

        WebClient webClient = WebClient.builder()
                .codecs(configurer ->
                        configurer.defaultCodecs()
                                .maxInMemorySize(50 * 1024 * 1024) // 50 MB
                )
                .build();

        return webClient
                .get()
                .uri(URI.create(presignedUrl))
                .exchangeToMono(response -> {

                    applicationFlowLogs.info(
                            "Presigned download response: {}",
                            response.statusCode()
                    );

                    if (response.statusCode().is2xxSuccessful()) {

                        return response.bodyToMono(byte[].class)
                                .doOnNext(bytes ->
                                        applicationFlowLogs.info(
                                                "Presigned download successful. Size: {} bytes",
                                                bytes.length
                                        )
                                );
                    }

                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> {

                                applicationFlowLogs.error(
                                        "Presigned download failed. Status: {} Body: {}",
                                        response.statusCode(),
                                        body
                                );

                                return Mono.error(
                                        new RuntimeException(
                                                "Presigned URL download failed: "
                                                        + response.statusCode()
                                                        + " "
                                                        + body
                                        )
                                );
                            });
                });
    }
    
    public Mono<ServerResponse> sendNotification(String applicationId, Integer serviceId, Long activityConfigId,
			String txnId, Map<String, Object> systemAttrMap, ServerRequest request) {

		String url = NOTIFICATION_SERVICE.concat("/trigger");

		Map<String, String> headers = Map.of("USER-DETAILS", request.headers().firstHeader("USER-DETAILS"));

		Map<String, Object> requestBody = new HashMap<>();
		requestBody.put("applicationId", applicationId);
		requestBody.put("serviceId", serviceId);
		requestBody.put("notificationId", activityConfigId);
		requestBody.put("applicationDetails", systemAttrMap);
		return AsynchronousApiExecutor.callExternalEndpoint(String.class, HttpMethod.POST, headers,
				Collections.emptyMap(), url, entityToString(requestBody), MediaType.APPLICATION_JSON)
				.flatMap(response -> {

					if (response.getBody() == null || response.getBody().isBlank()) {

						return Mono.error(new SPRuntimeError("Notification generation failed.",
								HttpStatus.FAILED_DEPENDENCY, txnId));
					}

					return ServerResponse.status(response.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
							.bodyValue(response.getBody());
				}).onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex, txnId));
	}

    public Mono<UploadStatusResponse> uploadBase64File(
            UserSessionObject user,
            String uploadId,
            String uploadToken,
            byte[] fileBytes,
            String fileName,
            String txnId) {

        String url = FILE_MANAGEMENT_SERVICE.concat("b/upload/base64");

        Map<String, String> headers = new HashMap<>();
        headers.put("USER-DETAILS", entityToString(user));
        headers.put("X-Upload-Id", uploadId);
        headers.put("Authorization", "Bearer ".concat(uploadToken));
        headers.put("x-file-name", fileName);

        Base64UploadRequest request = new Base64UploadRequest();
        request.setBase64(Base64.getEncoder().encodeToString(fileBytes));
        request.setFileName(fileName);

        return AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.POST,
                        headers,
                        Collections.emptyMap(),
                        url,
                        entityToString(request),
                        MediaType.APPLICATION_JSON
                )
                .flatMap(response -> {

                    String body = response.getBody();

                    if (body == null || body.isBlank()) {
                        return Mono.error(new SPRuntimeError("File upload failed.", HttpStatus.FAILED_DEPENDENCY, txnId));
                    }

                    UploadStatusResponse result = (UploadStatusResponse) stringToEntityUsingType(body, new TypeToken<UploadStatusResponse>() {}.getType());

                    return Mono.just(Objects.requireNonNull(result));
                })
                .onErrorResume(
                        WebClientResponseException.class,ex -> handleWebClientError(ex, txnId)
                );
    }
    
	public Mono<DSCSignResponse> signDocument(DSCSignRequest request, UserSessionObject user) {

		String url = FILE_MANAGEMENT_SERVICE.concat("a/dsc/sign");
		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

		return AsynchronousApiExecutor
				.callExternalEndpoint(String.class, HttpMethod.POST, headers, Collections.emptyMap(), url,
						entityToString(request), MediaType.APPLICATION_JSON)

				.flatMap(apiResponse -> {
					if (apiResponse == null || apiResponse.getBody() == null || apiResponse.getBody().isBlank()) {
						return Mono.error(new SPRuntimeError("Unable to process DSC signing request",
								HttpStatus.FAILED_DEPENDENCY, null));
					}

					try {
						DSCSignResponse response = (DSCSignResponse) stringToEntityUsingType(apiResponse.getBody(),
								new TypeToken<DSCSignResponse>() {
								}.getType());

						return Mono.just(response);

					} catch (Exception ex) {
						ex.printStackTrace();
						return Mono.error(new SPRuntimeError("Invalid response from File Management",
								HttpStatus.BAD_GATEWAY, null));
					}
				});
	}
	
	public Mono<EsignInitiateResponse> initiateEsign(EsignInitiateRequest request, UserSessionObject user) {
		String baseUrl = (PLUGINS_SERVICE != null && !PLUGINS_SERVICE.isBlank()) ? PLUGINS_SERVICE : "http://INTEGRATED-PLUGINS/plugins/";
		if (!baseUrl.endsWith("/")) {
			baseUrl = baseUrl.concat("/");
		}
		String url = baseUrl.concat("esign/initiate");
		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

		return AsynchronousApiExecutor
				.callExternalEndpoint(String.class, HttpMethod.POST, headers, Collections.emptyMap(), url,
						entityToString(request), MediaType.APPLICATION_JSON)
				.flatMap(apiResponse -> {
					if (apiResponse == null || apiResponse.getBody() == null || apiResponse.getBody().isBlank()) {
						return Mono.error(new SPRuntimeError("Unable to process eSign initiation request",
								HttpStatus.FAILED_DEPENDENCY, null));
					}
					try {
						EsignInitiateResponse response = (EsignInitiateResponse) stringToEntityUsingType(
								apiResponse.getBody(),
								new TypeToken<EsignInitiateResponse>() {
								}.getType()
						);
						return Mono.just(response);
					} catch (Exception ex) {
						ex.printStackTrace();
						return Mono.error(new SPRuntimeError("Invalid response from Integrated Plugins eSign",
								HttpStatus.BAD_GATEWAY, null));
					}
				});
	}

	public Mono<EsignProcessResponseResult> processEsignResponse(EsignCompleteRequest request, UserSessionObject user) {
		String baseUrl = (PLUGINS_SERVICE != null && !PLUGINS_SERVICE.isBlank()) ? PLUGINS_SERVICE : "http://INTEGRATED-PLUGINS/plugins/";
		if (!baseUrl.endsWith("/")) {
			baseUrl = baseUrl.concat("/");
		}
		String url = baseUrl.concat("esign/process-response");
		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

		return AsynchronousApiExecutor
				.callExternalEndpoint(String.class, HttpMethod.POST, headers, Collections.emptyMap(), url,
						entityToString(request), MediaType.APPLICATION_JSON)
				.flatMap(apiResponse -> {
					if (apiResponse == null || apiResponse.getBody() == null || apiResponse.getBody().isBlank()) {
						return Mono.error(new SPRuntimeError("Unable to process eSign completion request",
								HttpStatus.FAILED_DEPENDENCY, null));
					}
					try {
						EsignProcessResponseResult response = (EsignProcessResponseResult) stringToEntityUsingType(
								apiResponse.getBody(),
								new TypeToken<EsignProcessResponseResult>() {
								}.getType()
						);
						return Mono.just(response);
					} catch (Exception ex) {
						ex.printStackTrace();
						return Mono.error(new SPRuntimeError("Invalid response from Integrated Plugins eSign completion",
								HttpStatus.BAD_GATEWAY, null));
					}
				});
	}
	
	public Mono<Set<String>> getRelatedLocationIds(Integer sourceLevelCode, Long sourceUnitCode, Integer destLevelCode,
			UserSessionObject user) {

		String url = SPGD_SERVICE_URL.concat("/spgd/hierarchy/related-units");
		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
		Map<String, Object> params = new HashMap<>();
		params.put("sourceLevelCode", sourceLevelCode);
		params.put("sourceUnitCode", sourceUnitCode);
		params.put("destLevelCode", destLevelCode);

		return AsynchronousApiExecutor.callExternalEndpoint(String.class, HttpMethod.GET, headers, params, url, null,
				MediaType.APPLICATION_JSON).flatMap(apiResponse -> {

					if (apiResponse == null || apiResponse.getBody() == null || apiResponse.getBody().isBlank()) {

						return Mono.error(new SPRuntimeError("Unable to fetch related locations from SPGD",
								HttpStatus.FAILED_DEPENDENCY, null));
					}

					try {

						UnitDTO[] units = (UnitDTO[]) stringToEntityUsingType(apiResponse.getBody(),
								new TypeToken<UnitDTO[]>() {
								}.getType());
						Set<String> locationIds=new HashSet<>();
						Set<String> childLocationIds = Arrays.stream(units).map(UnitDTO::getChildEntityUnitCode)
								.filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toSet());
						Set<String> parentLocationIds = Arrays.stream(units).map(UnitDTO::getParentEntityUnitCode)
								.filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toSet());
						
						locationIds.addAll(childLocationIds);
						locationIds.addAll(parentLocationIds);
						applicationFlowLogs.info("SPGD related locations response location ids: {} ", locationIds.toArray());

						return Mono.just(locationIds);

					} catch (Exception ex) {

						applicationFlowLogs.error("Failed to parse SPGD related locations response", ex);

						return Mono
								.error(new SPRuntimeError("Invalid response from SPGD", HttpStatus.BAD_GATEWAY, null));
					}
				});
	}
	
	public Mono<Map<String, Object>> fetchApplicationAttributeValues(String applicationId, String taskId,
			String holderId, List<String> attributeIds, UserSessionObject user) {

		String url = FORM_MANAGEMENT_SERVICE.concat("getApplicationAttributeValue");

		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

		Map<String, Object> requestBody = Map.of("applicationId", applicationId, "taskId", taskId, "holderId", holderId,
				"attributeIds", attributeIds);

		Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(String.class,
				HttpMethod.POST, headers, Collections.emptyMap(), url, entityToString(requestBody),
				MediaType.APPLICATION_JSON);

		return callExternalEndpoint.flatMap(apiResponse -> {

			String body = apiResponse.getBody();

			if (body == null || body.isBlank()) {
				return Mono.error(
						new SPRuntimeError("Unable to process your request", HttpStatus.FAILED_DEPENDENCY, applicationId));
			}

			try {
				Map<String, Object> responseJson = new ObjectMapper().readValue(body,
						new TypeReference<Map<String, Object>>() {
						});

				Map<String, Object> attributeValues = (Map<String, Object>) responseJson.get("attributeValues");

				return Mono.just(attributeValues != null ? attributeValues : Collections.emptyMap());

			} catch (JsonProcessingException e) {

				e.printStackTrace();

				return Mono.error(new SPRuntimeError("Issue while processing the request [ERR - 002]",
						HttpStatus.INTERNAL_SERVER_ERROR, applicationId));
			}
		});
	}

    public Mono<String> getFileBase64(UserSessionObject user, String uploadId) {

        String url = FILE_MANAGEMENT_SERVICE.concat("b/").concat(uploadId).concat("/base64");

        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        return AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.GET,
                        headers,
                        Collections.emptyMap(),
                        url,
                        null,
                        MediaType.APPLICATION_JSON
                )
                .flatMap(response -> {

                    Base64FileResponse result = (Base64FileResponse) stringToEntityUsingType(response.getBody(), new TypeToken<Base64FileResponse>() {
                    }.getType());

                    return Mono.just(result.getBase64());
                });
    }

    public Mono<PaymentInitiateResponse> initiatePayment(
            PaymentRequest request,
            UserSessionObject user,
            String txnId) {

        String url = PAYMENT_SERVICE.concat("/b/initiate");

        Map<String, String> headers =
                Map.of(
                        "USER-DETAILS",
                        entityToString(user)
                );

        applicationFlowLogs.info(
                "Initiating payment. transactionId={}, gatewayCode={}, applicationId={}",
                request != null ? request.getTransactionId() : null,
                request != null ? request.getGatewayCode() : null,
                request != null ? request.getApplicationId() : null
        );

        return AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.POST,
                        headers,
                        Collections.emptyMap(),
                        url,
                        entityToString(request),
                        MediaType.APPLICATION_JSON
                )
                .flatMap(response -> {

                    String body = response.getBody();

                    if (body == null || body.isBlank()) {
                        return Mono.error(new SPRuntimeError("Empty response from Payment Service", HttpStatus.FAILED_DEPENDENCY, txnId));
                    }

                    try {

                        PaymentResponse paymentResponse = mapper.readValue(body, PaymentResponse.class);

                        PaymentInitiateResponse result = new PaymentInitiateResponse();

                        result.setPaymentId(paymentResponse.getGatewayTransactionId());
                        result.setGatewayTransactionId(paymentResponse.getGatewayTransactionId());
                        result.setStatus(paymentResponse.getStatus());
                        result.setRedirect(paymentResponse.getRedirect());
                        result.setAmount(paymentResponse.getAmount());
                        result.setCurrency(paymentResponse.getCurrency());

                        return Mono.just(result);

                    } catch (Exception e) {

                        applicationFlowLogs.error("Failed to parse Payment Service response. transactionId={}", txnId, e);

                        return Mono.error(new SPRuntimeError("Invalid response from Payment Service", HttpStatus.BAD_GATEWAY, txnId));
                    }
                }).onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex, txnId));
    }

    public Mono<PaymentInitiateResponse> handlePaymentWebhook(PaymentWebhookRequest request, UserSessionObject user, String txnId) {

        String url = PAYMENT_SERVICE.concat("/b/webhook");

        Map<String, String> headers =
                Map.of(
                        "USER-DETAILS",
                        entityToString(user));

        applicationFlowLogs.info(
                "Processing payment webhook. transactionId={}, gatewayCode={}",
                request != null ? request.getTransactionId() : null,
                request != null ? request.getGatewayCode() : null);

        return AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.POST,
                        headers,
                        Collections.emptyMap(),
                        url,
                        entityToString(request),
                        MediaType.APPLICATION_JSON)
                .flatMap(response -> {

                    String body = response.getBody();

                    if (body == null || body.isBlank()) {
                        return Mono.error(new SPRuntimeError("Empty response from Payment Service", HttpStatus.FAILED_DEPENDENCY, txnId));
                    }

                    try {
                        PaymentResponse paymentResponse = mapper.readValue(body, PaymentResponse.class);

                        PaymentInitiateResponse result = new PaymentInitiateResponse();

                        result.setPaymentId(paymentResponse.getTransactionId());
                        result.setGatewayTransactionId(paymentResponse.getGatewayTransactionId());
                        result.setStatus(paymentResponse.getStatus());
                        result.setRedirect(paymentResponse.getRedirect());
                        result.setAmount(paymentResponse.getAmount());
                        result.setCurrency(paymentResponse.getCurrency());

                        return Mono.just(result);

                    } catch (Exception e) {
                        applicationFlowLogs.error("Failed to parse Payment Service webhook response. transactionId={}", txnId, e);

                        return Mono.error(new SPRuntimeError("Invalid response from Payment Service", HttpStatus.BAD_GATEWAY, txnId));
                    }
                })
                .onErrorResume(
                        WebClientResponseException.class,
                        ex -> handleWebClientError(ex, txnId));
    }

    public Mono<PaymentInitiateResponse> queryPayment(PaymentRequest request,  String txnId) {

        String url = PAYMENT_SERVICE.concat("/b/query");

        applicationFlowLogs.info("Processing payment query. transactionId={}, gatewayCode={}, orderId={}",
                request != null ? request.getTransactionId() : null, request != null ? request.getGatewayCode() : null, request != null ? request.getOrderId() : null);

        return AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.POST,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        url,
                        entityToString(request),
                        MediaType.APPLICATION_JSON)
                .flatMap(response -> {
                    String body = response.getBody();

                    if (body == null || body.isBlank()) {
                        return Mono.error(new SPRuntimeError("Empty response from Payment Service", HttpStatus.FAILED_DEPENDENCY, txnId));
                    }

                    try {
                        PaymentResponse paymentResponse = mapper.readValue(body, PaymentResponse.class);

                        PaymentInitiateResponse result = new PaymentInitiateResponse();

                        result.setPaymentId(paymentResponse.getTransactionId());
                        result.setGatewayTransactionId(paymentResponse.getGatewayTransactionId());
                        result.setStatus(paymentResponse.getStatus());
                        result.setAmount(paymentResponse.getAmount());
                        result.setCurrency(paymentResponse.getCurrency());

                        return Mono.just(result);
                    } catch (Exception e) {

                        applicationFlowLogs.error("Failed to parse Payment Service query response. transactionId={}", txnId, e);
                        return Mono.error(new SPRuntimeError("Invalid response from Payment Service", HttpStatus.BAD_GATEWAY, txnId));
                    }
                })
                .onErrorResume(
                        WebClientResponseException.class,
                        ex -> handleWebClientError(ex, txnId));
    }
    
	public Mono<ResponseEntity<String>> callDigiLockerService(String endpoint, HttpMethod httpMethod,
			Map<String, String> headers, Map<String, Object> queryParams, Object requestBody, String txnId) {

		String url = PLUGINS_SERVICE.concat(endpoint);

		applicationFlowLogs.info("Calling DigiLocker service | url={}, method={}, txnId={}", url, httpMethod, txnId);

		String body = requestBody == null ? null : entityToString(requestBody);

		return AsynchronousApiExecutor.callExternalEndpoint(String.class, httpMethod, headers,
				queryParams == null ? Collections.emptyMap() : queryParams, url, body, MediaType.APPLICATION_JSON)
				.doOnNext(response -> applicationFlowLogs.info(
						"DigiLocker service response | url={}, status={}, txnId={}", url, response.getStatusCode(),
						txnId))
				.onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex, txnId));
	}

	public Mono<DigiLockerResponseDTO> performDigiLockerEKYC(DigiLockerEKYCRequestDTO request, UserSessionObject user,
			String txnId) {

		return getDigiLockerIdentity(request.getApplicationId(), user, txnId).flatMap(identity -> {
			if (identity.isLinked()) {
				DigiLockerResponseDTO result = new DigiLockerResponseDTO();
				result.setSuccess(true);
				result.setLinked(true);
				result.setDigilockerId(identity.getDigilockerId());
				result.setReferenceKey(identity.getReferenceKey());
				result.setApplicationId(request.getApplicationId());
				result.setMessage("DigiLocker identity linked successfully");

				if (request.getCertXml() != null && !request.getCertXml().isBlank()) {
					return extractDigiLockerAttributes(request.getCertXml(), request.getServiceId(), user, txnId)
							.map(attrs -> {
								result.setExtractedAttributes(attrs);
								return result;
							}).defaultIfEmpty(result);
				}
				return Mono.just(result);
			} else {
				return initiateDigiLockerAuth(request.getApplicationId(), user, txnId);
			}
		});
	}

	public Mono<DigiLockerIdentityResponseDTO> getDigiLockerIdentity(String applicationId, UserSessionObject user,
			String txnId) {

		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
		Map<String, Object> queryParams = (applicationId != null && !applicationId.isBlank())
				? Map.of("applicationId", applicationId)
				: Collections.emptyMap();

		return callDigiLockerService("digilocker/identity", HttpMethod.POST, headers, queryParams, null, txnId)
				.flatMap(response -> {
					String body = response.getBody();
					if (body == null || body.isBlank()) {
						return Mono.just(new DigiLockerIdentityResponseDTO());
					}
					try {
						DigiLockerIdentityResponseDTO dto = mapper.readValue(body, DigiLockerIdentityResponseDTO.class);
						return Mono.just(dto != null ? dto : new DigiLockerIdentityResponseDTO());
					} catch (Exception e) {
						applicationFlowLogs.warn("Failed to parse DigiLocker identity response | txnId={}", txnId, e);
						return Mono.just(new DigiLockerIdentityResponseDTO());
					}
				}).onErrorResume(ex -> {
					applicationFlowLogs.warn("Error calling DigiLocker identity endpoint | txnId={}, msg={}", txnId,
							ex.getMessage());
					return Mono.just(new DigiLockerIdentityResponseDTO());
				});
	}

	public Mono<DigiLockerResponseDTO> initiateDigiLockerAuth(String applicationId, UserSessionObject user,
			String txnId) {

		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
		Map<String, Object> authReq = new HashMap<>();
		authReq.put("applicationId", applicationId != null ? applicationId : "");
		authReq.put("userId", user.getUserID());
		authReq.put("tenantId", user.getTenantId());

		return callDigiLockerService("digilocker/authorize", HttpMethod.POST, headers, Collections.emptyMap(), authReq, txnId)
				.flatMap(response -> {
					String body = response.getBody();
					if (body == null || body.isBlank()) {
						return Mono.error(new SPRuntimeError("DigiLocker authorize returned empty response",
								HttpStatus.FAILED_DEPENDENCY, txnId));
					}
					try {
						DigiLockerResponseDTO dto = mapper.readValue(body, DigiLockerResponseDTO.class);
						if (dto != null) {
							dto.setSuccess(true);
							dto.setLinked(false);
							return Mono.just(dto);
						}
					} catch (Exception ignored) {
					}
					DigiLockerResponseDTO fallback = new DigiLockerResponseDTO();
					fallback.setSuccess(true);
					fallback.setLinked(false);
					fallback.setRawResponse(body);
					return Mono.just(fallback);
				});
	}

	@SuppressWarnings("unchecked")
	public Mono<Map<String, String>> extractDigiLockerAttributes(String certXml, Integer serviceId,
			UserSessionObject user, String txnId) {

		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
		Map<String, Object> body = new HashMap<>();
		body.put("certXml", certXml != null ? certXml : "");
		if (serviceId != null) {
			body.put("serviceId", serviceId);
		}

		return callDigiLockerService("documents/extract-attributes", HttpMethod.POST, headers, Collections.emptyMap(),
				body, txnId).flatMap(response -> {
					String resp = response.getBody();
					if (resp == null || resp.isBlank()) {
						return Mono.just(Collections.<String, String>emptyMap());
					}
					try {
						Map<String, String> attrs = mapper.readValue(resp, Map.class);
						return Mono.just(attrs != null ? attrs : Collections.<String, String>emptyMap());
					} catch (Exception e) {
						applicationFlowLogs.warn("Failed to parse extracted attributes | txnId={}", txnId, e);
						return Mono.just(Collections.<String, String>emptyMap());
					}
				}).onErrorResume(ex -> {
					applicationFlowLogs.warn("Error extracting attributes | txnId={}, msg={}", txnId, ex.getMessage());
					return Mono.just(Collections.<String, String>emptyMap());
				});
	}

	public Mono<DigiLockerResponseDTO> pushDocumentToDigiLocker(DigiLockerPushDocumentRequestDTO request,
			UserSessionObject user, String txnId) {

		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

		return callDigiLockerService("digilocker/issuer/push", HttpMethod.POST, headers, Collections.emptyMap(),
				request, txnId).flatMap(response -> {

					String body = response.getBody();

					if (body == null || body.isBlank()) {
						return Mono.error(new SPRuntimeError("DigiLocker push document returned empty response",
								HttpStatus.FAILED_DEPENDENCY, txnId));
					}

					try {

						DigiLockerResponseDTO result = mapper.readValue(body, DigiLockerResponseDTO.class);

						if (result == null) {
							result = new DigiLockerResponseDTO();
							result.setRawResponse(body);
						}
						result.setSuccess(true);
						result.setMessage("Document pushed to DigiLocker successfully");
						return Mono.just(result);

					} catch (JsonProcessingException e) {

						applicationFlowLogs.error("Failed to parse DigiLocker push document response | txnId={}", txnId,
								e);

						DigiLockerResponseDTO result = new DigiLockerResponseDTO();
						result.setSuccess(true);
						result.setRawResponse(body);
						result.setMessage("Document pushed to DigiLocker successfully");
						return Mono.just(result);
					}
				});
	}
	
	public Mono<SubmissionDecisionResponseDTO> validateSubmissionLocation(
			UserSessionObject user,
			Integer serviceId,
			Integer singleLocationId,
			List<Integer> locationIdList,
			String txnId) {

		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
		String url = METADATA_SERVICE.concat("apply/validate-submission-location");

		Map<String, Object> reqBody = new HashMap<>();
		reqBody.put("serviceId", serviceId);
		if (locationIdList != null && !locationIdList.isEmpty()) {
			reqBody.put("submissionLocationIds", locationIdList);
		} else if (singleLocationId != null) {
			reqBody.put("submissionLocationId", singleLocationId);
		}

		Mono<ResponseEntity<String>> response = AsynchronousApiExecutor.callExternalEndpoint(
				String.class,
				HttpMethod.POST,
				headers,
				Collections.emptyMap(),
				url,
				entityToString(reqBody),
				MediaType.APPLICATION_JSON);

		return response.flatMap(apiResponse -> {
			SubmissionDecisionResponseDTO decision = (SubmissionDecisionResponseDTO) stringToEntityUsingType(
					apiResponse.getBody(),
					new TypeToken<SubmissionDecisionResponseDTO>() {}.getType());
			return Mono.justOrEmpty(decision);
		}).onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex, txnId));
	}

	public Mono<Void> recordPendingAdoption(
			UserSessionObject user,
			RecordPendingApplicationRequestDTO requestDTO,
			String txnId) {

		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
		String url = METADATA_SERVICE.concat("apply/record-pending-adoption");

		Mono<ResponseEntity<String>> response = AsynchronousApiExecutor.callExternalEndpoint(
				String.class,
				HttpMethod.POST,
				headers,
				Collections.emptyMap(),
				url,
				entityToString(requestDTO),
				MediaType.APPLICATION_JSON);

		return response.then().onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex, txnId));
	}
	
	@SuppressWarnings("unchecked")
    public Mono<String> fetchFormPreviewHtml(String formId, String dataId, String applicationId, ServiceMeta service, String txnId, UserSessionObject user) {
        return fetchFormPreviewHtml(formId, dataId, applicationId, service, txnId, user, null);
    }

    @SuppressWarnings("unchecked")
    public Mono<String> fetchFormPreviewHtml(String formId, String dataId, String applicationId, ServiceMeta service, String txnId, UserSessionObject user, Map<String, Object> formData) {
        String url = FORM_MANAGEMENT_SERVICE.concat("form/preview-html");
        Map<String, String> headers = user != null ? Map.of("USER-DETAILS", entityToString(user)) : Collections.emptyMap();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("formId", formId);
        if (dataId != null && !dataId.isBlank()) {
            requestBody.put("dataId", dataId);
        }
        if (applicationId != null && !applicationId.isBlank()) {
            requestBody.put("applId", applicationId);
        }
        if (service != null) {
            if (service.getServiceId() != null) {
                requestBody.put("serviceId", service.getServiceId());
            }
            if (service.getTaskId() != null) {
                requestBody.put("taskId", service.getTaskId());
            }
        }
        if (formData != null && !formData.isEmpty()) {
            requestBody.put("formData", formData);
        }

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
                String.class,
                HttpMethod.POST,
                headers,
                Collections.emptyMap(),
                url,
                entityToString(requestBody),
                MediaType.APPLICATION_JSON);

        return callExternalEndpoint.flatMap(apiResponse -> {
            String body = apiResponse.getBody();
            if (body == null || body.isBlank()) {
                return Mono.error(new SPRuntimeError("Unable to fetch form preview", HttpStatus.FAILED_DEPENDENCY, txnId));
            }

            try {
                Map<String, Object> responseJson = mapper.readValue(body, new TypeReference<>() {});
                String previewHtml = (String) responseJson.get("previewHtml");
                if (previewHtml != null) {
                    previewHtml = previewHtml.replace("\r", "").replace("\n", " ").replaceAll("\\s+", " ").replaceAll(">\\s+<", "><").trim();
                }
                return Mono.just(previewHtml != null ? previewHtml : "");
            } catch (JsonProcessingException e) {
                applicationFlowLogs.error("Error parsing form preview response: {}", e.getMessage());
                return Mono.just("");
            }
        });
    }

}

