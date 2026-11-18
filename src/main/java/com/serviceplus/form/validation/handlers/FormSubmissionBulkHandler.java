package com.serviceplus.form.validation.handlers;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_BULK_STATUS_KEY;
import static com.serviceplus.form.validation.utility.Utility.decryptWorkflowKey;
import static com.serviceplus.form.validation.utility.Utility.entityToString;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static com.serviceplus.form.validation.utility.Utility.isEmpty;
import static com.serviceplus.form.validation.utility.Utility.stringToEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.service.BulkFormService;
import com.serviceplus.form.validation.service.ReactiveApiClient;
import static com.serviceplus.form.validation.utility.Utility.*;

import reactor.core.publisher.Mono;

@Service
@SanitizeRequest
public class FormSubmissionBulkHandler implements ApplicationFlowHandler {

    private final BulkFormService bulkFormService;
    private final ApplicationDetailsRepository applicationDetailsRepository;
    private final ReactiveApiClient reactiveApiClient;

    public FormSubmissionBulkHandler(
            BulkFormService bulkFormService,
            ApplicationDetailsRepository applicationDetailsRepository,
            ReactiveApiClient reactiveApiClient) {

        this.bulkFormService = bulkFormService;
        this.applicationDetailsRepository = applicationDetailsRepository;
        this.reactiveApiClient = reactiveApiClient;
    }

    @Override
    public String getActivityType() {
        return ACTIVITY_FORM_BULK_STATUS_KEY;
    }

	@Override
	@SuppressWarnings("unchecked")
	public Mono<ServerResponse> process(String applicationId, ServerRequest request, String statusKey, String txnId,
			Mono<TempTransactionLogs> fetch, ApplicationFlowStatusEntity flow, ServiceMeta service, boolean fromDraft) {

		List<String> applicationIds = Arrays.stream(applicationId.split(",")).map(String::trim)
				.filter(id -> !id.isBlank()).distinct().toList();

		if (applicationIds.isEmpty()) {
			return Mono.error(new SPRuntimeError("Application id is required", HttpStatus.BAD_REQUEST, txnId));
		}

//		if (applicationIds.size() == 1) {
//			return Mono.error(
//					new SPRuntimeError("BULKFS requires multiple application ids", HttpStatus.BAD_REQUEST, txnId));
//		}

		return request.bodyToMono(String.class).defaultIfEmpty("").flatMap(body -> {

			if (isEmpty(body)) {
				return Mono.error(new SPRuntimeError("Request body is required", HttpStatus.BAD_REQUEST, txnId));
			}

			Map<String, Object> requestBody = (Map<String, Object>) stringToEntity(body, Map.class);

			String workflowKey = (String) requestBody.remove("workflowKey");

			Mono<Boolean> priorityMono;

			if (requestBody.containsKey("isPriority")) {

				Boolean isPriority = (Boolean) requestBody.remove("isPriority");

				priorityMono = Mono.just(Objects.requireNonNullElse(isPriority, Boolean.FALSE));

			} else {

				priorityMono = applicationDetailsRepository.findByApplicationId(applicationIds.get(0))
						.map(ApplicationDetails::getIsPriority)
						.map(priority -> Objects.requireNonNullElse(priority, Boolean.FALSE))
						.defaultIfEmpty(Boolean.FALSE);
			}

			return priorityMono.flatMap(isPriority -> {

				service.setIsPriority(isPriority);

				if (!isEmpty(workflowKey)) {
					service.setWorkflowElementData(decryptWorkflowKey(workflowKey));
				}

				String formData = entityToString(requestBody);

				return reactiveApiClient.fetchServiceMetadata(getUserSessionDetails(request.exchange().getRequest()),
						service.getServiceId(), txnId).flatMap(metadataService -> {

							populateActionAndLocation(metadataService, service);

							String bulkApplicationIds = String.join(",", applicationIds);

							return bulkFormService.applicationSubmission(request.exchange().getRequest(), txnId,
									formData, bulkApplicationIds, fromDraft, request, flow,
									service.getServiceId().toString(), service, false);
						});
			});
		});
	}

	@Override
	public Mono<ServerResponse> fetch(String applicationId, ServerRequest request, String statusKey, String txnId,
			Mono<TempTransactionLogs> fetch, ApplicationFlowStatusEntity flow, ServiceMeta service, boolean fromDraft) {

		return Mono.error(new SPRuntimeError("BULKFS fetch is not supported", HttpStatus.BAD_REQUEST, txnId));
	}
}