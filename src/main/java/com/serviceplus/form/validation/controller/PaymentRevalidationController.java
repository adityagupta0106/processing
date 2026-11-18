package com.serviceplus.form.validation.controller;

import com.serviceplus.form.validation.dto.PaymentInitiateResponse;
import com.serviceplus.form.validation.dto.PaymentRevalidationResponse;
import com.serviceplus.form.validation.service.PaymentProcessingService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@RestController
public class PaymentRevalidationController {

    private final PaymentProcessingService paymentProcessingService;

    public PaymentRevalidationController(PaymentProcessingService paymentProcessingService) {
        this.paymentProcessingService = paymentProcessingService;
    }

    public Mono<ServerResponse> revalidatePayment(ServerRequest request) {
        return paymentProcessingService.revalidatePaymentById(request)
                .flatMap(response ->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> getApplicationsAvailableForRevalidation(ServerRequest request) {
        return paymentProcessingService.getApplicationsAvailableForRevalidation(request)
                .flatMap(applications ->
                        ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(applications)
                );
    }


}
