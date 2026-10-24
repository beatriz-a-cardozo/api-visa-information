package com.tcc.api_visa.scraper;

import static org.assertj.core.api.Assertions.assertThat;

import com.tcc.api_visa.scraper.destination.CanadaScraper;
import com.tcc.api_visa.scraper.destination.UnitedKingdomScraper;
import com.tcc.api_visa.scraper.fetch.HtmlFetcher;
import com.tcc.api_visa.scraper.fetch.PoliteHtmlFetcher;
import com.tcc.api_visa.scraper.fetch.RobotsTxtPolicy;
import com.tcc.api_visa.scraper.support.RequirementClassifier;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs every scraper against the real official website. Excluded from the normal build; run with
 * {@code ./mvnw test -Dgroups=live -DexcludedGroups=}.
 */
@Tag("live")
class LiveSourcesTest {

    private static final ScraperProperties PROPERTIES = new ScraperProperties(
            "api-visa-tcc/0.1 (+https://github.com/beatriz-a-cardozo/api-visa-information)",
            Duration.ofSeconds(30),
            Duration.ofSeconds(2),
            5 * 1024 * 1024,
            true);

    static Stream<VisaScraper> scrapers() {
        HtmlFetcher fetcher = new PoliteHtmlFetcher(PROPERTIES, new RobotsTxtPolicy(PROPERTIES, Clock.systemUTC()));
        RequirementClassifier classifier = new RequirementClassifier();
        return Stream.of(new CanadaScraper(fetcher, classifier), new UnitedKingdomScraper(fetcher, classifier));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scrapers")
    void scraperStillUnderstandsTheOfficialPage(VisaScraper scraper) {
        List<ScrapedVisaRequirement> results = scraper.scrape(Set.of("BRA", "JPN", "DEU", "CAN", "ZAF"));

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(result -> {
            assertThat(result.destinationIso3()).isEqualTo(scraper.destinationIso3());
            assertThat(result.content().visas()).isNotEmpty();
            assertThat(result.content().visas().getFirst().requirements()).isNotEmpty();
        });
    }
}
