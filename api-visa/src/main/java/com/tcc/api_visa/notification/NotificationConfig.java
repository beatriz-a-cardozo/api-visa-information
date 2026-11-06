package com.tcc.api_visa.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration(proxyBeanMethods = false)
class NotificationConfig {

    /** E-mail when a recipient is configured; otherwise only the log. Misconfiguration fails at startup. */
    @Bean
    FailureNotifier failureNotifier(
            NotificationProperties properties,
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${spring.mail.host:}") String mailHost) {
        if (!properties.emailEnabled()) {
            return new LogFailureNotifier();
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || mailHost.isBlank()) {
            throw new IllegalStateException("NOTIFICATION_EMAIL_TO is set but SMTP_HOST is not");
        }
        if (properties.emailFrom() == null || properties.emailFrom().isBlank()) {
            throw new IllegalStateException("NOTIFICATION_EMAIL_TO is set but NOTIFICATION_EMAIL_FROM is not");
        }
        return new EmailFailureNotifier(sender, properties);
    }
}
