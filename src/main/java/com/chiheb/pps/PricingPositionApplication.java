package com.chiheb.pps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class PricingPositionApplication {

    public static void main(String[] args) {
        SpringApplication.run(PricingPositionApplication.class, args);
    }
}
