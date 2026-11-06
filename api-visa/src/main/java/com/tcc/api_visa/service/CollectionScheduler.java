package com.tcc.api_visa.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-process schedule, for hosts that stay up. Disabled by default ({@code COLLECTION_CRON=-}):
 * in production the free host sleeps, so the GitHub Actions "collect" workflow triggers the
 * collection through {@code POST /admin/collections} instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CollectionScheduler {

    private final CollectionService collectionService;

    @Scheduled(cron = "${api-visa.collection.cron:-}", zone = "UTC")
    void collectAll() {
        try {
            log.info("Scheduled collection started: run {}", collectionService.start(null));
        } catch (CollectionAlreadyRunningException e) {
            log.info("Scheduled collection skipped: another one is still running");
        }
    }
}
