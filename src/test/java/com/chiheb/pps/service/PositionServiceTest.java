package com.chiheb.pps.service;

import com.chiheb.pps.domain.Position;
import com.chiheb.pps.domain.Side;
import com.chiheb.pps.domain.Trade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PositionServiceTest {

    private static Trade trade(String id, String instrument, Side side, long qty) {
        return new Trade(id, instrument, side, qty, new BigDecimal("100"), null);
    }

    @Test
    void redeliveredTradeIsIgnored() {
        List<Position> events = new CopyOnWriteArrayList<>();
        PositionService service = new PositionService(events::add);
        Trade t = trade("T-1", "AAA", Side.BUY, 10);

        assertThat(service.apply(t)).isEqualTo(PositionService.Outcome.APPLIED);
        assertThat(service.apply(t)).isEqualTo(PositionService.Outcome.DUPLICATE);
        assertThat(events).hasSize(1);
        assertThat(service.position("AAA")).get().extracting(Position::quantity).isEqualTo(10L);
    }

    @Test
    void concurrentTradesAreAllAccountedFor() throws InterruptedException {
        PositionService service = new PositionService(null);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicLong expectedNet = new AtomicLong();
        for (int thread = 0; thread < 8; thread++) {
            int t = thread;
            pool.submit(() -> {
                for (int i = 0; i < 5_000; i++) {
                    Side side = i % 3 == 0 ? Side.SELL : Side.BUY;
                    long qty = 1 + i % 7;
                    service.apply(trade(t + "-" + i, "I" + i % 4, side, qty));
                    expectedNet.addAndGet(side.sign() * qty);
                }
            });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(service.processedTrades()).isEqualTo(40_000);
        assertThat(service.all()).extracting(Position::instrument).containsExactly("I0", "I1", "I2", "I3");
        assertThat(service.all().stream().mapToLong(Position::quantity).sum()).isEqualTo(expectedNet.get());
        assertThat(service.all().stream().mapToLong(Position::tradeCount).sum()).isEqualTo(40_000);
    }
}
