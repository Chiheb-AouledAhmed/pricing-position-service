package com.chiheb.pps.pricing;

/**
 * Black-Scholes-Merton pricing of European options on a dividend-paying underlying
 * (continuous dividend yield q), with analytical greeks.
 *
 * <pre>
 *   d1 = (ln(S/K) + (r - q + sigma^2/2) T) / (sigma sqrt(T))      d2 = d1 - sigma sqrt(T)
 *   call = S e^{-qT} N(d1) - K e^{-rT} N(d2)
 *   put  = K e^{-rT} N(-d2) - S e^{-qT} N(-d1)
 * </pre>
 *
 * Theta is returned per calendar day, vega and rho per 1% move (market convention).
 */
public final class BlackScholes {

    private static final double INV_SQRT_2PI = 1.0 / Math.sqrt(2.0 * Math.PI);

    private BlackScholes() {}

    public static OptionResult price(OptionRequest req) {
        req.validate();
        final double s = req.spot(), k = req.strike(), t = req.maturityYears();
        final double r = req.rate(), q = req.dividendYield(), sigma = req.volatility();

        final double sqrtT = Math.sqrt(t);
        final double d1 = (Math.log(s / k) + (r - q + 0.5 * sigma * sigma) * t) / (sigma * sqrtT);
        final double d2 = d1 - sigma * sqrtT;
        final double dfR = Math.exp(-r * t);
        final double dfQ = Math.exp(-q * t);
        final double pdfD1 = pdf(d1);

        final double price, delta, theta, rho;
        if (req.type() == OptionType.CALL) {
            price = s * dfQ * cdf(d1) - k * dfR * cdf(d2);
            delta = dfQ * cdf(d1);
            theta = -s * dfQ * pdfD1 * sigma / (2 * sqrtT) - r * k * dfR * cdf(d2) + q * s * dfQ * cdf(d1);
            rho = k * t * dfR * cdf(d2);
        } else {
            price = k * dfR * cdf(-d2) - s * dfQ * cdf(-d1);
            delta = -dfQ * cdf(-d1);
            theta = -s * dfQ * pdfD1 * sigma / (2 * sqrtT) + r * k * dfR * cdf(-d2) - q * s * dfQ * cdf(-d1);
            rho = -k * t * dfR * cdf(-d2);
        }
        final double gamma = dfQ * pdfD1 / (s * sigma * sqrtT);
        final double vega = s * dfQ * pdfD1 * sqrtT;

        return new OptionResult(price, delta, gamma, vega / 100.0, theta / 365.0, rho / 100.0, d1, d2);
    }

    /** Standard normal density. */
    static double pdf(double x) {
        return INV_SQRT_2PI * Math.exp(-0.5 * x * x);
    }

    /** Standard normal cumulative distribution, via the complementary error function (~1e-15 accuracy). */
    static double cdf(double x) {
        return 0.5 * erfc(-x / Math.sqrt(2.0));
    }

    /** Complementary error function, Numerical Recipes (3rd ed.) erfccheb-style Chebyshev fit. */
    static double erfc(double x) {
        if (x < 0) return 2.0 - erfc(-x);
        // Chebyshev coefficients for erfc on [0, inf) (Numerical Recipes, 3rd ed., section 6.2.2).
        final double[] cof = {
            -1.3026537197817094, 6.4196979235649026e-1, 1.9476473204185836e-2, -9.561514786808631e-3,
            -9.46595344482036e-4, 3.66839497852761e-4, 4.2523324806907e-5, -2.0278578112534e-5,
            -1.624290004647e-6, 1.303655835580e-6, 1.5626441722e-8, -8.5238095915e-8,
            6.529054439e-9, 5.059343495e-9, -9.91364156e-10, -2.27365122e-10,
            9.6467911e-11, 2.394038e-12, -6.886027e-12, 8.94487e-13, 3.13092e-13,
            -1.12708e-13, 3.81e-16, 7.106e-15, -1.523e-15, -9.4e-17, 1.21e-16, -2.8e-17
        };
        final double t = 2.0 / (2.0 + x);
        final double ty = 4.0 * t - 2.0;
        double d = 0.0, dd = 0.0;
        for (int j = cof.length - 1; j > 0; j--) {
            final double tmp = d;
            d = ty * d - dd + cof[j];
            dd = tmp;
        }
        return t * Math.exp(-x * x + 0.5 * (cof[0] + ty * d) - dd);
    }
}
