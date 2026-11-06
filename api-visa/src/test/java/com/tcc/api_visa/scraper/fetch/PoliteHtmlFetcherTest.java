package com.tcc.api_visa.scraper.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tcc.api_visa.scraper.ScraperProperties;
import com.tcc.api_visa.scraper.ScrapingException;
import com.tcc.api_visa.security.PublicUrlValidator;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PoliteHtmlFetcherTest {

    private static final PublicUrlValidator PUBLIC_DNS =
            new PublicUrlValidator(host -> List.of(InetAddress.getByName("93.184.215.14")));

    private static ScraperProperties properties(boolean respectRobotsTxt) {
        return new ScraperProperties("api-visa-tcc/test", Duration.ofSeconds(1), Duration.ZERO, 1024, respectRobotsTxt);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.gov/page", "file:///etc/passwd", "ftp://example.gov/x", "/relative/path"})
    void rejectsAnythingButAbsoluteHttps(String url) {
        PoliteHtmlFetcher fetcher = new PoliteHtmlFetcher(properties(true), uri -> true, PUBLIC_DNS);

        assertThatThrownBy(() -> fetcher.fetch(url)).isInstanceOf(ScrapingException.class);
    }

    @Test
    void doesNotFetchHostsThatPointToAPrivateNetwork() {
        PublicUrlValidator privateDns = new PublicUrlValidator(host -> List.of(InetAddress.getByName("10.0.0.5")));
        PoliteHtmlFetcher fetcher = new PoliteHtmlFetcher(properties(false), uri -> true, privateDns);

        assertThatThrownBy(() -> fetcher.fetch("https://internal.example.gov/"))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("private");
    }

    @Test
    void doesNotFetchPagesDisallowedByRobotsTxt() {
        PoliteHtmlFetcher fetcher = new PoliteHtmlFetcher(properties(true), uri -> false, PUBLIC_DNS);

        assertThatThrownBy(() -> fetcher.fetch("https://example.gov/private"))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("robots.txt");
    }

    @Test
    void recognisesRedirectStatuses() {
        assertThat(List.of(301, 302, 303, 307, 308)).allMatch(PoliteHtmlFetcher::isRedirect);
        assertThat(List.of(200, 304, 404)).noneMatch(PoliteHtmlFetcher::isRedirect);
    }

    @Test
    void resolvesRelativeRedirectsAgainstThePage() {
        URI page = URI.create("https://www.gov.uk/visas/standard-visitor");

        assertThat(PoliteHtmlFetcher.resolve(page, "/standard-visitor"))
                .isEqualTo("https://www.gov.uk/standard-visitor");
        assertThat(PoliteHtmlFetcher.resolve(page, "http://169.254.169.254/latest"))
                .isEqualTo("http://169.254.169.254/latest"); // rejected by the next hop's validation
        assertThatThrownBy(() -> PoliteHtmlFetcher.resolve(page, "https://exa mple.gov"))
                .isInstanceOf(ScrapingException.class);
    }
}
