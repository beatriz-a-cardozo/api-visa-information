package com.tcc.api_visa.scraper;

import com.tcc.api_visa.domain.VisaRequirementContent;

/** What a scraper found for one origin/destination pair, before it is compared and saved. */
public record ScrapedVisaRequirement(String originIso3, String destinationIso3, VisaRequirementContent content) {}
