package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.TinyUrlUtil.generateDocumentTinyUrl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.Helpers.SystemAttributeHelper;
import com.serviceplus.form.validation.dto.DocumentGenerationDetails;
import com.serviceplus.form.validation.dto.GenerateDocDResDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.TrackingDocument;
import com.serviceplus.form.validation.dto.TrackingDocumentAttachRequest;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.AckGenerationJobEntity;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.ApplicationDocumentLogEntity;
import com.serviceplus.form.validation.entity.ApplicationDocumentSubmissionEntity;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.kafka.KafkaProducer;
import com.serviceplus.form.validation.repository.AckGenerationJobRepository;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.ApplicationDocumentLogRepository;
import com.serviceplus.form.validation.repository.ApplicationDocumentSubmissionRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

@Service
public class AckJobService {

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    private final AckGenerationJobRepository ackGenerationJobRepository;
    private final ReactiveApiClient reactiveApiClient;
    private final ApplicationDetailsRepository applicationDetailsRepository;
    private final SystemAttributeHelper systemAttributeHelper;
    private final ApplicationDocumentSubmissionRepository applicationDocumentSubmissionRepository;
    private final ApplicationDocumentLogRepository applicationDocumentLogRepository;
    private final CurrentProcessRepository currentProcessRepository;
    private final KafkaProducer kafkaProducer;

    @Value("${push.form.submission.data.inbox.topic}")
    private String PUSH_FORM_SUBMISSION_DATA_INBOX_TOPIC;

    public AckJobService(AckGenerationJobRepository ackGenerationJobRepository,
                          ReactiveApiClient reactiveApiClient,
                          ApplicationDetailsRepository applicationDetailsRepository,
                          SystemAttributeHelper systemAttributeHelper,
                          ApplicationDocumentSubmissionRepository applicationDocumentSubmissionRepository,
                          ApplicationDocumentLogRepository applicationDocumentLogRepository,
                          CurrentProcessRepository currentProcessRepository,
                          KafkaProducer kafkaProducer) {
        this.ackGenerationJobRepository = ackGenerationJobRepository;
        this.reactiveApiClient = reactiveApiClient;
        this.applicationDetailsRepository = applicationDetailsRepository;
        this.systemAttributeHelper = systemAttributeHelper;
        this.applicationDocumentSubmissionRepository = applicationDocumentSubmissionRepository;
        this.applicationDocumentLogRepository = applicationDocumentLogRepository;
        this.currentProcessRepository = currentProcessRepository;
        this.kafkaProducer = kafkaProducer;
    }

    public Mono<AckGenerationJobEntity> createAndTriggerJob(String applicationId,
                                                           String txnId,
                                                           UserSessionObject user,
                                                           ApplicationFlowStatusEntity flow,
                                                           ServiceMeta service,
                                                           DocumentGenerationDetails.DocMappingDTO ackMapping,
                                                           String domain,
                                                           String serviceHeaderUrl) {
        String documentTinyUrl = generateDocumentTinyUrl(domain, serviceHeaderUrl);
        return createAndTriggerJob(applicationId, txnId, user, flow, service, ackMapping, domain, serviceHeaderUrl, documentTinyUrl);
    }

    public Mono<AckGenerationJobEntity> createAndTriggerJob(String applicationId,
                                                           String txnId,
                                                           UserSessionObject user,
                                                           ApplicationFlowStatusEntity flow,
                                                           ServiceMeta service,
                                                           DocumentGenerationDetails.DocMappingDTO ackMapping,
                                                           String domain,
                                                           String serviceHeaderUrl,
                                                           String documentTinyUrl) {

        final String finalDocumentTinyUrl = (documentTinyUrl != null && !documentTinyUrl.isBlank())
                ? documentTinyUrl
                : generateDocumentTinyUrl(domain, serviceHeaderUrl);

        if (service != null && (service.getDocumentTinyUrl() == null || service.getDocumentTinyUrl().isBlank())) {
            service.setDocumentTinyUrl(finalDocumentTinyUrl);
        }

        String jobId = createUniqueId().toString();
        AckGenerationJobEntity job = new AckGenerationJobEntity();
        job.setId(jobId);
        job.setTxnId(txnId);
        job.setApplicationId(applicationId);
        job.setServiceId(service.getServiceId());
        job.setTaskId(service.getTaskId());
        job.setReferenceId(ackMapping != null ? ackMapping.getReferenceId() : "ACK");
        job.setDocumentName(ackMapping != null && ackMapping.getDocumentName() != null ? ackMapping.getDocumentName() : "System-Generated ACK");
        
        Long outputFormatId = -1L;
        if (ackMapping != null && ackMapping.getSystemGeneratedDocument() != null && ackMapping.getSystemGeneratedDocument().getValue() != null) {
            outputFormatId = ackMapping.getSystemGeneratedDocument().getValue().longValue();
        }
        job.setOutputFormatId(outputFormatId);
        job.setCategory("ACK");
        job.setStatus("PENDING");
        job.setTenantId(user != null ? user.getTenantId() : "DEFAULT");
        job.setCrBy(user != null ? user.getUserID() : 0L);
        job.setCrOn(LocalDateTime.now());
        job.setNew(true);

        return ackGenerationJobRepository.save(job)
                .doOnSuccess(savedJob -> {
                    applicationFlowLogs.info("TxnId : {} | ACK generation job created with jobId: {} for applicationId: {} | DocumentTinyUrl: {}",
                            txnId, savedJob.getId(), applicationId, finalDocumentTinyUrl);
                    // Trigger asynchronous execution without blocking
                    executeAckGeneration(savedJob, user, flow, service, ackMapping, domain, serviceHeaderUrl, finalDocumentTinyUrl)
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe();
                })
                .doOnError(e -> applicationFlowLogs.error("TxnId : {} | Failed to create ACK generation job: {}", txnId, e.getMessage(), e));
    }

