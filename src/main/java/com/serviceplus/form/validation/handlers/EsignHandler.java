package com.serviceplus.form.validation.handlers;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.dto.EsignInitiateRequest;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.service.EsignProcessingService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_ESIGN;

@Service
@SanitizeRequest
public class EsignHandler implements ApplicationFlowHandler {

    private final EsignProcessingService esignProcessingService;

    public EsignHandler(EsignProcessingService esignProcessingService) {
        this.esignProcessingService = esignProcessingService;
    }

    @Override
    public String getActivityType() {
        return ACTIVITY_ESIGN;
    }

    @Override
    public Mono<ServerResponse> process(
            String applicationId,
            ServerRequest request,
            String statusKey,
            String txnId,
            Mono<TempTransactionLogs> fetch,
            ApplicationFlowStatusEntity flow,
            ServiceMeta service,
            boolean fromDraft) {

        return request
                .bodyToMono(EsignInitiateRequest.class)
                .defaultIfEmpty(new EsignInitiateRequest())
                .flatMap(esignRequest -> {

                    String mode = esignRequest.getMode();
                    if (mode == null || mode.isBlank()) {
                        mode = esignRequest.getActionType();
                    }

                    if ("webhook".equalsIgnoreCase(mode)) {
                        return esignProcessingService.handleEsignWebhook(
                                applicationId, flow, request, esignRequest, txnId, fetch, service, fromDraft);
                    }

                    return esignProcessingService.initiateEsign(
                            applicationId, flow, request, esignRequest, txnId, fetch, service, fromDraft);
                });
    }

    @Override
    public Mono<ServerResponse> fetch(
            String applicationId,
            ServerRequest request,
            String statusKey,
            String txnId,
            Mono<TempTransactionLogs> fetch,
            ApplicationFlowStatusEntity flow,
            ServiceMeta service,
            boolean fromDraft) {

        return esignProcessingService
                .fetchEsign(
                        applicationId,
                        flow.getTaskId(),
                        request,
                        txnId,
                        flow,
                        service,
                        fromDraft
                );
    }
}
