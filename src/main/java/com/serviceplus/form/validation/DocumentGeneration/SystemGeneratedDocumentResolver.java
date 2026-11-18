package com.serviceplus.form.validation.DocumentGeneration;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.Helpers.SystemAttributeHelper;
import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.ApplicationDocumentLogEntity;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.ApplicationDocumentLogRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import com.serviceplus.form.validation.service.ReactiveApiClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.TinyUrlUtil.generateDocumentTinyUrl;

import static java.util.Objects.isNull;
import com.serviceplus.form.validation.pattern.dto.PatternConfigurationDTO;
import com.serviceplus.form.validation.pattern.dto.PatternGenerationRequestDTO;
import com.serviceplus.form.validation.pattern.service.PatternGenerationService;
import reactor.core.publisher.Flux;

@Service
public class SystemGeneratedDocumentResolver {

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    private static final String SYSTEM_GENERATED = "SYSTEM_GENERATED";

    private final ReactiveApiClient reactiveApiClient;

    private final ApplicationDocumentLogRepository applicationDocumentRepository;
    private final ApplicationDetailsRepository applicationDetailsRepository;
    private final SystemAttributeHelper systemAttributeHelper;
    private final PatternGenerationService patternGenerationService;

    public SystemGeneratedDocumentResolver(ReactiveApiClient reactiveApiClient, ApplicationDocumentLogRepository applicationDocumentRepository, ApplicationDetailsRepository applicationDetailsRepository, SystemAttributeHelper systemAttributeHelper, PatternGenerationService patternGenerationService) {
        this.reactiveApiClient = reactiveApiClient;
        this.applicationDocumentRepository = applicationDocumentRepository;
        this.applicationDetailsRepository = applicationDetailsRepository;
        this.systemAttributeHelper = systemAttributeHelper;
        this.patternGenerationService = patternGenerationService;
    }

    public Mono<List<ResolvedDocument>> resolve(UserSessionObject user,
                                                ApplicationFlowStatusEntity flow,
                                                ServiceMeta service,
                                                DocumentGenerationDetails.DocMappingDTO mapping,
                                                String txnId, String domain, String serviceHeaderUrl) {

        Mono<ApplicationDocumentLogEntity> documentMono;

        if (flow.getCurrentProcess() != null) {

            documentMono = applicationDocumentRepository
                    .findFirstByApplicationIdAndProcessIdAndReferenceIdAndSourceTypeAndStatusOrderByCreatedOnDesc(
                            flow.getApplicationId(),
                            flow.getCurrentProcess().getProcessId(),
                            mapping.getReferenceId(),
                            SYSTEM_GENERATED,
                            "P");

        } else {

            documentMono = applicationDocumentRepository
                    .findFirstByApplicationIdAndTaskIdAndReferenceIdAndSourceTypeAndStatusOrderByCreatedOnDesc(
                            flow.getApplicationId(),
                            service.getTaskId(),
                            mapping.getReferenceId(),
                            SYSTEM_GENERATED,
                            "P");
        }

        return documentMono
                .flatMap(existing ->
                        buildResolvedDocument(existing, mapping, user, flow.getTxnId()))
                .switchIfEmpty(
                        generateDocument(user, flow, service, mapping, txnId,domain,serviceHeaderUrl)
                )
                .doOnError(ex ->
                        applicationFlowLogs.error("Failed to resolve system document. ApplicationId: {}, txnId: {}", flow.getApplicationId(), txnId, ex)
                );
    }

