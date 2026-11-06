package com.tcc.api_visa.security;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiKey secret sent in the {@code X-Admin-Key} header. When empty, every {@code /admin}
 *               request is refused. Must have at least {@value #MIN_KEY_LENGTH} characters.
 */
@ConfigurationProperties("api-visa.admin")
public record AdminProperties(@Nullable String apiKey) {

    public static final int MIN_KEY_LENGTH = 32;

    public AdminProperties {
        if (apiKey != null && apiKey.isBlank()) {
            apiKey = null;
        }
        if (apiKey != null && apiKey.length() < MIN_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "ADMIN_API_KEY must have at least " + MIN_KEY_LENGTH + " characters (use: openssl rand -hex 32)");
        }
    }
}
