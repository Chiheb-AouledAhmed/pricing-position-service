package com.chiheb.pps.api;

import com.chiheb.pps.domain.Position;
import com.chiheb.pps.domain.Trade;
import com.chiheb.pps.service.PositionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PositionController {

    private final PositionService positions;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String tradesTopic;

    public PositionController(PositionService positions, KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                              @Value("${pps.topics.trades}") String tradesTopic) {
        this.positions = positions;
        this.kafka = kafka;
        this.mapper = mapper;
        this.tradesTopic = tradesTopic;
    }

    @GetMapping("/positions")
    public List<Position> all() {
        return positions.all();
    }

    @GetMapping("/positions/{instrument}")
    public ResponseEntity<Position> one(@PathVariable String instrument) {
        return ResponseEntity.of(positions.position(instrument));
    }

    /**
     * Convenience endpoint for demos: publishes a trade on the trades topic (keyed by instrument).
     * The position is updated asynchronously by {@code TradeConsumer}, exactly like a trade coming
     * from an upstream execution system.
     */
    @PostMapping("/trades")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> submit(@RequestBody Trade trade) throws JsonProcessingException {
        kafka.send(tradesTopic, trade.instrument(), mapper.writeValueAsString(trade));
        return Map.of("status", "accepted", "tradeId", trade.tradeId());
    }
}
