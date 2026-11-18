package com.serviceplus.form.validation.service;

import java.util.Arrays;
import java.time.OffsetDateTime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.dto.WebServiceReinitiateRequestDTO;
import com.serviceplus.form.validation.dto.WebServiceReinitiateResponseDTO;
import com.serviceplus.form.validation.entity.WorkflowWebServiceExecution;
import com.serviceplus.form.validation.enums.WebServiceExecutionStatus;
import com.serviceplus.form.validation.repository.WorkflowWebServiceExecutionRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class WorkflowWebServiceRetrySchedulerService {

	private static final Logger log = LogManager.getLogger("webServiceSchedulerLogger");

	private final WorkflowWebServiceExecutionRepository repository;
	private final WorkflowWebServiceRetryService retryService;

	public WorkflowWebServiceRetrySchedulerService(
			WorkflowWebServiceExecutionRepository repository,
			WorkflowWebServiceRetryService retryService) {
		this.repository = repository;
		this.retryService = retryService;
	}

	public Mono<Void> retryFailedExecutions() {

		log.info("Fetching eligible workflow web service executions for retry.");

		return repository.findByStatusInAndNextRetryTimeLessThanEqual(
				Arrays.asList(
						"FAILED",
						"API_SUCCESS",
						"FORM_SUCCESS"),
				OffsetDateTime.now())
				.doOnNext(execution ->
						log.info("Retrying executionId: {}, status: {}, attempt: {}",
								execution.getExecutionId(),
								execution.getStatus(),
								execution.getAttemptCount()))
				.flatMap(retryService::resume)
				.doOnError(ex ->
						log.error("Error while processing workflow web service retries.", ex))
				.then()
				.doOnSuccess(v ->
						log.info("Completed processing all eligible workflow web service executions."));
	}

	/**
	 * Re-initiates web service calls for failed applications.
	 * Type 1: Re-execute those applications which are failed and does not have any attempts left (exhausted).
	 * Type 2: Re-execute only those applications which have attempts left.
	 */
	public Mono<WebServiceReinitiateResponseDTO> reinitiate(WebServiceReinitiateRequestDTO req) {

		int type = req.getType() != null ? req.getType() : 2;
		boolean hasAppIds = req.getApplicationId() != null && !req.getApplicationId().isEmpty();

		log.info("Re-initiating web service tasks. Scenario: {}, serviceId: {}, taskId: {}, specified applications: {}",
				type, req.getServiceId(), req.getTaskId(), hasAppIds ? req.getApplicationId() : "ALL eligible");

		Flux<WorkflowWebServiceExecution> targetFlux;
		if (type == 1) {
			// Type 1: Re-execute failed applications without any attempts left
			targetFlux = hasAppIds
					? repository.findFailedWithoutAttemptsLeftByApplicationIds(req.getServiceId(), req.getTaskId(), req.getApplicationId())
					: repository.findFailedWithoutAttemptsLeft(req.getServiceId(), req.getTaskId());
		} else {
			// Type 2: Re-execute only those which have attempts left
			targetFlux = hasAppIds
					? repository.findFailedWithAttemptsLeftByApplicationIds(req.getServiceId(), req.getTaskId(), req.getApplicationId())
					: repository.findFailedWithAttemptsLeft(req.getServiceId(), req.getTaskId());
		}

		return targetFlux
				.flatMap(execution -> {
					log.info("Reinitiating execution: {} for application: {}, current status: {}, attempts: {}/{}",
							execution.getExecutionId(), execution.getApplicationId(), execution.getStatus(),
							execution.getAttemptCount(), execution.getMaxAttempt());

					if (type == 1) {
						// Reset attempts counter to grant a fresh retry cycle
						execution.setAttemptCount(0);
						execution.setStatus(WebServiceExecutionStatus.FAILED.name());
					}

					execution.setNextRetryTime(OffsetDateTime.now());
					execution.setModifiedOn(OffsetDateTime.now());
					execution.setNew(false);

					return repository.save(execution)
							.flatMap(saved -> retryService.resume(saved).thenReturn(saved.getApplicationId()))
							.onErrorResume(ex -> {
								log.error("Failed to reinitiate web service for appId: {}", execution.getApplicationId(), ex);
								return Mono.just(execution.getApplicationId() + " (Failed: " + ex.getMessage() + ")");
							});
				})
				.collectList()
				.map(processedList -> {
					String scenarioDesc = (type == 1)
							? "failed applications with no attempts left"
							: "failed applications with attempts left";
					String msg = String.format("Reinitiation completed. Total applications processed: %d",processedList.size());
					log.info(msg);
					return new WebServiceReinitiateResponseDTO(msg, processedList.size(), type);
				});
	}
}
