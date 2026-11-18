package com.serviceplus.form.validation.repository;

import com.netflix.appinfo.ApplicationInfoManager;
import com.serviceplus.form.validation.entity.ApplicationData;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Repository
public interface ApplicationDataRepository extends ReactiveCrudRepository<ApplicationData, String> {
    Mono<ApplicationData> findByApplicationIdAndTenantId(String applicationId, String tenantId);
}