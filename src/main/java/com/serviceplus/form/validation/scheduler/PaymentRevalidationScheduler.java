package com.serviceplus.form.validation.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.entity.ApplicationPayment;
import com.serviceplus.form.validation.enums.PaymentRevalidationMode;
import com.serviceplus.form.validation.repository.ApplicationPaymentRepository;
import com.serviceplus.form.validation.service.PaymentProcessingService;
import com.serviceplus.form.validation.service.TransactionalDBExecutor;
import com.serviceplus.payment.adapter.api.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

@Component
public class PaymentRevalidationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PaymentRevalidationScheduler.class);

    private final ApplicationPaymentRepository applicationPaymentRepository;
    private final PaymentProcessingService paymentProcessingService;
    private final TransactionalDBExecutor transactionalDBExecutor;
    private final ObjectMapper objectMapper;

    public PaymentRevalidationScheduler(ApplicationPaymentRepository applicationPaymentRepository, PaymentProcessingService paymentProcessingService, TransactionalDBExecutor transactionalDBExecutor, ObjectMapper objectMapper) {
        this.applicationPaymentRepository = applicationPaymentRepository;
        this.paymentProcessingService = paymentProcessingService;
        this.transactionalDBExecutor = transactionalDBExecutor;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${payment.revalidation.interval:600000}")
    public void revalidatePayments() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);

        logger.info(
                "Payment revalidation scheduler started. cutoff={}",
                cutoff);

        applicationPaymentRepository
                .findByStatusInAndCrDateBeforeOrderByCrDateAsc(
                        List.of(
                                PaymentStatus.INITIATED.name(),
                                PaymentStatus.PENDING.name()),
                        cutoff, PageRequest.of(0, 1000))
                .flatMap(
                        payment -> {
                            Integer revalidationDays = getAutoRevalidationDays(payment);

                            if (revalidationDays == null) {
                                return Mono.empty();
                            }

                            if (isRevalidationExpired(payment, revalidationDays)) {
                                return cancelExpiredPayment(payment);
                            }

                            String originalStatus = payment.getStatus();

                            return claimPaymentForRevalidation(payment)
                                    .flatMap(claimed -> {
                                        if (!claimed) {
                                            logger.info(
                                                    "Payment already claimed for revalidation. PaymentId={}",
                                                    payment.getPaymentId());
                                            return Mono.empty();
                                        }

                                        payment.setStatus(
                                                PaymentStatus.REVALIDATION_IN_PROGRESS.name());

                                        return paymentProcessingService.revalidatePayment(
                                                payment, originalStatus);
                                    });
                        },
                        5)
                .doOnComplete(() ->
                        logger.info("Payment revalidation scheduler completed."))
                .doOnError(error ->
                        logger.error("Payment revalidation scheduler failed.", error))
                .subscribe();
    }

    private Integer getAutoRevalidationDays(ApplicationPayment payment) {
        if (payment.getRevalidationConfiguration() == null || payment.getRevalidationConfiguration().isBlank()) {
            return null;
        }

        try {
            Map<String, Object> configuration = objectMapper.readValue(payment.getRevalidationConfiguration(), new TypeReference<Map<String, Object>>() {
            });

            Object enabled = configuration.get("enabled");

            if (!Boolean.TRUE.equals(enabled)) {
                return null;
            }

            Object modesObject = configuration.get("modes");

            if (!(modesObject instanceof List<?> modes) || modes.stream()
                    .noneMatch(mode -> PaymentRevalidationMode.AUTO.name().equalsIgnoreCase(String.valueOf(mode)))) {
                return null;
            }

            Object periodsObject = configuration.get("periods");

            if (!(periodsObject instanceof Map<?, ?> periods)) {
                return null;
            }

            Object days = periods.get(PaymentRevalidationMode.AUTO.name());

            if (days == null) {
                return null;
            }

            int revalidationDays = Integer.parseInt(String.valueOf(days));

            if (revalidationDays <= 0) {
                return null;
            }

            return revalidationDays;
        } catch (Exception e) {
            logger.error("Failed to parse payment revalidation configuration. PaymentId={}", payment.getPaymentId(), e);
            return null;
        }
    }

    private boolean isRevalidationExpired(ApplicationPayment payment, Integer revalidationDays) {

        if (payment.getCrDate() == null) {
            return false;
        }

        LocalDateTime expiryTime = payment.getCrDate().plusDays(revalidationDays);

        boolean expired = LocalDateTime.now().isAfter(expiryTime);

        if (expired) {
            logger.info("Payment revalidation period expired. PaymentId={}, revalidationDays={}, expiryTime={}", payment.getPaymentId(), revalidationDays, expiryTime);
        }

        return expired;
    }

    private Mono<Void> cancelExpiredPayment(ApplicationPayment payment) {
        String paymentUpdate = """
            UPDATE %s
            SET status = '%s',
                up_date = CURRENT_TIMESTAMP,
                completed_date = CURRENT_TIMESTAMP
            WHERE payment_id = :paymentId
              AND status IN ('%s', '%s')
            """;

        String paymentSql = String.format(
                paymentUpdate,
                SP_SCHEMA_NAME.concat(".application_payment"),
                PaymentStatus.CANCELLED.name(),
                PaymentStatus.INITIATED.name(),
                PaymentStatus.PENDING.name());

        return transactionalDBExecutor.executeRawSql(
                        paymentSql,
                        Map.of("paymentId", payment.getPaymentId()),
                        "")
                .doOnNext(rows ->
                        logger.info(
                                "Expired payment cancelled. PaymentId={}, rows={}",
                                payment.getPaymentId(),
                                rows))
                .then();
    }

    private Mono<Boolean> claimPaymentForRevalidation(
            ApplicationPayment payment) {

        String paymentUpdate = """
            UPDATE %s
            SET status = '%s',
                up_date = CURRENT_TIMESTAMP
            WHERE payment_id = :paymentId
              AND status IN ('%s', '%s')
            """;

        String paymentSql = String.format(
                paymentUpdate,
                SP_SCHEMA_NAME.concat(".application_payment"),
                PaymentStatus.REVALIDATION_IN_PROGRESS.name(),
                PaymentStatus.INITIATED.name(),
                PaymentStatus.PENDING.name());

        return transactionalDBExecutor.executeRawSql(
                        paymentSql,
                        Map.of("paymentId", payment.getPaymentId()),
                        "")
                .map(rows -> {
                    logger.info(
                            "Payment revalidation claim completed. PaymentId={}, rows={}",
                            payment.getPaymentId(),
                            rows);
                    return rows > 0;
                });
    }
}