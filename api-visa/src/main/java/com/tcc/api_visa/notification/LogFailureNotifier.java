package com.tcc.api_visa.notification;

import com.tcc.api_visa.service.CollectionRunSummary;
import lombok.extern.slf4j.Slf4j;

/** Used when no e-mail is configured, for example in local development. */
@Slf4j
class LogFailureNotifier implements FailureNotifier {

    @Override
    public void notifyFailures(CollectionRunSummary summary) {
        log.warn("Collection {} had failures:\n{}", summary.runId(), FailureReport.body(summary));
    }
}
