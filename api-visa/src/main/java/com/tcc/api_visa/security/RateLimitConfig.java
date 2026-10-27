package com.tcc.api_visa.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
class RateLimitConfig {

    /**
     * Only the public API is limited: health checks and the key-protected {@code /admin} are not.
     * Runs after the forwarded-headers filter (so the client IP is known) and before Spring Security.
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitProperties properties) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(properties));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
