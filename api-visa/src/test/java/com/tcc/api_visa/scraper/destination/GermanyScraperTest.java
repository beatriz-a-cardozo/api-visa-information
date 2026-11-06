package com.tcc.api_visa.scraper.destination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tcc.api_visa.domain.Requirement;
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

class GermanyScraperTest {

    private static final String URL = GermanyScraper.VISA_LIST_URL;

    @Test
    void readsTheAnswerOfEachOriginFromTheTable() {
        Map<String, VisaRequirementContent> results =
                byOrigin(scraperFor(new FixtureHtmlFetcher().withFixture(URL, "deu/staatenliste-visumpflicht.html"))
                        .scrape(Set.of("BRA", "JPN", "DEU", "CAN", "ZAF")));

        assertThat(results).containsOnlyKeys("BRA", "CAN", "JPN", "ZAF");
        assertThat(List.of("BRA", "CAN", "JPN"))
                .allSatisfy(origin -> assertThat(results.get(origin).policy()).isEqualTo(VisaPolicy.VISA_FREE));
        assertThat(results.get("ZAF").policy()).isEqualTo(VisaPolicy.VISA_REQUIRED);
        assertThat(results.values())
                .allSatisfy(content -> assertThat(content.sourceUrls()).containsExactly(URL));
    }

    @Test
    void visaFreeEntryCarriesTheStayAndTheNotesTheRowCites() {
        VisaOption brazil = byOrigin(
                        scraperFor(new FixtureHtmlFetcher().withFixture(URL, "deu/staatenliste-visumpflicht.html"))
                                .scrape(Set.of("BRA")))
                .get("BRA")
                .visas()
                .getFirst();

        assertThat(brazil.name()).isEqualTo("Visumfreie Einreise");
        assertThat(brazil.maxStayDays()).isEqualTo(90);
        assertThat(brazil.cost()).isNull();
        // "Nein (1, 4)": the 90-day rule and the residence permit note.
        assertThat(brazil.requirements())
                .extracting(Requirement::description)
                .satisfiesExactly(
                        first -> assertThat(first).startsWith("Inhaber von Nationalpässen der Staaten"),
                        second -> assertThat(second)
                                .startsWith("Aufenthaltstitel")
                                .endsWith("wenn keine Erwerbstätigkeit aufgenommen wird."));
    }

    @Test
    void visaRequiredRowsWithoutNotesHaveNoRequirements() {
        VisaOption southAfrica = byOrigin(
                        scraperFor(new FixtureHtmlFetcher().withFixture(URL, "deu/staatenliste-visumpflicht.html"))
                                .scrape(Set.of("ZAF")))
                .get("ZAF")
                .visas()
                .getFirst();

        assertThat(southAfrica.name()).isEqualTo("Visum für die Einreise nach Deutschland");
        assertThat(southAfrica.maxStayDays()).isNull();
        assertThat(southAfrica.requirements()).isEmpty();
    }

    @Test
    void failsWhenTheTableDisappearsOrShrinks() {
        FixtureHtmlFetcher missing = new FixtureHtmlFetcher().withHtml(URL, "<html><body><p>Neu</p></body></html>");
        FixtureHtmlFetcher shrunk = new FixtureHtmlFetcher().withHtml(URL, table(1, "Südafrika", "Ja"));

        assertThatThrownBy(() -> scraperFor(missing).scrape(Set.of("ZAF")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Visa table not found");
        assertThatThrownBy(() -> scraperFor(shrunk).scrape(Set.of("ZAF")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("rows in the visa table");
    }

    @Test
    void failsWhenACitedNoteIsMissing() {
        FixtureHtmlFetcher fetcher =
                new FixtureHtmlFetcher().withHtml(URL, table(GermanyScraper.MIN_ROWS, "Brasilien", "Nein (1, 7)"));

        assertThatThrownBy(() -> scraperFor(fetcher).scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Note 1");
    }

    /** A visa table with one real row and enough filler rows to pass the size check. */
    private static String table(int rows, String country, String answer) {
        String fillers = IntStream.range(0, rows)
                .mapToObj(i -> "<tr><td>Afghanistan</td><td>Ja</td></tr>")
                .collect(Collectors.joining());
        return "<table><tr><td>Staaten</td><td>Visumpflicht für Deutschland: Ja/Nein</td></tr><tr><td>" + country
                + "</td><td>" + answer + "</td></tr>" + fillers + "</table>";
    }

    private static Map<String, VisaRequirementContent> byOrigin(List<ScrapedVisaRequirement> results) {
        assertThat(results)
                .allSatisfy(result -> assertThat(result.destinationIso3()).isEqualTo("DEU"));
        return results.stream()
                .collect(Collectors.toMap(ScrapedVisaRequirement::originIso3, ScrapedVisaRequirement::content));
    }

    private static GermanyScraper scraperFor(FixtureHtmlFetcher fetcher) {
        return new GermanyScraper(fetcher, new RequirementClassifier());
    }
}
