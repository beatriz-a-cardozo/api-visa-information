package com.tcc.api_visa.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tcc.api_visa.domain.CollectionLog;
import com.tcc.api_visa.domain.TravelPurpose;
import com.tcc.api_visa.domain.VisaHistory;
import com.tcc.api_visa.domain.VisaRequirement;
import com.tcc.api_visa.repository.CollectionLogRepository;
import com.tcc.api_visa.repository.VisaHistoryRepository;
import com.tcc.api_visa.repository.VisaRequirementRepository;
import com.tcc.api_visa.support.MongoTestcontainersConfiguration;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;

@SpringBootTest
@Import(MongoTestcontainersConfiguration.class)
class TravelPurposeMigrationIntegrationTest {

    @Autowired
    private TravelPurposeMigration migration;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private VisaRequirementRepository requirementRepository;

    @Autowired
    private VisaHistoryRepository historyRepository;

    @Autowired
    private CollectionLogRepository logRepository;

    @BeforeEach
    void cleanUp() {
        requirementRepository.deleteAll();
        historyRepository.deleteAll();
        logRepository.deleteAll();
    }

    @Test
    void marksDataSavedBeforePurposesAsTourismAndDropsTheOldIndexes() {
        // What the database looked like before: no purpose, unique index on origin and destination only.
        mongoTemplate
                .indexOps(VisaRequirement.class)
                .createIndex(new Index()
                        .on("originIso3", Sort.Direction.ASC)
                        .on("destinationIso3", Sort.Direction.ASC)
                        .named("origin_destination")
                        .unique());
        mongoTemplate
                .indexOps(VisaHistory.class)
                .createIndex(new Index()
                        .on("originIso3", Sort.Direction.ASC)
                        .on("destinationIso3", Sort.Direction.ASC)
                        .on("changedAt", Sort.Direction.DESC)
                        .named("pair_changed_at"));
        mongoTemplate
                .getCollection("visa_requirements")
                .insertOne(new Document("originIso3", "BRA")
                        .append("destinationIso3", "GBR")
                        .append("contentHash", "old"));
        mongoTemplate
                .getCollection("visa_history")
                .insertOne(new Document("originIso3", "BRA").append("destinationIso3", "GBR"));
        mongoTemplate.getCollection("collection_logs").insertOne(new Document("destinationIso3", "GBR"));

        migration.run(null);
        migration.run(null); // a second start changes nothing

        assertThat(requirementRepository.findByOriginIso3AndDestinationIso3AndPurpose(
                        "BRA", "GBR", TravelPurpose.TOURISM))
                .isPresent();
        assertThat(historyRepository.findAll())
                .extracting(VisaHistory::getPurpose)
                .containsExactly(TravelPurpose.TOURISM);
        assertThat(logRepository.findAll())
                .extracting(CollectionLog::getPurpose)
                .containsExactly(TravelPurpose.TOURISM);
        assertThat(mongoTemplate.indexOps(VisaRequirement.class).getIndexInfo())
                .extracting(IndexInfo::getName)
                .contains("origin_destination_purpose")
                .doesNotContain("origin_destination");
        assertThat(mongoTemplate.indexOps(VisaHistory.class).getIndexInfo())
                .extracting(IndexInfo::getName)
                .doesNotContain("pair_changed_at");
    }
}
