package com.chiheb.pps.messaging;

import com.chiheb.pps.domain.Trade;
import com.chiheb.pps.service.PositionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumes executed trades. Trades are keyed by instrument on the producer side, so all trades of an
 * instrument land on the same partition and are applied in order.
 *
 * A malformed message is logged and skipped rather than blocking the partition (a production
 * version would route it to a dead-letter topic).
 */
@Component
public class TradeConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeConsumer.class);

    private final PositionService positions;
    private final ObjectMapper mapper;
    private final AtomicLong rejected = new AtomicLong();

    public TradeConsumer(PositionService positions, ObjectMapper mapper) {
        this.positions = positions;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "${pps.topics.trades}", groupId = "${spring.kafka.consumer.group-id}")
    public void onTrade(String payload) {
        final Trade trade;
        try {
            trade = mapper.readValue(payload, Trade.class);
        } catch (Exception e) {
            rejected.incrementAndGet();
            log.warn("rejected malformed trade message: {}", e.getMessage());
            return;
        }
        PositionService.Outcome outcome = positions.apply(trade);
        if (outcome == PositionService.Outcome.DUPLICATE) {
            log.info("duplicate trade {} ignored", trade.tradeId());
        }
    }

    public long rejectedCount() {
        return rejected.get();
    }
}
