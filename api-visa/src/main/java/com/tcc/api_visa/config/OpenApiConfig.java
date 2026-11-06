package com.tcc.api_visa.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document at {@code /v3/api-docs}, browsable at {@code /swagger-ui.html}. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    /** Security scheme referenced by {@code @SecurityRequirement(name = "adminKey")} on admin controllers. */
    private static final String ADMIN_KEY = "adminKey";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Visa")
                        .version("v1")
                        .description("Visa requirements collected from official government sources, with the"
                                + " source URL and collection date of every record.")
                        .license(new License().name("MIT").url("https://opensource.org/license/mit")))
                .components(new Components()
                        .addSecuritySchemes(
                                ADMIN_KEY,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Admin-Key")));
    }
}
