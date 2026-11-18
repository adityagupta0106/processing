package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static java.util.Objects.isNull;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.InboxApplReqDTO;
import com.serviceplus.form.validation.dto.WebServiceReinitiateRequestDTO;
import java.util.List;
import com.serviceplus.form.validation.dto.GuestSessionContext;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;

import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

@Service
@SanitizeRequest
public class PreProcessingService {

    @Autowired
    private PreProcessingFacade preProcessingFacade;

    @Autowired
    private ReactiveApiClient reactiveApiClient;

    @Autowired
    private GuestSessionService guestSessionService;

    @Autowired
    private WorkflowWebServiceRetrySchedulerService workflowWebServiceRetrySchedulerService;

    public Mono<ServerResponse> serviceList(
            ServerHttpRequest request) {

        try {
            UserSessionObject user =
                    getUserSessionDetails(request);

            return preProcessingFacade
                    .getServiceList(user)
                    .flatMap(response ->
                            ServerResponse.ok()
                                    .bodyValue(response)
                    );

        } catch (Exception e) {
            e.printStackTrace();

            return Mono.error(
                    new SPRuntimeError(
                            "Internal Server Error",
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            ""
                    )
            );
        }
    }

    public Mono<ServerResponse> apply(
            ServerRequest fluxRequest) {

        try {
            UserSessionObject user =
                    getUserSessionDetails(
                            fluxRequest.exchange()
                                    .getRequest()
                    );

            return applyInternal(
                    fluxRequest,
                    user
            );

        } catch (Exception e) {
            e.printStackTrace();

            return Mono.error(
                    new SPRuntimeError(
                            "Internal Server Error",
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            null
                    )
            );
        }
    }

    private Mono<ServerResponse> applyInternal(
            ServerRequest fluxRequest,
            UserSessionObject user) {

        Optional<String> applyKeyOpt =
                fluxRequest.queryParam(
                        "serviceKey"
                );

        Optional<String> serviceIdOpt =
                fluxRequest.queryParam(
                        "serviceId"
                );

        Optional<String> appIdOpt =
                fluxRequest.queryParam(
                        "appId"
                );

        if (applyKeyOpt.isEmpty()
                || serviceIdOpt.isEmpty()) {

            return Mono.error(
                    new SPRuntimeError(
                            "Parameters missing",
                            HttpStatus.BAD_REQUEST,
                            null
                    )
            );
        }

        String applyKey =
                applyKeyOpt.get();

        String serviceId =
                serviceIdOpt.get();

        String appId =
                appIdOpt.orElse(null);

        ServerHttpRequest request =
                fluxRequest.exchange()
                        .getRequest();

        final ServiceMeta service;

        try {
            service =
                    preProcessingFacade
                            .decryptApplyKey(
                                    applyKey
                            );

        } catch (Exception e) {
            return Mono.error(
                    new SPRuntimeError(
                            "Invalid service key",
                            HttpStatus.BAD_REQUEST,
                            null
                    )
            );
        }

        if (service == null
                || service.getServiceId() == null
                || !service.getServiceId()
                .toString()
                .equals(serviceId)) {

            return Mono.error(
                    new SPRuntimeError(
                            "Key mismatch",
                            HttpStatus.NOT_ACCEPTABLE,
                            null
                    )
            );
        }

        return preProcessingFacade
                .getFormDataAndSaveTempTxn(
                        appId,
                        service,
                        user,
                        request
                )
                .flatMap(response -> {
                    GuestSessionContext context = fluxRequest.exchange()
                            .getAttribute(GuestSessionService.CONTEXT_ATTRIBUTE);
                    if (context == null) {
                        return Mono.just(response);
                    }
                    return guestSessionService.bindTxn(context, response.getTxnId())
                            .filter(Boolean.TRUE::equals)
                            .switchIfEmpty(Mono.error(new SPRuntimeError(
                                    "Unable to bind guest transaction", HttpStatus.SERVICE_UNAVAILABLE, response.getTxnId())))
                            .thenReturn(response);
                })
                .flatMap(response ->
                        ServerResponse.ok()
                                .bodyValue(response)
                )
                .onErrorResume(Exception.class, ex -> {

                    Throwable actual = Exceptions.unwrap(ex);


                    if (actual instanceof SPRuntimeError spr) {
                        return Mono.error(
                                new SPRuntimeError(
                                        spr.getMessage(),
                                        spr.getErrorCode(),
                                        null
                                )
                        );
                    }

                    return Mono.error(
                            new SPRuntimeError(
                                    "REN-01: "
                                            + actual.getClass().getSimpleName()
                                            + " - "
                                            + actual.getMessage(),
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    null
                            )
                    );
                });
    }

