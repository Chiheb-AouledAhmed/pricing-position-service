package com.chiheb.pps.api;

import com.chiheb.pps.pricing.PricingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PricingController.class)
@Import(PricingService.class)
class PricingControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void pricesACall() throws Exception {
        mvc.perform(post("/api/pricing/options").contentType(MediaType.APPLICATION_JSON).content("""
                        {"type":"CALL","spot":100,"strike":100,"maturityYears":1,
                         "rate":0.05,"dividendYield":0,"volatility":0.2}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price", closeTo(10.4505836, 1e-6)))
                .andExpect(jsonPath("$.delta", closeTo(0.6368307, 1e-6)));
    }

    @Test
    void invalidInputIsA400() throws Exception {
        mvc.perform(post("/api/pricing/options").contentType(MediaType.APPLICATION_JSON).content("""
                        {"type":"PUT","spot":100,"strike":100,"maturityYears":1,
                         "rate":0.05,"dividendYield":0,"volatility":-0.2}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("volatility")));
    }

    @Test
    void malformedJsonIsA400() throws Exception {
        mvc.perform(post("/api/pricing/options").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
