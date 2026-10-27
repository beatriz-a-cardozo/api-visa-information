package com.tcc.api_visa.api.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.tcc.api_visa.domain.DataOrigin;
import com.tcc.api_visa.domain.VisaHistory;
import com.tcc.api_visa.domain.VisaRequirement;
import com.tcc.api_visa.exchange.ExchangeRate;
import com.tcc.api_visa.exchange.ExchangeRateProvider;
import com.tcc.api_visa.repository.VisaHistoryRepository;
import com.tcc.api_visa.repository.VisaRequirementRepository;
import com.tcc.api_visa.support.MongoTestcontainersConfiguration;
import com.tcc.api_visa.support.TestData;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Runs over real HTTP so validation, error handling, security and the rate limit are all exercised. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MongoTestcontainersConfiguration.class)
class PublicApiTest {

    private static final Instant COLLECTED_AT = Instant.parse("2026-10-20T05:00:00Z");

    @Autowired
    private Environment environment;

    @Autowired
    private VisaRequirementRepository visaRequirementRepository;

    @Autowired
    private VisaHistoryRepository visaHistoryRepository;

    @MockitoBean
    private ExchangeRateProvider exchangeRateProvider;

    @BeforeEach
    void cleanUp() {
        visaRequirementRepository.deleteAll();
        visaHistoryRepository.deleteAll();
        when(exchangeRateProvider.rate(anyString(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void listsTheCountryCatalogSortedByCode() throws Exception {
        HttpResponse<String> response = get("/api/v1/countries");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .startsWith("[{\"iso3\":\"ARG\"")
                .contains("\"iso3\":\"BRA\"", "\"currency\":\"BRL\"", "\"officialSources\":[{\"url\":\"https://");
    }

    @Test
    void returnsTheVisaWithTheFeeConvertedToTheOriginCurrency() throws Exception {
        visaRequirementRepository.save(requirement("BRA", "GBR", "135.50"));
        when(exchangeRateProvider.rate("GBP", "BRL"))
                .thenReturn(Optional.of(
                        new ExchangeRate("GBP", "BRL", new BigDecimal("7.0123"), LocalDate.parse("2026-10-26"))));

        HttpResponse<String> response = get("/api/v1/visas?origin=BRA&destination=GBR");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains(
                        "\"origin\":{\"iso3\":\"BRA\",\"name\":\"Brazil\",\"currency\":\"BRL\"}",
                        "\"policy\":\"VISA_REQUIRED\"",
                        "\"cost\":{\"amount\":135.50,\"currency\":\"GBP\"}",
                        "\"costInOriginCurrency\":{\"amount\":950.17,\"currency\":\"BRL\",\"rate\":7.0123,"
                                + "\"rateDate\":\"2026-10-26\",\"source\":\"European Central Bank (via Frankfurter)\"}",
                        "\"sourceUrls\":[\"" + TestData.SOURCE + "\"]",
                        "\"dataOrigin\":\"SCRAPER\"",
                        "\"collectedAt\":\"2026-10-20T05:00:00Z\"");
    }

    @Test
    void stillAnswersWhenNoExchangeRateIsAvailable() throws Exception {
        visaRequirementRepository.save(requirement("BRA", "GBR", "135.50"));

        HttpResponse<String> response = get("/api/v1/visas?origin=BRA&destination=GBR");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"cost\":{\"amount\":135.50,\"currency\":\"GBP\"}", "\"costInOriginCurrency\":null");
    }

    @Test
    void answersNotFoundWhenThePairWasNotCollectedYet() throws Exception {
        HttpResponse<String> response = get("/api/v1/visas?origin=BRA&destination=GBR");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"title\":\"No visa data\"");
    }

    @Test
    void answersNotFoundForCountriesOutsideTheirRole() throws Exception {
        // GBR is only a destination; ZZZ is not in the catalog.
        assertThat(get("/api/v1/visas?origin=GBR&destination=BRA").statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/visas?origin=BRA&destination=ZZZ").body())
                .contains("\"title\":\"Unknown country\"", "ZZZ is not a supported destination country");
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        assertThat(get("/api/v1/visas?origin=BRA&destination=BRA").body())
                .contains("\"title\":\"Invalid country pair\"");

        HttpResponse<String> lowercase = get("/api/v1/visas?origin=bra&destination=GBR");
        assertThat(lowercase.statusCode()).isEqualTo(400);
        assertThat(lowercase.body()).doesNotContain("Exception");

        assertThat(get("/api/v1/visas?origin=BRA").statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/visas/history?origin=BRA&destination=gbr").statusCode())
                .isEqualTo(400);
    }

    @Test
    void returnsTheChangesOfTheLastYearNewestFirst() throws Exception {
        Instant now = Instant.now();
        visaHistoryRepository.save(history(now.minus(Duration.ofDays(30)), "100.00"));
        visaHistoryRepository.save(history(now.minus(Duration.ofDays(5)), "120.00"));
        visaHistoryRepository.save(history(now.minus(Duration.ofDays(400)), "90.00"));

        HttpResponse<String> response = get("/api/v1/visas/history?origin=BRA&destination=GBR");

        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body).contains("\"changeOrigin\":\"SCRAPER\"").doesNotContain("90.00");
        assertThat(body.indexOf("120.00")).isLessThan(body.indexOf("100.00"));
        assertThat(body).contains("\"costInOriginCurrency\":null");
    }

    @Test
    void returnsAnEmptyHistoryForAPairThatNeverChanged() throws Exception {
        HttpResponse<String> response = get("/api/v1/visas/history?origin=JPN&destination=CAN");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"changes\":[]");
    }

    @Test
    void reportsTheRateLimitInTheHeaders() throws Exception {
        HttpResponse<String> response = get("/api/v1/countries");

        assertThat(response.headers().firstValue("X-RateLimit-Limit")).hasValue("60");
        assertThat(response.headers().firstValue("X-RateLimit-Remaining")).isPresent();
        assertThat(get("/actuator/health").headers().firstValue("X-RateLimit-Limit"))
                .isEmpty();
    }

    @Test
    void publishesTheOpenApiDocument() throws Exception {
        HttpResponse<String> docs = get("/v3/api-docs");

        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(docs.body())
                .contains("\"/api/v1/visas\"", "\"/api/v1/visas/history\"", "\"/api/v1/countries\"")
                .contains("\"/admin/collections\"", "\"adminKey\"", "X-Admin-Key")
                .doesNotContain("/actuator");
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        String port = environment.getRequiredProperty("local.server.port");
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    private static VisaRequirement requirement(String origin, String destination, String fee) {
        VisaRequirement requirement = new VisaRequirement();
        requirement.setOriginIso3(origin);
        requirement.setDestinationIso3(destination);
        requirement.setContent(TestData.content(fee));
        requirement.setContentHash("hash");
        requirement.setDataOrigin(DataOrigin.SCRAPER);
        requirement.setCollectedAt(COLLECTED_AT);
        requirement.setUpdatedAt(COLLECTED_AT);
        return requirement;
    }

    private static VisaHistory history(Instant changedAt, String previousFee) {
        VisaHistory history = new VisaHistory();
        history.setOriginIso3("BRA");
        history.setDestinationIso3("GBR");
        history.setPreviousContent(TestData.content(previousFee));
        history.setChangeOrigin(DataOrigin.SCRAPER);
        history.setChangedAt(changedAt);
        return history;
    }
}
