package com.tcc.api_visa.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;

/** A real MongoDB in Docker for integration tests, shared by every test that imports it. */
@TestConfiguration(proxyBeanMethods = false)
public class MongoTestcontainersConfiguration {

    /**
     * MongoDB 7.0, not 8.x: 8.x refuses to start on Linux kernels 6.19 to 7.0.13 (SERVER-121912),
     * which some GitHub runners already use. Same version as docker-compose.yml.
     */
    @Bean
    @ServiceConnection
    MongoDBContainer mongoDbContainer() {
        return new MongoDBContainer("mongo:7.0");
    }
}