    public Mono<ServerResponse> fetchServiceKeyByToken(
            ServerHttpRequest request,
            String token,
            String quickLink,
            String appId,
            String taskId) {
        UserSessionObject user = getUserSessionDetails(request);
        long userId = (user != null && user.getUserID() != null) ? user.getUserID() : 0L;

        return reactiveApiClient.resolveToken(token)
                .flatMap(tokenData -> {
                    Object serviceIdObj = tokenData.get("serviceId");
                    if (serviceIdObj == null) {
                        return Mono.error(new SPRuntimeError("Service not found for token", HttpStatus.BAD_REQUEST, null));
                    }
                    Integer serviceId = ((Number) serviceIdObj).intValue();
                    Object baseServiceIdObj = tokenData.get("baseServiceId");
                    Integer baseServiceId = baseServiceIdObj != null ? ((Number) baseServiceIdObj).intValue() : serviceId;

                    return reactiveApiClient.fetchServiceMetadata(user, serviceId, "FETCH_SERVICE_KEY")
                            .flatMap(metadata -> {
                                Boolean regRequired = isNull(metadata.getRegistrationRequired()) ? Boolean.TRUE : metadata.getRegistrationRequired();

                                if (Boolean.TRUE.equals(regRequired) && userId == 0L) {
                                    SPRuntimeError err = new SPRuntimeError("User registration required to apply for this service", HttpStatus.FORBIDDEN, null);
                                    Map<String, Object> data = new HashMap<>();
                                    data.put("continue", quickLink != null ? quickLink : "");
                                    data.put("registrationRequired", true);
                                    err.setData(data);
                                    return Mono.error(err);
                                }

                                return preProcessingFacade.fetchServiceKey(
                                        baseServiceId,
                                        user,
                                        request,
                                        appId,
                                        taskId,
                                        serviceId
                                );
                            });
                });
    }

    public Mono<ServerResponse> fetchServiceKeyByToken(
            ServerHttpRequest request,
            String token,
            String appId,
            String taskId) {
        return fetchServiceKeyByToken(request, token, "", appId, taskId);
    }

    public Mono<ServerResponse> fetchServiceKey(
            ServerHttpRequest request,
            String baseServiceId,
            String appId,
            String taskId,
            String serviceId) {

        try {
            UserSessionObject user =
                    getUserSessionDetails(request);

            return preProcessingFacade
                    .fetchServiceKey(
                            Integer.parseInt(baseServiceId),
                            user,
                            request,
                            appId,
                            taskId,
                            Integer.parseInt(serviceId)
                    );

        } catch (Exception e) {
            e.printStackTrace();

            return Mono.error(
                    new SPRuntimeError(
                            "Internal Server Error",
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            null
                    )
            );
        }
    }

    public Mono<ServerResponse> getWFPInbox(
            ServerHttpRequest request) {

        try {
            UserSessionObject user =
                    getUserSessionDetails(request);

            return preProcessingFacade
                    .getWFPInbox(
                            request,
                            user
                    )
                    .flatMap(response ->
                            ServerResponse.ok()
                                    .bodyValue(response)
                    )
                    .onErrorResume(
                            Exception.class,
                            ex -> {

                                Throwable actual =
                                        Exceptions.unwrap(ex);

                                if (actual
                                        instanceof SPRuntimeError spr) {

                                    return Mono.error(
                                            new SPRuntimeError(
                                                    spr.getMessage(),
                                                    spr.getErrorCode(),
                                                    null
                                            )
                                    );
                                }

                                ex.printStackTrace();

                                return Mono.error(
                                        new SPRuntimeError(
                                                "Internal server error [WFP-INBOX-01]",
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                null
                                        )
                                );
                            }
                    );

        } catch (Exception e) {
            e.printStackTrace();

            return Mono.error(
                    new SPRuntimeError(
                            "Internal Server Error",
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            null
                    )
            );
        }
    }


