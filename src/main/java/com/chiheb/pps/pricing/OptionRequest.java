package com.chiheb.pps.pricing;

/**
 * Inputs of a European option pricing.
 *
 * @param type          CALL or PUT
 * @param spot          underlying price (&gt; 0)
 * @param strike        strike (&gt; 0)
 * @param maturityYears time to maturity in years (&gt; 0)
 * @param rate          continuously compounded risk-free rate (e.g. 0.03 for 3%)
 * @param dividendYield continuous dividend yield (0 if none)
 * @param volatility    annualised volatility (e.g. 0.20 for 20%, &gt; 0)
 */
public record OptionRequest(OptionType type, double spot, double strike, double maturityYears, double rate,
                            double dividendYield, double volatility) implements java.io.Serializable {

    public void validate() {
        if (type == null) throw new IllegalArgumentException("type is required");
        if (!(spot > 0)) throw new IllegalArgumentException("spot must be > 0");
        if (!(strike > 0)) throw new IllegalArgumentException("strike must be > 0");
        if (!(maturityYears > 0)) throw new IllegalArgumentException("maturityYears must be > 0");
        if (!(volatility > 0)) throw new IllegalArgumentException("volatility must be > 0");
        if (!Double.isFinite(rate) || !Double.isFinite(dividendYield)) {
            throw new IllegalArgumentException("rate and dividendYield must be finite");
        }
    }
}
