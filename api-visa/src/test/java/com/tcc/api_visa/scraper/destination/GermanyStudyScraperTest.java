package com.tcc.api_visa.scraper.destination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tcc.api_visa.domain.RequirementType;
import com.tcc.api_visa.domain.TravelPurpose;
import com.tcc.api_visa.domain.VisaOption;
import com.tcc.api_visa.domain.VisaPolicy;
import com.tcc.api_visa.domain.VisaRequirementContent;
import com.tcc.api_visa.domain.VisaType;
import com.tcc.api_visa.scraper.ScrapedVisaRequirement;
import com.tcc.api_visa.scraper.ScrapingException;
import com.tcc.api_visa.scraper.support.RequirementClassifier;
import com.tcc.api_visa.support.FixtureHtmlFetcher;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class GermanyStudyScraperTest {

    private static final String NATIONAL_VISA_URL = GermanyStudyScraper.NATIONAL_VISA_URL;
    private static final String STUDY_URL = GermanyStudyScraper.STUDY_URL;

    @Test
    void nationalitiesOnTheListApplyAfterEntryAndTheOthersNeedAVisa() {
        Map<String, VisaRequirementContent> results =
                byOrigin(scraperFor(savedPages()).scrape(Set.of("BRA", "JPN", "DEU", "CAN", "ZAF")));

        assertThat(results).containsOnlyKeys("BRA", "CAN", "JPN", "ZAF");
        assertThat(List.of("CAN", "JPN"))
                .allSatisfy(
                        origin -> assertThat(results.get(origin).policy()).isEqualTo(VisaPolicy.PERMIT_AFTER_ENTRY));
        assertThat(List.of("BRA", "ZAF"))
                .allSatisfy(origin -> assertThat(results.get(origin).policy()).isEqualTo(VisaPolicy.VISA_REQUIRED));
        assertThat(results.values())
                .allSatisfy(content -> assertThat(content.sourceUrls()).containsExactly(NATIONAL_VISA_URL, STUDY_URL));
    }

    @Test
    void theStudyOptionCarriesTheConditionsOfTheStudySection() {
        Map<String, VisaRequirementContent> results =
                byOrigin(scraperFor(savedPages()).scrape(Set.of("BRA", "JPN")));
        VisaOption brazil = results.get("BRA").visas().getFirst();
        VisaOption japan = results.get("JPN").visas().getFirst();

        assertThat(brazil.type()).isEqualTo(VisaType.STUDENT);
        assertThat(brazil.name()).isEqualTo("Nationales Visum (D) zum Studium");
        assertThat(brazil.sourceUrl()).isEqualTo(STUDY_URL);
        assertThat(japan.name()).isEqualTo("Aufenthaltserlaubnis zum Studium nach der Einreise");
        assertThat(japan.sourceUrl()).isEqualTo(NATIONAL_VISA_URL);
        assertThat(brazil.requirements())
                .satisfiesExactly(
                        admission -> {
                            assertThat(admission.description()).contains("an einer deutschen Hochschule bewerben");
                            assertThat(admission.type()).isEqualTo(RequirementType.ACADEMIC_ADMISSION);
                        },
                        money -> {
                            assertThat(money.description()).contains("Lebensunterhalts für das erste Jahr");
                            assertThat(money.type()).isEqualTo(RequirementType.FINANCIAL_PROOF);
                        });
        assertThat(japan.requirements()).isEqualTo(brazil.requirements());
    }

    @Test
    void everyResultIsForStudy() {
        assertThat(scraperFor(savedPages()).purpose()).isEqualTo(TravelPurpose.STUDY);
        assertThat(scraperFor(savedPages()).scrape(Set.of("BRA", "CAN")))
                .extracting(ScrapedVisaRequirement::purpose)
                .containsOnly(TravelPurpose.STUDY);
    }

    @Test
    void readsNothingWhenNoOriginIsLeft() {
        assertThat(scraperFor(new FixtureHtmlFetcher()).scrape(Set.of("DEU"))).isEmpty();
    }

    @Test
    void failsWhenTheListHasANationalityItDoesNotKnow() {
        FixtureHtmlFetcher fetcher = savedPages()
                .withHtml(
                        NATIONAL_VISA_URL,
                        permitSentence("Australiens, Brasiliens, Israels, Japans, Kanadas und Neuseelands"));

        assertThatThrownBy(() -> scraperFor(fetcher).scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Unknown nationalities [Brasiliens]");
    }

    @Test
    void failsWhenTheListDisappearsOrShrinks() {
        FixtureHtmlFetcher missing =
                savedPages().withHtml(NATIONAL_VISA_URL, "<html><body><p>Neue Regeln</p></body></html>");
        FixtureHtmlFetcher shrunk = savedPages().withHtml(NATIONAL_VISA_URL, permitSentence("Japans und Kanadas"));

        assertThatThrownBy(() -> scraperFor(missing).scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> scraperFor(shrunk).scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Only 2 nationalities");
    }

    @Test
    void failsWhenTheStudySectionChanges() {
        FixtureHtmlFetcher noSection =
                savedPages().withHtml(STUDY_URL, "<html><body><h2>Sprachkurs</h2><p>Text</p></body></html>");
        FixtureHtmlFetcher noConditions = savedPages()
                .withHtml(STUDY_URL, "<html><body><h2>Studium</h2><p>Siehe study-in-germany.de.</p></body></html>");

        assertThatThrownBy(() -> scraperFor(noSection).scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Section 'Studium' not found");
        assertThatThrownBy(() -> scraperFor(noConditions).scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Study requirements not found");
    }

    private static String permitSentence(String names) {
        return "<html><body><p>Staatsangehörige " + names
                + " können darüber hinaus einen erforderlichen Aufenthaltstitel auch nach der Einreise einholen.</p>"
                + "</body></html>";
    }

    private static FixtureHtmlFetcher savedPages() {
        return new FixtureHtmlFetcher()
                .withFixture(NATIONAL_VISA_URL, "deu/nationale-visa.html")
                .withFixture(STUDY_URL, "deu/studium.html");
    }

    private static Map<String, VisaRequirementContent> byOrigin(List<ScrapedVisaRequirement> results) {
        assertThat(results)
                .allSatisfy(result -> assertThat(result.destinationIso3()).isEqualTo("DEU"));
        return results.stream()
                .collect(Collectors.toMap(ScrapedVisaRequirement::originIso3, ScrapedVisaRequirement::content));
    }

    private static GermanyStudyScraper scraperFor(FixtureHtmlFetcher fetcher) {
        return new GermanyStudyScraper(fetcher, new RequirementClassifier());
    }
}
