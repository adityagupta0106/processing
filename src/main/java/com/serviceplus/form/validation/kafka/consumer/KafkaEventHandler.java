package com.serviceplus.form.validation.kafka.consumer;

import reactor.core.publisher.Mono;

public interface KafkaEventHandler<T> {

    String getTopic();

    Class<T> getPayloadType();

    Mono<Void> process(T event);
}
