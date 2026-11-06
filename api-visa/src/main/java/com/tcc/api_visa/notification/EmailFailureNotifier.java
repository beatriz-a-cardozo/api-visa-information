package com.tcc.api_visa.notification;

import com.tcc.api_visa.service.CollectionRunSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Sends a plain-text e-mail through any SMTP provider (Brevo in production). */
@Slf4j
@RequiredArgsConstructor
class EmailFailureNotifier implements FailureNotifier {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    @Override
    public void notifyFailures(CollectionRunSummary summary) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(properties.emailTo());
        message.setFrom(properties.emailFrom());
        message.setSubject(FailureReport.subject(summary));
        message.setText(FailureReport.body(summary));
        mailSender.send(message);
        log.info("Failure e-mail sent for collection {}", summary.runId());
    }
}
