package com.chiheb.pps.pricing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BlackScholesTest {

    private static OptionResult price(OptionType type, double s, double k, double t, double r, double q, double vol) {
        return BlackScholes.price(new OptionRequest(type, s, k, t, r, q, vol));
    }

    @Test
    void matchesReferencePrices() {
        // Hull's textbook case: S=K=100, T=1, r=5%, vol=20%
        assertThat(price(OptionType.CALL, 100, 100, 1, 0.05, 0, 0.2).price()).isCloseTo(10.450583572185565, within(1e-8));
        assertThat(price(OptionType.PUT, 100, 100, 1, 0.05, 0, 0.2).price()).isCloseTo(5.573526022256971, within(1e-8));
    }

    @ParameterizedTest
    @CsvSource({
            "100, 100, 1.0, 0.05, 0.00, 0.20",
            "100,  95, 0.5, 0.03, 0.02, 0.25",
            " 50,  80, 2.0, 0.01, 0.00, 0.60",
            "120, 100, 0.1, 0.04, 0.03, 0.15"
    })
    void satisfiesPutCallParity(double s, double k, double t, double r, double q, double vol) {
        double call = price(OptionType.CALL, s, k, t, r, q, vol).price();
        double put = price(OptionType.PUT, s, k, t, r, q, vol).price();
        assertThat(call - put).isCloseTo(s * Math.exp(-q * t) - k * Math.exp(-r * t), within(1e-9));
    }

    @Test
    void analyticalGreeksMatchFiniteDifferences() {
        OptionResult c = price(OptionType.CALL, 100, 100, 1, 0.05, 0, 0.2);
        double h = 1e-4;
        double up = price(OptionType.CALL, 100 + h, 100, 1, 0.05, 0, 0.2).price();
        double dn = price(OptionType.CALL, 100 - h, 100, 1, 0.05, 0, 0.2).price();
        assertThat((up - dn) / (2 * h)).isCloseTo(c.delta(), within(1e-6));
        assertThat((up - 2 * c.price() + dn) / (h * h)).isCloseTo(c.gamma(), within(1e-3));

        double volUp = price(OptionType.CALL, 100, 100, 1, 0.05, 0, 0.21).price();
        assertThat(volUp - c.price()).isCloseTo(c.vega(), within(2e-3));

        double oneDayLater = price(OptionType.CALL, 100, 100, 1 - 1 / 365.0, 0.05, 0, 0.2).price();
        assertThat(oneDayLater - c.price()).isCloseTo(c.theta(), within(2e-4));

        double dr = 1e-4; // rho is quoted per 1% move
        double rateUp = price(OptionType.CALL, 100, 100, 1, 0.05 + dr, 0, 0.2).price();
        double rateDn = price(OptionType.CALL, 100, 100, 1, 0.05 - dr, 0, 0.2).price();
        assertThat((rateUp - rateDn) / (2 * dr) / 100).isCloseTo(c.rho(), within(1e-6));
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> price(OptionType.CALL, 100, 100, 1, 0.05, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("volatility");
        assertThatThrownBy(() -> price(OptionType.CALL, -1, 100, 1, 0.05, 0, 0.2))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("spot");
        assertThatThrownBy(() -> price(null, 100, 100, 1, 0.05, 0, 0.2))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("type");
    }
}