    public Mono<ServerResponse> getWFPOutbox(
            ServerHttpRequest request) {

        try {
            UserSessionObject user = null;
            try {
                user = getUserSessionDetails(request);
            } catch (Exception ignored) {
            }

            return preProcessingFacade
                    .getWFPOutbox(
                            request,
                            user
                    )
                    .flatMap(response ->
                            ServerResponse.ok()
                                    .bodyValue(response)
                    )
                    .onErrorResume(
                            Exception.class,
                            ex -> {

                                Throwable actual =
                                        Exceptions.unwrap(ex);

                                if (actual
                                        instanceof SPRuntimeError spr) {

                                    return Mono.error(
                                            new SPRuntimeError(
                                                    spr.getMessage(),
                                                    spr.getErrorCode(),
                                                    null
                                            )
                                    );
                                }

                                ex.printStackTrace();

                                return Mono.error(
                                        new SPRuntimeError(
                                                "Internal server error [WFP-OUTBOX-01]",
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                null
                                        )
                                );
                            }
                    );

        } catch (Exception e) {
            e.printStackTrace();

            return Mono.error(
                    new SPRuntimeError(
                            "Internal Server Error",
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            null
                    )
            );
        }
    }

    public Mono<ServerResponse> getApplicantInbox(
            ServerHttpRequest request) {

        try {
            UserSessionObject user =
                    getUserSessionDetails(request);

            return DataBufferUtils
                    .join(request.getBody())
                    .map(dataBuffer -> {

                        try {
                            byte[] bytes =
                                    new byte[
                                            dataBuffer
                                                    .readableByteCount()
                                    ];

                            dataBuffer.read(bytes);

                            return new String(
                                    bytes,
                                    StandardCharsets.UTF_8
                            );

                        } finally {
                            DataBufferUtils.release(
                                    dataBuffer
                            );
                        }
                    })
                    .flatMap(requestBody ->
                            preProcessingFacade
                                    .getApplicantInbox(
                                            requestBody,
                                            request,
                                            user
                                    )
                    )
                    .flatMap(response ->
                            ServerResponse.ok()
                                    .bodyValue(response)
                    )
                    .onErrorResume(
                            Exception.class,
                            ex -> {

                                Throwable actual =
                                        Exceptions.unwrap(ex);

                                if (actual
                                        instanceof SPRuntimeError spr) {

                                    return Mono.error(
                                            new SPRuntimeError(
                                                    spr.getMessage(),
                                                    spr.getErrorCode(),
                                                    null
                                            )
                                    );
                                }

                                ex.printStackTrace();

                                return Mono.error(
                                        new SPRuntimeError(
                                                "Internal server error [APPLICANT-INBOX-01]",
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                null
                                        )
                                );
                            }
                    );

        } catch (Exception e) {
            e.printStackTrace();

            return Mono.error(
                    new SPRuntimeError(
                            "Internal Server Error",
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            null
                    )
            );
        }
    }

    public Mono<ServerResponse> getWFPInboxFilterApplications(
            ServerHttpRequest request,
            InboxApplReqDTO inboxApplReqDTO) {

        try {
            UserSessionObject user =
                    getUserSessionDetails(request);

            return preProcessingFacade
                    .getWFPInboxFilterApplications(
                            request,
                            user,
                            inboxApplReqDTO
                    )
                    .flatMap(response ->
                            ServerResponse.ok()
                                    .bodyValue(response)
                    )
                    .onErrorResume(
                            Exception.class,
                            ex -> {

                                Throwable actual =
                                        Exceptions.unwrap(ex);

                                if (actual
                                        instanceof SPRuntimeError spr) {

                                    return Mono.error(
                                            new SPRuntimeError(
                                                    spr.getMessage(),
                                                    spr.getErrorCode(),
                                                    null
                                            )
                                    );
                                }

                                ex.printStackTrace();

                                return Mono.error(
                                        new SPRuntimeError(
                                                "Internal server error [WFP-INBOX-01]",
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                null
                                        )
                                );
                            }
                    );

        } catch (Exception e) {
            e.printStackTrace();

            return Mono.error(
                    new SPRuntimeError(
                            "Internal Server Error",
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            null
                    )
            );
        }
    }

