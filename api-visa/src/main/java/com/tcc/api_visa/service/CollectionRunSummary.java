package com.tcc.api_visa.service;

import com.tcc.api_visa.domain.CollectionLog;
import com.tcc.api_visa.domain.CollectionStatus;
import java.time.Instant;
import java.util.List;

/** Result of one collection run: one log per scraper. */
public record CollectionRunSummary(String runId, Instant startedAt, Instant finishedAt, List<CollectionLog> logs) {

    public CollectionRunSummary {
        logs = List.copyOf(logs);
    }

    public List<CollectionLog> failures() {
        return logs.stream()
                .filter(log -> log.getStatus() != CollectionStatus.SUCCESS)
                .toList();
    }

    public boolean hasFailures() {
        return !failures().isEmpty();
    }
}
