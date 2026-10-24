package com.chiheb.pps.api;

import com.chiheb.pps.pricing.OptionRequest;
import com.chiheb.pps.pricing.OptionResult;
import com.chiheb.pps.pricing.PricingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final PricingService pricing;

    public PricingController(PricingService pricing) {
        this.pricing = pricing;
    }

    @PostMapping("/options")
    public OptionResult price(@RequestBody OptionRequest request) {
        return pricing.price(request);
    }
}
