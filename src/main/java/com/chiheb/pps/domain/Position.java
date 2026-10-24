package com.chiheb.pps.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Net position on one instrument, maintained with the weighted average cost method.
 *
 * <ul>
 *   <li>Trades in the direction of the position (or opening it) update the average cost.</li>
 *   <li>Trades against the position realise P&amp;L on the closed quantity at
 *       {@code (price - averageCost) * closedQty * direction}; the average cost is unchanged.</li>
 *   <li>A trade larger than the position flips it: the remainder opens a new position at the trade price.</li>
 * </ul>
 *
 * Immutable: {@link #apply(Trade)} returns a new instance, which makes concurrent updates safe when
 * combined with an atomic compute on the position map.
 */
public record Position(String instrument, long quantity, BigDecimal averageCost, BigDecimal realizedPnl, long tradeCount) {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_EVEN);
    private static final int SCALE = 8;

    public static Position flat(String instrument) {
        return new Position(instrument, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }

    public Position apply(Trade trade) {
        if (!trade.instrument().equals(instrument)) {
            throw new IllegalArgumentException("trade " + trade.tradeId() + " is for " + trade.instrument()
                    + ", not " + instrument);
        }
        final long delta = trade.signedQuantity();
        final BigDecimal px = trade.price();

        // Opening or increasing the position: new weighted average cost.
        if (quantity == 0 || Long.signum(quantity) == Long.signum(delta)) {
            long newQty = quantity + delta;
            BigDecimal totalCost = averageCost.multiply(BigDecimal.valueOf(Math.abs(quantity)))
                    .add(px.multiply(BigDecimal.valueOf(Math.abs(delta))));
            BigDecimal newAvg = totalCost.divide(BigDecimal.valueOf(Math.abs(newQty)), MC).setScale(SCALE, RoundingMode.HALF_EVEN);
            return new Position(instrument, newQty, newAvg, realizedPnl, tradeCount + 1);
        }

        // Reducing, closing or flipping the position.
        long closed = Math.min(Math.abs(delta), Math.abs(quantity));
        BigDecimal direction = BigDecimal.valueOf(Long.signum(quantity));
        BigDecimal pnl = px.subtract(averageCost).multiply(BigDecimal.valueOf(closed)).multiply(direction);
        BigDecimal newRealized = realizedPnl.add(pnl).setScale(SCALE, RoundingMode.HALF_EVEN);
        long newQty = quantity + delta;

        if (newQty == 0) {
            return new Position(instrument, 0, BigDecimal.ZERO, newRealized, tradeCount + 1);
        }
        if (Long.signum(newQty) == Long.signum(quantity)) {
            return new Position(instrument, newQty, averageCost, newRealized, tradeCount + 1);
        }
        // Flipped: the remaining quantity is a new position opened at the trade price.
        return new Position(instrument, newQty, px.setScale(SCALE, RoundingMode.HALF_EVEN), newRealized, tradeCount + 1);
    }

    /** Unrealised P&amp;L of the open quantity at the given market price. */
    public BigDecimal unrealizedPnl(BigDecimal marketPrice) {
        if (quantity == 0) return BigDecimal.ZERO;
        return marketPrice.subtract(averageCost).multiply(BigDecimal.valueOf(quantity))
                .setScale(SCALE, RoundingMode.HALF_EVEN);
    }
}
