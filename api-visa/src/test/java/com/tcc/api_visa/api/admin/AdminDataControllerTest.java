package com.tcc.api_visa.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.tcc.api_visa.domain.Country;
import com.tcc.api_visa.domain.DataOrigin;
import com.tcc.api_visa.domain.OfficialSource;
import com.tcc.api_visa.domain.VisaHistory;
import com.tcc.api_visa.repository.CountryRepository;
import com.tcc.api_visa.repository.VisaHistoryRepository;
import com.tcc.api_visa.repository.VisaRequirementRepository;
import com.tcc.api_visa.security.HostResolver;
import com.tcc.api_visa.service.PairOutcome;
import com.tcc.api_visa.service.VisaRequirementWriter;
import com.tcc.api_visa.support.MongoTestcontainersConfiguration;
import com.tcc.api_visa.support.TestData;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Official sources and manual corrections, over real HTTP with the admin key. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "api-visa.admin.api-key=" + AdminDataControllerTest.KEY)
@Import(MongoTestcontainersConfiguration.class)
class AdminDataControllerTest {

    static final String KEY = "test-admin-key-0123456789abcdef0123456789";
    private static final String AUS_SOURCE = "https://immi.homeaffairs.gov.au/visas/getting-a-visa/visa-finder";
    private static final String SOURCES = "/admin/countries/AUS/sources";

    @Autowired
    private Environment environment;

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private VisaRequirementRepository visaRequirementRepository;

    @Autowired
    private VisaHistoryRepository visaHistoryRepository;

    @Autowired
    private VisaRequirementWriter writer;

    @MockitoBean
    private HostResolver hostResolver;

    @BeforeEach
    void setUp() throws Exception {
        when(hostResolver.resolve(anyString())).thenReturn(List.of(InetAddress.getByName("93.184.215.14")));
        when(hostResolver.resolve("intranet.example.org")).thenReturn(List.of(InetAddress.getByName("10.0.0.7")));

        Country australia = countryRepository.findById("AUS").orElseThrow();
        australia.setOfficialSources(List.of(new OfficialSource(AUS_SOURCE, "Home Affairs - visa finder", "en", null)));
        countryRepository.save(australia);
        visaRequirementRepository.deleteAll();
        visaHistoryRepository.deleteAll();
    }

    @Test
    void addsAndListsASource() throws Exception {
        HttpResponse<String> added =
                send("POST", SOURCES, source("https://www.abf.gov.au/entering-and-leaving-australia", true));

        assertThat(added.statusCode()).isEqualTo(201);
        assertThat(added.body())
                .contains("\"url\":\"https://www.abf.gov.au/entering-and-leaving-australia\"", "\"verifiedAt\":\"");
        assertThat(send("GET", SOURCES, null).body()).contains(AUS_SOURCE, "www.abf.gov.au");
        assertThat(send("GET", "/api/v1/countries", null).body()).contains("www.abf.gov.au");
    }

    @Test
    void refusesADuplicateUrlUnsafeUrlsAndUnknownCountries() throws Exception {
        HttpResponse<String> duplicate = send("POST", SOURCES, source(AUS_SOURCE, false));
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("\"title\":\"Duplicate source\"");

        for (String unsafe : List.of(
                "http://www.abf.gov.au/", "https://intranet.example.org/admin", "https://169.254.169.254/latest")) {
            HttpResponse<String> response = send("POST", SOURCES, source(unsafe, false));
            assertThat(response.statusCode()).as(unsafe).isEqualTo(400);
            assertThat(response.body()).contains("\"title\":\"Invalid URL\"");
        }

        assertThat(send("POST", "/admin/countries/ZZZ/sources", source("https://www.gov.uk/", false))
                        .statusCode())
                .isEqualTo(404);
        assertThat(send("POST", SOURCES, "{\"url\":\"https://www.abf.gov.au/\"}")
                        .statusCode())
                .isEqualTo(400);
        assertThat(countryRepository.findById("AUS").orElseThrow().getOfficialSources())
                .hasSize(1);
    }

    @Test
    void replacesASourceByItsUrl() throws Exception {
        String newUrl = "https://immi.homeaffairs.gov.au/visas/getting-a-visa/visa-listing";

        HttpResponse<String> replaced = send("PUT", SOURCES + "?url=" + encode(AUS_SOURCE), source(newUrl, true));

        assertThat(replaced.statusCode()).isEqualTo(200);
        List<OfficialSource> sources =
                countryRepository.findById("AUS").orElseThrow().getOfficialSources();
        assertThat(sources).singleElement().satisfies(source -> {
            assertThat(source.url()).isEqualTo(newUrl);
            assertThat(source.verifiedAt()).isNotNull();
        });

        assertThat(send("PUT", SOURCES + "?url=" + encode(AUS_SOURCE), source(newUrl, false))
                        .statusCode())
                .isEqualTo(404);
    }

    @Test
    void doesNotReplaceASourceWithAUrlTheCountryAlreadyHas() throws Exception {
        String other = "https://www.abf.gov.au/entering-and-leaving-australia";
        send("POST", SOURCES, source(other, false));

        HttpResponse<String> response = send("PUT", SOURCES + "?url=" + encode(other), source(AUS_SOURCE, false));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(countryRepository.findById("AUS").orElseThrow().getOfficialSources())
                .extracting(OfficialSource::url)
                .containsExactly(AUS_SOURCE, other);
    }

