package com.serviceplus.form.validation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.List;
import java.util.Objects;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.utility.ApplicationConstants;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.GuestEligibilityDTO;
import com.serviceplus.form.validation.dto.GuestSessionContext;
import com.serviceplus.form.validation.utility.Utility;

import reactor.core.publisher.Mono;

@Service
public class GuestSessionService {

    private static final org.apache.logging.log4j.Logger log =
            org.apache.logging.log4j.LogManager.getLogger(GuestSessionService.class);

    public static final String COOKIE_NAME = "SP_GUEST";
    public static final String CONTEXT_ATTRIBUTE = GuestSessionService.class.getName() + ".context";

    @Value("${guest.user-id:2966374}")
    private Long guestUserId;

    @Value("${guest.start-limit:100}")
    private int startLimit;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Value("${guest.session-minutes:120}")
    private Integer sessionMinutes;

    public GuestSessionService(
            ReactiveRedisTemplate<String, String> redisTemplate) {

        this.redisTemplate = redisTemplate;
    }

    public Mono<CreatedGuestSession> create(
            GuestEligibilityDTO eligibility) {

        int validSessionMinutes = getValidatedSessionMinutes();

        String token = generateToken();

        String guestSessionId = UUID.randomUUID().toString();

        long expiresAt = Instant.now()
                .plus(Duration.ofMinutes(validSessionMinutes))
                .toEpochMilli();

        GuestSessionContext context = new GuestSessionContext();

        context.setGuestSessionId(guestSessionId);
        context.setServiceId(eligibility.getServiceId());
        context.setBaseServiceId(eligibility.getBaseServiceId());
        context.setTenantId(eligibility.getTenantId());
        context.setStateId(eligibility.getStateId());
        context.setTaskId(eligibility.getTaskId());
        context.setFormId(eligibility.getFormId());
        context.setExpiresAtEpochMs(expiresAt);

        String redisKey =
                "guest:session:" + hashToken(token);

        String json = Utility.entityToString(context);

        return redisTemplate
                .opsForValue()
                .set(
                        redisKey,
                        json,
                        Duration.ofMinutes(validSessionMinutes)
                )
                .defaultIfEmpty(false)
                .flatMap(saved -> {

                    if (!Boolean.TRUE.equals(saved)) {

                        return Mono.error(
                                new SPRuntimeError(
                                        "Unable to create guest session",
                                        HttpStatus.SERVICE_UNAVAILABLE,
                                        null
                                )
                        );
                    }

                    return Mono.just(
                            new CreatedGuestSession(
                                    token,
                                    guestSessionId,
                                    validSessionMinutes * 60L
                            )
                    );
                });
    }

    public Mono<GuestSessionContext> require(String token) {

        if (token == null
                || !token.matches("[A-Za-z0-9_-]{43}")) {

            return Mono.error(
                    new SPRuntimeError(
                            "Invalid guest session",
                            HttpStatus.UNAUTHORIZED,
                            null
                    )
            );
        }

        String redisKey =
                "guest:session:" + hashToken(token);

        return redisTemplate
                .opsForValue()
                .get(redisKey)
                .switchIfEmpty(
                        Mono.error(
                                new SPRuntimeError(
                                        "Guest session expired or invalid",
                                        HttpStatus.UNAUTHORIZED,
                                        null
                                )
                        )
                )
                .map(json ->
                        (GuestSessionContext) Utility.stringToEntity(
                                json,
                                GuestSessionContext.class
                        )
                )
                .flatMap(context -> {

                    if (context == null
                            || context.getGuestSessionId() == null
                            || context.getExpiresAtEpochMs() == null
                            || Instant.now().toEpochMilli()
                            >= context.getExpiresAtEpochMs()) {

                        return Mono.error(
                                new SPRuntimeError(
                                        "Guest session expired or invalid",
                                        HttpStatus.UNAUTHORIZED,
                                        null
                                )
                        );
                    }

                    return Mono.just(context);
                });
    }

