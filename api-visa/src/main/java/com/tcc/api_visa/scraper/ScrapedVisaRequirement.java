package com.tcc.api_visa.scraper;

import com.tcc.api_visa.domain.TravelPurpose;
import com.tcc.api_visa.domain.VisaRequirementContent;

/** What a scraper found for one origin/destination pair and purpose, before it is compared and saved. */
public record ScrapedVisaRequirement(
        String originIso3, String destinationIso3, TravelPurpose purpose, VisaRequirementContent content) {

    /** A tourism result, which is what most scrapers collect. */
    public ScrapedVisaRequirement(String originIso3, String destinationIso3, VisaRequirementContent content) {
        this(originIso3, destinationIso3, TravelPurpose.TOURISM, content);
    }
}
