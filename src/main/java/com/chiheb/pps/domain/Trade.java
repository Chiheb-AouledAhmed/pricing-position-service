package com.chiheb.pps.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * An executed trade, as received from the trade capture / execution system.
 *
 * @param tradeId    unique id, used to make processing idempotent (Kafka delivers at least once)
 * @param instrument instrument identifier (ISIN, ticker...)
 * @param side       BUY or SELL
 * @param quantity   strictly positive quantity
 * @param price      strictly positive execution price
 * @param executedAt execution timestamp (optional)
 */
public record Trade(String tradeId, String instrument, Side side, long quantity, BigDecimal price, Instant executedAt) {

    public Trade {
        if (tradeId == null || tradeId.isBlank()) throw new IllegalArgumentException("tradeId is required");
        if (instrument == null || instrument.isBlank()) throw new IllegalArgumentException("instrument is required");
        Objects.requireNonNull(side, "side is required");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        if (price == null || price.signum() <= 0) throw new IllegalArgumentException("price must be > 0");
    }

    /** Quantity with its sign: positive for a buy, negative for a sell. */
    public long signedQuantity() {
        return side.sign() * quantity;
    }
}
