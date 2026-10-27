package com.tcc.api_visa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tcc.api_visa.domain.CollectionLog;
import com.tcc.api_visa.domain.CollectionStatus;
import com.tcc.api_visa.domain.Country;
import com.tcc.api_visa.notification.FailureNotifier;
import com.tcc.api_visa.repository.CollectionLogRepository;
import com.tcc.api_visa.repository.CountryRepository;
import com.tcc.api_visa.scraper.ScrapedVisaRequirement;
import com.tcc.api_visa.scraper.ScrapingException;
import com.tcc.api_visa.scraper.VisaScraper;
import com.tcc.api_visa.support.TestData;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

class CollectionServiceTest {

    private final CountryRepository countryRepository = mock(CountryRepository.class);
    private final CollectionLogRepository logRepository = mock(CollectionLogRepository.class);
    private final VisaRequirementWriter writer = mock(VisaRequirementWriter.class);
    private final FailureNotifier notifier = mock(FailureNotifier.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T05:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        when(countryRepository.findByOriginTrue()).thenReturn(List.of(country("BRA"), country("JPN")));
        when(logRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(writer.apply(any(), any())).thenReturn(PairOutcome.CREATED);
    }

    @Test
    void recordsOneLogPerScraperAndCountsWhatChanged() {
        when(writer.apply(argThat(result -> result.originIso3().equals("JPN")), any()))
                .thenReturn(PairOutcome.UNCHANGED);
        CollectionService service = service(working("CAN"));

        CollectionRunSummary summary = service.collect(null);

        assertThat(summary.logs()).singleElement().satisfies(log -> {
            assertThat(log.getRunId()).isEqualTo(summary.runId());
            assertThat(log.getDestinationIso3()).isEqualTo("CAN");
            assertThat(log.getStatus()).isEqualTo(CollectionStatus.SUCCESS);
            assertThat(log.getPairsCollected()).isEqualTo(2);
            assertThat(log.getPairsChanged()).isEqualTo(1);
            assertThat(log.getErrorMessage()).isNull();
        });
        verify(notifier, never()).notifyFailures(any());
    }

    @Test
    void oneBrokenSourceDoesNotStopTheOthersAndSendsOneAlert() {
        CollectionService service = service(working("CAN"), broken("GBR"), working("DEU"));

        CollectionRunSummary summary = service.collect(null);

        assertThat(summary.logs())
                .extracting(CollectionLog::getDestinationIso3, CollectionLog::getStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("CAN", CollectionStatus.SUCCESS),
                        org.assertj.core.groups.Tuple.tuple("DEU", CollectionStatus.SUCCESS),
                        org.assertj.core.groups.Tuple.tuple("GBR", CollectionStatus.FAILED));
        assertThat(summary.failures())
                .singleElement()
                .satisfies(log -> assertThat(log.getErrorMessage()).isEqualTo("HTTP 403 from https://example.gov"));
        verify(notifier).notifyFailures(summary);
    }

    @Test
    void marksTheRunPartialWhenSomePairsCannotBeSaved() {
        when(writer.apply(argThat(result -> result.originIso3().equals("JPN")), any()))
                .thenThrow(new IllegalStateException("database unavailable"));
        CollectionService service = service(working("CAN"));

        CollectionLog log = service.collect(null).logs().getFirst();

        assertThat(log.getStatus()).isEqualTo(CollectionStatus.PARTIAL);
        assertThat(log.getPairsCollected()).isEqualTo(1);
        assertThat(log.getErrorMessage()).isEqualTo("JPN: database unavailable");
    }

    @Test
    void rejectsResultsForOriginsThatWereNotRequested() {
        CollectionService service = service(scraper("CAN", origins -> List.of(TestData.scraped("USA", "CAN", "1"))));

        CollectionLog log = service.collect(null).logs().getFirst();

        assertThat(log.getStatus()).isEqualTo(CollectionStatus.FAILED);
        assertThat(log.getErrorMessage()).contains("USA was not requested");
        verify(writer, never()).apply(any(), any());
    }

    @Test
    void anEmptyResultCountsAsAFailure() {
        CollectionService service = service(scraper("CAN", origins -> List.of()));

        CollectionLog log = service.collect(null).logs().getFirst();

        assertThat(log.getStatus()).isEqualTo(CollectionStatus.FAILED);
        assertThat(log.getErrorMessage()).isEqualTo("Scraper returned no results");
    }

    @Test
    void truncatesLongErrorMessages() {
        CollectionService service = service(scraper("CAN", origins -> {
            throw new ScrapingException("x".repeat(2_000));
        }));

        assertThat(service.collect(null).logs().getFirst().getErrorMessage())
                .hasSize(CollectionService.MAX_ERROR_LENGTH + 1);
    }

    @Test
    void canCollectASingleDestination() {
        CollectionService service = service(working("CAN"), working("GBR"));

        assertThat(service.collect("GBR").logs())
                .extracting(CollectionLog::getDestinationIso3)
                .containsExactly("GBR");
    }

    @Test
    void rejectsADestinationWithoutScraper() {
        CollectionService service = service(working("CAN"));

        assertThatThrownBy(() -> service.start("ZZZ")).isInstanceOf(UnknownDestinationException.class);
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void runsOnlyOneCollectionAtATime() {
        List<Runnable> queued = new ArrayList<>();
        CollectionService service = service(queued::add, working("CAN"));

        String runId = service.start(null);

        assertThat(runId).isNotBlank();
        assertThat(service.isRunning()).isTrue();
        assertThatThrownBy(() -> service.start(null)).isInstanceOf(CollectionAlreadyRunningException.class);
        assertThatThrownBy(() -> service.collect(null)).isInstanceOf(CollectionAlreadyRunningException.class);

        queued.getFirst().run();

        assertThat(service.isRunning()).isFalse();
        verify(logRepository).save(argThat(log -> log.getRunId().equals(runId)));
    }

    @Test
    void aFailingNotifierDoesNotBreakTheCollection() {
        doThrow(new IllegalStateException("SMTP down")).when(notifier).notifyFailures(any());
        CollectionService service = service(broken("GBR"));

        assertThat(service.collect(null).hasFailures()).isTrue();
        assertThat(service.isRunning()).isFalse();
    }

    private CollectionService service(VisaScraper... scrapers) {
        return service(new SyncTaskExecutor(), scrapers);
    }

    private CollectionService service(TaskExecutor executor, VisaScraper... scrapers) {
        return new CollectionService(
                List.of(scrapers), countryRepository, logRepository, writer, notifier, executor, clock);
    }

    private static VisaScraper working(String destination) {
        return scraper(
                destination,
                origins -> origins.stream()
                        .sorted()
                        .map(origin -> TestData.scraped(origin, destination, "100"))
                        .toList());
    }

    private static VisaScraper broken(String destination) {
        return scraper(destination, origins -> {
            throw new ScrapingException("HTTP 403 from https://example.gov");
        });
    }

    private static VisaScraper scraper(
            String destination, Function<Set<String>, List<ScrapedVisaRequirement>> behaviour) {
        return new VisaScraper() {
            @Override
            public String destinationIso3() {
                return destination;
            }

            @Override
            public List<ScrapedVisaRequirement> scrape(Set<String> originIso3s) {
                return behaviour.apply(originIso3s);
            }
        };
    }

    private static Country country(String iso3) {
        Country country = new Country();
        country.setIso3(iso3);
        country.setOrigin(true);
        return country;
    }
}
