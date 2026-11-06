package com.tcc.api_visa.security;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Requests to the public API ({@code /api/**}) allowed per client IP.
 *
 * @param capacity requests allowed in each {@code period}; short bursts up to this number are fine
 * @param period   time to refill the whole capacity
 */
@Validated
@ConfigurationProperties("api-visa.rate-limit")
public record RateLimitProperties(
        @Positive int capacity, @NotNull Duration period) {}
