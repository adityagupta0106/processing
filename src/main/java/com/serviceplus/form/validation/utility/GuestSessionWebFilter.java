package com.serviceplus.form.validation.utility;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.GuestSessionContext;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;
import com.serviceplus.form.validation.service.GuestSessionService;
import com.serviceplus.form.validation.service.ReactiveApiClient;

import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.service.GuestSessionService.CONTEXT_ATTRIBUTE;
import static com.serviceplus.form.validation.service.GuestSessionService.COOKIE_NAME;

@Component
public class GuestSessionWebFilter implements WebFilter {

    private static final Logger log = LogManager.getLogger(GuestSessionWebFilter.class);

    private static final String ENTRY = "/a/apply/serviceKey";
    private static final String RENDER = "/a/form/render";
    private static final String ACTION = "/a/form/handler/action";

    private final GuestSessionService sessions;
    private final ReactiveApiClient api;
    private final ProcessingTxnRepository transactions;

    @Value("${service.context.path}")
    private String contextPath;

    @Value("${guest.enabled:false}")
    private boolean enabled;

    @Value("${guest.cookie-secure:true}")
    private boolean secureCookie;

    public GuestSessionWebFilter(
            GuestSessionService sessions,
            ReactiveApiClient api,
            ProcessingTxnRepository transactions) {

        this.sessions = sessions;
        this.api = api;
        this.transactions = transactions;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (!path.startsWith(contextPath + "/")) {
            return chain.filter(exchange);
        }

        String route = path.substring(contextPath.length());
        boolean hasCookie = request.getCookies().containsKey(COOKIE_NAME);
        boolean hasIdentity = hasIdentityHeaders(request);

        if (!hasCookie && hasIdentity) {
            return chain.filter(exchange);
        }

        if (!hasCookie && !isGuestRoute(route)) {
            return chain.filter(exchange);
        }

        Mono<Void> flow;

        try {

            if (!enabled) {

                flow = deny(
                        "Guest application is not available",
                        HttpStatus.FORBIDDEN);

            } else if (hasIdentity) {

                flow = deny(
                        "Identity headers are not allowed with a guest session",
                        HttpStatus.BAD_REQUEST);

            } else if (request.getMethod() != HttpMethod.POST
                    || !isGuestRoute(route)) {

                flow = deny(
                        "Guest operation is not allowed",
                        HttpStatus.FORBIDDEN);

            } else {

                validateOrigin(request);
                validateParameters(request, route);

                int serviceId = serviceId(request);

                if (!hasCookie) {

                    if (!ENTRY.equals(route)) {

                        flow = deny(
                                "Guest session is required",
                                HttpStatus.UNAUTHORIZED);

                    } else {

                        var remote = request.getRemoteAddress();

                        String ip =
                                remote == null || remote.getAddress() == null
                                        ? "unknown"
                                        : remote.getAddress().getHostAddress();

                        flow = sessions.checkStartRateLimit(ip)
                                .then(api.fetchGuestEligibility(serviceId))
                                .switchIfEmpty(
                                        GuestSessionWebFilter
                                                .<com.serviceplus.form.validation.dto.GuestEligibilityDTO>deny(
                                                        "Guest eligibility unavailable",
                                                        HttpStatus.SERVICE_UNAVAILABLE))
                                .flatMap(eligibility -> {

                                    if (Boolean.TRUE.equals(
                                            eligibility.getRegistrationRequired())) {

                                        return writeLoginRequiredResponse(
                                                exchange,
                                                serviceId);
                                    }

                                    sessions.validateEligibility(
                                            serviceId,
                                            eligibility);

                                    return sessions.create(eligibility)
                                            .flatMap(created ->
                                                    sessions.require(created.getToken())
                                                            .flatMap(context -> {

                                                                sessions.validateGuestContext(
                                                                        serviceId,
                                                                        context);

                                                                exchange.getResponse()
                                                                        .beforeCommit(() -> {

                                                                            if (exchange.getResponse()
                                                                                    .getStatusCode() == null
                                                                                    || exchange.getResponse()
                                                                                            .getStatusCode()
                                                                                            .is2xxSuccessful()) {

                                                                                exchange.getResponse()
                                                                                        .addCookie(
                                                                                                ResponseCookie
                                                                                                        .from(
                                                                                                                COOKIE_NAME,
                                                                                                                created.getToken())
                                                                                                        .httpOnly(true)
                                                                                                        .secure(secureCookie)
                                                                                                        .sameSite("Lax")
                                                                                                        .path("/")
                                                                                                        .maxAge(
                                                                                                                created.getExpiresInSeconds())
                                                                                                        .build());

                                                                                log.info(
                                                                                        "Guest session established for serviceId {}",
                                                                                        serviceId);
                                                                            }

                                                                            return Mono.empty();
                                                                        });

                                                                return continueWithGuest(
                                                                        exchange,
                                                                        chain,
                                                                        context);
                                                            }));
                                });
                    }

                } else {

                    var cookies =
                            request.getCookies().get(COOKIE_NAME);

                    if (cookies == null || cookies.size() != 1) {

                        flow = deny(
                                "Invalid guest cookie",
                                HttpStatus.UNAUTHORIZED);

                    } else {

                        flow = sessions.require(cookies.get(0).getValue())
                                .flatMap(context -> {

                                    sessions.validateGuestContext(
                                            serviceId,
                                            context);

                                    Mono<Void> ownership =
                                            ACTION.equals(route)
                                                    ? requireTransaction(
                                                            context,
                                                            request)
                                                    : Mono.empty();

                                    return ownership
                                            .then(api.fetchGuestEligibility(serviceId))
                                            .switchIfEmpty(
                                                    GuestSessionWebFilter
                                                            .<com.serviceplus.form.validation.dto.GuestEligibilityDTO>deny(
                                                                    "Guest eligibility unavailable",
                                                                    HttpStatus.SERVICE_UNAVAILABLE))
                                            .flatMap(eligibility -> {

                                                if (Boolean.TRUE.equals(
                                                        eligibility.getRegistrationRequired())) {

                                                    return deny(
                                                            "Guest application is no longer available",
                                                            HttpStatus.FORBIDDEN);
                                                }

                                                sessions.validateEligibility(
                                                        context,
                                                        eligibility);

                                                if (!ENTRY.equals(route)) {

                                                    ServiceMeta service;

                                                    try {

                                                        service =
                                                                Utility.decryptServiceKeys(
                                                                        required(
                                                                                request,
                                                                                "serviceKey"));

                                                    } catch (Exception ex) {

                                                        throw new SPRuntimeError(
                                                                "Invalid service key",
                                                                HttpStatus.BAD_REQUEST,
                                                                null);
                                                    }

                                                    sessions.validateServiceMeta(
                                                            context,
                                                            service);
                                                }

                                                return continueWithGuest(
                                                        exchange,
                                                        chain,
                                                        context);
                                            });
                                });
                    }
                }
            }

        } catch (Throwable error) {
            return handleGuestError(exchange, error);
        }

        return flow.onErrorResume(
                error -> handleGuestError(exchange, error));
    }

