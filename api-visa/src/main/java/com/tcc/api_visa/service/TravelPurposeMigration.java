package com.tcc.api_visa.service;

import com.tcc.api_visa.domain.CollectionLog;
import com.tcc.api_visa.domain.TravelPurpose;
import com.tcc.api_visa.domain.VisaHistory;
import com.tcc.api_visa.domain.VisaRequirement;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Data saved before travel purposes existed is all about tourism. On startup this marks it
 * {@code TOURISM} and drops the indexes keyed only by origin and destination: the old unique index
 * would reject a second purpose for the same pair. Safe to run on every start.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TravelPurposeMigration implements ApplicationRunner {

    static final String PURPOSE = "purpose";

    /** Index names used before the purpose was part of the key. */
    static final Map<Class<?>, String> OLD_INDEXES =
            Map.of(VisaRequirement.class, "origin_destination", VisaHistory.class, "pair_changed_at");

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        for (Class<?> type : List.of(VisaRequirement.class, VisaHistory.class, CollectionLog.class)) {
            long updated = mongoTemplate
                    .updateMulti(
                            Query.query(Criteria.where(PURPOSE).exists(false)),
                            Update.update(PURPOSE, TravelPurpose.TOURISM),
                            type)
                    .getModifiedCount();
            if (updated > 0) {
                log.info("Marked {} {} document(s) as {}", updated, type.getSimpleName(), TravelPurpose.TOURISM);
            }
        }
        OLD_INDEXES.forEach((type, name) -> {
            IndexOperations indexes = mongoTemplate.indexOps(type);
            if (indexes.getIndexInfo().stream()
                    .anyMatch(index -> index.getName().equals(name))) {
                indexes.dropIndex(name);
                log.info("Dropped index {} of {}", name, type.getSimpleName());
            }
        });
    }
}
