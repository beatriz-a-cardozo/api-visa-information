package com.tcc.api_visa.scraper.fetch;

import com.tcc.api_visa.scraper.ScraperProperties;
import com.tcc.api_visa.scraper.ScrapingException;
import com.tcc.api_visa.security.PublicUrlValidator;
import com.tcc.api_visa.security.UnsafeUrlException;
import java.io.IOException;
import java.net.URI;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Fetches official pages the way a well-behaved crawler should: HTTPS only, an honest
 * User-Agent, robots.txt respected and a minimum delay between requests to the same host.
 *
 * <p>Redirects are followed here, not by Jsoup, so every hop goes through the same checks: a
 * redirect can neither downgrade to plain HTTP nor point into a private network.
 */
@Component
public class PoliteHtmlFetcher implements HtmlFetcher {

    static final int MAX_REDIRECTS = 5;

    private final ScraperProperties properties;
    private final RobotsPolicy robotsPolicy;
    private final PublicUrlValidator urlValidator;
    private final HostThrottle throttle;

    public PoliteHtmlFetcher(ScraperProperties properties, RobotsPolicy robotsPolicy, PublicUrlValidator urlValidator) {
        this.properties = properties;
        this.robotsPolicy = robotsPolicy;
        this.urlValidator = urlValidator;
        this.throttle = new HostThrottle(properties.minDelayPerHost());
    }

    @Override
    public Document fetch(String url) {
        String current = url;
        for (int redirects = 0; ; redirects++) {
            URI uri = allowed(current);
            Connection.Response response = execute(uri);
            if (!isRedirect(response.statusCode())) {
                return parse(response, current);
            }
            String location = response.header("Location");
            if (location == null || redirects == MAX_REDIRECTS) {
                throw new ScrapingException("Too many or invalid redirects from " + url);
            }
            current = resolve(uri, location);
        }
    }

    private URI allowed(String url) {
        URI uri;
        try {
            uri = urlValidator.validate(url);
        } catch (UnsafeUrlException e) {
            throw new ScrapingException(e.getMessage(), e);
        }
        if (properties.respectRobotsTxt() && !robotsPolicy.isAllowed(uri)) {
            throw new ScrapingException("Disallowed by robots.txt: " + url);
        }
        return uri;
    }

    private Connection.Response execute(URI uri) {
        try {
            throttle.awaitTurn(uri.getHost());
            return Jsoup.connect(uri.toString())
                    .userAgent(properties.userAgent())
                    .timeout((int) properties.timeout().toMillis())
                    .maxBodySize(properties.maxBodyBytes())
                    .followRedirects(false)
                    .execute();
        } catch (HttpStatusException e) {
            throw new ScrapingException("HTTP " + e.getStatusCode() + " from " + uri, e);
        } catch (IOException e) {
            throw new ScrapingException("Could not fetch " + uri + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScrapingException("Interrupted while fetching " + uri, e);
        }
    }

    private static Document parse(Connection.Response response, String url) {
        try {
            return response.parse();
        } catch (IOException e) {
            throw new ScrapingException("Could not read " + url + ": " + e.getMessage(), e);
        }
    }

    static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    /** The {@code Location} header may be relative to the page that sent it. */
    static String resolve(URI base, String location) {
        try {
            return base.resolve(location.strip()).toString();
        } catch (IllegalArgumentException e) {
            throw new ScrapingException("Invalid redirect from " + base + ": " + location, e);
        }
    }
}
