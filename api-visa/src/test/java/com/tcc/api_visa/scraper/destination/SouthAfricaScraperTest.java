package com.tcc.api_visa.scraper.destination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tcc.api_visa.domain.VisaOption;
import com.tcc.api_visa.domain.VisaPolicy;
import com.tcc.api_visa.domain.VisaRequirementContent;
import com.tcc.api_visa.scraper.ScrapedVisaRequirement;
import com.tcc.api_visa.scraper.ScrapingException;
import com.tcc.api_visa.support.FixtureHtmlFetcher;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SouthAfricaScraperTest {

    private static final String URL = SouthAfricaScraper.EXEMPT_COUNTRIES_URL;

    @Test
    void ordinaryPassportsOnTheListAreExemptForTheirDays() {
        Map<String, VisaRequirementContent> results = scrape(Set.of("BRA", "JPN", "DEU", "CAN", "ZAF"));

        assertThat(results).containsOnlyKeys("BRA", "CAN", "DEU", "JPN");
        assertThat(results.values()).allSatisfy(content -> {
            assertThat(content.policy()).isEqualTo(VisaPolicy.VISA_FREE);
            assertThat(content.sourceUrls()).containsExactly(URL);
            VisaOption exemption = content.visas().getFirst();
            assertThat(exemption.name()).isEqualTo("Visa exemption (ordinary passport)");
            assertThat(exemption.maxStayDays()).isEqualTo(90);
        });
    }

    @Test
    void listedCountriesWithoutAnOrdinaryExemptionNeedAVisa() {
        // Egypt is listed, but only diplomatic, official and service passports are exempt.
        VisaRequirementContent egypt = scrape(Set.of("EGY")).get("EGY");

        assertThat(egypt.policy()).isEqualTo(VisaPolicy.VISA_REQUIRED);
        assertThat(egypt.visas().getFirst().name()).isEqualTo("Port of entry visa");
    }

    @Test
    void countriesMissingFromTheListNeedAVisa() {
        String html =
                table(SouthAfricaScraper.MIN_ROWS, "<tr><td>Brazil</td><td colspan=\"5\">90 Days</td><td>No</td></tr>");

        Map<String, VisaRequirementContent> results = byOrigin(
                new SouthAfricaScraper(new FixtureHtmlFetcher().withHtml(URL, html)).scrape(Set.of("BRA", "JPN")));

        // A colspan over all passport types counts for ordinary passports too.
        assertThat(results.get("BRA").policy()).isEqualTo(VisaPolicy.VISA_FREE);
        assertThat(results.get("BRA").visas().getFirst().maxStayDays()).isEqualTo(90);
        assertThat(results.get("JPN").policy()).isEqualTo(VisaPolicy.VISA_REQUIRED);
    }

    @Test
    void failsWhenTheTableDisappearsOrShrinks() {
        SouthAfricaScraper missing = new SouthAfricaScraper(
                new FixtureHtmlFetcher().withHtml(URL, "<html><body><p>Moved</p></body></html>"));
        SouthAfricaScraper shrunk = new SouthAfricaScraper(new FixtureHtmlFetcher().withHtml(URL, table(1, "")));

        // Without these checks every origin would silently become "visa required".
        assertThatThrownBy(() -> missing.scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Exemption table not found");
        assertThatThrownBy(() -> shrunk.scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("rows in the exemption table");
    }

    @Test
    void failsWhenAlmostNoCountryIsExempt() {
        // What a moved "Ordinary" column would look like: every country without days.
        SouthAfricaScraper misread = new SouthAfricaScraper(
                new FixtureHtmlFetcher().withHtml(URL, table(SouthAfricaScraper.MIN_ROWS, "", "")));

        assertThatThrownBy(() -> misread.scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("countries exempt for ordinary passports");
    }

    private static Map<String, VisaRequirementContent> scrape(Set<String> origins) {
        return byOrigin(new SouthAfricaScraper(new FixtureHtmlFetcher().withFixture(URL, "zaf/exempt-countries.html"))
                .scrape(origins));
    }

    /**
     * The page's two header rows, then {@code row} and {@code fillers} rows for other countries
     * (never Brazil or Japan), exempt for 90 days with an ordinary passport.
     */
    private static String table(int fillers, String row) {
        return table(fillers, row, "90 Days");
    }

    private static String table(int fillers, String row, String fillerOrdinary) {
        String filler = Stream.of(Locale.getISOCountries())
                .filter(code -> !code.equals("BR") && !code.equals("JP"))
                .limit(fillers)
                .map(code -> "<tr><td>" + Locale.of("", code).getDisplayCountry(Locale.ENGLISH)
                        + "</td><td></td><td></td><td></td><td>" + fillerOrdinary + "</td><td></td><td>No</td></tr>")
                .collect(Collectors.joining());
        return """
                <table>
                <tr><td rowspan="2"><strong>COUNTRY NAME/ORGANISATIONS</strong></td>
                    <td colspan="5"><strong>Passport Type</strong></td><td rowspan="2"><strong>Visa Fees</strong></td></tr>
                <tr><td><strong>Diplomatic</strong></td><td><strong>Official</strong></td><td><strong>Service</strong></td>
                    <td><strong>Ordinary</strong></td><td><strong>Special</strong></td></tr>
                """ + row + filler + "</table>";
    }

    private static Map<String, VisaRequirementContent> byOrigin(List<ScrapedVisaRequirement> results) {
        assertThat(results)
                .allSatisfy(result -> assertThat(result.destinationIso3()).isEqualTo("ZAF"));
        return results.stream()
                .collect(Collectors.toMap(ScrapedVisaRequirement::originIso3, ScrapedVisaRequirement::content));
    }
}
