package com.chiheb.pps.pricing;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Prices options. Results are cached (Redis in production, see application.yml): identical requests
 * for the same market inputs are common when several screens or risk jobs ask for the same instrument.
 */
@Service
public class PricingService {

    @Cacheable(cacheNames = "option-prices")
    public OptionResult price(OptionRequest request) {
        return BlackScholes.price(request);
    }
}
