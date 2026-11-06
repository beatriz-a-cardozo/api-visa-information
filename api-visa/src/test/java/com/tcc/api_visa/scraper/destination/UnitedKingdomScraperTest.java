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
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UnitedKingdomScraperTest {

    private static final String LIST = UnitedKingdomScraper.VISA_NATIONAL_LIST_URL;
    private static final String VISITOR = UnitedKingdomScraper.STANDARD_VISITOR_URL;
    private static final String ETA = UnitedKingdomScraper.ETA_URL;
    private static final String ETA_APPLY = UnitedKingdomScraper.ETA_APPLY_URL;

    @Test
    void visaNationalsNeedAVisaAndEveryoneElseAnEta() {
        Map<String, VisaRequirementContent> results =
                byOrigin(scraperFor(allPages()).scrape(Set.of("BRA", "JPN", "DEU", "CAN", "ZAF", "GBR", "IRL")));

        assertThat(results).containsOnlyKeys("BRA", "CAN", "DEU", "JPN", "ZAF");
        assertThat(results.get("ZAF").policy()).isEqualTo(VisaPolicy.VISA_REQUIRED);
        assertThat(results.get("ZAF").sourceUrls()).containsExactly(LIST, VISITOR);
        assertThat(List.of("BRA", "CAN", "DEU", "JPN"))
                .allSatisfy(origin ->
                        assertThat(results.get(origin).policy()).isEqualTo(VisaPolicy.ELECTRONIC_TRAVEL_AUTHORIZATION));
        assertThat(results.get("BRA").sourceUrls()).containsExactly(LIST, ETA, ETA_APPLY);
    }

    @Test
    void theStandardVisitorVisaComesWithFeeStayAndRequirements() {
        VisaOption visa = byOrigin(scraperFor(allPages()).scrape(Set.of("ZAF")))
                .get("ZAF")
                .visas()
                .getFirst();

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
        // Lists that are not introduced as mandatory are left out.
        assertThat(visa.requirements())
                .extracting(Requirement::description)
                .noneMatch(text -> text.startsWith("to study"));
    }

    @Test
    void theEtaComesWithFeeStayAndWhatTheApplicationNeeds() {
        VisaOption eta = byOrigin(scraperFor(allPages()).scrape(Set.of("BRA")))
                .get("BRA")
                .visas()
                .getFirst();

        assertThat(eta.name()).isEqualTo("Electronic travel authorisation (ETA)");
        assertThat(eta.cost()).isEqualTo(Money.of("20", "GBP"));
        assertThat(eta.maxStayDays()).isEqualTo(180);
        assertThat(eta.sourceUrl()).isEqualTo(ETA_APPLY);
        assertThat(eta.requirements())
                .extracting(Requirement::description)
                .containsExactly(
                        "the passport you’ll travel with",
                        "an email address",
                        "a credit card, debit card, Apple Pay or Google Pay");
        assertThat(eta.requirements().getFirst().type()).isEqualTo(RequirementType.DOCUMENT);
    }

    @Test
    void fetchesOnlyThePagesTheOriginsNeed() {
        // No Standard Visitor fixture: ETA origins must not trigger the visa page.
        FixtureHtmlFetcher fetcher = new FixtureHtmlFetcher()
                .withFixture(LIST, "gbr/visa-national-list.html")
                .withFixture(ETA, "gbr/eta.html")
                .withFixture(ETA_APPLY, "gbr/eta-apply.html");

        assertThat(scraperFor(fetcher).scrape(Set.of("BRA", "JPN"))).hasSize(2);
    }

    @Test
    void leavesFeeAndStayEmptyWhenThePageDoesNotStateThem() {
        String html = """
                <html><body>
                  <h2>Check you meet the eligibility requirements</h2>
                  <p>You must have a passport or travel document to enter the UK.</p>
                </body></html>
                """;
        FixtureHtmlFetcher fetcher = new FixtureHtmlFetcher()
                .withHtml(LIST, visaNationalList("South Africa"))
                .withHtml(VISITOR, html);

        VisaOption visa = scraperFor(fetcher)
                .scrape(Set.of("ZAF"))
                .getFirst()
                .content()
                .visas()
                .getFirst();

        assertThat(visa.cost()).isNull();
        assertThat(visa.maxStayDays()).isNull();
        assertThat(visa.requirements()).hasSize(1);
    }

    @Test
    void failsWhenTheVisaPageStructureChanges() {
        FixtureHtmlFetcher fetcher = new FixtureHtmlFetcher()
                .withHtml(LIST, visaNationalList("South Africa"))
                .withHtml(VISITOR, "<html><body><h2>Overview</h2></body></html>");

        assertThatThrownBy(() -> scraperFor(fetcher).scrape(Set.of("ZAF")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("eligibility requirements");
    }

    @Test
    void failsWhenTheVisaNationalListDisappearsOrShrinks() {
        FixtureHtmlFetcher missing = new FixtureHtmlFetcher().withHtml(LIST, "<html><body><p>Moved.</p></body></html>");
        FixtureHtmlFetcher shrunk = new FixtureHtmlFetcher()
                .withHtml(
                        LIST,
                        "<ol><li>(a) Nationals or citizens of the following countries:<ol><li>Afghanistan</li></ol></li></ol>");

        // Without this check every origin would silently become an ETA country.
        assertThatThrownBy(() -> scraperFor(missing).scrape(Set.of("ZAF")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Visa national list not found");
        assertThatThrownBy(() -> scraperFor(shrunk).scrape(Set.of("ZAF")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Only 1 nationalities");
    }

    @Test
    void failsWhenTheEtaRequirementsDisappear() {
        FixtureHtmlFetcher fetcher = new FixtureHtmlFetcher()
                .withHtml(LIST, visaNationalList("South Africa"))
                .withFixture(ETA, "gbr/eta.html")
                .withHtml(ETA_APPLY, "<html><body><p>It costs £20.</p></body></html>");

        assertThatThrownBy(() -> scraperFor(fetcher).scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("ETA requirements not found");
    }

    private static FixtureHtmlFetcher allPages() {
        return new FixtureHtmlFetcher()
                .withFixture(LIST, "gbr/visa-national-list.html")
                .withFixture(VISITOR, "gbr/standard-visitor-overview.html")
                .withFixture(ETA, "gbr/eta.html")
                .withFixture(ETA_APPLY, "gbr/eta-apply.html");
    }

    /** A list with {@code country} and enough filler items to pass the size check. */
    private static String visaNationalList(String country) {
        String fillers = IntStream.range(0, UnitedKingdomScraper.MIN_LIST_SIZE)
                .mapToObj(i -> "<li>Afghanistan</li>")
                .collect(Collectors.joining());
        return "<ol><li>(a) Nationals or citizens of the following countries:<ol><li>" + country + "</li>" + fillers
                + "</ol></li></ol>";
    }

    private static Map<String, VisaRequirementContent> byOrigin(List<ScrapedVisaRequirement> results) {
        assertThat(results)
                .allSatisfy(result -> assertThat(result.destinationIso3()).isEqualTo("GBR"));
        return results.stream()
                .collect(Collectors.toMap(ScrapedVisaRequirement::originIso3, ScrapedVisaRequirement::content));
    }

    private static UnitedKingdomScraper scraperFor(FixtureHtmlFetcher fetcher) {
        return new UnitedKingdomScraper(fetcher, new RequirementClassifier());
    }
}