    private Mono<Void> executeAckGeneration(AckGenerationJobEntity job,
                                            UserSessionObject user,
                                            ApplicationFlowStatusEntity flow,
                                            ServiceMeta service,
                                            DocumentGenerationDetails.DocMappingDTO ackMapping,
                                            String domain,
                                            String serviceHeaderUrl,
                                            String documentTinyUrl) {

        job.setStatus("IN_PROGRESS");
        job.setUpOn(LocalDateTime.now());
        job.setNew(false);

        Map<String, Object> systemAttrMap = new HashMap<>();

        return ackGenerationJobRepository.save(job)
                .then(applicationDetailsRepository.findByApplicationIdAndTenantId(job.getApplicationId(), job.getTenantId()))
                .flatMap(details -> {
                    List<String> requiredPatterns = ackMapping != null ? ackMapping.getRequiredPatterns() : List.of();
                    systemAttributeHelper.systemAttrMap(systemAttrMap, details, service, documentTinyUrl, requiredPatterns, "DOCUMENT");

                    Integer outFmtId = job.getOutputFormatId() != null ? job.getOutputFormatId().intValue() : -1;
                    applicationFlowLogs.info("TxnId : {} | Executing background ACK generation for jobId: {}, outputFormatId: {}",
                            job.getTxnId(), job.getId(), outFmtId);

                    return reactiveApiClient.generateDocument(
                            user,
                            job.getServiceId(),
                            outFmtId,
                            job.getApplicationId(),
                            false,
                            job.getTxnId(),
                            systemAttrMap
                    ).map(genResponse -> Tuples.of(details, genResponse));
                })
                .flatMap(tuple -> {
                    ApplicationDetails details = tuple.getT1();
                    GenerateDocDResDTO genResponse = tuple.getT2();

                    applicationFlowLogs.info("TxnId : {} | Background ACK generated successfully. UploadId: {}, Status: {}",
                            job.getTxnId(), genResponse.getUploadId(), genResponse.getStatus());

                    job.setStatus("COMPLETED");
                    job.setUploadId(genResponse.getUploadId());
                    job.setUpOn(LocalDateTime.now());
                    job.setNew(false);

                    // Resolve processId from flow or database
                    return resolveProcessId(job, flow)
                            .flatMap(resolvedProcessId -> {
                                // Save record into application_document_submission so applicant can access it
                                ApplicationDocumentSubmissionEntity submission = new ApplicationDocumentSubmissionEntity();
                                submission.setId(createUniqueId());
                                submission.setTxnId(job.getTxnId());
                                submission.setApplicationId(job.getApplicationId());
                                submission.setServiceId(job.getServiceId());
                                submission.setTaskId(job.getTaskId());
                                submission.setReferenceId(job.getReferenceId());
                                submission.setDocumentName(job.getDocumentName());
                                submission.setProcessId(resolvedProcessId);
                                submission.setUploadId(genResponse.getUploadId());
                                submission.setSignedUploadId("");
                                submission.setMerged(Boolean.FALSE);
                                submission.setStatus("P");
                                submission.setCreatedBy(job.getCrBy());
                                submission.setCreatedOn(LocalDateTime.now());
                                submission.setTenantId(job.getTenantId());
                                submission.setDocumentTinyUrl(documentTinyUrl);
                                submission.setNew(true);

                                // Also save log
                                ApplicationDocumentLogEntity log = new ApplicationDocumentLogEntity();
                                log.setId(createUniqueId());
                                log.setTxnId(job.getTxnId());
                                log.setApplicationId(job.getApplicationId());
                                log.setServiceId(job.getServiceId());
                                log.setTaskId(job.getTaskId());
                                log.setProcessId(resolvedProcessId);
                                log.setReferenceId(job.getReferenceId());
                                log.setDocumentName(job.getDocumentName());
                                log.setSourceType("SYSTEM_GENERATED");
                                log.setUploadId(genResponse.getUploadId());
                                log.setStatus("P");
                                log.setCreatedBy(job.getCrBy() != null ? job.getCrBy().intValue() : 0);
                                log.setCreatedOn(LocalDateTime.now());
                                log.setTenantId(job.getTenantId());
                                log.setDocumentTinyUrl(documentTinyUrl);
                                log.setNewEntity(true);

                                // Construct TrackingDocument
                                TrackingDocument trackingDoc = new TrackingDocument();
                                trackingDoc.setUploadId(genResponse.getUploadId());
                                trackingDoc.setDocumentName(job.getDocumentName() != null ? job.getDocumentName() : "System-Generated ACK");
                                trackingDoc.setReferenceId(job.getReferenceId());
                                trackingDoc.setSourceType("SYSTEM_GENERATED");
                                List<String> viewPerm = (ackMapping != null && ackMapping.getViewPermission() != null && !ackMapping.getViewPermission().isEmpty())
                                        ? ackMapping.getViewPermission()
                                        : List.of("applicant", "official");
                                trackingDoc.setViewPermission(viewPerm);
                                trackingDoc.setDocumentTinyUrl(documentTinyUrl);

                                // Build REST request for tracking service
                                TrackingDocumentAttachRequest attachReq = new TrackingDocumentAttachRequest();
                                attachReq.setApplicationId(job.getApplicationId());
                                attachReq.setApplicationRefNo(details != null ? details.getReferenceNo() : null);
                                attachReq.setProcessId(resolvedProcessId);
                                attachReq.setTenantId(job.getTenantId());
                                attachReq.setUploadId(genResponse.getUploadId());
                                attachReq.setDocumentName(trackingDoc.getDocumentName());
                                attachReq.setSourceType(trackingDoc.getSourceType());
                                attachReq.setViewPermission(viewPerm);
                                attachReq.setDocumentTinyUrl(documentTinyUrl);

                                applicationFlowLogs.info("TxnId : {} | Sharing generated ACK to tracking service. ProcessId: {}, UploadId: {}",
                                        job.getTxnId(), resolvedProcessId, genResponse.getUploadId());

                                return ackGenerationJobRepository.save(job)
                                        .then(applicationDocumentSubmissionRepository.save(submission))
                                        .then(applicationDocumentLogRepository.save(log))
                                        .then(reactiveApiClient.attachTrackingDocument(user, attachReq))
                                        .then();
                            });
                })
                .onErrorResume(e -> {
                    applicationFlowLogs.error("TxnId : {} | Background ACK generation failed for jobId: {}: {}",
                            job.getTxnId(), job.getId(), e.getMessage(), e);
                    job.setStatus("FAILED");
                    job.setRetryCount(job.getRetryCount() != null ? job.getRetryCount() + 1 : 1);
                    job.setErrorMessage(e.getMessage());
                    job.setUpOn(LocalDateTime.now());
                    job.setNew(false);
                    return ackGenerationJobRepository.save(job).then();
                });
    }

    private Mono<String> resolveProcessId(AckGenerationJobEntity job, ApplicationFlowStatusEntity flow) {
        if (flow != null && flow.getCurrentProcess() != null && flow.getCurrentProcess().getProcessId() != null
                && !flow.getCurrentProcess().getProcessId().isBlank()) {
            return Mono.just(flow.getCurrentProcess().getProcessId());
        }

        return currentProcessRepository
                .findByServiceIdAndApplicationIdAndCurrentTaskAndActionTakenAndTenantId(
                        job.getServiceId(),
                        job.getApplicationId(),
                        job.getTaskId(),
                        "Y",
                        job.getTenantId()
                )
                .map(CurrentProcess::getProcessId)
                .switchIfEmpty(
                        currentProcessRepository
                                .findByApplicationIdAndCurrentTaskAndActionTaken(
                                        job.getApplicationId(),
                                        job.getTaskId(),
                                        "Y"
                                )
                                .map(CurrentProcess::getProcessId)
                )
                .switchIfEmpty(
                        currentProcessRepository
                                .findByApplicationIdAndTenantId(job.getApplicationId(), job.getTenantId())
                                .map(CurrentProcess::getProcessId)
                )
                .defaultIfEmpty(createUniqueId().toString());
    }
}
