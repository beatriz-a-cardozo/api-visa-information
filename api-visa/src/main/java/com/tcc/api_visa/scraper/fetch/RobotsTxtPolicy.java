package com.tcc.api_visa.scraper.fetch;

import com.tcc.api_visa.scraper.ScraperProperties;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRules.RobotRulesMode;
import crawlercommons.robots.SimpleRobotRulesParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Applies each site's robots.txt, following RFC 9309: a missing file (4xx) allows everything,
 * and an unreachable file (5xx or network error) disallows everything until the next attempt.
 */
@Slf4j
@Component
public class RobotsTxtPolicy implements RobotsPolicy {

    static final Duration CACHE_TTL = Duration.ofHours(24);

    private final ScraperProperties properties;
    private final Clock clock;
    private final HttpClient httpClient;
    private final SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
    private final Map<String, CachedRules> cache = new ConcurrentHashMap<>();

    public RobotsTxtPolicy(ScraperProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public boolean isAllowed(URI uri) {
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        Instant now = clock.instant();
        CachedRules cached = cache.compute(
                origin,
                (key, current) -> current == null || current.isExpired(now)
                        ? new CachedRules(download(URI.create(key + "/robots.txt")), now)
                        : current);
        return cached.rules().isAllowed(uri.toString());
    }

    private BaseRobotRules download(URI robotsUri) {
        HttpRequest request = HttpRequest.newBuilder(robotsUri)
                .timeout(properties.timeout())
                .header("User-Agent", properties.userAgent())
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers().firstValue("Content-Type").orElse("text/plain");
            return parse(robotsUri.toString(), response.statusCode(), response.body(), contentType);
        } catch (IOException e) {
            log.warn("Could not read {}: {}", robotsUri, e.getMessage());
            return new SimpleRobotRules(RobotRulesMode.ALLOW_NONE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SimpleRobotRules(RobotRulesMode.ALLOW_NONE);
        }
    }

    BaseRobotRules parse(String robotsUrl, int status, byte[] body, String contentType) {
        if (status >= 200 && status < 300) {
            return parser.parseContent(robotsUrl, body, contentType, List.of(robotName()));
        }
        if (status >= 400 && status < 500) {
            return new SimpleRobotRules(RobotRulesMode.ALLOW_ALL);
        }
        return new SimpleRobotRules(RobotRulesMode.ALLOW_NONE);
    }

    /** First token of the User-Agent, e.g. {@code api-visa-tcc} for {@code api-visa-tcc/0.1 (...)}. */
    String robotName() {
        return properties.userAgent().split("[/\\s]", 2)[0].toLowerCase(Locale.ROOT);
    }

    private record CachedRules(BaseRobotRules rules, Instant fetchedAt) {

        boolean isExpired(Instant now) {
            return fetchedAt.plus(CACHE_TTL).isBefore(now);
        }
    }
}
