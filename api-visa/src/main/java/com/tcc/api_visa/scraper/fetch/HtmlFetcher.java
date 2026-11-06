package com.tcc.api_visa.scraper.fetch;

import org.jsoup.nodes.Document;

/** Downloads and parses a page. Scrapers depend on this interface so tests can serve saved HTML. */
public interface HtmlFetcher {

    /**
     * @throws com.tcc.api_visa.scraper.ScrapingException if the URL is not allowed or cannot be read
     */
    Document fetch(String url);
}
