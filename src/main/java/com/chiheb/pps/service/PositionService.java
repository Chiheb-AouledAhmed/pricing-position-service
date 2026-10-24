package com.chiheb.pps.service;

import com.chiheb.pps.domain.Position;
import com.chiheb.pps.domain.Trade;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Keeps net positions up to date from the trade flow.
 *
 * <ul>
 *   <li><b>Idempotent</b>: Kafka gives at-least-once delivery, so a redelivered trade id is ignored.</li>
 *   <li><b>Thread-safe</b>: positions are immutable and updated with {@link ConcurrentHashMap#compute},
 *       which is atomic per instrument; different instruments are updated in parallel.</li>
 *   <li>Each applied trade emits the new position to a listener (published on Kafka by the app).</li>
 * </ul>
 */
public class PositionService {

    public enum Outcome { APPLIED, DUPLICATE }

    private final Map<String, Position> positions = new ConcurrentHashMap<>();
    private final Set<String> processedTradeIds = ConcurrentHashMap.newKeySet();
    private final Consumer<Position> listener;

    public PositionService(Consumer<Position> listener) {
        this.listener = listener == null ? p -> {} : listener;
    }

    public Outcome apply(Trade trade) {
        if (!processedTradeIds.add(trade.tradeId())) {
            return Outcome.DUPLICATE;
        }
        try {
            Position updated = positions.compute(trade.instrument(),
                    (instrument, current) -> (current == null ? Position.flat(instrument) : current).apply(trade));
            listener.accept(updated);
            return Outcome.APPLIED;
        } catch (RuntimeException e) {
            processedTradeIds.remove(trade.tradeId()); // allow a corrected retry
            throw e;
        }
    }

    public Optional<Position> position(String instrument) {
        return Optional.ofNullable(positions.get(instrument));
    }

    public List<Position> all() {
        Collection<Position> values = positions.values();
        return values.stream().sorted(Comparator.comparing(Position::instrument)).toList();
    }

    public int processedTrades() {
        return processedTradeIds.size();
    }
}
