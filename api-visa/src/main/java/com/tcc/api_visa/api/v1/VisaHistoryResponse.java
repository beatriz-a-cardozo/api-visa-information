package com.tcc.api_visa.api.v1;

import com.tcc.api_visa.domain.DataOrigin;
import com.tcc.api_visa.domain.VisaHistory;
import com.tcc.api_visa.domain.VisaPolicy;
import com.tcc.api_visa.domain.VisaRequirementContent;
import java.time.Instant;
import java.util.List;

/** Changes of the last year for one pair, newest first. */
public record VisaHistoryResponse(CountrySummary origin, CountrySummary destination, List<Change> changes) {

    /**
     * What the content was before {@link #changedAt}. Fees are shown as published at the time,
     * without conversion.
     *
     * @param changeOrigin who made the change: the scraper or an administrator
     */
    public record Change(
            Instant changedAt,
            DataOrigin changeOrigin,
            VisaPolicy previousPolicy,
            List<VisaOptionResponse> previousVisas,
            List<String> previousSourceUrls) {

        static Change from(VisaHistory history) {
            VisaRequirementContent previous = history.getPreviousContent();
            return new Change(
                    history.getChangedAt(),
                    history.getChangeOrigin(),
                    previous.policy(),
                    previous.visas().stream()
                            .map(visa -> VisaOptionResponse.from(visa, null))
                            .toList(),
                    previous.sourceUrls());
        }
    }
}
