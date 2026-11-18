package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.dto.ApplicationCountResponseDTO;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.ApplicationConstants.APPLICATION_STATUS_DRAFT;

@Service
public class ApplicationCountService {

    private static final Logger log = LogManager.getLogger(ApplicationCountService.class);

    private final ApplicationDetailsRepository applicationDetailsRepository;

    public ApplicationCountService(ApplicationDetailsRepository applicationDetailsRepository) {
        this.applicationDetailsRepository = applicationDetailsRepository;
    }

    /**
     * Get count of submitted applications (where status <> 'S').
     * If tenantId is specified, filters by tenantId; otherwise counts across all tenants.
     */
    public Mono<ApplicationCountResponseDTO> getApplicationCount(Integer serviceId, String tenantId) {
        if (serviceId == null) {
            log.warn("Application count requested with null serviceId");
            return Mono.just(new ApplicationCountResponseDTO(0L));
        }

        Mono<Long> countMono;
        if (tenantId != null && !tenantId.isBlank()) {
            countMono = applicationDetailsRepository.countByServiceIdAndTenantIdAndStatusNot(serviceId, tenantId, APPLICATION_STATUS_DRAFT);
        } else {
            countMono = applicationDetailsRepository.countByServiceIdAndStatusNot(serviceId, APPLICATION_STATUS_DRAFT);
        }

        return countMono
                .defaultIfEmpty(0L)
                .map(count -> {
                    log.info("Submitted application count for serviceId={}, tenantId={}: {}", serviceId, tenantId, count);
                    return new ApplicationCountResponseDTO(count);
                });
    }
}
