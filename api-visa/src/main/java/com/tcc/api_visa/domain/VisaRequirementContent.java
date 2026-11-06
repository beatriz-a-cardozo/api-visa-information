package com.tcc.api_visa.domain;

import java.util.List;

/**
 * The part of a visa requirement that comes from the source. It is what gets hashed to detect
 * changes and what is copied into the history when it changes.
 */
public record VisaRequirementContent(VisaPolicy policy, List<VisaOption> visas, List<String> sourceUrls) {

    public VisaRequirementContent {
        visas = visas == null ? List.of() : List.copyOf(visas);
        sourceUrls = sourceUrls == null ? List.of() : List.copyOf(sourceUrls);
    }
}
