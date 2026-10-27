package com.tcc.api_visa.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tcc.api_visa.domain.DataOrigin;
import com.tcc.api_visa.domain.VisaHistory;
import com.tcc.api_visa.domain.VisaRequirement;
import com.tcc.api_visa.repository.VisaHistoryRepository;
import com.tcc.api_visa.repository.VisaRequirementRepository;
import com.tcc.api_visa.support.MongoTestcontainersConfiguration;
import com.tcc.api_visa.support.TestData;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MongoTestcontainersConfiguration.class)
class VisaRequirementWriterIntegrationTest {

    private static final Instant FIRST = Instant.parse("2026-10-01T05:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-10-02T05:00:00Z");

    @Autowired
    private VisaRequirementWriter writer;

    @Autowired
    private VisaRequirementRepository requirementRepository;

    @Autowired
    private VisaHistoryRepository historyRepository;

    @BeforeEach
    void cleanUp() {
        requirementRepository.deleteAll();
        historyRepository.deleteAll();
    }

    @Test
    void createsANewPairWithoutHistory() {
        assertThat(writer.apply(TestData.scraped("BRA", "GBR", "135"), FIRST)).isEqualTo(PairOutcome.CREATED);

        VisaRequirement saved = load();
        assertThat(saved.getDataOrigin()).isEqualTo(DataOrigin.SCRAPER);
        assertThat(saved.getCollectedAt()).isEqualTo(FIRST);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIRST);
        assertThat(saved.getContentHash()).hasSize(64);
        assertThat(historyRepository.count()).isZero();
    }

    @Test
    void sameContentOnlyRefreshesTheCollectionDate() {
        writer.apply(TestData.scraped("BRA", "GBR", "135"), FIRST);

        assertThat(writer.apply(TestData.scraped("BRA", "GBR", "135"), SECOND)).isEqualTo(PairOutcome.UNCHANGED);

        VisaRequirement saved = load();
        assertThat(saved.getCollectedAt()).isEqualTo(SECOND);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIRST);
        assertThat(historyRepository.count()).isZero();
    }

    @Test
    void changedContentKeepsThePreviousVersionInTheHistory() {
        writer.apply(TestData.scraped("BRA", "GBR", "135"), FIRST);
        String firstHash = load().getContentHash();

        assertThat(writer.apply(TestData.scraped("BRA", "GBR", "140"), SECOND)).isEqualTo(PairOutcome.CHANGED);

        VisaRequirement saved = load();
        assertThat(saved.getContent()).isEqualTo(TestData.content("140"));
        assertThat(saved.getUpdatedAt()).isEqualTo(SECOND);

        assertThat(historyRepository.findByOriginIso3AndDestinationIso3OrderByChangedAtDesc("BRA", "GBR"))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getPreviousContent()).isEqualTo(TestData.content("135"));
                    assertThat(history.getPreviousContentHash()).isEqualTo(firstHash);
                    assertThat(history.getNewContentHash()).isEqualTo(saved.getContentHash());
                    assertThat(history.getChangedAt()).isEqualTo(SECOND);
                    assertThat(history.getChangeOrigin()).isEqualTo(DataOrigin.SCRAPER);
                });
    }

    @Test
    void neverOverwritesAManualCorrection() {
        writer.apply(TestData.scraped("BRA", "GBR", "135"), FIRST);
        VisaRequirement corrected = load();
        corrected.setDataOrigin(DataOrigin.MANUAL);
        requirementRepository.save(corrected);

        assertThat(writer.apply(TestData.scraped("BRA", "GBR", "999"), SECOND)).isEqualTo(PairOutcome.SKIPPED_MANUAL);

        assertThat(load().getContent()).isEqualTo(TestData.content("135"));
        assertThat(historyRepository.findAll()).extracting(VisaHistory::getId).isEmpty();
    }

    private VisaRequirement load() {
        return requirementRepository
                .findByOriginIso3AndDestinationIso3("BRA", "GBR")
                .orElseThrow();
    }
}
