package com.serviceplus.form.validation.handlers;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.dto.PaymentSelectionRequest;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.service.PaymentProcessingService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_PAYMENT;

@Service
@SanitizeRequest
public class PaymentHandler implements ApplicationFlowHandler {

    private final PaymentProcessingService paymentProcessingService;

    public PaymentHandler(PaymentProcessingService paymentProcessingService) {
        this.paymentProcessingService = paymentProcessingService;
    }

    @Override
    public String getActivityType() {
        return ACTIVITY_PAYMENT;
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
                .bodyToMono(PaymentSelectionRequest.class)
                .defaultIfEmpty(new PaymentSelectionRequest())
                .flatMap(paymentRequest -> {

                    if (paymentRequest.getGatewayCode() == null) {
                        return paymentProcessingService.fetchPaymentOptions(applicationId, request, flow, txnId);
                    }

                    if(paymentRequest.getActionType().equals("webhook")){
                        return paymentProcessingService.handlePaymentWebhook(applicationId, flow, request, paymentRequest, txnId,fetch,service,fromDraft);
                    }

                    return paymentProcessingService.initiatePayment(applicationId, flow, request, paymentRequest, txnId,fetch,service,fromDraft);
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

        return paymentProcessingService
                .fetchPayment(
                        applicationId,
                        flow.getTaskId(),
                        request,
                        txnId,
                        flow,
                        service,fromDraft
                )
                .flatMap(payment ->
                        ServerResponse
                                .ok()
                                .bodyValue(payment)
                );
    }
}