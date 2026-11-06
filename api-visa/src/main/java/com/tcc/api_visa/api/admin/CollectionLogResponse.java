package com.tcc.api_visa.api.admin;

import com.tcc.api_visa.domain.CollectionLog;
import com.tcc.api_visa.domain.CollectionStatus;
import com.tcc.api_visa.domain.TravelPurpose;
import java.time.Instant;

public record CollectionLogResponse(
        String runId,
        String destination,
        TravelPurpose purpose,
        String scraper,
        CollectionStatus status,
        Instant startedAt,
        Instant finishedAt,
        int pairsCollected,
        int pairsChanged,
        int pairsSkipped,
        String errorMessage) {

    static CollectionLogResponse from(CollectionLog log) {
        return new CollectionLogResponse(
                log.getRunId(),
                log.getDestinationIso3(),
                log.getPurpose(),
                log.getScraper(),
                log.getStatus(),
                log.getStartedAt(),
                log.getFinishedAt(),
                log.getPairsCollected(),
                log.getPairsChanged(),
                log.getPairsSkipped(),
                log.getErrorMessage());
    }
}