    private Mono<Void> handleGuestError(
            ServerWebExchange exchange,
            Throwable error) {

        if (exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }

        HttpStatus status =
                error instanceof SPRuntimeError spr
                        && spr.getErrorCode() != null
                                ? spr.getErrorCode()
                                : HttpStatus.SERVICE_UNAVAILABLE;

        log.warn(
                "Guest request denied: status {}, reason={}",
                status.value(),
                error.getClass().getSimpleName());

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse()
                .getHeaders()
                .setCacheControl("no-store");

        return exchange.getResponse().setComplete();
    }

    /**
     * Adapts a validated guest session to the existing Processing identity contract.
     */
    private Mono<Void> continueWithGuest(
            ServerWebExchange exchange,
            WebFilterChain chain,
            GuestSessionContext context) {

        ServerHttpRequest trusted =
                exchange.getRequest()
                        .mutate()
                        .headers(headers -> {

                            headers.remove(HttpHeaders.AUTHORIZATION);
                            headers.remove("Atk");

                            headers.set(
                                    "USER-DETAILS",
                                    Utility.entityToString(
                                            sessions.createGuestUser(context)));
                        })
                        .build();

        exchange.getAttributes()
                .put(CONTEXT_ATTRIBUTE, context);

        exchange.getResponse()
                .getHeaders()
                .setCacheControl("no-store");

        return chain.filter(
                exchange.mutate()
                        .request(trusted)
                        .build());
    }

    /**
     * Validates Redis guest ownership and the Processing transaction context.
     */
    private Mono<Void> requireTransaction(
            GuestSessionContext context,
            ServerHttpRequest request) {

        String txnId = required(request, "txnId");
        String appId =
                request.getQueryParams()
                        .getFirst("appId");

        return sessions.requireOwner(context, txnId)
                .then(transactions.findById(txnId))
                .map(txn -> {

                    if (!Objects.equals(
                                context.getServiceId(),
                                txn.getServiceId())
                            || !Objects.equals(
                                context.getTenantId(),
                                txn.getTenantId())
                            || !Objects.equals(
                                context.getTaskId(),
                                txn.getTaskId())
                            || !Objects.equals(
                                context.getFormId(),
                                txn.getFormId())
                            || !Objects.equals(
                                appId,
                                txn.getApplicationId())) {

                        throw new SPRuntimeError(
                                "Guest transaction context mismatch",
                                HttpStatus.FORBIDDEN,
                                txnId);
                    }

                    return true;
                })
                .switchIfEmpty(
                        deny(
                                "Guest transaction access denied",
                                HttpStatus.FORBIDDEN))
                .then();
    }

