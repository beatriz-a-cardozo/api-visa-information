package com.tcc.api_visa.security;

import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Websites allowed to call the public API from a browser. Empty (the default) means none: browsers
 * on other sites cannot read the responses, while servers and command-line clients are unaffected.
 *
 * @param allowedOrigins exact origins such as {@code https://app.example.org}; no wildcards or paths
 */
@Validated
@ConfigurationProperties("api-visa.cors")
public record CorsProperties(List<@Pattern(regexp = "https?://[a-z0-9.-]+(:[0-9]{1,5})?") String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
