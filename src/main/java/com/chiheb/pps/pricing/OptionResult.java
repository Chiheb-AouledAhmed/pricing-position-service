package com.chiheb.pps.pricing;

/**
 * Price and greeks of a European option.
 *
 * @param price premium
 * @param delta dV/dS
 * @param gamma d2V/dS2
 * @param vega  change in price for a +1 volatility point (1%)
 * @param theta change in price for one calendar day
 * @param rho   change in price for a +1% rate move
 */
public record OptionResult(double price, double delta, double gamma, double vega, double theta, double rho,
                           double d1, double d2) implements java.io.Serializable {}
