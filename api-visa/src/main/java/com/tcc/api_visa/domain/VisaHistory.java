package com.tcc.api_visa.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Previous content of a visa requirement. Saved only when the content actually changes, and
 * removed by MongoDB one year after the change.
 */
@Getter
@Setter
@Document(collection = "visa_history")
@CompoundIndex(name = "pair_changed_at", def = "{'originIso3': 1, 'destinationIso3': 1, 'changedAt': -1}")
public class VisaHistory {

    @Id
    private String id;

    private String originIso3;
    private String destinationIso3;
    private VisaRequirementContent previousContent;
    private String previousContentHash;
    private String newContentHash;
    private DataOrigin changeOrigin;

    @Indexed(name = "changed_at_ttl", expireAfter = "365d")
    private Instant changedAt;
}
