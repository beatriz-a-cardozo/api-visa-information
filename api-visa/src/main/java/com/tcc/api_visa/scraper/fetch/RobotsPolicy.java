package com.tcc.api_visa.scraper.fetch;

import java.net.URI;

public interface RobotsPolicy {

    boolean isAllowed(URI uri);
}
