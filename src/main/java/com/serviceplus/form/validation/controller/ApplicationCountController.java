package com.serviceplus.form.validation.controller;

import com.serviceplus.form.validation.dto.ApplicationCountRequestDTO;
import com.serviceplus.form.validation.dto.ApplicationCountResponseDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.service.ApplicationCountService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

@Component
public class ApplicationCountController {

    private static final Logger log = LogManager.getLogger(ApplicationCountController.class);

    private final ApplicationCountService applicationCountService;

    public ApplicationCountController(ApplicationCountService applicationCountService) {
        this.applicationCountService = applicationCountService;
    }

    /**
     * Unified handler for GET and POST requests:
     * - Frontend can call via GET /a/application/count?serviceId=10023
     * - Backend (ServiceMetadata) can call via GET /b/application/count?serviceId=10023&tenantId=JH
     * - Also supports POST with JSON body {"serviceId": 10023, "tenantId": "JH"}
     */
    public Mono<ServerResponse> getCount(ServerRequest request) {
        Optional<String> serviceIdParam = request.queryParam("serviceId");
        Optional<String> tenantIdParam = request.queryParam("tenantId");

        if (serviceIdParam.isPresent()) {
            try {
                Integer serviceId = Integer.parseInt(serviceIdParam.get());
                String tenantId = tenantIdParam.orElse(null);

                // Fallback to session tenantId if not provided in query param
                if (tenantId == null || tenantId.isBlank()) {
                    try {
                        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
                        if (user != null && user.getTenantId() != null && !user.getTenantId().isBlank()) {
                            tenantId = user.getTenantId();
                        }
                    } catch (Exception ignored) {
                    }
                }

                return applicationCountService.getApplicationCount(serviceId, tenantId)
                        .flatMap(dto -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(dto));
            } catch (NumberFormatException e) {
                log.warn("Invalid serviceId param: {}", serviceIdParam.get());
                return ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ApplicationCountResponseDTO(0L));
            }
        }

        // Handle POST body if query params are not present
        return request.bodyToMono(ApplicationCountRequestDTO.class)
                .flatMap(body -> {
                    String tenantId = body.getTenantId();
                    if (tenantId == null || tenantId.isBlank()) {
                        try {
                            UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
                            if (user != null && user.getTenantId() != null && !user.getTenantId().isBlank()) {
                                tenantId = user.getTenantId();
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    return applicationCountService.getApplicationCount(body.getServiceId(), tenantId);
                })
                .flatMap(dto -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(dto))
                .switchIfEmpty(ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ApplicationCountResponseDTO(0L)));
    }
}
