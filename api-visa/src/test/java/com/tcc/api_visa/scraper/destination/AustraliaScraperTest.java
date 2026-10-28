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

class AustraliaScraperTest {

    private static final String ETA = AustraliaScraper.ETA_URL;
    private static final String EVISITOR = AustraliaScraper.EVISITOR_URL;
    private static final String VISITOR = AustraliaScraper.VISITOR_URL;

    @Test
    void eachOriginGetsTheVisasItsPassportAllows() {
        Map<String, VisaRequirementContent> results =
                scrape(allPages(), Set.of("BRA", "JPN", "DEU", "CAN", "ZAF", "AUS"));

        assertThat(results).containsOnlyKeys("BRA", "CAN", "DEU", "JPN", "ZAF");
        assertThat(results.get("JPN").policy()).isEqualTo(VisaPolicy.ELECTRONIC_TRAVEL_AUTHORIZATION);
        assertThat(results.get("JPN").sourceUrls()).containsExactly(ETA);
        assertThat(results.get("CAN").policy()).isEqualTo(VisaPolicy.ELECTRONIC_TRAVEL_AUTHORIZATION);

        // Germany is on both lists.
        assertThat(results.get("DEU").policy()).isEqualTo(VisaPolicy.ELECTRONIC_TRAVEL_AUTHORIZATION);
        assertThat(results.get("DEU").visas())
                .extracting(VisaOption::name)
                .containsExactly("Electronic Travel Authority", "eVisitor");
        assertThat(results.get("DEU").sourceUrls()).containsExactly(ETA, EVISITOR);

        assertThat(results.get("BRA").policy()).isEqualTo(VisaPolicy.VISA_REQUIRED);
        assertThat(results.get("ZAF").policy()).isEqualTo(VisaPolicy.VISA_REQUIRED);
        assertThat(results.get("ZAF").sourceUrls()).containsExactly(VISITOR);
    }

    @Test
    void optionsCarryStayCostAndCriteriaFromThePageContent() {
        Map<String, VisaRequirementContent> results = scrape(allPages(), Set.of("DEU", "BRA"));
        VisaOption eta = results.get("DEU").visas().get(0);
        VisaOption evisitor = results.get("DEU").visas().get(1);
        VisaOption visitor = results.get("BRA").visas().getFirst();

        assertThat(eta.maxStayDays()).isEqualTo(90);
        assertThat(eta.cost()).isEqualTo(Money.of("20", "AUD"));
        assertThat(eta.sourceUrl()).isEqualTo(ETA);
        assertThat(eta.requirements())
                .extracting(Requirement::description)
                .contains("Be a genuine visitor", "Meet our health requirement")
                .noneMatch(text -> text.contains("eligible passport"));
        assertThat(eta.requirements())
                .filteredOn(requirement -> requirement.description().equals("Meet our health requirement"))
                .extracting(Requirement::type)
                .containsExactly(RequirementType.HEALTH);

        assertThat(evisitor.cost()).isEqualTo(Money.of("0", "AUD"));
        assertThat(evisitor.requirements()).extracting(Requirement::type).contains(RequirementType.FINANCIAL_PROOF);

        assertThat(visitor.name()).isEqualTo("Tourist stream (apply outside Australia)");
        assertThat(visitor.maxStayDays()).isEqualTo(360);
        assertThat(visitor.cost()).isNull(); // filled in by script on the page
    }

    @Test
    void fetchesTheVisitorVisaOnlyWhenSomeOriginNeedsIt() {
        FixtureHtmlFetcher withoutVisitor = new FixtureHtmlFetcher()
                .withFixture(ETA, "aus/eta-601.html")
                .withFixture(EVISITOR, "aus/evisitor-651.html");

        assertThat(scrape(withoutVisitor, Set.of("JPN", "DEU"))).hasSize(2);
    }

    @Test
    void failsWhenThePageContentFieldIsMissingOrBroken() {
        FixtureHtmlFetcher missing = new FixtureHtmlFetcher()
                .withHtml(ETA, "<html><body><p>Loading...</p></body></html>")
                .withFixture(EVISITOR, "aus/evisitor-651.html");
        FixtureHtmlFetcher broken = new FixtureHtmlFetcher()
                .withHtml(ETA, field("{not json"))
                .withFixture(EVISITOR, "aus/evisitor-651.html");

        assertThatThrownBy(() -> scrape(missing, Set.of("JPN")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Page content field not found");
        assertThatThrownBy(() -> scrape(broken, Set.of("JPN")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void failsWhenThePassportListShrinks() {
        String schema = """
                {"title": "Electronic Travel Authority", "applicant": {"eligibility": {"criteria": [
                  {"title": "Have an eligible passport", "description": "<ul><li>Japan</li></ul>"}]}}}
                """;
        FixtureHtmlFetcher fetcher =
                new FixtureHtmlFetcher().withHtml(ETA, field(schema)).withFixture(EVISITOR, "aus/evisitor-651.html");

        // Without this check every origin would silently need a Visitor visa.
        assertThatThrownBy(() -> scrape(fetcher, Set.of("JPN")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Only 1 passports");
    }

    private static FixtureHtmlFetcher allPages() {
        return new FixtureHtmlFetcher()
                .withFixture(ETA, "aus/eta-601.html")
                .withFixture(EVISITOR, "aus/evisitor-651.html")
                .withFixture(VISITOR, "aus/visitor-600-tourist-overseas.html");
    }

    /** A page whose only content is the hidden field with {@code json}, as the site serves it. */
    private static String field(String json) {
        String escaped = json.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return "<html><body><input type=\"hidden\" id=\"ctl00_PlaceHolderMain_PageSchemaHiddenField_Input\" value=\""
                + escaped + "\" /></body></html>";
    }

    private static Map<String, VisaRequirementContent> scrape(FixtureHtmlFetcher fetcher, Set<String> origins) {
        List<ScrapedVisaRequirement> results =
                new AustraliaScraper(fetcher, new RequirementClassifier()).scrape(origins);
        assertThat(results)
                .allSatisfy(result -> assertThat(result.destinationIso3()).isEqualTo("AUS"));
        return results.stream()
                .collect(Collectors.toMap(ScrapedVisaRequirement::originIso3, ScrapedVisaRequirement::content));
    }
}
