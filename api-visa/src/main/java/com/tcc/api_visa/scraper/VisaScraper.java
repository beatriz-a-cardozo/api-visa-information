package com.tcc.api_visa.scraper;

import java.util.List;
import java.util.Set;

/**
 * Collects visa information published by one destination country. Each destination has its own
 * implementation, because every government website is structured differently.
 */
public interface VisaScraper {

    /** ISO alpha-3 code of the destination this scraper reads. */
    String destinationIso3();

    /**
     * Reads the official sources and returns one result per origin it could determine.
     *
     * @param originIso3s origins to collect; the destination itself is ignored if present
     * @throws ScrapingException if a source cannot be read or no longer has the expected structure
     */
    List<ScrapedVisaRequirement> scrape(Set<String> originIso3s);
}
