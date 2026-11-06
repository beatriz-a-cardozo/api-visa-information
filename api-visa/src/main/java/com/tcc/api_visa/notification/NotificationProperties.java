package com.tcc.api_visa.notification;

import jakarta.validation.constraints.Email;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param emailTo   administrator address; when empty, failures are only logged
 * @param emailFrom sender address, which must be verified with the SMTP provider
 */
@Validated
@ConfigurationProperties("api-visa.notification")
public record NotificationProperties(
        @Nullable @Email String emailTo, @Nullable @Email String emailFrom) {

    public boolean emailEnabled() {
        return emailTo != null && !emailTo.isBlank();
    }
}
