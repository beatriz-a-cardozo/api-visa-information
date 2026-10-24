package com.tcc.api_visa.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;

/** A real MongoDB in Docker for integration tests, shared by every test that imports it. */
@TestConfiguration(proxyBeanMethods = false)
public class MongoTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MongoDBContainer mongoDbContainer() {
        return new MongoDBContainer("mongo:8.0");
    }
}
