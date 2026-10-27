package com.tcc.api_visa.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class UrlValidationConfig {

    @Bean
    HostResolver hostResolver() {
        return HostResolver.system();
    }
}
