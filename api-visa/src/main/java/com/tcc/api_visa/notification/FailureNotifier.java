package com.tcc.api_visa.notification;

import com.tcc.api_visa.service.CollectionRunSummary;

/** Tells the administrator that a collection had failures. Called at most once per run. */
public interface FailureNotifier {

    void notifyFailures(CollectionRunSummary summary);
}
