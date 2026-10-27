package com.tcc.api_visa.service;

import com.tcc.api_visa.domain.CollectionLog;
import com.tcc.api_visa.domain.CollectionStatus;
import com.tcc.api_visa.domain.Country;
import com.tcc.api_visa.notification.FailureNotifier;
import com.tcc.api_visa.repository.CollectionLogRepository;
import com.tcc.api_visa.repository.CountryRepository;
import com.tcc.api_visa.scraper.ScrapedVisaRequirement;
import com.tcc.api_visa.scraper.VisaScraper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Runs the scrapers, saves what changed and records one {@link CollectionLog} per scraper. Only one
 * collection runs at a time; failures are reported once per run through the {@link FailureNotifier}.
 */
@Slf4j
@Service
public class CollectionService {

    static final int MAX_ERROR_LENGTH = 500;

    private final List<VisaScraper> scrapers;
    private final CountryRepository countryRepository;
    private final CollectionLogRepository logRepository;
    private final VisaRequirementWriter writer;
    private final FailureNotifier notifier;
    private final TaskExecutor executor;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    public CollectionService(
            List<VisaScraper> scrapers,
            CountryRepository countryRepository,
            CollectionLogRepository logRepository,
            VisaRequirementWriter writer,
            FailureNotifier notifier,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor,
            Clock clock) {
        this.scrapers = scrapers.stream()
                .sorted(Comparator.comparing(VisaScraper::destinationIso3))
                .toList();
        this.countryRepository = countryRepository;
        this.logRepository = logRepository;
        this.writer = writer;
        this.notifier = notifier;
        this.executor = executor;
        this.clock = clock;
    }

    /**
     * Starts a collection in the background and returns its run id right away.
     *
     * @param destinationIso3 collect only this destination, or every destination when {@code null}
     * @throws UnknownDestinationException if no scraper reads that destination
     * @throws CollectionAlreadyRunningException if another collection has not finished
     */
    public String start(@Nullable String destinationIso3) {
        List<VisaScraper> selected = select(destinationIso3);
        acquire();
        String runId = newRunId();
        try {
            executor.execute(() -> {
                try {
                    run(runId, selected);
                } catch (RuntimeException e) {
                    log.error("Collection {} stopped unexpectedly", runId, e);
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        return runId;
    }

    /** Same as {@link #start} but waits for the collection to finish. */
    public CollectionRunSummary collect(@Nullable String destinationIso3) {
        List<VisaScraper> selected = select(destinationIso3);
        acquire();
        try {
            return run(newRunId(), selected);
        } finally {
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private CollectionRunSummary run(String runId, List<VisaScraper> selected) {
        Instant startedAt = clock.instant();
        Set<String> origins = countryRepository.findByOriginTrue().stream()
                .map(Country::getIso3)
                .collect(Collectors.toUnmodifiableSet());
        log.info("Collection {} started: {} scraper(s), origins {}", runId, selected.size(), origins);

        List<CollectionLog> logs = new ArrayList<>();
        for (VisaScraper scraper : selected) {
            logs.add(collectOne(runId, scraper, origins));
        }

        CollectionRunSummary summary = new CollectionRunSummary(runId, startedAt, clock.instant(), logs);
        log.info(
                "Collection {} finished: {} of {} scraper(s) failed",
                runId,
                summary.failures().size(),
                logs.size());
        if (summary.hasFailures()) {
            notifySafely(summary);
        }
        return summary;
    }

    private CollectionLog collectOne(String runId, VisaScraper scraper, Set<String> origins) {
        CollectionLog entry = new CollectionLog();
        entry.setRunId(runId);
        entry.setDestinationIso3(scraper.destinationIso3());
        entry.setScraper(scraper.getClass().getSimpleName());
        entry.setStartedAt(clock.instant());

        try {
            List<ScrapedVisaRequirement> results = scraper.scrape(origins);
            List<String> errors = new ArrayList<>();
            for (ScrapedVisaRequirement result : results) {
                try {
                    checkBelongsTo(result, scraper, origins);
                    count(entry, writer.apply(result, clock.instant()));
                } catch (RuntimeException e) {
                    log.warn(
                            "Could not save {} -> {}: {}", result.originIso3(), result.destinationIso3(), e.toString());
                    errors.add(result.originIso3() + ": " + e.getMessage());
                }
            }
            entry.setStatus(statusFor(entry, errors));
            entry.setErrorMessage(errors.isEmpty() ? null : truncate(String.join("; ", errors)));
        } catch (RuntimeException e) {
            log.warn("Scraper {} failed: {}", entry.getScraper(), e.toString());
            entry.setStatus(CollectionStatus.FAILED);
            entry.setErrorMessage(truncate(e.getMessage()));
        }

        entry.setFinishedAt(clock.instant());
        return logRepository.save(entry);
    }

    private static void checkBelongsTo(ScrapedVisaRequirement result, VisaScraper scraper, Set<String> origins) {
        if (!scraper.destinationIso3().equals(result.destinationIso3())) {
            throw new IllegalStateException("Scraper returned destination " + result.destinationIso3());
        }
        if (!origins.contains(result.originIso3())) {
            throw new IllegalStateException("Origin " + result.originIso3() + " was not requested");
        }
    }

    private static void count(CollectionLog entry, PairOutcome outcome) {
        entry.setPairsCollected(entry.getPairsCollected() + 1);
        switch (outcome) {
            case CREATED, CHANGED -> entry.setPairsChanged(entry.getPairsChanged() + 1);
            case SKIPPED_MANUAL -> entry.setPairsSkipped(entry.getPairsSkipped() + 1);
            case UNCHANGED -> {}
        }
    }

    private static CollectionStatus statusFor(CollectionLog entry, List<String> errors) {
        if (errors.isEmpty() && entry.getPairsCollected() > 0) {
            return CollectionStatus.SUCCESS;
        }
        if (entry.getPairsCollected() > 0) {
            return CollectionStatus.PARTIAL;
        }
        if (errors.isEmpty()) {
            errors.add("Scraper returned no results");
        }
        return CollectionStatus.FAILED;
    }

    private void notifySafely(CollectionRunSummary summary) {
        try {
            notifier.notifyFailures(summary);
        } catch (RuntimeException e) {
            log.error("Could not send the failure notification for collection {}", summary.runId(), e);
        }
    }

    private List<VisaScraper> select(@Nullable String destinationIso3) {
        if (destinationIso3 == null) {
            return scrapers;
        }
        List<VisaScraper> selected = scrapers.stream()
                .filter(scraper -> scraper.destinationIso3().equals(destinationIso3))
                .toList();
        if (selected.isEmpty()) {
            throw new UnknownDestinationException(destinationIso3);
        }
        return selected;
    }

    private void acquire() {
        if (!running.compareAndSet(false, true)) {
            throw new CollectionAlreadyRunningException();
        }
    }

    private static String newRunId() {
        return UUID.randomUUID().toString();
    }

    private static @Nullable String truncate(@Nullable String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH) + "…";
    }
}