    private Mono<List<ResolvedDocument>> generateDocument(UserSessionObject user,
                                                          ApplicationFlowStatusEntity flow,
                                                          ServiceMeta service,
                                                          DocumentGenerationDetails.DocMappingDTO mapping, String txnId, String domain, String serviceHeaderUrl) {

        Integer outputFormatId = mapping.getSystemGeneratedDocument().getValue();

        Mono<ApplicationDetails> applicationDetails =
                applicationDetailsRepository.findByApplicationIdAndTenantId(
                        flow.getApplicationId(),
                        user.getTenantId());
        Map<String, Object> systemAttrMap = new HashMap<>();
        String documentTinyUrl = generateDocumentTinyUrl(domain, serviceHeaderUrl);
        applicationFlowLogs.info(
                "Generating system document. ApplicationId: {}, ServiceId: {}, OutputFormatId: {}, txnId: {}",
                flow.getApplicationId(),
                service.getServiceId(),
                outputFormatId,
                flow.getTxnId());

        return applicationDetails
                .flatMap(details -> {

                    applicationFlowLogs.info("Application details found. ApplicationId: {}, TenantId: {}, txnId: {}", flow.getApplicationId(), user.getTenantId(), flow.getTxnId());
                    List<String> requiredPatterns = mapping.getRequiredPatterns();
                    applicationFlowLogs.info("TxnId : {} | Document mapping prepared with requiredPatterns: {} for documentId: {}", flow.getTxnId(), requiredPatterns, mapping.getDocumentName());
                    systemAttributeHelper.systemAttrMap(systemAttrMap, details, service, documentTinyUrl, requiredPatterns, "DOCUMENT");

                    return reactiveApiClient.generateDocument(
                            user,
                            service.getServiceId(),
                            outputFormatId,
                            flow.getApplicationId(),
                            false,
                            flow.getTxnId(),
                            systemAttrMap);
                })
                .flatMap(response -> {

                    applicationFlowLogs.info(
                            "Document generated successfully. UploadId: {}, Status: {}, txnId: {}",
                            response.getUploadId(),
                            response.getStatus(),
                            flow.getTxnId());

                    String processId = isNull(flow.getCurrentProcess())
                            ? null
                            : flow.getCurrentProcess().getProcessId();

                    ApplicationDocumentLogEntity entity =
                            new ApplicationDocumentLogEntity();

                    entity.setNewEntity(Boolean.TRUE);
                    entity.setId(createUniqueId());
                    entity.setApplicationId(flow.getApplicationId());
                    entity.setTxnId(flow.getTxnId());
                    entity.setServiceId(service.getServiceId());
                    entity.setTaskId(flow.getTaskId());
                    entity.setProcessId(processId);

                    entity.setReferenceId(mapping.getReferenceId());
                    entity.setDocumentName(mapping.getDocumentName());

                    entity.setSourceType(SYSTEM_GENERATED);
                    entity.setUploadId(response.getUploadId());
                    entity.setPreviewUrl(response.getPreviewUrl());
                    entity.setStatus("P");
                    entity.setCreatedOn(LocalDateTime.now());
                    entity.setTenantId(user.getTenantId());
                    entity.setDocumentTinyUrl(documentTinyUrl);

                    return applicationDocumentRepository
                            .save(entity)
                            .flatMap(saved -> {
                                saved.setPreviewUrl(response.getPreviewUrl());
                                return buildResolvedDocument(
                                        saved,
                                        mapping,
                                        user,
                                        flow.getTxnId());
                            });
                })
                .doOnError(ex ->
                        applicationFlowLogs.error(
                                "Failed to generate system document for ApplicationId: {}, txnId: {}",
                                flow.getApplicationId(),
                                txnId,
                                ex));
    }
    private Mono<List<ResolvedDocument>> buildResolvedDocument(
            ApplicationDocumentLogEntity entity,
            DocumentGenerationDetails.DocMappingDTO mapping,
            UserSessionObject user,
            String txnId) {

        ResolvedDocument document = new ResolvedDocument();

        document.setUploadId(entity.getUploadId());
        document.setDocumentId(entity.getId());
        document.setReferenceId(mapping.getReferenceId());
        document.setDocumentName(mapping.getDocumentName());
        document.setSourceType("systemGenerated");
        document.setStatus(entity.getStatus());
        document.setSigningPlacement(mapping.getSigningPlacement());
        document.setEsignPlacementMode(mapping.getEsignPlacementMode());
        document.setEsignPositionVariable(mapping.getEsignPositionVariable());

        List<String> signatureModes = new java.util.ArrayList<>();
        if (mapping.getDigitalSignatureMethods() != null && !mapping.getDigitalSignatureMethods().isEmpty()) {
            signatureModes.addAll(mapping.getDigitalSignatureMethods());
        } else if (Boolean.TRUE.equals(mapping.getIsEsignRequired())) {
            signatureModes.add("eSign");
        }

        boolean hasEsign = signatureModes.stream()
                .anyMatch(mode -> mode != null && "esign".equalsIgnoreCase(mode.trim()));
        if (hasEsign) {
            mapping.setIsEsignRequired(true);
        }

        boolean isDigitalSignatureRequired = "true".equalsIgnoreCase(mapping.getDigitalSignatureRequired())
                || "yes".equalsIgnoreCase(mapping.getDigitalSignatureRequired())
                || "optional".equalsIgnoreCase(mapping.getDigitalSignatureRequired())
                || Boolean.TRUE.equals(mapping.getIsEsignRequired())
                || !signatureModes.isEmpty();

        return reactiveApiClient
                .getFileView(user, List.of(entity.getUploadId()), txnId)
                .flatMap(response -> {

                    FileViewResponse.Data data = response.getResults().get(entity.getUploadId());

                    if (data != null && data.getData() != null) {

                        data.getData().forEach(item -> {

                            switch (item.getType()) {

                                case "preview" ->
                                        document.setPreviewUrl(item.getData().getUrl());

                                case "download" ->
                                        document.setDownloadUrl(item.getData().getUrl());

                                case "thumbnail" ->
                                        document.setThumbnailUrl(item.getData().getUrl());
                            }
                        });
                    }

                    if (isDigitalSignatureRequired) {

                        return reactiveApiClient
                                .getFileBase64(user, entity.getUploadId())
                                .map(bytes -> {

                                    document.setBase64Content(bytes);
                                    document.setDigitalSignatureModes(signatureModes);

                                    return List.of(document);
                                });
                    }

                    document.setDigitalSignatureModes(signatureModes);

                    return Mono.just(List.of(document));
                });
    }

    public Mono<List<ResolvedDocument>> resolveSubmission(UserSessionObject user,
                                                          ApplicationFlowStatusEntity flow,
                                                          ServiceMeta service,
                                                          DocumentGenerationDetails.DocMappingDTO mapping,
                                                          DocumentGenerationRequest.DocumentSectionRequest.DocumentRequest document,
                                                          String txnId) {

        return applicationDocumentRepository
                .findById(document.getDocumentId())
                .switchIfEmpty(Mono.error(
                        new SPRuntimeError("System generated document not found.", HttpStatus.BAD_REQUEST, flow.getTxnId()))
                )
                .flatMap(entity -> {

                    if (!flow.getApplicationId().equals(entity.getApplicationId())
                            || !service.getTaskId().equals(entity.getTaskId())
                            || !mapping.getReferenceId().equals(entity.getReferenceId())
                            || !SYSTEM_GENERATED.equalsIgnoreCase(entity.getSourceType())) {

                        return Mono.error(new SPRuntimeError("Invalid system generated document.", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                    }

                    return buildResolvedDocument(entity, mapping, user, txnId);
                });
    }
}