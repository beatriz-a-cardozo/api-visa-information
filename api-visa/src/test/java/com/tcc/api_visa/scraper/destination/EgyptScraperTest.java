package com.tcc.api_visa.scraper.destination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
import java.util.Set;
import org.junit.jupiter.api.Test;

class EgyptScraperTest {

    private static final String URL = EgyptScraper.FAQ_URL;

    @Test
    void eligibleNationalitiesGetTheEVisaWithFeesAndRequirements() {
        List<ScrapedVisaRequirement> results =
                scraperFor(fixture()).scrape(Set.of("BRA", "JPN", "DEU", "CAN", "ZAF", "EGY"));

        assertThat(results)
                .extracting(ScrapedVisaRequirement::originIso3)
                .containsExactly("BRA", "CAN", "DEU", "JPN", "ZAF");
        VisaRequirementContent content = results.getFirst().content();
        assertThat(content.policy()).isEqualTo(VisaPolicy.E_VISA);
        assertThat(content.sourceUrls()).containsExactly(URL);
        assertThat(content.visas())
                .extracting(VisaOption::name, VisaOption::cost)
                .containsExactly(
                        tuple("e-Visa: Single entry visa (Tourism)", Money.of("30", "USD")),
                        tuple("e-Visa: Multiple entries visa (Tourism)", Money.of("65", "USD")));
        assertThat(content.visas().getFirst().requirements())
                .extracting(Requirement::description)
                .containsExactly(
                        "Passport valid for at least 6 months from arrival date.",
                        "e-Visa printout",
                        "Travel itinerary",
                        "Supporting letter e.g. letter from company or invitation letter for business/family visits.",
                        "Hotel bookings/details about places to visit if going as tourist.");
        assertThat(content.visas().getFirst().requirements().getFirst().type()).isEqualTo(RequirementType.DOCUMENT);
    }

    @Test
    void originsNotOnTheListAreLeftOut() {
        // The page does not say whether they are exempt or must apply at a consulate.
        assertThat(scraperFor(fixture()).scrape(Set.of("AFG"))).isEmpty();
    }

    @Test
    void failsWhenTheListShrinks() {
        FixtureHtmlFetcher fetcher =
                new FixtureHtmlFetcher().withHtml(URL, "<div class=\"countries-grid\"><label>Brazil</label></div>");

        assertThatThrownBy(() -> scraperFor(fetcher).scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Only 1 nationalities");
    }

    @Test
    void failsWhenFeesOrRequirementsDisappear() {
        String labels = "<label>Brazil</label>".repeat(EgyptScraper.MIN_COUNTRIES);
        String noFees =
                "<div class=\"countries-grid\">" + labels + "</div><h3>Requirements</h3><div><p>- Passport</p></div>";
        String noRequirements =
                "<div class=\"countries-grid\">" + labels + "</div><table><tr><td>Single</td><td>30$</td></tr></table>";

        assertThatThrownBy(() -> scraperFor(new FixtureHtmlFetcher().withHtml(URL, noFees))
                        .scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("No visa fees");
        assertThatThrownBy(() -> scraperFor(new FixtureHtmlFetcher().withHtml(URL, noRequirements))
                        .scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Requirements");
    }

    private static FixtureHtmlFetcher fixture() {
        return new FixtureHtmlFetcher().withFixture(URL, "egy/faq.html");
    }

    private static EgyptScraper scraperFor(FixtureHtmlFetcher fetcher) {
        return new EgyptScraper(fetcher, new RequirementClassifier());
    }
}
