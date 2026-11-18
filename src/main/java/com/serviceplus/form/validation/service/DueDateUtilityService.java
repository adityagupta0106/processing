package com.serviceplus.form.validation.service;

import com.google.gson.reflect.TypeToken;
import com.serviceplus.form.validation.dto.HolidayDocumentDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class DueDateUtilityService {
    private static final String GLOBAL_TENANT_ID = "0";
    private final RedisService redisService;
    private final WebClient webClient;
    public DueDateUtilityService(
            RedisService redisService,
            WebClient.Builder webClientBuilder,
            @Value("${instance.configuration.service}")
            String instanceConfigurationService) {
        this.redisService = redisService;
        this.webClient = webClientBuilder
                .baseUrl(instanceConfigurationService)
                .build();
    }

    /**
     * Calculates final application due date.
     *
     * Rules:
     * 1. Apply date itself is not counted.
     * 2. Next calendar date is Day 1.
     * 3. Holiday dates are skipped.
     * 4. Sundays are not hardcoded here.
     *    They are expected in global holiday calendar.
     * 5. Cross-year SLA calculation is supported.
     */
    public Mono<LocalDateTime> calculateDueDate(
            LocalDateTime applyDate,
            Integer serviceLevel,
            String slaUnit,
            String tenantId) {

        if (applyDate == null) {
            return Mono.error(
                    new IllegalArgumentException("Apply date is required")
            );
        }

        if (tenantId == null || tenantId.isBlank()) {
            return Mono.error(
                    new IllegalArgumentException("Tenant id is required")
            );
        }

        if (serviceLevel == null) {
            return Mono.error(
                    new IllegalArgumentException("Service level is required")
            );
        }

        if (serviceLevel < 0) {
            return Mono.error(
                    new IllegalArgumentException(
                            "Service level cannot be negative"
                    )
            );
        }

        /*
         * Same Day:
         * Metadata sends sla = 0 and slaUnit = null.
         * Due date remains the final application submission time.
         * No holiday lookup is required.
         */
        if (serviceLevel == 0) {
            return Mono.just(applyDate);
        }

        if (slaUnit == null || slaUnit.isBlank()) {
            return Mono.error(
                    new IllegalArgumentException(
                            "SLA unit is required for custom service level"
                    )
            );
        }

        String normalizedUnit = slaUnit.trim();

        if ("d".equalsIgnoreCase(normalizedUnit)) {
            return calculateAcrossYears(
                    applyDate,
                    serviceLevel,
                    tenantId.trim()
            );
        }

        if ("w".equalsIgnoreCase(normalizedUnit)) {
            final int serviceLevelDays;

            try {
                serviceLevelDays = Math.multiplyExact(serviceLevel, 7);
            } catch (ArithmeticException exception) {
                return Mono.error(
                        new IllegalArgumentException(
                                "Service level weeks value is too large",
                                exception
                        )
                );
            }

            return calculateAcrossYears(
                    applyDate,
                    serviceLevelDays,
                    tenantId.trim()
            );
        }

        return Mono.error(
                new IllegalArgumentException(
                        "Unsupported SLA unit: " + slaUnit
                )
        );
    }

    /**
     * Calculates due date year-by-year.
     *
     * This solves:
     *
     * Apply Date = 30 Dec 2026
     * SLA = 10
     *
     * It will automatically load holiday calendars
     * for 2026 and then 2027 when required.
     */
    private Mono<LocalDateTime> calculateAcrossYears(
            LocalDateTime currentDate,
            int remainingWorkingDays,
            String tenantId) {
        if (remainingWorkingDays == 0) {
            return Mono.just(currentDate);
        }
        /*
         * We count starting from the NEXT date.
         *
         * Therefore load the calendar belonging to
         * currentDate + 1 day.
         */
        int yearToLoad =
                currentDate
                        .plusDays(1)
                        .getYear();
        return getApplicableHolidays(
                tenantId,
                yearToLoad
        ).flatMap(holidaySet -> {
            LocalDateTime cursor = currentDate;
            int remaining =
                    remainingWorkingDays;
            /*
             * Process dates only while the NEXT date
             * belongs to the currently loaded year.
             */
            while (remaining > 0 &&
                    cursor.plusDays(1).getYear()
                            == yearToLoad) {
                cursor = cursor.plusDays(1);
                /*
                 * Count only when this date
                 * is NOT present in holiday calendar.
                 */
                if (!holidaySet.contains(
                        cursor.toLocalDate())) {
                    remaining--;
                }
            }
            /*
             * SLA completed in current year.
             */
            if (remaining == 0) {
                return Mono.just(cursor);
            }
            /*
             * Year boundary reached.
             *
             * Recursively load next year's calendar.
             */
            return calculateAcrossYears(
                    cursor,
                    remaining,
                    tenantId
            );
        });
    }

    /**
     * Combines:
     *
     * tenantId = 0
     *      GLOBAL / All India calendar
     *
     * +
     *
     * actual tenant calendar
     *
     * Result is Set for fast contains(date).
     */
    private Mono<Set<LocalDate>> getApplicableHolidays(
            String tenantId,
            int year) {
        Mono<List<LocalDate>> globalHolidays =
                getHolidaysWithCacheFallback(
                        GLOBAL_TENANT_ID,
                        year
                );
        /*
         * Prevent duplicate call in case tenant itself is 0.
         */
        if (GLOBAL_TENANT_ID.equals(tenantId)) {
            return globalHolidays.map(
                    HashSet::new
            );
        }
        Mono<List<LocalDate>> tenantHolidays =
                getHolidaysWithCacheFallback(
                        tenantId,
                        year
                );
        return Mono.zip(
                globalHolidays,
                tenantHolidays
        ).map(tuple -> {
            Set<LocalDate> holidays =
                    new HashSet<>();
            holidays.addAll(tuple.getT1());
            holidays.addAll(tuple.getT2());
            return holidays;
        });
    }

    /**
     * CACHE-ASIDE
     *
     * Redis HIT
     *      -> return dates
     *
     * Redis MISS
     *      -> Configuration API
     *      -> Mongo
     *      -> save in Redis
     *      -> return dates
     */
    private Mono<List<LocalDate>> getHolidaysWithCacheFallback(String tenantId,int year) {
        String cacheKey = buildHolidayCacheKey(
                        tenantId,
                        year
                );
        Type listType =
                new TypeToken<List<String>>() {
                }.getType();
        /*
         * Redis stores:
         *
         * [
         *   "2026-01-04",
         *   "2026-01-11"
         * ]
         *
         * NOT List<LocalDate>.
         */
        Mono<List<LocalDate>> cacheLookup =
                redisService
                        .<List<String>>fetch(
                                cacheKey,
                                listType
                        )
                        .map(this::convertToLocalDates)
                        /*
                         * Global calendar cannot be empty.
                         *
                         * If somehow [] is cached for tenant 0,
                         * force an API refresh.
                         */
                        .flatMap(dates -> {
                            if (GLOBAL_TENANT_ID.equals(tenantId)
                                    && dates.isEmpty()) {
                                return Mono.empty();
                            }
                            return Mono.just(dates);
                        })
                        /*
                         * Redis should be an optimization.
                         *
                         * If Redis itself fails,
                         * try source-of-truth API.
                         */
                        .onErrorResume(
                                exception -> Mono.empty()
                        );
        return cacheLookup.switchIfEmpty(
                fetchHolidaysFromConfiguration(
                        tenantId,
                        year,
                        cacheKey
                )
        );
    }

    /**
     * Called only after cache miss.
     */
    private Mono<List<LocalDate>> fetchHolidaysFromConfiguration(
            String tenantId,
            int year,
            String cacheKey) {
    	return webClient.get()
    	        .uri(uriBuilder -> uriBuilder
    	                .path("/a/api/v1/holidays")
    	                .queryParam("tenantId", tenantId)
    	                .queryParam("year", year)
    	                .build())
                .exchangeToMono(httpResponse -> {
                    if (httpResponse.statusCode().is2xxSuccessful()) {
                        return httpResponse
                                .bodyToMono(HolidayDocumentDto.class)
                                .switchIfEmpty(
                                        Mono.error(
                                                new IllegalStateException(
                                                        "Holiday API returned an empty response for tenant "
                                                                + tenantId
                                                                + " and year "
                                                                + year
                                                )
                                        )
                                )
                                .map(document -> {
                                    if (document.getHolidays() == null) {
                                        return List.<String>of();
                                    }
                                    return document
                                            .getHolidays()
                                            .stream()
                                            .map(
                                                    HolidayDocumentDto
                                                            .HolidayEntry
                                                            ::getDate
                                            )
                                            .filter(Objects::nonNull)
                                            .map(LocalDate::toString)
                                            .toList();
                                });
                    }
                    if (httpResponse.statusCode().value() == 404
                            && !GLOBAL_TENANT_ID.equals(tenantId)) {
                        return Mono.just(
                                Collections.<String>emptyList()
                        );
                    }
                    if (httpResponse.statusCode().value() == 404) {
                        return Mono.error(
                                new IllegalStateException(
                                        "Global holiday calendar not found for year "
                                                + year
                                )
                        );
                    }
                    return httpResponse
                            .createException()
                            .flatMap(exception ->
                                    Mono.error(exception)
                            );
                })
                .flatMap(dateStrings -> {
                    if (GLOBAL_TENANT_ID.equals(tenantId)
                            && dateStrings.isEmpty()) {
                        return Mono.error(
                                new IllegalStateException(
                                        "Global holiday calendar is empty for year "
                                                + year
                                )
                        );
                    }
                    return redisService
                            .add(
                                    dateStrings,
                                    cacheKey,
                                    false,
                                    null
                            )
                            .onErrorReturn(false)
                            .thenReturn(dateStrings);
                })
                .map(this::convertToLocalDates);
    }
    private List<LocalDate> convertToLocalDates(
            List<String> dateStrings) {
        if (dateStrings == null) {
            return Collections.emptyList();
        }
        return dateStrings
                .stream()
                .filter(
                        Objects::nonNull
                )
                .map(
                        LocalDate::parse
                )
                .toList();
    }
    private String buildHolidayCacheKey(
            String tenantId,
            int year) {
        return "holidays:tenant:"
                + tenantId
                + ":year:"
                + year;
    }
}
