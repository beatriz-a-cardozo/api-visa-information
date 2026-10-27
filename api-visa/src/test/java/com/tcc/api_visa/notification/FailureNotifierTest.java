package com.tcc.api_visa.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.tcc.api_visa.domain.CollectionLog;
import com.tcc.api_visa.domain.CollectionStatus;
import com.tcc.api_visa.service.CollectionRunSummary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class FailureNotifierTest {

    private static final NotificationProperties EMAIL =
            new NotificationProperties("admin@example.com", "alerts@example.com");

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @Test
    void sendsOnePlainTextEmailListingTheFailures() {
        new EmailFailureNotifier(mailSender, EMAIL).notifyFailures(summary());

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        SimpleMailMessage message = sent.getValue();
        assertThat(message.getTo()).containsExactly("admin@example.com");
        assertThat(message.getFrom()).isEqualTo("alerts@example.com");
        assertThat(message.getSubject()).isEqualTo("[api-visa] Falha na coleta: 1 de 2 destino(s)");
        assertThat(message.getText())
                .contains("GBR (UnitedKingdomScraper): FAILED")
                .contains("HTTP 403 from https://www.gov.uk")
                .contains("/admin/collections/run-1")
                .doesNotContain("CAN");
    }

    @Test
    void logsOnlyWhenNoRecipientIsConfigured() {
        FailureNotifier notifier =
                new NotificationConfig().failureNotifier(new NotificationProperties("", null), provider(null), "");

        assertThat(notifier).isInstanceOf(LogFailureNotifier.class);
        notifier.notifyFailures(summary());
    }

    @Test
    void usesEmailWhenRecipientAndSmtpAreConfigured() {
        FailureNotifier notifier =
                new NotificationConfig().failureNotifier(EMAIL, provider(mailSender), "smtp-relay.brevo.com");

        assertThat(notifier).isInstanceOf(EmailFailureNotifier.class);
    }

    @Test
    void refusesToStartWithARecipientButNoSmtpServer() {
        assertThatThrownBy(() -> new NotificationConfig().failureNotifier(EMAIL, provider(mailSender), ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP_HOST");
    }

    @Test
    void refusesToStartWithoutASender() {
        NotificationProperties noSender = new NotificationProperties("admin@example.com", null);

        assertThatThrownBy(() -> new NotificationConfig().failureNotifier(noSender, provider(mailSender), "smtp"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOTIFICATION_EMAIL_FROM");
    }

    private static ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (sender != null) {
            factory.addBean("mailSender", sender);
        }
        return factory.getBeanProvider(JavaMailSender.class);
    }

    private static CollectionRunSummary summary() {
        return new CollectionRunSummary(
                "run-1",
                Instant.parse("2026-10-24T05:00:00Z"),
                Instant.parse("2026-10-24T05:01:00Z"),
                List.of(
                        log("CAN", "CanadaScraper", CollectionStatus.SUCCESS, null),
                        log(
                                "GBR",
                                "UnitedKingdomScraper",
                                CollectionStatus.FAILED,
                                "HTTP 403 from https://www.gov.uk")));
    }

    private static CollectionLog log(String destination, String scraper, CollectionStatus status, String error) {
        CollectionLog log = new CollectionLog();
        log.setRunId("run-1");
        log.setDestinationIso3(destination);
        log.setScraper(scraper);
        log.setStatus(status);
        log.setErrorMessage(error);
        return log;
    }
}
