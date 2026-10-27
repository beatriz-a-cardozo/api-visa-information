package com.tcc.api_visa.service;

/** What happened to one origin/destination pair during a collection. */
public enum PairOutcome {
    CREATED,
    CHANGED,
    UNCHANGED,
    /** An administrator corrected this pair; the scraper does not overwrite it. */
    SKIPPED_MANUAL
}