    public Mono<Boolean> bindTxn(
            GuestSessionContext context,
            String txnId) {

        if (context == null
                || context.getGuestSessionId() == null
                || txnId == null
                || txnId.isBlank()
                || context.getExpiresAtEpochMs() == null) {

            return Mono.error(
                    new SPRuntimeError(
                            "Invalid guest transaction",
                            HttpStatus.FORBIDDEN,
                            txnId
                    )
            );
        }

        long remainingMillis =
                context.getExpiresAtEpochMs()
                        - Instant.now().toEpochMilli();

        if (remainingMillis <= 0) {

            return Mono.error(
                    new SPRuntimeError(
                            "Guest session expired",
                            HttpStatus.UNAUTHORIZED,
                            txnId
                    )
            );
        }

        return redisTemplate
                .opsForValue()
                .setIfAbsent(
                        "guest:txn:" + txnId,
                        context.getGuestSessionId(),
                        Duration.ofMillis(remainingMillis)
                )
                .flatMap(saved -> Boolean.TRUE.equals(saved)
                        ? Mono.just(true)
                        : requireOwner(context, txnId).thenReturn(true))
                .doOnNext(saved -> log.info("Guest transaction bound for serviceId {} txnId {}", context.getServiceId(), txnId));
    }

    public Mono<Void> requireOwner(
            GuestSessionContext context,
            String txnId) {

        if (context == null
                || context.getGuestSessionId() == null
                || txnId == null
                || txnId.isBlank()) {

            return Mono.error(
                    new SPRuntimeError(
                            "Guest transaction access denied",
                            HttpStatus.FORBIDDEN,
                            txnId
                    )
            );
        }

        return redisTemplate
                .opsForValue()
                .get("guest:txn:" + txnId)
                .switchIfEmpty(
                        Mono.error(
                                new SPRuntimeError(
                                        "Guest transaction access denied",
                                        HttpStatus.FORBIDDEN,
                                        txnId
                                )
                        )
                )
                .flatMap(owner -> {

                    if (!context.getGuestSessionId().equals(owner)) {

                        return Mono.error(
                                new SPRuntimeError(
                                        "Guest transaction access denied",
                                        HttpStatus.FORBIDDEN,
                                        txnId
                                )
                        );
                    }

                    return Mono.<Void>empty();
                })
                .doOnError(error -> log.warn("Guest transaction ownership check failed for serviceId {} txnId {}",
                        context.getServiceId(), txnId));
    }

    public Mono<Void> checkStartRateLimit(String clientIp) {

        String ip =
                clientIp == null || clientIp.isBlank()
                        ? "unknown"
                        : clientIp;

        String redisKey =
                "guest:start:rate:" + hashToken(ip);

        return redisTemplate
                .opsForValue()
                .increment(redisKey)
                .switchIfEmpty(Mono.error(new SPRuntimeError(
                        "Guest rate limit unavailable", HttpStatus.SERVICE_UNAVAILABLE, null)))
                .flatMap(count -> {

                    Mono<Boolean> expiry;

                    if (count == 1) {

                        expiry = redisTemplate.expire(
                                redisKey,
                                Duration.ofMinutes(10)
                        );

                    } else {

                        expiry = Mono.just(true);
                    }

                    return expiry.filter(Boolean.TRUE::equals)
                            .switchIfEmpty(Mono.error(new SPRuntimeError(
                                    "Guest rate limit unavailable", HttpStatus.SERVICE_UNAVAILABLE, null)))
                            .then(
                            Mono.defer(() -> {

                                if (startLimit < 1 || count > startLimit) {

                                    return Mono.error(
                                            new SPRuntimeError(
                                                    "Too many guest application requests",
                                                    HttpStatus.TOO_MANY_REQUESTS,
                                                    null
                                            )
                                    );
                                }

                                return Mono.<Void>empty();
                            })
                    );
                });
    }

    private int getValidatedSessionMinutes() {

        if (sessionMinutes == null
                || sessionMinutes < 1
                || sessionMinutes > 1440) {

            throw new SPRuntimeError(
                    "Guest session configuration is invalid",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    null
            );
        }

        return sessionMinutes;
    }

