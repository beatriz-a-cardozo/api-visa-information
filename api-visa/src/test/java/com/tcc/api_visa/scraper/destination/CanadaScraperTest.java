package com.tcc.api_visa.scraper.destination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tcc.api_visa.domain.Money;
import com.tcc.api_visa.domain.Requirement;
import com.tcc.api_visa.domain.RequirementType;
import com.tcc.api_visa.domain.VisaOption;
import com.tcc.api_visa.domain.VisaPolicy;
import com.tcc.api_visa.domain.VisaRequirementContent;
import com.tcc.api_visa.scraper.ScrapedVisaRequirement;
import com.tcc.api_visa.scraper.ScrapingException;
import com.tcc.api_visa.scraper.support.RequirementClassifier;
import com.tcc.api_visa.support.FixtureHtmlFetcher;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CanadaScraperTest {

    private static final String ENTRY = CanadaScraper.ENTRY_REQUIREMENTS_URL;
    private static final String VISA = CanadaScraper.VISITOR_ELIGIBILITY_URL;
    private static final String ETA = CanadaScraper.ETA_URL;

    @Test
    void readsThePolicyOfEachOriginFromTheNationalityLists() {
        Map<String, VisaRequirementContent> results =
                byOrigin(scraperFor(allPages()).scrape(Set.of("BRA", "JPN", "DEU", "ZAF", "CAN")));

        assertThat(results).containsOnlyKeys("BRA", "DEU", "JPN", "ZAF");
        assertThat(results.get("ZAF").policy()).isEqualTo(VisaPolicy.VISA_REQUIRED);
        assertThat(results.get("BRA").policy()).isEqualTo(VisaPolicy.VISA_REQUIRED);
        assertThat(results.get("JPN").policy()).isEqualTo(VisaPolicy.ELECTRONIC_TRAVEL_AUTHORIZATION);
        assertThat(results.get("DEU").policy()).isEqualTo(VisaPolicy.ELECTRONIC_TRAVEL_AUTHORIZATION);
    }

    @Test
    void visaRequiredOriginsGetTheVisitorVisaFromItsPage() {
        VisaRequirementContent southAfrica =
                byOrigin(scraperFor(allPages()).scrape(Set.of("ZAF"))).get("ZAF");

        assertThat(southAfrica.sourceUrls()).containsExactly(ENTRY, VISA);
        VisaOption visa = southAfrica.visas().getFirst();
        assertThat(southAfrica.visas()).hasSize(1);
        assertThat(visa.name()).isEqualTo("Visitor visa (temporary resident visa)");
        assertThat(visa.sourceUrl()).isEqualTo(VISA);
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
    void etaOriginsGetTheEtaWithItsFee() {
        VisaRequirementContent japan =
                byOrigin(scraperFor(allPages()).scrape(Set.of("JPN"))).get("JPN");

        assertThat(japan.sourceUrls()).containsExactly(ENTRY, ETA);
        VisaOption eta = japan.visas().getFirst();
        assertThat(eta.name()).isEqualTo("Electronic travel authorization (eTA)");
        assertThat(eta.cost()).isEqualTo(Money.of("7", "CAD"));
        assertThat(eta.requirements()).singleElement().satisfies(requirement -> {
            assertThat(requirement.description()).startsWith("To apply, you must have a valid passport");
            assertThat(requirement.type()).isEqualTo(RequirementType.DOCUMENT);
        });
    }

    @Test
    void visaRequiredOriginsThatMayFlyWithAnEtaGetBothOptions() {
        VisaRequirementContent brazil =
                byOrigin(scraperFor(allPages()).scrape(Set.of("BRA"))).get("BRA");

        assertThat(brazil.visas())
                .extracting(VisaOption::name)
                .containsExactly(
                        "Visitor visa (temporary resident visa)",
                        "Electronic travel authorization (eTA) instead of a visa, when flying");
        assertThat(brazil.sourceUrls()).containsExactly(ENTRY, VISA, ETA);
        VisaOption eta = brazil.visas().get(1);
        assertThat(eta.cost()).isEqualTo(Money.of("7", "CAD"));
        assertThat(eta.requirements().getFirst().description())
                .startsWith("If you’re from one of the countries listed below, you may be eligible")
                .endsWith("meet certain requirements and are travelling by air");
        assertThat(eta.requirements()).hasSize(2);
    }

    @Test
    void fetchesOnlyThePagesTheOriginsNeed() {
        // No eTA fixture: an origin that needs a visa must not trigger the eTA page.
        FixtureHtmlFetcher fetcher = new FixtureHtmlFetcher()
                .withFixture(ENTRY, "can/entry-requirements-country.html")
                .withFixture(VISA, "can/visitor-eligibility.html");

        assertThat(scraperFor(fetcher).scrape(Set.of("ZAF"))).hasSize(1);
    }

    @Test
    void failsWhenAListDisappears() {
        FixtureHtmlFetcher fetcher = new FixtureHtmlFetcher()
                .withHtml(ENTRY, "<html><body><h2>Travellers who need a visa</h2></body></html>");

        assertThatThrownBy(() -> scraperFor(fetcher).scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining(CanadaScraper.VISA_LIST);
    }

    @Test
    void failsWhenAListIsMuchShorterThanExpected() {
        String html = """
                <html><body>
                  <details><summary>Visa-required countries or territories</summary><ul><li>South Africa</li></ul></details>
                  <details><summary>eTA-required countries or territories</summary><ul><li>Japan</li></ul></details>
                  <details><summary>Eligible visa-required countries</summary><ul><li>Brazil</li></ul></details>
                </body></html>
                """;
        FixtureHtmlFetcher fetcher = new FixtureHtmlFetcher().withHtml(ENTRY, html);

        assertThatThrownBy(() -> scraperFor(fetcher).scrape(Set.of("ZAF")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Only 1 countries");
    }

    @Test
    void failsWhenTheVisitorVisaPageChanges() {
        FixtureHtmlFetcher fetcher = new FixtureHtmlFetcher()
                .withFixture(ENTRY, "can/entry-requirements-country.html")
                .withHtml(VISA, "<html><body><h2>Something else</h2></body></html>");

        assertThatThrownBy(() -> scraperFor(fetcher).scrape(Set.of("ZAF")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Who can get a visa");
    }

    @Test
    void failsWhenTheEtaPageHasNoRequirements() {
        FixtureHtmlFetcher fetcher = new FixtureHtmlFetcher()
                .withFixture(ENTRY, "can/entry-requirements-country.html")
                .withHtml(ETA, "<html><body><p>It only costs CAN$7.</p></body></html>");

        assertThatThrownBy(() -> scraperFor(fetcher).scrape(Set.of("JPN")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("No eTA requirements");
    }

    private static FixtureHtmlFetcher allPages() {
        return new FixtureHtmlFetcher()
                .withFixture(ENTRY, "can/entry-requirements-country.html")
                .withFixture(VISA, "can/visitor-eligibility.html")
                .withFixture(ETA, "can/eta.html");
    }

    private static Map<String, VisaRequirementContent> byOrigin(List<ScrapedVisaRequirement> results) {
        assertThat(results)
                .allSatisfy(result -> assertThat(result.destinationIso3()).isEqualTo("CAN"));
        return results.stream()
                .collect(Collectors.toMap(ScrapedVisaRequirement::originIso3, ScrapedVisaRequirement::content));
    }

    private static CanadaScraper scraperFor(FixtureHtmlFetcher fetcher) {
        return new CanadaScraper(fetcher, new RequirementClassifier());
    }
}
