package com.tcc.api_visa.scraper.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CountryNamesTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Brazil|BRA",
                "Brazil (Some citizens of Brazil may be eligible for an eTA if they .)|BRA",
                "South Africa|ZAF",
                "People’s Republic of China*|CHN",
                "China, People's Republic of|CHN",
                "Republic of Korea|KOR",
                "Korea (North)|PRK",
                "Korea, North|PRK",
                "Côte d’Ivoire (formerly Ivory Coast)|CIV",
                "Congo|COG",
                "Congo, Democratic Republic of|COD",
                "Cameroon, Republic of|CMR",
                "St. Lucia|LCA",
                "Trinidad and Tobago|TTO",
                "Türkiye|TUR"
            })
    void resolvesEnglishNames(String name, String iso3) {
        assertThat(CountryNames.iso3(name, Locale.ENGLISH)).hasValue(iso3);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Brasilien|BRA",
                "Südafrika|ZAF",
                "Kanada|CAN",
                "Japan|JPN",
                "Korea (Republik Korea, Südkorea)|KOR",
                "Kongo (Demokratische Republik)|COD",
                "Vereinigtes Königreich Großbritannien und Nordirland|GBR"
            })
    void resolvesGermanNames(String name, String iso3) {
        assertThat(CountryNames.iso3(name, Locale.GERMAN)).hasValue(iso3);
    }

    @Test
    void doesNotGuessUnknownNames() {
        assertThat(CountryNames.iso3("Korea", Locale.ENGLISH)).isEmpty();
        assertThat(CountryNames.iso3("Atlantis", Locale.ENGLISH)).isEmpty();
        assertThat(CountryNames.iso3("Brasilien", Locale.ENGLISH)).isEmpty();
    }

    /**
     * Every nationality on the saved official lists is recognized, except Kosovo (no ISO 3166 code)
     * and Canada's British categories other than "British citizen". An unrecognized name on the UK
     * list would be treated as an ETA country, so this guards real data.
     */
    @Test
    void recognizesEveryCountryOnTheSavedLists() throws IOException {
        List<String> uk = fixture("gbr/visa-national-list.html").select("li").stream()
                .filter(item -> item.ownText().contains("Nationals or citizens of the following countries"))
                .findFirst()
                .orElseThrow()
                .select("> ol > li")
                .eachText();
        List<String> canada = fixture("can/entry-requirements-country.html")
                .select("details > summary:matchesOwn(countries) + ul > li")
                .stream()
                .map(Element::ownText)
                .filter(name -> !name.startsWith("British") || name.equals("British citizen"))
                .toList();
        List<String> germany = fixture("deu/staatenliste-visumpflicht.html").select("table tr").stream()
                .skip(1)
                .map(row -> row.select("> td").first())
                .filter(cell -> cell != null)
                .map(Element::text)
                .toList();

        assertThat(uk).hasSizeGreaterThan(100);
        assertThat(canada).hasSizeGreaterThan(200);
        assertThat(germany).hasSizeGreaterThan(190);
        assertThat(uk)
                .filteredOn(name -> CountryNames.iso3(name, Locale.ENGLISH).isEmpty())
                .containsExactly("Kosovo");
        assertThat(canada)
                .filteredOn(name -> CountryNames.iso3(name, Locale.ENGLISH).isEmpty())
                .containsExactly("Kosovo");
        assertThat(germany)
                .filteredOn(name -> CountryNames.iso3(name, Locale.GERMAN).isEmpty())
                .containsExactly("Kosovo");
    }

    private Document fixture(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/" + path)) {
            return Jsoup.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
