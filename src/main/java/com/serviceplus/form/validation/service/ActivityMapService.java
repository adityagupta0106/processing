package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ActivityMapDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import static com.serviceplus.form.validation.utility.ApplicationConstants.*;
import static java.util.Objects.isNull;

@Service
public class ActivityMapService {

    private final RedisService redis;
    private final ReactiveApiClient reactiveApiClient;

    public ActivityMapService(RedisService redis,
                              ReactiveApiClient reactiveApiClient) {
        this.redis = redis;
        this.reactiveApiClient = reactiveApiClient;
    }

    public Mono<ActivityMapDTO> getActivityMap(ServiceMeta service,
                                               UserSessionObject user,
                                               String applicationId,
                                               String txnId) {

        return reactiveApiClient
                .fetchServiceKey(
                        service.getBaseServiceId(),
                        user,
                        applicationId,
                        service.getTaskId(),
                        service.getServiceId()
                )
                .switchIfEmpty(Mono.error(
                        new SPRuntimeError(
                                "Execution error [EX - 02]",
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                txnId
                        )))
                .map(ServiceMeta::getActivityMap);
    }

    public ActivityMapDTO.ActivityData findNextActivity(String currentActivity,
                                                        ActivityMapDTO activityMap) {

        if (activityMap == null || activityMap.getData() == null || activityMap.getData().isEmpty()) {
            return new ActivityMapDTO.ActivityData("NA");
        }

        List<ActivityMapDTO.ActivityData> activities = activityMap.getData().stream()
                .filter(Objects::nonNull)
                .filter(a -> a.getActivityType() != null && !a.getActivityType().isBlank())
                .sorted(Comparator.comparing(
                        ActivityMapDTO.ActivityData::getIndex,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();

        if (activities.isEmpty()) {
            return new ActivityMapDTO.ActivityData("NA");
        }

        int currentIndex = -1;
        for (int i = 0; i < activities.size(); i++) {
            ActivityMapDTO.ActivityData a = activities.get(i);
            if (currentActivity.equalsIgnoreCase(a.getActivityType())) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex != -1) {
            ActivityMapDTO.ActivityData current = activities.get(currentIndex);

            if (Boolean.TRUE.equals(current.getLast()) || currentIndex == activities.size() - 1) {
                return new ActivityMapDTO.ActivityData("NA");
            }

            return activities.get(currentIndex + 1);
        }

        return new ActivityMapDTO.ActivityData("NA");
    }

    public ActivityMapDTO.ActivityData findCurrentActivity(String taskId, String activityType, ActivityMapDTO activityMap) {

        if (isNull(activityMap.getData()) || activityMap.getData().isEmpty()) {
            return null;
        }

        return activityMap.getData()
                .stream()
                .filter(activity -> activityType.equals(activity.getActivityType()))
                .findFirst()
                .orElse(null);
    }

    public Mono<Boolean> isUserSubmissionRequired(ServiceMeta service,
                                                  UserSessionObject user,
                                                  String applicationId,
                                                  String txnId,
                                                  String activityType) {

        return getActivityMap(service, user, applicationId, txnId)
                .map(activityMap -> {

                    ActivityMapDTO.ActivityData currentActivity = findCurrentActivity(service.getTaskId(),activityType, activityMap);

                    if (currentActivity == null) {
                        throw new SPRuntimeError("Current activity not found in activity map.", HttpStatus.INTERNAL_SERVER_ERROR, txnId);
                    }

                    return Boolean.TRUE.equals(currentActivity.getUserSubmissionRequired());
                });
    }
    public Mono<Long> activityConfigId(ServiceMeta service,
    		UserSessionObject user,
    		String applicationId,
    		String txnId,
    		String activityType) {
    	
    	return getActivityMap(service, user, applicationId, txnId)
    			.map(activityMap -> {
    				ActivityMapDTO.ActivityData currentActivity = findCurrentActivity(service.getTaskId(),activityType, activityMap);
    				if (currentActivity == null) {
    					throw new SPRuntimeError("Current activity not found in activity map.", HttpStatus.INTERNAL_SERVER_ERROR, txnId);
    				}
    				return currentActivity.getActivityConfigId();
    			});
    }
}