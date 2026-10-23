package com.tcc.api_visa.scraper.destination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tcc.api_visa.domain.Requirement;
import com.tcc.api_visa.domain.RequirementType;
import com.tcc.api_visa.domain.VisaOption;
import com.tcc.api_visa.domain.VisaPolicy;
import com.tcc.api_visa.domain.VisaType;
import com.tcc.api_visa.scraper.ScrapedVisaRequirement;
import com.tcc.api_visa.scraper.ScrapingException;
import com.tcc.api_visa.scraper.support.RequirementClassifier;
import com.tcc.api_visa.support.FixtureHtmlFetcher;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CanadaScraperTest {

    private static final String URL = CanadaScraper.VISITOR_ELIGIBILITY_URL;

    @Test
    void extractsVisitorVisaRequirementsFromOfficialPage() {
        CanadaScraper scraper = scraperFor(new FixtureHtmlFetcher().withFixture(URL, "can/visitor-eligibility.html"));

        List<ScrapedVisaRequirement> results = scraper.scrape(Set.of("BRA", "JPN"));

        assertThat(results).extracting(ScrapedVisaRequirement::originIso3).containsExactly("BRA", "JPN");
        assertThat(results).allSatisfy(result -> {
            assertThat(result.destinationIso3()).isEqualTo("CAN");
            assertThat(result.content().policy()).isEqualTo(VisaPolicy.UNKNOWN);
            assertThat(result.content().sourceUrls()).containsExactly(URL);
        });

        VisaOption visa = results.getFirst().content().visas().getFirst();
        assertThat(visa.type()).isEqualTo(VisaType.TOURIST);
        assertThat(visa.sourceUrl()).isEqualTo(URL);
        assertThat(visa.requirements())
                .extracting(Requirement::type)
                .contains(
                        RequirementType.DOCUMENT,
                        RequirementType.HEALTH,
                        RequirementType.CRIMINAL_RECORD,
                        RequirementType.FINANCIAL_PROOF);
        assertThat(visa.requirements())
                .extracting(Requirement::description)
                .contains("have a valid travel document, like a passport", "be in good health");
    }

    @Test
    void skipsTheDestinationItself() {
        CanadaScraper scraper = scraperFor(new FixtureHtmlFetcher().withFixture(URL, "can/visitor-eligibility.html"));

        assertThat(scraper.scrape(Set.of("CAN", "DEU")))
                .extracting(ScrapedVisaRequirement::originIso3)
                .containsExactly("DEU");
    }

    @Test
    void failsWhenThePageStructureChanges() {
        CanadaScraper scraper =
                scraperFor(new FixtureHtmlFetcher().withHtml(URL, "<html><body><h2>Something else</h2></body></html>"));

        assertThatThrownBy(() -> scraper.scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Who can get a visa");
    }

    @Test
    void failsWhenTheSectionHasNoRequirementList() {
        CanadaScraper scraper = scraperFor(new FixtureHtmlFetcher()
                .withHtml(URL, "<html><body><h2>Who can get a visa</h2><p>Text only.</p></body></html>"));

        assertThatThrownBy(() -> scraper.scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("No requirements");
    }

    private static CanadaScraper scraperFor(FixtureHtmlFetcher fetcher) {
        return new CanadaScraper(fetcher, new RequirementClassifier());
    }
}