    private String generateToken() {

        byte[] bytes = new byte[32];

        SECURE_RANDOM.nextBytes(bytes);

        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String value) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(hash);

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "SHA-256 algorithm unavailable",
                    e
            );
        }
    }

    public UserSessionObject createGuestUser(
            GuestSessionContext context) {

        validateGuestUserConfig();

        UserSessionObject user =
                new UserSessionObject();

        user.setUserID(guestUserId);
        user.setUserName("Applicant");
        user.setSignNo("Applicant");
        user.setTenantId(
                context.getTenantId()
        );
        user.setLocationId(
                context.getStateId()
        );
        user.setActive(true);
        user.setRoles(
                List.of(
                        new UserSessionObject.Roles(
                                6,
                                "CTZN"
                        )
                )
        );
        user.setGuestSessionId(
                context.getGuestSessionId()
        );

        return user;
    }

    private void validateGuestUserConfig() {
        if (guestUserId == null
                || guestUserId <= 0) {
            throw new SPRuntimeError(
                    "Guest user configuration is invalid",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    null
            );
        }
    }

    public void validateEligibility(
            Integer serviceId,
            GuestEligibilityDTO eligibility) {

        if (eligibility == null
                || eligibility.getServiceId() == null
                || eligibility.getBaseServiceId() == null
                || isBlank(eligibility.getTenantId())
                || isBlank(eligibility.getTaskId())
                || isBlank(eligibility.getFormId())) {

            throw new SPRuntimeError(
                    "Invalid guest eligibility response",
                    HttpStatus.BAD_GATEWAY,
                    null
            );
        }

        if (!Objects.equals(
                serviceId,
                eligibility.getServiceId())) {

            throw new SPRuntimeError(
                    "Guest service mismatch",
                    HttpStatus.FORBIDDEN,
                    null
            );
        }
    }

    public void validateGuestContext(
            Integer serviceId,
            GuestSessionContext context) {

        if (context == null
                || isBlank(context.getGuestSessionId())
                || context.getServiceId() == null
                || context.getBaseServiceId() == null
                || isBlank(context.getTenantId())
                || isBlank(context.getTaskId())
                || isBlank(context.getFormId())) {

            throw new SPRuntimeError(
                    "Invalid guest session",
                    HttpStatus.UNAUTHORIZED,
                    null
            );
        }

        if (!Objects.equals(
                serviceId,
                context.getServiceId())) {

            throw new SPRuntimeError(
                    "Guest service mismatch",
                    HttpStatus.FORBIDDEN,
                    null
            );
        }
    }

    public void validateServiceMeta(
            GuestSessionContext context,
            ServiceMeta service) {

        validateServiceMetaCommon(service);

        if (!Objects.equals(
                service.getServiceId(),
                context.getServiceId())
                || !Objects.equals(
                service.getBaseServiceId(),
                context.getBaseServiceId())
                || !Objects.equals(
                service.getTaskId(),
                context.getTaskId())
                || !Objects.equals(
                service.getFormId(),
                context.getFormId())
                || !ApplicationConstants
                .APPLICATION_SUBMISSION_TASK_FLAG
                .equals(service.getTaskType())) {

            throw new SPRuntimeError(
                    "Guest service metadata mismatch",
                    HttpStatus.FORBIDDEN,
                    null
            );
        }
    }

    private void validateServiceMetaCommon(
            ServiceMeta service) {

        if (service == null
                || service.getServiceId() == null
                || service.getBaseServiceId() == null
                || isBlank(service.getTaskId())
                || isBlank(service.getFormId())
                || isBlank(service.getTaskType())) {

            throw new SPRuntimeError(
                    "Invalid service metadata",
                    HttpStatus.BAD_GATEWAY,
                    null
            );
        }
    }

    private boolean isBlank(
            String value) {

        return value == null
                || value.isBlank();
    }

    public void validateEligibility(GuestSessionContext context, GuestEligibilityDTO eligibility) {
        validateGuestContext(context.getServiceId(), context);
        validateEligibility(context.getServiceId(), eligibility);
        if (!Objects.equals(context.getBaseServiceId(), eligibility.getBaseServiceId())
                || !Objects.equals(context.getTenantId(), eligibility.getTenantId())
                || !Objects.equals(context.getStateId(), eligibility.getStateId())
                || !Objects.equals(context.getTaskId(), eligibility.getTaskId())
                || !Objects.equals(context.getFormId(), eligibility.getFormId())) {
            throw new SPRuntimeError("Guest eligibility changed", HttpStatus.FORBIDDEN, null);
        }
    }

    public static class CreatedGuestSession {

        private final String token;
        private final String guestSessionId;
        private final Long expiresInSeconds;

        public CreatedGuestSession(
                String token,
                String guestSessionId,
                Long expiresInSeconds) {

            this.token = token;
            this.guestSessionId = guestSessionId;
            this.expiresInSeconds = expiresInSeconds;
        }

        public String getToken() {
            return token;
        }

        public String getGuestSessionId() {
            return guestSessionId;
        }

        public Long getExpiresInSeconds() {
            return expiresInSeconds;
        }
    }
}