    @Test
    void removesASource() throws Exception {
        assertThat(send("DELETE", SOURCES + "?url=" + encode(AUS_SOURCE), null).statusCode())
                .isEqualTo(204);
        assertThat(countryRepository.findById("AUS").orElseThrow().getOfficialSources())
                .isEmpty();
        assertThat(send("DELETE", SOURCES + "?url=" + encode(AUS_SOURCE), null).body())
                .contains("\"title\":\"Unknown source\"");
    }

    @Test
    void sourcesRequireTheAdminKey() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(SOURCES))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(source("https://www.abf.gov.au/", false)))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString())
                            .statusCode())
                    .isEqualTo(401);
        }
    }

    @Test
    void aManualCorrectionIsServedAndProtectedFromTheScraper() throws Exception {
        HttpResponse<String> created = send("PUT", "/admin/visas?origin=BRA&destination=GBR", manualVisa("150.00"));
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"outcome\":\"CREATED\"");

        assertThat(send("GET", "/api/v1/visas?origin=BRA&destination=GBR", null).body())
                .contains("\"dataOrigin\":\"MANUAL\"", "\"amount\":150.00", "\"collectedAt\":null");

        // A collection does not overwrite it.
        assertThat(writer.apply(TestData.scraped("BRA", "GBR", "135.50"), Instant.now()))
                .isEqualTo(PairOutcome.SKIPPED_MANUAL);

        HttpResponse<String> changed = send("PUT", "/admin/visas?origin=BRA&destination=GBR", manualVisa("160.00"));
        assertThat(changed.statusCode()).isEqualTo(200);
        assertThat(changed.body()).contains("\"outcome\":\"CHANGED\"");
        assertThat(send("PUT", "/admin/visas?origin=BRA&destination=GBR", manualVisa("160.00"))
                        .body())
                .contains("\"outcome\":\"UNCHANGED\"");

        assertThat(visaHistoryRepository.findAll())
                .singleElement()
                .extracting(VisaHistory::getChangeOrigin)
                .isEqualTo(DataOrigin.MANUAL);
    }

    @Test
    void aReleasedPairGoesBackToTheScraper() throws Exception {
        send("PUT", "/admin/visas?origin=BRA&destination=GBR", manualVisa("150.00"));

        assertThat(send("DELETE", "/admin/visas/manual?origin=BRA&destination=GBR", null)
                        .statusCode())
                .isEqualTo(204);

        assertThat(writer.apply(TestData.scraped("BRA", "GBR", "135.50"), Instant.now()))
                .isEqualTo(PairOutcome.CHANGED);
        assertThat(send("DELETE", "/admin/visas/manual?origin=BRA&destination=CAN", null)
                        .statusCode())
                .isEqualTo(404);
    }

    @Test
    void rejectsInvalidManualData() throws Exception {
        String path = "/admin/visas?origin=BRA&destination=GBR";

        HttpResponse<String> unknownCurrency =
                send("PUT", path, manualVisa("150.00").replace("GBP", "ZZZ"));
        assertThat(unknownCurrency.statusCode()).isEqualTo(400);
        assertThat(unknownCurrency.body()).doesNotContain("Exception");

        assertThat(send("PUT", path, manualVisa("-1")).statusCode()).isEqualTo(400);
        assertThat(send(
                                "PUT",
                                path,
                                manualVisa("150.00")
                                        .replace(
                                                "\"sourceUrls\":[\"https://www.gov.uk/standard-visitor\"]",
                                                "\"sourceUrls\":[]"))
                        .statusCode())
                .isEqualTo(400);
        assertThat(send("PUT", path, manualVisa("150.00").replace("TOURIST", "HOLIDAY"))
                        .statusCode())
                .isEqualTo(400);
        assertThat(send(
                                "PUT",
                                path,
                                manualVisa("150.00")
                                        .replace(
                                                "https://www.gov.uk/standard-visitor",
                                                "https://intranet.example.org/visa"))
                        .body())
                .contains("\"title\":\"Invalid URL\"");
        assertThat(send("PUT", "/admin/visas?origin=GBR&destination=BRA", manualVisa("150.00"))
                        .statusCode())
                .isEqualTo(404);
        assertThat(visaRequirementRepository.count()).isZero();
    }

    private static String source(String url, boolean verified) {
        return """
                {"url": "%s", "description": "Official page", "language": "en", "verified": %s}
                """.formatted(url, verified);
    }

    private static String manualVisa(String fee) {
        return """
                {
                  "policy": "VISA_REQUIRED",
                  "visas": [{
                    "type": "TOURIST",
                    "name": "Standard Visitor visa",
                    "maxStayDays": 180,
                    "cost": {"amount": %s, "currency": "GBP"},
                    "requirements": [{"description": "Valid passport", "type": "DOCUMENT"}],
                    "sourceUrl": "https://www.gov.uk/standard-visitor"
                  }],
                  "sourceUrls":["https://www.gov.uk/standard-visitor"]
                }
                """.formatted(fee);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    }

    private HttpResponse<String> send(String method, String path, String json)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header("X-Admin-Key", KEY)
                .method(
                        method,
                        json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            request.header("Content-Type", "application/json");
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
