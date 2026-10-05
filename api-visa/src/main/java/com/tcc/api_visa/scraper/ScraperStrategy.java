package com.tcc.api_visa.scraper;

import com.tcc.api_visa.model.Visa;
import java.io.IOException;

public interface ScraperStrategy {

    Visa scrape() throws IOException;
}