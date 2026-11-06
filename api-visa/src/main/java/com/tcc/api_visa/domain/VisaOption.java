package com.tcc.api_visa.domain;

import java.util.List;

/**
 * One visa (or entry authorization) available for an origin/destination pair.
 *
 * @param maxStayDays maximum stay allowed, or {@code null} when the source does not say
 * @param cost        fee in the currency published by the destination, or {@code null} when unknown
 * @param sourceUrl   official page the information was read from
 */
public record VisaOption(
        VisaType type, String name, Integer maxStayDays, Money cost, List<Requirement> requirements, String sourceUrl) {

    public VisaOption {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
    }
}
