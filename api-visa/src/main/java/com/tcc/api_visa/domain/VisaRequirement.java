package com.tcc.api_visa.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Current visa information for one origin/destination pair. */
@Getter
@Setter
@Document(collection = "visa_requirements")
@CompoundIndex(name = "origin_destination", def = "{'originIso3': 1, 'destinationIso3': 1}", unique = true)
public class VisaRequirement {

    @Id
    private String id;

    private String originIso3;
    private String destinationIso3;
    private VisaRequirementContent content;

    /** SHA-256 of {@link #content}, used to detect changes between collections. */
    private String contentHash;

    private DataOrigin dataOrigin;

    /** Last time the source was read successfully, even if nothing changed. */
    private Instant collectedAt;

    /** Last time {@link #content} changed. */
    private Instant updatedAt;

    @Version
    private Long version;
}
