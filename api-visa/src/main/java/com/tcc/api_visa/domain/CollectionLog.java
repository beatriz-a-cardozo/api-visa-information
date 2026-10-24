package com.tcc.api_visa.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Audit entry for one scraper run. Removed by MongoDB after 180 days. */
@Getter
@Setter
@Document(collection = "collection_logs")
public class CollectionLog {

    @Id
    private String id;

    @Indexed
    private String destinationIso3;

    private String scraper;

    @Indexed(name = "started_at_ttl", expireAfter = "180d")
    private Instant startedAt;

    private Instant finishedAt;
    private CollectionStatus status;
    private int pairsCollected;
    private int pairsChanged;
    private String errorMessage;
}
