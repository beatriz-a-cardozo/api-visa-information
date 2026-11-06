package com.tcc.api_visa.exchange;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class ExchangeConfig {

    @Bean
    ExchangeRateProvider exchangeRateProvider(ExchangeProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        return new FrankfurterExchangeRateProvider(restClient, properties);
    }
}
