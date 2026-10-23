package com.tcc.api_visa;

import static org.assertj.core.api.Assertions.assertThat;

import com.tcc.api_visa.support.MongoTestcontainersConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MongoTestcontainersConfiguration.class)
class ApiVisaApplicationTests {

    @Autowired
    private Environment environment;

    @Test
    void healthIsUpAndDoesNotExposeDetails() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("components", "details", "mongo");
    }

    @Test
    void onlyTheHealthEndpointIsExposed() throws IOException, InterruptedException {
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
        assertThat(get("/actuator/beans").statusCode()).isEqualTo(404);
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
}