    public Mono<ServerResponse> getPendingApplications(
            ServerHttpRequest request) {

        try {
            UserSessionObject user =
                    getUserSessionDetails(request);

            return preProcessingFacade
                    .getInboxApplications(
                            request,
                            user
                    )
                    .flatMap(response ->
                            ServerResponse.ok()
                                    .bodyValue(response)
                    )
                    .onErrorResume(
                            Exception.class,
                            ex -> {

                                Throwable actual =
                                        Exceptions.unwrap(ex);

                                if (actual
                                        instanceof SPRuntimeError spr) {

                                    return Mono.error(
                                            new SPRuntimeError(
                                                    spr.getMessage(),
                                                    spr.getErrorCode(),
                                                    null
                                            )
                                    );
                                }

                                ex.printStackTrace();

                                return Mono.error(
                                        new SPRuntimeError(
                                                "Internal server error [WFP-INBOX-01]",
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                null
                                        )
                                );
                            }
                    );

        } catch (Exception e) {
            e.printStackTrace();

            return Mono.error(
                    new SPRuntimeError(
                            "Internal Server Error",
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            null
                    )
            );
        }
    }
    public Mono<ServerResponse> getPendingOutboxApplications(
            ServerHttpRequest request) {

        try {
            UserSessionObject user = null;
            try {
                user = getUserSessionDetails(request);
            } catch (Exception ignored) {
            }

            return preProcessingFacade
                    .getOutboxApplications(
                            request,
                            user
                    )
                    .flatMap(response ->
                            ServerResponse.ok()
                                    .bodyValue(response)
                    )
                    .onErrorResume(
                            Exception.class,
                            ex -> {
                                Throwable actual =
                                        Exceptions.unwrap(ex);

                                if (actual instanceof SPRuntimeError spr) {
                                    return Mono.error(
                                            new SPRuntimeError(
                                                    spr.getMessage(),
                                                    spr.getErrorCode(),
                                                    null
                                            )
                                    );
                                }

                                ex.printStackTrace();

                                return Mono.error(
                                        new SPRuntimeError(
                                                "Internal server error [OUTBOX-APPL-01]",
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                null
                                        )
                                );
                            }
                    );

        } catch (Exception e) {
            e.printStackTrace();

            return Mono.error(
                    new SPRuntimeError(
                            "Internal Server Error",
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            null
                    )
            );
        }
    }

    public Mono<ServerResponse> reinitiateWebService(ServerRequest request) {

        return request.bodyToMono(WebServiceReinitiateRequestDTO.class)
                .defaultIfEmpty(new WebServiceReinitiateRequestDTO())
                .flatMap(dto -> {
                    if (dto.getType() == null) {
                        request.queryParam("type").ifPresent(sc -> {
                            try {
                                dto.setType(Integer.parseInt(sc));
                            } catch (NumberFormatException ignored) {}
                        });
                    }
                    if (dto.getApplicationId() == null || dto.getApplicationId().isEmpty()) {
                        request.queryParam("applicationId").ifPresent(appId -> {
                            dto.setApplicationId(List.of(appId));
                        });
                    }
                    return workflowWebServiceRetrySchedulerService.reinitiate(dto)
                            .flatMap(response -> ServerResponse.ok().bodyValue(response));
                })
                .onErrorResume(Exception.class, ex -> {
                    Throwable actual = Exceptions.unwrap(ex);
                    if (actual instanceof SPRuntimeError spr) {
                        return Mono.error(spr);
                    }
                    ex.printStackTrace();
                    return Mono.error(
                            new SPRuntimeError(
                                    "Internal server error [WS-REINITIATE-01]",
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    null
                            )
                    );
                });
    }
    
    //----------------------
    public Mono<ServerResponse> getSlaApplications(
            ServerHttpRequest request) {

        UserSessionObject user = getUserSessionDetails(request);

        if (user == null) {
            return Mono.error(new SPRuntimeError(
                    "Invalid session",
                    HttpStatus.UNAUTHORIZED,
                    null
            ));
        }

        return preProcessingFacade
                .getSlaApplications(request, user)
                .flatMap(response ->
                        ServerResponse.ok().bodyValue(response)
                );
    }
}
