package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.Utility.entityToString;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ApiExecutionResponse;
import com.serviceplus.form.validation.dto.FormSubmissionResponse;
import com.serviceplus.form.validation.dto.InboxKafka;
import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.dto.WebServiceTaskDTO;
import com.serviceplus.form.validation.dto.WorkflowContext;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.entity.WorkflowWebServiceExecution;
import com.serviceplus.form.validation.enums.TaskType;
import com.serviceplus.form.validation.enums.WebServiceExecutionStatus;
import com.serviceplus.form.validation.kafka.KafkaProducer;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import com.serviceplus.form.validation.repository.WorkflowWebServiceExecutionRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class WorkflowWebServiceTaskExecutor {

	private static final Logger log = LogManager.getLogger("workFlowWebServiceLogger");
	@Value("${push.form.submission.data.inbox.topic}")
    private String PUSH_FORM_SUBMISSION_DATA_INBOX_TOPIC;
	private final WebServiceExecutionService webServiceExecutionService;
	private final ReactiveApiClient apiClient;
	private final ObjectMapper objectMapper;
	private final TempTransactionLogService tempTransactionLogService;
	private final PreProcessingFacade preProcessingFacade;
	private final TransactionalDBExecutor transactionalDBExecutor;
	private final WorkflowService workflowService;
	private final ApplicationDetailsRepository applicationDetailsRepository;
	private final CurrentProcessRepository currentProcessRepository;
	private final WorkflowWebServiceExecutionRepository workflowExecutionRepository;
	private final AssociatedTaskService associatedTaskService;
	private final KafkaProducer kafkaProducer;

	public WorkflowWebServiceTaskExecutor(WebServiceExecutionService webServiceExecutionService,
			ReactiveApiClient apiClient, ObjectMapper objectMapper, TempTransactionLogService tempTransactionLogService,
			@Lazy PreProcessingFacade preProcessingFacade,
			TransactionalDBExecutor transactionalDBExecutor,
			WorkflowService workflowService, CurrentProcessRepository currentProcessRepository,
			ApplicationDetailsRepository applicationDetailsRepository,
			WorkflowWebServiceExecutionRepository workflowExecutionRepository, AssociatedTaskService associatedTaskService, KafkaProducer kafkaProducer) {
		this.webServiceExecutionService = webServiceExecutionService;
		this.apiClient = apiClient;
		this.objectMapper = objectMapper;
		this.tempTransactionLogService = tempTransactionLogService;
		this.preProcessingFacade = preProcessingFacade;
		this.transactionalDBExecutor = transactionalDBExecutor;
		this.workflowService = workflowService;
		this.applicationDetailsRepository = applicationDetailsRepository;
		this.currentProcessRepository = currentProcessRepository;
		this.workflowExecutionRepository = workflowExecutionRepository;
		this.associatedTaskService = associatedTaskService;
		this.kafkaProducer = kafkaProducer;
	}

	public Mono<Object> execute(List<CurrentProcess> processList, ApplicationDetails applicationDetails,
			UserSessionObject user) {

		CurrentProcess process = processList.stream()
				.filter(cp -> TaskType.WEB_SERVICE_TASK.getType().equals(cp.getCurrentTaskType()))
				.filter(cp -> "N".equalsIgnoreCase(cp.getActionTaken())).findFirst().orElse(null);

		if (process == null) {
			return Mono.empty();
		}
		user.setUserID(0l);
		log.info("Executing workflow web service task [{}]", process.getCurrentTask());

		return apiClient.fetchServiceMetadata(user, process.getServiceId(), "WEB_SERVICE_TASK").flatMap(metadata -> {

			WebServiceTaskDTO task = metadata.getWebServiceTasks().stream()
					.filter(t -> process.getCurrentTask().equals(t.getTaskId())).findFirst()
					.orElseThrow(() -> new SPRuntimeError("Web Service Task configuration not found.",
							HttpStatus.INTERNAL_SERVER_ERROR, null));

			return getOrCreateExecution(task, process, applicationDetails, user)

					.flatMap(execution -> webServiceExecutionService
							.execute(task.getWebserviceDetails(), new HashMap<String, Object>(), applicationDetails,
									process)
							.flatMap(response ->
							processResponse(response, task, process, applicationDetails, user, metadata)
									.then(updateCompleted(execution)))
							.onErrorResume(ex ->
							updateFailure(execution, ex).then(Mono.error(ex))));
		});

	}

	private Mono<?> processResponse(ApiExecutionResponse response, WebServiceTaskDTO task,
			CurrentProcess currentProcess, ApplicationDetails application, UserSessionObject user,
			ServiceJSONDTO serviceJson) {

		return submitWebServiceForm(response, task, currentProcess, application, user, serviceJson)

				.flatMap(submission ->

				processSubmission(submission, user, application)

						.flatMap(ctx ->

						completeCurrentProcess(ctx, currentProcess, application, user)));
	}

	private Mono<FormSubmissionResponse> submitWebServiceForm(ApiExecutionResponse response, WebServiceTaskDTO task,
			CurrentProcess currentProcess, ApplicationDetails application, UserSessionObject user,
			ServiceJSONDTO metadata) {

		ServiceMeta service = new ServiceMeta();
		String formId = metadata.getTaskFormMapping().get(task.getTaskId());
		service.setServiceId(application.getServiceId());
		service.setBaseServiceId(currentProcess.getBaseServiceId());
		service.setCurrentProcessId(currentProcess.getProcessId());

		service.setTaskId(task.getTaskId());
		service.setFormId(formId);
		
		return tempTransactionLogService.mergeTransactionLog(service, user, null)

				.flatMap(txnLog -> {
					if (txnLog == null) {
						return Mono.error(new SPRuntimeError("Unable to create transaction.",
								HttpStatus.INTERNAL_SERVER_ERROR, null));
					}
					return apiClient.fetchFormData(txnLog.getTxnId(), service, null, user)
							.flatMap(handler -> {
								String appData = buildAppData(handler.getData(), response);
								return apiClient
										.saveFormData(txnLog.getTxnId(), service, appData, user,
												currentProcess.getDataId(), application.getApplicationId(),Boolean.FALSE)
										.map(saveResponse -> {
											FormSubmissionResponse result = new FormSubmissionResponse();
											result.setTxnId(txnLog.getTxnId());
											result.setService(service);
											result.setAppData(appData);
											result.setTempTransactionLogs(txnLog);
											result.setResponseBody(saveResponse.getBody());
											return result;
										});
							});
				});
	}

	private Mono<WorkflowContext> processSubmission(FormSubmissionResponse submission, UserSessionObject user,
			ApplicationDetails application) {
		Map<String, Object> responseJson;
		try {
			responseJson = objectMapper.readValue(submission.getResponseBody(),
                    new TypeReference<>() {
                    });

		} catch (Exception ex) {
			return Mono
					.error(new SPRuntimeError("Invalid Form response", HttpStatus.BAD_GATEWAY, submission.getTxnId()));
		}
		String dataId = String.valueOf(responseJson.get("dataId"));

		return preProcessingFacade.getFormDataAndSaveTxn(submission.getService(), user, null,
						submission.getTempTransactionLogs(), String.valueOf(application.getApplicationId()), dataId,
						"FS", false, null, "","")
				.map(txn -> {
					WorkflowContext ctx = new WorkflowContext();
					ctx.setTxn(txn);
					ctx.setService(submission.getService());
					ctx.setDataId(dataId);
					return ctx;
				});
	}

	private Mono<?> completeCurrentProcess(WorkflowContext ctx, CurrentProcess currentProcess,
			ApplicationDetails application, UserSessionObject user) {

		currentProcess.setActionTaken("Y");
		currentProcess.setActionOn(LocalDateTime.now());
		currentProcess.setDataId(ctx.getDataId());
		currentProcess.setUserId(user.getUserID());
		currentProcess.setFormId(ctx.getService().getFormId());
		if (ctx.getService().getSelectedWorkflowElementData() != null
				&& ctx.getService().getSelectedWorkflowElementData().getActionAttribute() != null) {

			ServiceProcessFlowDTO.Data.ActionAttribute action = ctx.getService().getSelectedWorkflowElementData()
					.getActionAttribute().getFirst();

			if (Boolean.TRUE.equals(action.getCompleteClosure())) {

				return transactionalDBExecutor
						.execute(ctx.getTxn().getTxnId(), currentProcess, application, ctx.getTxn())

						.then(sendCurrentProcessToTracking(currentProcess, application,
								ctx.getService(), user));
			}
		}

		return workflowService.generateNextWorkflow(application, ctx.getTxn(), ctx.getService(), user, currentProcess)
				.flatMap(inbox ->
				persistWorkflow(application, (InboxKafka) inbox, ctx.getTxn(), user)
						.doOnSuccess(x -> sendToInboxService((InboxKafka) inbox,
								application, ctx.getService())));

	}
	
	public Mono<Void> persistWorkflow(
            ApplicationDetails ad,
            InboxKafka inboxKafka,
            ProcessingTxn txn,
            UserSessionObject user) {

        return transactionalDBExecutor.execute(txn.getTxnId(),inboxKafka.getProcessList(), ad, txn)
        		.then(Mono.fromRunnable(() ->
                associatedTaskService.executeAssociatedTasks(
                        inboxKafka.getProcessList(),
                        ad,
                        user)))
        .then(Mono.fromRunnable(() -> execute(
                inboxKafka.getProcessList(),
                ad,
                user)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(
                                result -> log.info("Workflow web service task completed for applicationId: {}", ad.getApplicationId()),
                                error -> log.error("Workflow web service task failed for applicationId: {}", ad.getApplicationId(), error)
                        )));
    }


    public Mono<Object> sendToInboxService(InboxKafka inboxKafka, ApplicationDetails appDetails,ServiceMeta service) {
        String key = appDetails.getApplicationId().concat("_").concat(UUID.randomUUID().toString());
        inboxKafka.setApplicationRefNo(appDetails.getReferenceNo());
        inboxKafka.setActionTinyUrl(appDetails.getActionTinyUrl());
        kafkaProducer.sendMessage(PUSH_FORM_SUBMISSION_DATA_INBOX_TOPIC,key,entityToString(inboxKafka));
        return Mono.just(true);
    }

	public Mono<Void> sendCurrentProcessToTracking(CurrentProcess currentProcess, ApplicationDetails application,
			ServiceMeta service, UserSessionObject user) {

		InboxKafka inboxKafka = new InboxKafka();

		inboxKafka.setProcessList(List.of(currentProcess));
		inboxKafka.setOfficeDetails(Collections.emptyList());

		inboxKafka.setServiceName(service.getServiceName());
		inboxKafka.setApplicationRefNo(application.getReferenceNo());

		inboxKafka.setAppliedBy(application.getBeneficiaryId());
		inboxKafka.setBeneficiaryName(application.getBeneficiaryName());
		inboxKafka.setApplyDate(application.getApplyDate());

		inboxKafka.setLoggedInUserId(user.getUserID());
		inboxKafka.setLoggedInUserLocation(user.getLocationId());
		inboxKafka.setActionTinyUrl(application.getActionTinyUrl());

		String key = application.getApplicationId().concat("_").concat(UUID.randomUUID().toString());

		kafkaProducer.sendMessage(PUSH_FORM_SUBMISSION_DATA_INBOX_TOPIC, key, entityToString(inboxKafka));

		log.info("Terminal action. Current process pushed to tracking. applicationId={}, processId={}",
				application.getApplicationId(), currentProcess.getProcessId());

		return Mono.empty();
	}
	@SuppressWarnings("unchecked")
	private String buildAppData(Map<String, Object> formDataResponse, ApiExecutionResponse response) {
		try {
			Map<String, Object> renderingData = (Map<String, Object>) formDataResponse.get("formJsonData");
			
			if (renderingData == null) {
				throw new SPRuntimeError("Form data not found.", HttpStatus.INTERNAL_SERVER_ERROR, null);
			}
			Map<String, Object> formData = new HashMap<String, Object>();

			List<Map<String, Object>> formElements = (List<Map<String, Object>>) renderingData.get("formElements");
			populateDefaultFormData(formElements, formData);
			if (response != null && response.getNormalizedResponse() != null) {
				for (Map.Entry<String, ApiExecutionResponse.DynamicAttributeData> entry : response.getNormalizedResponse()
					.entrySet()) {
				String attributeId = entry.getKey();
				if(attributeId!=null && !attributeId.isBlank()) {
					ApiExecutionResponse.DynamicAttributeData data = entry.getValue();
					Object value = convertDynamicValue(data);
					formData.put(attributeId, value);
				}
			}
			}
			Object formId = formDataResponse.get("formId") != null ? formDataResponse.get("formId") : renderingData.get("formId");
			Map<String, Object> finalData = new LinkedHashMap<>();
			finalData.put("formId", formId);
			finalData.put("formData", formData);
			return objectMapper.writeValueAsString(finalData);

		} catch (Exception ex) {
			ex.printStackTrace();
			throw new SPRuntimeError("Unable to prepare form data.", HttpStatus.INTERNAL_SERVER_ERROR, null);
		}
	}

	private Object convertDynamicValue(ApiExecutionResponse.DynamicAttributeData attribute) {

		List<Map<String, Object>> values = attribute.getData();

		if (values == null || values.isEmpty()) {
			return null;
		}

		if ("single".equalsIgnoreCase(attribute.getTargetType())) {

			if (values.size() == 1) {
				return values.get(0).get("value");
			}

			Map<String, Object> object = new LinkedHashMap<>();

			for (Map<String, Object> value : values) {
				object.put(String.valueOf(value.get("label")), value.get("value"));
			}

			return object;
		}

		return values;
	}

	@SuppressWarnings("unchecked")
	private void populateDefaultFormData(List<Map<String, Object>> formElements, Map<String, Object> formData) {
		if (formElements == null) {
			return;
		}
		for (Map<String, Object> element : formElements) {
			if (element == null) {
				continue;
			}
			String uniqueId = (String) element.get("uniqueId");
			if (uniqueId != null && !uniqueId.isBlank()) {
				formData.put(uniqueId, getDefaultValue(element));
			}
			Object children = element.get("formElements");
			if (children instanceof List<?>) {
				populateDefaultFormData((List<Map<String, Object>>) children, formData);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private Object getDefaultValue(Map<String, Object> element) {
		if (element == null) {
			return "";
		}

		// 1. Action attribute: if it has exactly one value, set it; otherwise blank
		if (isActionAttribute(element)) {
			List<Map<String, Object>> optionsList = extractOptions(element);
			List<Map<String, Object>> enabledActions = new ArrayList<>();
			for (Map<String, Object> opt : optionsList) {
				if (!opt.containsKey("enabled") || Boolean.TRUE.equals(opt.get("enabled"))) {
					enabledActions.add(opt);
				}
			}
			if (enabledActions.size() == 1) {
				return toLabelValue(enabledActions.get(0));
			}
			return "";
		}

		// 2. Element with options: set object {label, value} if single-select, or array [{label, value}] if multi-select
		List<Map<String, Object>> optionsList = extractOptions(element);
		if (!optionsList.isEmpty()) {
			boolean multiSelect = isMultiSelect(element);
			if (multiSelect) {
				List<Map<String, Object>> selectedList = new ArrayList<>();
				for (Map<String, Object> opt : optionsList) {
					if (Boolean.TRUE.equals(opt.get("selected"))
							|| Boolean.TRUE.equals(opt.get("default"))
							|| Boolean.TRUE.equals(opt.get("checked"))) {
						selectedList.add(toLabelValue(opt));
					}
				}
				if (!selectedList.isEmpty()) {
					return selectedList;
				}
				Object defVal = getRawDefaultValue(element);
				if (defVal != null) {
					List<Map<String, Object>> matched = matchOptions(optionsList, defVal);
					if (!matched.isEmpty()) {
						return matched;
					}
				}
				return List.of(toLabelValue(optionsList.get(0)));
			} else {
				for (Map<String, Object> opt : optionsList) {
					if (Boolean.TRUE.equals(opt.get("selected"))
							|| Boolean.TRUE.equals(opt.get("default"))
							|| Boolean.TRUE.equals(opt.get("checked"))) {
						return toLabelValue(opt);
					}
				}
				Object defVal = getRawDefaultValue(element);
				if (defVal != null) {
					Map<String, Object> matched = matchSingleOption(optionsList, defVal);
					if (matched != null) {
						return matched;
					}
				}
				return toLabelValue(optionsList.get(0));
			}
		}

		// 3. Fallback for non-options fields (text, number, date, file, etc.)
		Object rawDefault = getRawDefaultValue(element);
		if (rawDefault != null) {
			return rawDefault;
		}

		return "";
	}

	private boolean isActionAttribute(Map<String, Object> element) {
		if (element == null) {
			return false;
		}
		return Boolean.TRUE.equals(element.get("isActionElement"))
				|| "Action".equalsIgnoreCase((String) element.get("name"))
				|| Integer.valueOf(100).equals(element.get("typeId"));
	}

	@SuppressWarnings("unchecked")
	private boolean isMultiSelect(Map<String, Object> element) {
		if (element == null) {
			return false;
		}
		Object typeId = element.get("typeId");
		if (Integer.valueOf(7).equals(typeId) || "Checkbox Group".equalsIgnoreCase((String) element.get("name"))) {
			return true;
		}
		if (Boolean.TRUE.equals(element.get("multiple")) || Boolean.TRUE.equals(element.get("isMultiSelect"))) {
			return true;
		}
		Object displaySettingsObj = element.get("displaySettings");
		if (displaySettingsObj instanceof Map<?, ?> displaySettings) {
			Object multipleObj = displaySettings.get("multiple");
			if (multipleObj instanceof Map<?, ?> multipleMap) {
				if (Boolean.TRUE.equals(multipleMap.get("value"))) {
					return true;
				}
			} else if (Boolean.TRUE.equals(multipleObj)) {
				return true;
			}
		}
		Object dataSettingsObj = element.get("dataSettings");
		if (dataSettingsObj instanceof Map<?, ?> dataSettings) {
			if (Boolean.TRUE.equals(dataSettings.get("multiple")) || Boolean.TRUE.equals(dataSettings.get("isMultiSelect"))) {
				return true;
			}
			Object optionsObj = dataSettings.get("options");
			if (optionsObj instanceof Map<?, ?> options) {
				if (Boolean.TRUE.equals(options.get("multiple")) || Boolean.TRUE.equals(options.get("isMultiSelect"))) {
					return true;
				}
			}
		}
		Object htmlTagObj = element.get("htmlTag");
		if (htmlTagObj instanceof Map<?, ?> htmlTag) {
			Object attrsObj = htmlTag.get("attributes");
			if (attrsObj instanceof Map<?, ?> attrs) {
				if (Boolean.TRUE.equals(attrs.get("multiple"))) {
					return true;
				}
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> extractOptions(Map<String, Object> element) {
		Object dataSettingsObj = element.get("dataSettings");
		if (dataSettingsObj instanceof Map<?, ?> dataSettings) {
			Object optionsObj = dataSettings.get("options");
			if (optionsObj instanceof Map<?, ?> optionsMap) {
				Object val = optionsMap.get("value");
				if (val instanceof List<?>) {
					return (List<Map<String, Object>>) val;
				}
			} else if (optionsObj instanceof List<?>) {
				return (List<Map<String, Object>>) optionsObj;
			}
		}
		if (element.get("options") instanceof List<?>) {
			return (List<Map<String, Object>>) element.get("options");
		}
		return Collections.emptyList();
	}

	private Map<String, Object> toLabelValue(Map<String, Object> option) {
		Map<String, Object> lv = new LinkedHashMap<>();
		String label = "";
		Object actionLabels = option.get("actionLabels");
		if (actionLabels instanceof Map<?, ?> alMap) {
			Object formObj = alMap.get("form");
			if (formObj instanceof Map<?, ?> formMap && formMap.get("text") != null) {
				label = String.valueOf(formMap.get("text"));
			}
		}
		if (label.isEmpty() && option.get("label") != null) {
			label = String.valueOf(option.get("label"));
		}
		if (label.isEmpty() && option.get("name") != null) {
			label = String.valueOf(option.get("name"));
		}
		Object value = option.get("value") != null ? option.get("value") : option.get("id");
		lv.put("label", label);
		lv.put("value", value);
		return lv;
	}

	private Object getRawDefaultValue(Map<String, Object> element) {
		if (element.get("defaultValue") != null) {
			return element.get("defaultValue");
		}
		if (element.get("value") != null) {
			return element.get("value");
		}
		Object displaySettingsObj = element.get("displaySettings");
		if (displaySettingsObj instanceof Map<?, ?> displaySettings) {
			Object defValObj = displaySettings.get("defaultValue");
			if (defValObj instanceof Map<?, ?> defValMap && defValMap.get("value") != null) {
				return defValMap.get("value");
			} else if (defValObj != null) {
				return defValObj;
			}
		}
		Object dataSettingsObj = element.get("dataSettings");
		if (dataSettingsObj instanceof Map<?, ?> dataSettings) {
			if (dataSettings.get("defaultValue") != null) {
				return dataSettings.get("defaultValue");
			}
			Object optionsObj = dataSettings.get("options");
			if (optionsObj instanceof Map<?, ?> options && options.get("defaultValue") != null) {
				return options.get("defaultValue");
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> matchSingleOption(List<Map<String, Object>> optionsList, Object defVal) {
		if (defVal instanceof Map<?, ?> defMap && defMap.containsKey("label") && defMap.containsKey("value")) {
			return (Map<String, Object>) defMap;
		}
		String targetVal = String.valueOf(defVal);
		for (Map<String, Object> opt : optionsList) {
			if (targetVal.equals(String.valueOf(opt.get("value"))) || targetVal.equals(String.valueOf(opt.get("label")))) {
				return toLabelValue(opt);
			}
		}
		return null;
	}

	private List<Map<String, Object>> matchOptions(List<Map<String, Object>> optionsList, Object defVal) {
		List<Map<String, Object>> result = new ArrayList<>();
		if (defVal instanceof List<?> list) {
			for (Object item : list) {
				Map<String, Object> matched = matchSingleOption(optionsList, item);
				if (matched != null) {
					result.add(matched);
				}
			}
		} else {
			Map<String, Object> matched = matchSingleOption(optionsList, defVal);
			if (matched != null) {
				result.add(matched);
			}
		}
		return result;
	}

	public Mono<Void> resumeApiExecution(WorkflowWebServiceExecution execution) {

		Mono<ApplicationDetails> applicationMono = applicationDetailsRepository
				.findByApplicationId(execution.getApplicationId());

		Mono<CurrentProcess> processMono = currentProcessRepository.findById(execution.getCurrentProcessId());
		return Mono.zip(applicationMono, processMono).flatMap(tuple -> {
			ApplicationDetails application = tuple.getT1();
			CurrentProcess process = tuple.getT2();
			UserSessionObject user = new UserSessionObject();
			user.setTenantId(application.getTenantId());
			return execute(List.of(process), application, user).then();
		});
	}

	private Mono<WorkflowWebServiceExecution> getOrCreateExecution(WebServiceTaskDTO task, CurrentProcess process,
			ApplicationDetails application, UserSessionObject user) {

		return workflowExecutionRepository.findByCurrentProcessId(process.getProcessId())
				.flatMap(existing -> {
					existing.setNew(false);
					int attempts = (existing.getAttemptCount() != null ? existing.getAttemptCount() : 0) + 1;
					existing.setAttemptCount(attempts);
					int max = existing.getMaxAttempt() != null ? existing.getMaxAttempt() : 1;
					if (attempts >= max) {
						existing.setStatus(WebServiceExecutionStatus.EXHAUSTED.name());
					} else {
						existing.setStatus(WebServiceExecutionStatus.FAILED.name());
					}
					existing.setNextRetryTime(calculateNextRetry(existing.getRetryInterval(), existing.getRetryIntervalUnit()));
					existing.setModifiedOn(OffsetDateTime.now());
					return workflowExecutionRepository.save(existing);
				})
				.switchIfEmpty(Mono.defer(() -> {
					WorkflowWebServiceExecution execution = new WorkflowWebServiceExecution();
					execution.setExecutionId(createUniqueId());
					execution.setApplicationId(application.getApplicationId());
					execution.setServiceId(process.getServiceId());
					execution.setBaseServiceId(process.getBaseServiceId());
					execution.setCurrentProcessId(process.getProcessId());
					execution.setCurrentTaskId(process.getCurrentTask());
					execution.setApiId(task.getWebserviceDetails().getDefinition().getId());
					execution.setStatus(WebServiceExecutionStatus.FAILED.name());
					execution.setAttemptCount(1);
					execution.setMaxAttempt(Integer.parseInt(task.getWebserviceDetails().getWsCall().getWsCallFrequency()));
					execution.setRetryInterval(Integer.parseInt(task.getWebserviceDetails().getWsCall().getWsCallInterval()));
					execution.setRetryIntervalUnit(
							String.valueOf(task.getWebserviceDetails().getWsCall().getWsCallIntervalUnit().getValue()));
					execution.setNextRetryTime(calculateNextRetry(execution.getRetryInterval(), execution.getRetryIntervalUnit()));
					execution.setCreatedOn(OffsetDateTime.now());
					execution.setModifiedOn(OffsetDateTime.now());
					return workflowExecutionRepository.save(execution);
				}));
	}

	private OffsetDateTime calculateNextRetry(Integer interval, String unit) {

		OffsetDateTime now = OffsetDateTime.now();
		if (interval == null || unit == null) {
			return now;
		}
		switch (unit.toUpperCase()) {
		case "MINUTE":
			return now.plusMinutes(interval);

		case "HOUR":
			return now.plusHours(interval);

		case "DAY":
			return now.plusDays(interval);

		}
		return now;
	}

	private Mono<Void> updateFailure(WorkflowWebServiceExecution execution, Throwable ex) {
		execution.setNew(false);
		execution.setErrorMessage(ex.getMessage());
		execution.setModifiedOn(OffsetDateTime.now());
		return workflowExecutionRepository.save(execution).then();
	}

	private Mono<Void> updateCompleted(WorkflowWebServiceExecution execution) {
		execution.setNew(false);
		execution.setStatus("COMPLETED");
		execution.setModifiedOn(OffsetDateTime.now());
		return workflowExecutionRepository.save(execution).then();
	}
}
