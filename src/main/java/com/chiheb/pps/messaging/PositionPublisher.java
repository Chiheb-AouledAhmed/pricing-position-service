package com.chiheb.pps.messaging;

import com.chiheb.pps.domain.Position;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.function.Consumer;

/**
 * Publishes every position update on a compacted topic keyed by instrument, so a consumer
 * (risk, P&amp;L, UI) can rebuild the latest state of every position by reading the topic.
 */
public class PositionPublisher implements Consumer<Position> {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public PositionPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper, String topic) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
    }

    @Override
    public void accept(Position position) {
        try {
            kafka.send(topic, position.instrument(), mapper.writeValueAsString(position));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise position " + position.instrument(), e);
        }
    }
}