    private boolean hasIdentityHeaders(
            ServerHttpRequest request) {

        HttpHeaders headers = request.getHeaders();

        return headers.containsKey("USER-DETAILS")
                || headers.containsKey("Atk")
                || headers.containsKey(
                        HttpHeaders.AUTHORIZATION);
    }

    private boolean isGuestRoute(String route) {
        return Set.of(ENTRY, RENDER, ACTION)
                .contains(route);
    }

    private void validateParameters(
            ServerHttpRequest request,
            String route) {

        Set<String> allowed =
                ENTRY.equals(route)
                        ? Set.of("serviceId")
                        : RENDER.equals(route)
                                ? Set.of(
                                        "serviceId",
                                        "serviceKey")
                                : Set.of(
                                        "serviceId",
                                        "serviceKey",
                                        "txnId",
                                        "appId");

        request.getQueryParams()
                .forEach((name, values) -> {

                    if (!allowed.contains(name)
                            || values.size() != 1
                            || values.get(0).isBlank()) {

                        throw new SPRuntimeError(
                                "Invalid guest parameters",
                                HttpStatus.BAD_REQUEST,
                                null);
                    }
                });
    }

    private int serviceId(
            ServerHttpRequest request) {

        try {

            int id =
                    Integer.parseInt(
                            required(
                                    request,
                                    "serviceId"));

            if (id > 0) {
                return id;
            }

        } catch (NumberFormatException ignored) {
            // handled below
        }

        throw new SPRuntimeError(
                "Invalid serviceId",
                HttpStatus.BAD_REQUEST,
                null);
    }

    private String required(
            ServerHttpRequest request,
            String name) {

        String value =
                request.getQueryParams()
                        .getFirst(name);

        if (value == null || value.isBlank()) {
            throw new SPRuntimeError(
                    name + " is required",
                    HttpStatus.BAD_REQUEST,
                    null);
        }

        return value;
    }

    private void validateOrigin(
            ServerHttpRequest request) {

        if ("cross-site".equalsIgnoreCase(
                request.getHeaders()
                        .getFirst("Sec-Fetch-Site"))) {

            throw new SPRuntimeError(
                    "Cross-site guest request denied",
                    HttpStatus.FORBIDDEN,
                    null);
        }

        String origin =
                request.getHeaders()
                        .getOrigin();

        if (origin == null) {
            return;
        }

        URI originUri;

        try {
            originUri = URI.create(origin);
        } catch (IllegalArgumentException ex) {
            throw new SPRuntimeError(
                    "Invalid origin",
                    HttpStatus.FORBIDDEN,
                    null);
        }

        URI target = request.getURI();

        if (!Objects.equals(
                    originUri.getScheme(),
                    target.getScheme())
                || !Objects.equals(
                    originUri.getRawAuthority(),
                    target.getRawAuthority())) {

            throw new SPRuntimeError(
                    "Cross-origin guest request denied",
                    HttpStatus.FORBIDDEN,
                    null);
        }
    }

    /**
     * Typed response consumed by the frontend to continue with the existing login flow.
     * This is intentionally HTTP 200; generic 403 responses must not be treated as
     * "login required".
     */
    private Mono<Void> writeLoginRequiredResponse(
            ServerWebExchange exchange,
            int serviceId) {

        String body =
                "{\"serviceId\":"
                        + serviceId
                        + ",\"registrationRequired\":true}";

        byte[] bytes =
                body.getBytes(
                        StandardCharsets.UTF_8);

        exchange.getResponse()
                .setStatusCode(HttpStatus.OK);

        exchange.getResponse()
                .getHeaders()
                .setContentType(
                        MediaType.APPLICATION_JSON);

        exchange.getResponse()
                .getHeaders()
                .setCacheControl("no-store");

        return exchange.getResponse()
                .writeWith(
                        Mono.just(
                                exchange.getResponse()
                                        .bufferFactory()
                                        .wrap(bytes)));
    }

    private static <T> Mono<T> deny(
            String message,
            HttpStatus status) {

        return Mono.<T>error(
                new SPRuntimeError(
                        message,
                        status,
                        null));
    }
}
