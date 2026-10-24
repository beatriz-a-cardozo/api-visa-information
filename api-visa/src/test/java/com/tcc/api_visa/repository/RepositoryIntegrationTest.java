package com.tcc.api_visa.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tcc.api_visa.domain.DataOrigin;
import com.tcc.api_visa.domain.Money;
import com.tcc.api_visa.domain.Requirement;
import com.tcc.api_visa.domain.RequirementType;
import com.tcc.api_visa.domain.VisaHistory;
import com.tcc.api_visa.domain.VisaOption;
import com.tcc.api_visa.domain.VisaPolicy;
import com.tcc.api_visa.domain.VisaRequirement;
import com.tcc.api_visa.domain.VisaRequirementContent;
import com.tcc.api_visa.domain.VisaType;
import com.tcc.api_visa.support.MongoTestcontainersConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

@SpringBootTest
@Import(MongoTestcontainersConfiguration.class)
class RepositoryIntegrationTest {

    @Autowired
    private VisaRequirementRepository visaRequirementRepository;

    @Autowired
    private VisaHistoryRepository visaHistoryRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanUp() {
        visaRequirementRepository.deleteAll();
        visaHistoryRepository.deleteAll();
    }

    @Test
    void storesOneDocumentPerOriginDestinationPair() {
        visaRequirementRepository.save(requirement("BRA", "DEU"));

        assertThatThrownBy(() -> visaRequirementRepository.save(requirement("BRA", "DEU")))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(visaRequirementRepository.findByOriginIso3AndDestinationIso3("BRA", "DEU"))
                .isPresent();
    }

    @Test
    void keepsMoneyExactAndStoresItAsDecimal128() {
        visaRequirementRepository.save(requirement("JPN", "GBR"));

        VisaRequirement loaded = visaRequirementRepository
                .findByOriginIso3AndDestinationIso3("JPN", "GBR")
                .orElseThrow();
        assertThat(loaded.getContent().visas().getFirst().cost()).isEqualTo(Money.of("135.50", "GBP"));

        Document raw = mongoTemplate.getCollection("visa_requirements").find().first();
        Object amount = raw.get("content", Document.class)
                .getList("visas", Document.class)
                .getFirst()
                .get("cost", Document.class)
                .get("amount");
        assertThat(amount).isInstanceOf(Decimal128.class);
    }

    @Test
    void expiresHistoryAfterOneYear() {
        IndexInfo ttl = mongoTemplate.indexOps(VisaHistory.class).getIndexInfo().stream()
                .filter(index -> index.getName().equals("changed_at_ttl"))
                .findFirst()
                .orElseThrow();

        assertThat(ttl.getExpireAfter())
                .hasValueSatisfying(
                        expireAfter -> assertThat(expireAfter.toSeconds()).isEqualTo(TimeUnit.DAYS.toSeconds(365)));
    }

    @Test
    void returnsHistoryNewestFirst() {
        visaHistoryRepository.saveAll(List.of(
                history(Instant.parse("2026-01-01T00:00:00Z")), history(Instant.parse("2026-06-01T00:00:00Z"))));

        assertThat(visaHistoryRepository.findByOriginIso3AndDestinationIso3OrderByChangedAtDesc("BRA", "CAN"))
                .extracting(VisaHistory::getChangedAt)
                .containsExactly(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static VisaRequirement requirement(String origin, String destination) {
        VisaRequirement requirement = new VisaRequirement();
        requirement.setOriginIso3(origin);
        requirement.setDestinationIso3(destination);
        requirement.setContent(content());
        requirement.setContentHash("hash");
        requirement.setDataOrigin(DataOrigin.SCRAPER);
        requirement.setCollectedAt(Instant.now());
        requirement.setUpdatedAt(Instant.now());
        return requirement;
    }

    private static VisaHistory history(Instant changedAt) {
        VisaHistory history = new VisaHistory();
        history.setOriginIso3("BRA");
        history.setDestinationIso3("CAN");
        history.setPreviousContent(content());
        history.setChangeOrigin(DataOrigin.SCRAPER);
        history.setChangedAt(changedAt);
        return history;
    }

    private static VisaRequirementContent content() {
        VisaOption visa = new VisaOption(
                VisaType.TOURIST,
                "Standard Visitor visa",
                180,
                Money.of("135.50", "GBP"),
                List.of(new Requirement("Valid passport", RequirementType.DOCUMENT)),
                "https://www.gov.uk/standard-visitor");
        return new VisaRequirementContent(VisaPolicy.VISA_REQUIRED, List.of(visa), List.of(visa.sourceUrl()));
    }
}
