package com.tcc.api_visa.scraper.destination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tcc.api_visa.domain.Money;
import com.tcc.api_visa.domain.Requirement;
import com.tcc.api_visa.domain.RequirementType;
import com.tcc.api_visa.domain.VisaOption;
import com.tcc.api_visa.scraper.ScrapedVisaRequirement;
import com.tcc.api_visa.scraper.ScrapingException;
import com.tcc.api_visa.scraper.support.RequirementClassifier;
import com.tcc.api_visa.support.FixtureHtmlFetcher;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UnitedKingdomScraperTest {

    private static final String URL = UnitedKingdomScraper.STANDARD_VISITOR_URL;

    @Test
    void extractsRequirementsFeeAndStayFromOfficialPage() {
        UnitedKingdomScraper scraper =
                scraperFor(new FixtureHtmlFetcher().withFixture(URL, "gbr/standard-visitor-overview.html"));

        List<ScrapedVisaRequirement> results = scraper.scrape(Set.of("BRA", "ZAF", "GBR"));

        assertThat(results).extracting(ScrapedVisaRequirement::originIso3).containsExactly("BRA", "ZAF");

        VisaOption visa = results.getFirst().content().visas().getFirst();
        assertThat(visa.name()).isEqualTo("Standard Visitor visa");
        assertThat(visa.cost()).isEqualTo(Money.of("135", "GBP"));
        assertThat(visa.maxStayDays()).isEqualTo(180);
        assertThat(visa.requirements())
                .extracting(Requirement::type)
                .containsExactly(
                        RequirementType.DOCUMENT,
                        RequirementType.OTHER,
                        RequirementType.FINANCIAL_PROOF,
                        RequirementType.RETURN_TICKET,
                        RequirementType.OTHER);
    }

    @Test
    void ignoresOptionalListsThatAreNotIntroducedAsMandatory() {
        UnitedKingdomScraper scraper =
                scraperFor(new FixtureHtmlFetcher().withFixture(URL, "gbr/standard-visitor-overview.html"));

        VisaOption visa =
                scraper.scrape(Set.of("BRA")).getFirst().content().visas().getFirst();

        assertThat(visa.requirements())
                .extracting(Requirement::description)
                .noneMatch(text -> text.startsWith("to study"));
    }

    @Test
    void leavesFeeAndStayEmptyWhenThePageDoesNotStateThem() {
        String html = """
                <html><body>
                  <h2>Check you meet the eligibility requirements</h2>
                  <p>You must have a passport or travel document to enter the UK.</p>
                </body></html>
                """;
        UnitedKingdomScraper scraper = scraperFor(new FixtureHtmlFetcher().withHtml(URL, html));

        VisaOption visa =
                scraper.scrape(Set.of("BRA")).getFirst().content().visas().getFirst();

        assertThat(visa.cost()).isNull();
        assertThat(visa.maxStayDays()).isNull();
        assertThat(visa.requirements()).hasSize(1);
    }

    @Test
    void failsWhenThePageStructureChanges() {
        UnitedKingdomScraper scraper =
                scraperFor(new FixtureHtmlFetcher().withHtml(URL, "<html><body><h2>Overview</h2></body></html>"));

        assertThatThrownBy(() -> scraper.scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("eligibility requirements");
    }

    private static UnitedKingdomScraper scraperFor(FixtureHtmlFetcher fetcher) {
        return new UnitedKingdomScraper(fetcher, new RequirementClassifier());
    }
}
