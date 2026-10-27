package com.tcc.api_visa.exchange;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Rates from the European Central Bank through <a href="https://frankfurter.dev">Frankfurter</a>
 * (free, no key). Rates are cached, so each pair is fetched at most once per {@code cacheTtl}.
 * Network failures are not cached and are retried on the next request.
 */
@Slf4j
class FrankfurterExchangeRateProvider implements ExchangeRateProvider {

    private final RestClient restClient;
    private final Cache<String, Optional<ExchangeRate>> cache;

    FrankfurterExchangeRateProvider(RestClient restClient, ExchangeProperties properties) {
        this.restClient = restClient;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.cacheTtl())
                .maximumSize(1_000)
                .build();
    }

    @Override
    public Optional<ExchangeRate> rate(String fromCurrency, String toCurrency) {
        Optional<ExchangeRate> rate =
                cache.get(fromCurrency + ">" + toCurrency, key -> fetch(fromCurrency, toCurrency));
        return rate == null ? Optional.empty() : rate;
    }

    /** {@code null} (not cached) when the source could not be reached. */
    private @Nullable Optional<ExchangeRate> fetch(String fromCurrency, String toCurrency) {
        try {
            FrankfurterResponse response = restClient
                    .get()
                    .uri(uri -> uri.path("/latest")
                            .queryParam("base", fromCurrency)
                            .queryParam("symbols", toCurrency)
                            .build())
                    .retrieve()
                    .body(FrankfurterResponse.class);
            BigDecimal rate = response == null || response.rates() == null
                    ? null
                    : response.rates().get(toCurrency);
            if (rate == null || response.date() == null) {
                return Optional.empty();
            }
            return Optional.of(new ExchangeRate(fromCurrency, toCurrency, rate, response.date()));
        } catch (HttpClientErrorException e) {
            // The ECB does not publish this currency (e.g. EGP, ARS); remember that for cacheTtl.
            log.info(
                    "No ECB rate for {} -> {}: HTTP {}",
                    fromCurrency,
                    toCurrency,
                    e.getStatusCode().value());
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Exchange rate {} -> {} unavailable: {}", fromCurrency, toCurrency, e.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FrankfurterResponse(LocalDate date, Map<String, BigDecimal> rates) {}
}
