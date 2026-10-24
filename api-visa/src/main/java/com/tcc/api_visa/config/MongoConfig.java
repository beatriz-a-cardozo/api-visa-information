package com.tcc.api_visa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions.BigDecimalRepresentation;

@Configuration(proxyBeanMethods = false)
class MongoConfig {

    /** Stores {@code BigDecimal} as Decimal128, so money keeps its exact value and stays numeric in queries. */
    @Bean
    MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(config -> config.bigDecimal(BigDecimalRepresentation.DECIMAL128));
    }
}
