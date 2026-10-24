package com.chiheb.pps.domain;

public enum Side {
    BUY(1),
    SELL(-1);

    private final int sign;

    Side(int sign) {
        this.sign = sign;
    }

    /** +1 for a buy, -1 for a sell: signed quantity = sign * quantity. */
    public int sign() {
        return sign;
    }
}
