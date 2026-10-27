package com.tcc.api_visa.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tcc.api_visa.domain.CollectionLog;
import com.tcc.api_visa.domain.CollectionStatus;
import com.tcc.api_visa.repository.CollectionLogRepository;
import com.tcc.api_visa.service.CollectionAlreadyRunningException;
import com.tcc.api_visa.service.CollectionService;
import com.tcc.api_visa.service.UnknownDestinationException;
import com.tcc.api_visa.support.MongoTestcontainersConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Runs over real HTTP so the whole security filter chain is exercised. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "api-visa.admin.api-key=" + AdminCollectionControllerTest.KEY)
@Import(MongoTestcontainersConfiguration.class)
class AdminCollectionControllerTest {

    static final String KEY = "test-admin-key-0123456789abcdef0123456789";
    private static final String RUN_ID = "4c0e6a9e-2b1f-4a43-9a51-2b7c1f0d9e11";

    @Autowired
    private Environment environment;

    @Autowired
    private CollectionLogRepository logRepository;

    @MockitoBean
    private CollectionService collectionService;

    @BeforeEach
    void cleanUp() {
        logRepository.deleteAll();
    }

    @Test
    void refusesRequestsWithoutTheKey() throws Exception {
        assertThat(send("POST", "/admin/collections", null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/admin/collections/logs", null).statusCode()).isEqualTo(401);
        verify(collectionService, never()).start(null);
    }

    @Test
    void refusesAWrongKey() throws Exception {
        assertThat(send("POST", "/admin/collections", KEY + "x").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/admin/collections", KEY.toUpperCase()).statusCode())
                .isEqualTo(401);
    }

    @Test
    void startsACollectionInTheBackground() throws Exception {
        when(collectionService.start(null)).thenReturn(RUN_ID);

        HttpResponse<String> response = send("POST", "/admin/collections", KEY);

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.headers().firstValue("Location")).hasValue("/admin/collections/" + RUN_ID);
        assertThat(response.body()).contains("\"runId\":\"" + RUN_ID + "\"");
    }

    @Test
    void startsASingleDestination() throws Exception {
        when(collectionService.start("GBR")).thenReturn(RUN_ID);

        assertThat(send("POST", "/admin/collections?destination=GBR", KEY).statusCode())
                .isEqualTo(202);
    }

    @Test
    void answersConflictWhileACollectionIsRunning() throws Exception {
        when(collectionService.start(null)).thenThrow(new CollectionAlreadyRunningException());

        HttpResponse<String> response = send("POST", "/admin/collections", KEY);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    }

    @Test
    void answersNotFoundForADestinationWithoutScraper() throws Exception {
        when(collectionService.start("ZZZ")).thenThrow(new UnknownDestinationException("ZZZ"));

        assertThat(send("POST", "/admin/collections?destination=ZZZ", KEY).statusCode())
                .isEqualTo(404);
    }

    @Test
    void validatesTheDestinationCode() throws Exception {
        HttpResponse<String> response = send("POST", "/admin/collections?destination=br", KEY);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).doesNotContain("Exception");
    }

    @Test
    void listsTheLogsOfARun() throws Exception {
        logRepository.save(log(RUN_ID, "GBR"));
        logRepository.save(log("other-run", "CAN"));

        HttpResponse<String> response = send("GET", "/admin/collections/" + RUN_ID, KEY);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"destination\":\"GBR\"").doesNotContain("\"CAN\"");
    }

    @Test
    void listsRecentLogsWithALimit() throws Exception {
        logRepository.save(log(RUN_ID, "GBR"));
        logRepository.save(log(RUN_ID, "CAN"));

        assertThat(send("GET", "/admin/collections/logs?destination=CAN", KEY).body())
                .contains("\"CAN\"")
                .doesNotContain("\"GBR\"");
        assertThat(send("GET", "/admin/collections/logs?limit=500", KEY).statusCode())
                .isEqualTo(400);
    }

    @Test
    void healthStaysPublicButWritesOutsideAdminAreRefused() throws Exception {
        assertThat(send("GET", "/actuator/health", null).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/actuator/health", null).statusCode()).isEqualTo(401);
        assertThat(send("DELETE", "/anything", KEY).statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> send(String method, String path, String key) throws IOException, InterruptedException {
        String port = environment.getRequiredProperty("local.server.port");
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (key != null) {
            request.header("X-Admin-Key", key);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static CollectionLog log(String runId, String destination) {
        CollectionLog log = new CollectionLog();
        log.setRunId(runId);
        log.setDestinationIso3(destination);
        log.setScraper("TestScraper");
        log.setStatus(CollectionStatus.SUCCESS);
        log.setStartedAt(Instant.parse("2026-10-24T05:00:00Z"));
        log.setFinishedAt(Instant.parse("2026-10-24T05:00:10Z"));
        return log;
    }
}
