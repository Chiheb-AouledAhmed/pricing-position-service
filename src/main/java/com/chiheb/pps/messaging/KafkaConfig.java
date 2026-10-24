package com.chiheb.pps.messaging;

import com.chiheb.pps.service.PositionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic tradesTopic(@Value("${pps.topics.trades}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic positionsTopic(@Value("${pps.topics.positions}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).compact().build();
    }

    @Bean
    PositionPublisher positionPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                                        @Value("${pps.topics.positions}") String topic) {
        return new PositionPublisher(kafka, mapper, topic);
    }

    /** The domain service has no framework dependency; Spring only wires the publisher in. */
    @Bean
    PositionService positionService(PositionPublisher publisher) {
        return new PositionService(publisher);
    }
}
