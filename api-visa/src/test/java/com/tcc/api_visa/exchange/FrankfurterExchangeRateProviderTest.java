package com.tcc.api_visa.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FrankfurterExchangeRateProviderTest {

    private static final String BASE_URL = "https://api.frankfurter.test/v1";

    private MockRestServiceServer server;
    private FrankfurterExchangeRateProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new FrankfurterExchangeRateProvider(
                builder.build(),
                new ExchangeProperties(URI.create(BASE_URL), Duration.ofSeconds(5), Duration.ofHours(12)));
    }

    @Test
    void readsTheRateAndReusesItFromTheCache() {
        server.expect(once(), requestTo(BASE_URL + "/latest?base=GBP&symbols=BRL"))
                .andRespond(withSuccess("""
                        {"amount":1.0,"base":"GBP","date":"2026-10-26","rates":{"BRL":7.0123}}""", MediaType.APPLICATION_JSON));

        ExchangeRate expected = new ExchangeRate("GBP", "BRL", new BigDecimal("7.0123"), LocalDate.parse("2026-10-26"));
        assertThat(provider.rate("GBP", "BRL")).contains(expected);
        assertThat(provider.rate("GBP", "BRL")).contains(expected);
        server.verify();
    }

    @Test
    void remembersThatACurrencyIsNotPublished() {
        server.expect(once(), requestTo(BASE_URL + "/latest?base=EGP&symbols=BRL"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(provider.rate("EGP", "BRL")).isEmpty();
        assertThat(provider.rate("EGP", "BRL")).isEmpty();
        server.verify();
    }

    @Test
    void retriesAfterTheSourceFails() {
        server.expect(twice(), requestTo(BASE_URL + "/latest?base=CAD&symbols=JPY"))
                .andRespond(withServerError());

        assertThat(provider.rate("CAD", "JPY")).isEmpty();
        assertThat(provider.rate("CAD", "JPY")).isEmpty();
        server.verify();
    }

    @Test
    void treatsAnAnswerWithoutTheRateAsUnavailable() {
        server.expect(once(), requestTo(BASE_URL + "/latest?base=GBP&symbols=ZAR"))
                .andRespond(withSuccess("""
                        {"amount":1.0,"base":"GBP","date":"2026-10-26","rates":{}}""", MediaType.APPLICATION_JSON));

        assertThat(provider.rate("GBP", "ZAR")).isEmpty();
    }
}
