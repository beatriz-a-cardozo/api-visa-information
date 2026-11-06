package com.tcc.api_visa.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    /** Injected wherever the current time is needed, so tests can fix it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
