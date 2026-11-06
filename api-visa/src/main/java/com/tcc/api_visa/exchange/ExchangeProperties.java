package com.tcc.api_visa.exchange;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param baseUrl  Frankfurter API (European Central Bank rates)
 * @param timeout  maximum time to wait for a rate; on timeout the response goes without conversion
 * @param cacheTtl how long a rate is reused; the ECB publishes once per working day
 */
@Validated
@ConfigurationProperties("api-visa.exchange")
public record ExchangeProperties(
        @NotNull URI baseUrl,
        @NotNull Duration timeout,
        @NotNull Duration cacheTtl) {}
