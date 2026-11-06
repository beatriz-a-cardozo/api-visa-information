package com.tcc.api_visa.scraper.destination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tcc.api_visa.domain.Money;
import com.tcc.api_visa.domain.Requirement;
import com.tcc.api_visa.domain.RequirementType;
import com.tcc.api_visa.domain.TravelPurpose;
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

class UnitedKingdomStudyScraperTest {

    private static final String OVERVIEW_URL = UnitedKingdomStudyScraper.OVERVIEW_URL;
    private static final String MONEY_URL = UnitedKingdomStudyScraper.MONEY_URL;
    private static final String DOCUMENTS_URL = UnitedKingdomStudyScraper.DOCUMENTS_URL;

    @Test
    void everyOriginInScopeNeedsTheStudentVisa() {
        List<ScrapedVisaRequirement> results =
                scraperFor(savedPages()).scrape(Set.of("BRA", "JPN", "DEU", "CAN", "ZAF", "GBR", "IRL"));

        assertThat(results)
                .extracting(ScrapedVisaRequirement::originIso3)
                .containsExactly("BRA", "CAN", "DEU", "JPN", "ZAF");
        assertThat(results).allSatisfy(result -> {
            assertThat(result.destinationIso3()).isEqualTo("GBR");
            assertThat(result.purpose()).isEqualTo(TravelPurpose.STUDY);
            assertThat(result.content().policy()).isEqualTo(VisaPolicy.VISA_REQUIRED);
            assertThat(result.content().sourceUrls()).containsExactly(OVERVIEW_URL, MONEY_URL, DOCUMENTS_URL);
        });
    }

    @Test
    void theStudentVisaHasTheFeeTheStayAndTheRequirementsOfTheGuide() {
        VisaOption visa = scraperFor(savedPages())
                .scrape(Set.of("BRA"))
                .getFirst()
                .content()
                .visas()
                .getFirst();

        assertThat(visa.type()).isEqualTo(VisaType.STUDENT);
        assertThat(visa.name()).isEqualTo("Student visa");
        assertThat(visa.cost()).isEqualTo(Money.of("558", "GBP"));
        assertThat(visa.maxStayDays()).isEqualTo(5 * 365);
        assertThat(visa.sourceUrl()).isEqualTo(OVERVIEW_URL);
        assertThat(visa.requirements())
                .extracting(Requirement::description)
                .contains(
                        "have been offered a place on a course by a licensed student sponsor",
                        "can speak, read, write and understand English",
                        "£1,529 per month (for up to 9 months) for courses in London",
                        "£1,171 per month (for up to 9 months) for courses outside London",
                        "a current passport or other valid travel documentation");
        assertThat(visa.requirements())
                .filteredOn(requirement -> requirement.description().startsWith("have been offered a place"))
                .extracting(Requirement::type)
                .containsExactly(RequirementType.ACADEMIC_ADMISSION);
        assertThat(visa.requirements())
                .filteredOn(requirement -> requirement.description().contains("per month"))
                .extracting(Requirement::type)
                .containsOnly(RequirementType.FINANCIAL_PROOF);
    }

    @Test
    void readsNothingWhenNoOriginNeedsTheVisa() {
        assertThat(scraperFor(new FixtureHtmlFetcher()).scrape(Set.of("GBR", "IRL")))
                .isEmpty();
    }

    @Test
    void failsWhenTheFeeOrTheEligibilityListIsMissing() {
        String eligibility = "<p>You can apply for a Student visa to study in the UK if you’re 16 or over and you:</p>"
                + "<ul><li>have a place</li><li>have money</li><li>speak English</li></ul>";
        FixtureHtmlFetcher noFee = savedPages().withHtml(OVERVIEW_URL, "<html><body>" + eligibility + "</body></html>");
        FixtureHtmlFetcher noList =
                savedPages().withHtml(OVERVIEW_URL, "<html><body><p>This guide has moved.</p></body></html>");

        assertThatThrownBy(() -> scraperFor(noFee).scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Student visa fee not found");
        assertThatThrownBy(() -> scraperFor(noList).scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("not found at " + OVERVIEW_URL);
    }

    @Test
    void failsWhenTheMandatoryDocumentsShrink() {
        FixtureHtmlFetcher shrunk = savedPages()
                .withHtml(
                        DOCUMENTS_URL,
                        "<html><body><p>When you apply for your Student visa you must provide:</p>"
                                + "<ul><li>a passport</li></ul></body></html>");

        assertThatThrownBy(() -> scraperFor(shrunk).scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Only 1 mandatory documents");
    }

    private static FixtureHtmlFetcher savedPages() {
        return new FixtureHtmlFetcher()
                .withFixture(OVERVIEW_URL, "gbr/student-visa.html")
                .withFixture(MONEY_URL, "gbr/student-visa-money.html")
                .withFixture(DOCUMENTS_URL, "gbr/student-visa-documents.html");
    }

    private static UnitedKingdomStudyScraper scraperFor(FixtureHtmlFetcher fetcher) {
        return new UnitedKingdomStudyScraper(fetcher, new RequirementClassifier());
    }
}
