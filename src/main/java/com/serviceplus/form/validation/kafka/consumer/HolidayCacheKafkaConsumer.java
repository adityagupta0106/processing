package com.serviceplus.form.validation.kafka.consumer;

import com.serviceplus.form.validation.dto.HolidaySyncEvent;
import com.serviceplus.form.validation.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class HolidayCacheKafkaConsumer
        implements KafkaEventHandler<HolidaySyncEvent> {

    private static final Logger logger =
            LoggerFactory.getLogger("kafkaLogger");

    private static final String HOLIDAY_CONFIG_CHANGED =
            "HOLIDAY_CONFIG_CHANGED";

    private final RedisService redisService;
    private final String topic;

    public HolidayCacheKafkaConsumer(
            RedisService redisService,
            @Value("${kafka.topic.holiday-sync}") String topic) {

        this.redisService = redisService;
        this.topic = topic;
    }

    @Override
    public String getTopic() {
        return topic;
    }

    @Override
    public Class<HolidaySyncEvent> getPayloadType() {
        return HolidaySyncEvent.class;
    }

    @Override
    public Mono<Void> process(HolidaySyncEvent event) {

        if (event == null) {
            logger.warn("Received null holiday sync event");
            return Mono.empty();
        }

        if (!HOLIDAY_CONFIG_CHANGED.equals(event.getEventType())) {
            logger.info("Ignoring unsupported holiday event type={}",event.getEventType());
            return Mono.empty();
        }

        if (event.getTenantId() == null|| event.getTenantId().isBlank()|| event.getYear() == null) {

            logger.warn("Invalid holiday sync event tenantId={} year={}",event.getTenantId(),event.getYear());

            return Mono.empty();
        }

        String cacheKey ="holidays:tenant:"+ event.getTenantId()+ ":year:"+ event.getYear();
        logger.info("Invalidating holiday cache key={}",cacheKey);

        return redisService
                .remove(cacheKey)
                .doOnSuccess(unused ->
                        logger.info("Holiday cache invalidated successfully key={}",cacheKey))
                .doOnError(error ->
                        logger.error("Unable to invalidate holiday cache key={}",cacheKey,error));
    }
}