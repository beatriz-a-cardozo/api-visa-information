package com.tcc.api_visa.scraper.destination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class ArgentinaScraperTest {

    private static final String URL = ArgentinaScraper.VISA_REGIME_URL;

    @Test
    void anEmptyOrdinaryPassportCellMeansEntryWithoutAVisa() {
        Map<String, VisaRequirementContent> results = scrape(Set.of("BRA", "JPN", "DEU", "CAN", "ZAF", "ARG"));

        assertThat(results).containsOnlyKeys("BRA", "CAN", "DEU", "JPN", "ZAF");
        assertThat(results.values()).allSatisfy(content -> {
            assertThat(content.policy()).isEqualTo(VisaPolicy.VISA_FREE);
            assertThat(content.sourceUrls()).containsExactly(URL);
            assertThat(content.visas().getFirst().name()).isEqualTo("Ingreso sin visa (pasaporte ordinario, turismo)");
        });
    }

    @Test
    void visaRequiredNationalitiesGetTheAveWhenEnabled() {
        Map<String, VisaRequirementContent> results = scrape(Set.of("EGY", "ALB", "CHN"));

        assertThat(results.get("EGY").policy()).isEqualTo(VisaPolicy.VISA_REQUIRED);
        assertThat(results.get("EGY").visas()).extracting(VisaOption::name).containsExactly("Visa de turismo");

        // Albania: visa required, AVE enabled.
        assertThat(results.get("ALB").policy()).isEqualTo(VisaPolicy.VISA_REQUIRED);
        assertThat(results.get("ALB").visas())
                .extracting(VisaOption::name)
                .containsExactly("Visa de turismo", "Autorización de Viaje Electrónica (AVE) en lugar de la visa");

        // China: "HABILITADO" only in the US visa / green card columns, which this scraper does not read.
        assertThat(results.get("CHN").visas()).extracting(VisaOption::name).containsExactly("Visa de turismo");
    }

    @Test
    void failsWhenTheTableDisappearsOrShrinks() {
        String shrunk = table(1);

        assertThatThrownBy(() -> scraperFor("<html><body><p>Mantenimiento</p></body></html>")
                        .scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Visa table not found");
        assertThatThrownBy(() -> scraperFor(shrunk).scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("Only 1 nationalities");
    }

    @Test
    void ignoresValuesItDoesNotUnderstand() {
        // Only Afghanistan's row (the first) gets the value.
        String html = table(ArgentinaScraper.MIN_ROWS).replaceFirst("REQUIERE VISA", "CERTIFICADO VIAJE");

        assertThat(scraperFor(html).scrape(Set.of("AFG"))).isEmpty();
    }

    @Test
    void failsWhenAlmostNoNationalityNeedsAVisa() {
        // What a moved ordinary passport column would look like: every cell empty, i.e. "no visa".
        String misread = table(ArgentinaScraper.MIN_ROWS).replace("REQUIERE VISA", "");

        assertThatThrownBy(() -> scraperFor(misread).scrape(Set.of("BRA")))
                .isInstanceOf(ScrapingException.class)
                .hasMessageContaining("require a visa");
    }

    private static Map<String, VisaRequirementContent> scrape(Set<String> origins) {
        List<ScrapedVisaRequirement> results = new ArgentinaScraper(
                        new FixtureHtmlFetcher().withFixture(URL, "arg/regimen-de-visas.html"),
                        new RequirementClassifier())
                .scrape(origins);
        assertThat(results)
                .allSatisfy(result -> assertThat(result.destinationIso3()).isEqualTo("ARG"));
        return results.stream()
                .collect(Collectors.toMap(ScrapedVisaRequirement::originIso3, ScrapedVisaRequirement::content));
    }

    private static ArgentinaScraper scraperFor(String html) {
        return new ArgentinaScraper(new FixtureHtmlFetcher().withHtml(URL, html), new RequirementClassifier());
    }

    /** The page's header and {@code rows} rows for Afghanistan, all with distinct flag codes. */
    private static String table(int rows) {
        String body = IntStream.range(0, rows)
                .mapToObj(i -> "<tr><td><img src=\"imagenes/banderas/" + (i == 0 ? "AFG" : code(i))
                        + ".jpg\"></td><td>X</td><td>REQUIERE VISA</td><td></td><td></td><td></td><td></td></tr>")
                .collect(Collectors.joining());
        return "<table><thead><th></th><th></th><th>Pasaporte Ordinario (Turista)</th><th>AVE Global Turista</th>"
                + "<th>Otro pasaporte (Diplomático/Servicio)</th><th>Visa EEUU</th><th>Green CARD (EEUU)</th></thead>"
                + "<tbody>" + body + "</tbody></table>";
    }

    /** "QAB", "QAC"...: codes starting with Q, so none collides with AFG. */
    private static String code(int i) {
        return "Q" + (char) ('A' + i / 26 % 26) + (char) ('A' + i % 26);
    }
}
