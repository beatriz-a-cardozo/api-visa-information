package com.tcc.api_visa.scraper.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tcc.api_visa.scraper.ScraperProperties;
import com.tcc.api_visa.scraper.ScrapingException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PoliteHtmlFetcherTest {

    private static ScraperProperties properties(boolean respectRobotsTxt) {
        return new ScraperProperties("api-visa-tcc/test", Duration.ofSeconds(1), Duration.ZERO, 1024, respectRobotsTxt);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.gov/page", "file:///etc/passwd", "ftp://example.gov/x", "/relative/path"})
    void rejectsAnythingButAbsoluteHttps(String url) {
        PoliteHtmlFetcher fetcher = new PoliteHtmlFetcher(properties(true), uri -> true);

        assertThatThrownBy(() -> fetcher.fetch(url))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("https");
    }

    @Test
    void rejectsMalformedUrls() {
        assertThatThrownBy(() -> PoliteHtmlFetcher.requireHttps("https://exa mple.gov"))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Invalid URL");
    }

    @Test
    void doesNotFetchPagesDisallowedByRobotsTxt() {
        PoliteHtmlFetcher fetcher = new PoliteHtmlFetcher(properties(true), uri -> false);

        assertThatThrownBy(() -> fetcher.fetch("https://example.gov/private"))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("robots.txt");
    }

    @Test
    void acceptsAbsoluteHttpsUrls() {
        assertThat(PoliteHtmlFetcher.requireHttps("https://www.gov.uk/standard-visitor")
                        .getHost())
                .isEqualTo("www.gov.uk");
    }
}
