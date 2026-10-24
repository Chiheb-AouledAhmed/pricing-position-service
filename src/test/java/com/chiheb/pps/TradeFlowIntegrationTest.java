package com.chiheb.pps;

import com.chiheb.pps.domain.Position;
import com.chiheb.pps.messaging.TradeConsumer;
import com.chiheb.pps.service.PositionService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end: trades are published on an embedded Kafka broker, consumed by the service,
 * exposed over REST, and every position change is re-published on the position-updates topic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1) // topics are created by the app's KafkaAdmin (NewTopic beans)
@ActiveProfiles("test")
class TradeFlowIntegrationTest {

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired PositionService positions;
    @Autowired TradeConsumer consumer;
    @Autowired TestRestTemplate http;
    @Autowired EmbeddedKafkaBroker broker;

    private void send(String json) {
        kafka.send("trades", "FR0000120271", json);
    }

    @Test
    void tradesFlowFromKafkaToPositionsAndBack() {
        send("""
                {"tradeId":"E-1","instrument":"FR0000120271","side":"BUY","quantity":300,"price":60.10,
                 "executedAt":"2026-10-23T09:00:00Z"}""");
        send("""
                {"tradeId":"E-2","instrument":"FR0000120271","side":"SELL","quantity":100,"price":61.10}""");
        send("""
                {"tradeId":"E-2","instrument":"FR0000120271","side":"SELL","quantity":100,"price":61.10}""");
        send("this is not a trade");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            // one partition, in-order: once the malformed message is rejected, everything before it is applied
            assertThat(consumer.rejectedCount()).isGreaterThanOrEqualTo(1);
            assertThat(positions.position("FR0000120271")).get()
                    .extracting(Position::tradeCount).isEqualTo(2L); // the redelivered E-2 was ignored
        });

        Position p = positions.position("FR0000120271").orElseThrow();
        assertThat(p.quantity()).isEqualTo(200);
        assertThat(p.averageCost()).isEqualByComparingTo("60.10");
        assertThat(p.realizedPnl()).isEqualByComparingTo("100");

        ResponseEntity<Position> rest = http.getForEntity("/api/positions/FR0000120271", Position.class);
        assertThat(rest.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getBody()).isNotNull();
        assertThat(rest.getBody().quantity()).isEqualTo(200);
        assertThat(http.getForEntity("/api/positions/UNKNOWN", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> props = KafkaTestUtils.consumerProps("verifier", "true", broker);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> updates = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            updates.subscribe(List.of("position-updates"));
            List<String> values = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15_000;
            while (values.size() < 2 && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> r : updates.poll(Duration.ofMillis(500))) {
                    if ("FR0000120271".equals(r.key())) values.add(r.value());
                }
            }
            assertThat(values).hasSize(2);
            assertThat(values.get(0)).contains("\"quantity\":300");
            assertThat(values.get(1)).contains("\"quantity\":200");
        }
    }

    @Test
    void restEndpointPublishesTradesOnKafka() {
        var body = Map.of("tradeId", "R-1", "instrument", "US0378331005", "side", "BUY",
                "quantity", 5, "price", new BigDecimal("230.5"));
        assertThat(http.postForEntity("/api/trades", body, Map.class).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(positions.position("US0378331005")).get()
                        .extracting(Position::quantity).isEqualTo(5L));
    }
}
