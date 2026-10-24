package com.chiheb.pps.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionTest {

    private static Trade trade(String id, Side side, long qty, String price) {
        return new Trade(id, "FR0000131104", side, qty, new BigDecimal(price), null);
    }

    private static void assertAmount(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }

    @Test
    void buysUpdateTheWeightedAverageCost() {
        Position p = Position.flat("FR0000131104")
                .apply(trade("1", Side.BUY, 100, "10"))
                .apply(trade("2", Side.BUY, 100, "12"));
        assertThat(p.quantity()).isEqualTo(200);
        assertAmount(p.averageCost(), "11");
        assertAmount(p.realizedPnl(), "0");
        assertThat(p.tradeCount()).isEqualTo(2);
    }

    @Test
    void reducingRealisesPnlAndKeepsAverageCost() {
        Position p = Position.flat("FR0000131104")
                .apply(trade("1", Side.BUY, 100, "10"))
                .apply(trade("2", Side.BUY, 100, "12"))
                .apply(trade("3", Side.SELL, 50, "15"));
        assertThat(p.quantity()).isEqualTo(150);
        assertAmount(p.averageCost(), "11");
        assertAmount(p.realizedPnl(), "200");
    }

    @Test
    void flippingReopensAtTheTradePrice() {
        Position p = Position.flat("FR0000131104")
                .apply(trade("1", Side.BUY, 100, "10"))
                .apply(trade("2", Side.BUY, 100, "12"))
                .apply(trade("3", Side.SELL, 50, "15"))
                .apply(trade("4", Side.SELL, 250, "9"));
        assertThat(p.quantity()).isEqualTo(-100);
        assertAmount(p.averageCost(), "9");
        assertAmount(p.realizedPnl(), "-100"); // +200 then -300 on the 150 closed at 9

        Position flat = p.apply(trade("5", Side.BUY, 100, "8"));
        assertThat(flat.quantity()).isZero();
        assertAmount(flat.averageCost(), "0");
        assertAmount(flat.realizedPnl(), "0"); // short 100 at 9, covered at 8: +100
    }

    @Test
    void unrealizedPnlOfAShortPosition() {
        Position shortPos = Position.flat("FR0000131104").apply(trade("1", Side.SELL, 10, "50"));
        assertAmount(shortPos.unrealizedPnl(new BigDecimal("45")), "50");
    }

    @Test
    void tradeValidation() {
        assertThatThrownBy(() -> trade("", Side.BUY, 1, "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> trade("1", Side.BUY, 0, "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> trade("1", Side.BUY, 1, "-2")).isInstanceOf(IllegalArgumentException.class);
    }
}
