package com.tcc.api_visa.scraper;

import static org.assertj.core.api.Assertions.assertThat;

import com.tcc.api_visa.domain.VisaPolicy;
import com.tcc.api_visa.scraper.destination.ArgentinaScraper;
import com.tcc.api_visa.scraper.destination.AustraliaScraper;
import com.tcc.api_visa.scraper.destination.CanadaScraper;
import com.tcc.api_visa.scraper.destination.EgyptScraper;
import com.tcc.api_visa.scraper.destination.GermanyScraper;
import com.tcc.api_visa.scraper.destination.SouthAfricaScraper;
import com.tcc.api_visa.scraper.destination.UnitedKingdomScraper;
import com.tcc.api_visa.scraper.fetch.HtmlFetcher;
import com.tcc.api_visa.scraper.fetch.PoliteHtmlFetcher;
import com.tcc.api_visa.scraper.fetch.RobotsTxtPolicy;
import com.tcc.api_visa.scraper.support.RequirementClassifier;
import com.tcc.api_visa.security.HostResolver;
import com.tcc.api_visa.security.PublicUrlValidator;
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
        HtmlFetcher fetcher = new PoliteHtmlFetcher(
                PROPERTIES,
                new RobotsTxtPolicy(PROPERTIES, Clock.systemUTC()),
                new PublicUrlValidator(HostResolver.system()));
        RequirementClassifier classifier = new RequirementClassifier();
        return Stream.of(
                new CanadaScraper(fetcher, classifier),
                new UnitedKingdomScraper(fetcher, classifier),
                new GermanyScraper(fetcher, classifier),
                new SouthAfricaScraper(fetcher),
                new ArgentinaScraper(fetcher, classifier),
                new EgyptScraper(fetcher, classifier),
                new AustraliaScraper(fetcher, classifier));
    }

    /** Every origin in scope gets a known policy, so a site change cannot silently drop or misread one. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("scrapers")
    void scraperStillUnderstandsTheOfficialPage(VisaScraper scraper) {
        Set<String> origins = Set.of("BRA", "JPN", "DEU", "CAN", "ZAF");

        List<ScrapedVisaRequirement> results = scraper.scrape(origins);

        assertThat(results)
                .extracting(ScrapedVisaRequirement::originIso3)
                .containsExactlyInAnyOrderElementsOf(origins.stream()
                        .filter(origin -> !origin.equals(scraper.destinationIso3()))
                        .toList());
        assertThat(results).allSatisfy(result -> {
            assertThat(result.destinationIso3()).isEqualTo(scraper.destinationIso3());
            assertThat(result.content().policy()).isNotEqualTo(VisaPolicy.UNKNOWN);
            assertThat(result.content().visas()).isNotEmpty();
        });
    }
}
