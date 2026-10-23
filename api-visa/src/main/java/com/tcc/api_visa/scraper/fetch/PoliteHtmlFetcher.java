package com.tcc.api_visa.scraper.fetch;

import com.tcc.api_visa.scraper.ScraperProperties;
import com.tcc.api_visa.scraper.ScrapingException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Fetches official pages the way a well-behaved crawler should: HTTPS only, an honest
 * User-Agent, robots.txt respected and a minimum delay between requests to the same host.
 */
@Component
public class PoliteHtmlFetcher implements HtmlFetcher {

    private final ScraperProperties properties;
    private final RobotsPolicy robotsPolicy;
    private final HostThrottle throttle;

    public PoliteHtmlFetcher(ScraperProperties properties, RobotsPolicy robotsPolicy) {
        this.properties = properties;
        this.robotsPolicy = robotsPolicy;
        this.throttle = new HostThrottle(properties.minDelayPerHost());
    }

    @Override
    public Document fetch(String url) {
        URI uri = requireHttps(url);

        if (properties.respectRobotsTxt() && !robotsPolicy.isAllowed(uri)) {
            throw new ScrapingException("Disallowed by robots.txt: " + url);
        }

        try {
            throttle.awaitTurn(uri.getHost());
            var response = Jsoup.connect(url)
                    .userAgent(properties.userAgent())
                    .timeout((int) properties.timeout().toMillis())
                    .maxBodySize(properties.maxBodyBytes())
                    .followRedirects(true)
                    .execute();

            // A redirect must not downgrade the connection to plain HTTP.
            requireHttps(response.url().toString());
            return response.parse();
        } catch (HttpStatusException e) {
            throw new ScrapingException("HTTP " + e.getStatusCode() + " from " + url, e);
        } catch (IOException e) {
            throw new ScrapingException("Could not fetch " + url + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScrapingException("Interrupted while fetching " + url, e);
        }
    }

    static URI requireHttps(String url) {
        try {
            URI uri = new URI(url);
            if (!"https".equals(String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT)) || uri.getHost() == null) {
                throw new ScrapingException("Only absolute https URLs are allowed: " + url);
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new ScrapingException("Invalid URL: " + url, e);
        }
    }
}
