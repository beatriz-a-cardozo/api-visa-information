package com.tcc.api_visa.api.v1;

import com.tcc.api_visa.domain.DataOrigin;
import com.tcc.api_visa.domain.TravelPurpose;
import com.tcc.api_visa.domain.VisaPolicy;
import java.time.Instant;
import java.util.List;

/**
 * Current visa information for one pair and purpose.
 *
 * @param dataOrigin  {@code SCRAPER} when read from the official source, {@code MANUAL} when entered
 *                    by an administrator
 * @param collectedAt last time the source was read successfully, even if nothing changed
 * @param updatedAt   last time the content changed
 */
public record VisaResponse(
        CountrySummary origin,
        CountrySummary destination,
        TravelPurpose purpose,
        VisaPolicy policy,
        List<VisaOptionResponse> visas,
        List<String> sourceUrls,
        DataOrigin dataOrigin,
        Instant collectedAt,
        Instant updatedAt) {}
