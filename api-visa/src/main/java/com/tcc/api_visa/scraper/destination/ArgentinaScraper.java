package com.tcc.api_visa.scraper.destination;

import com.tcc.api_visa.domain.Requirement;
import com.tcc.api_visa.domain.VisaOption;
import com.tcc.api_visa.domain.VisaPolicy;
import com.tcc.api_visa.domain.VisaRequirementContent;
import com.tcc.api_visa.domain.VisaType;
import com.tcc.api_visa.scraper.ScrapedVisaRequirement;
import com.tcc.api_visa.scraper.ScrapingException;
import com.tcc.api_visa.scraper.VisaScraper;
import com.tcc.api_visa.scraper.fetch.HtmlFetcher;
import com.tcc.api_visa.scraper.support.HtmlSections;
import com.tcc.api_visa.scraper.support.RequirementClassifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Reads the visa regime table of Argentina's Dirección Nacional de Migraciones. Each row has the
 * country's flag, named after its ISO alpha-3 code ({@code banderas/BRA.jpg}), so no name matching
 * is needed. "REQUIERE VISA" in the ordinary passport column means a tourist visa is required; an
 * empty cell means entry without a visa. Some visa-required nationalities may use the electronic
 * travel authorization (AVE) instead.
 */
@Component
@RequiredArgsConstructor
public class ArgentinaScraper implements VisaScraper {

    static final String DESTINATION = "ARG";
    static final String VISA_REGIME_URL = "https://www.migraciones.gob.ar/accesible/indexdnm.php?visas";

    static final String ORDINARY_COLUMN = "Pasaporte Ordinario (Turista)";
    static final String AVE_COLUMN = "AVE Global Turista";

    /** The table has about 200 nationalities; far fewer means the page changed. */
    static final int MIN_ROWS = 150;

    /** About 100 nationalities need a visa; almost none means the columns were misread. */
    static final int MIN_VISA_REQUIRED = 30;

    private static final Pattern FLAG = Pattern.compile("banderas/([A-Z]{3})\\.[a-z]+$");

    private final HtmlFetcher fetcher;
    private final RequirementClassifier classifier;

    @Override
    public String destinationIso3() {
        return DESTINATION;
    }

    @Override
    public List<ScrapedVisaRequirement> scrape(Set<String> originIso3s) {
        Map<String, Row> rows = rows(fetcher.fetch(VISA_REGIME_URL));

        List<ScrapedVisaRequirement> results = new ArrayList<>();
        originIso3s.stream()
                .filter(origin -> !origin.equals(DESTINATION) && rows.containsKey(origin))
                .sorted()
                .forEach(origin -> content(rows.get(origin))
                        .ifPresent(content -> results.add(new ScrapedVisaRequirement(origin, DESTINATION, content))));
        return results;
    }

    /** Empty for values this scraper does not interpret (e.g. a travel certificate alone). */
    private Optional<VisaRequirementContent> content(Row row) {
        if (row.ordinary().isEmpty()) {
            VisaOption entry = new VisaOption(
                    VisaType.TOURIST,
                    "Ingreso sin visa (pasaporte ordinario, turismo)",
                    null,
                    null,
                    List.of(),
                    VISA_REGIME_URL);
            return Optional.of(
                    new VisaRequirementContent(VisaPolicy.VISA_FREE, List.of(entry), List.of(VISA_REGIME_URL)));
        }
        if (!row.ordinary().startsWith("REQUIERE VISA")) {
            return Optional.empty();
        }
        List<VisaOption> visas = new ArrayList<>();
        visas.add(new VisaOption(VisaType.TOURIST, "Visa de turismo", null, null, List.of(), VISA_REGIME_URL));
        if (row.aveEnabled()) {
            String condition = "Nacionalidad habilitada para la " + AVE_COLUMN;
            visas.add(new VisaOption(
                    VisaType.TOURIST,
                    "Autorización de Viaje Electrónica (AVE) en lugar de la visa",
                    null,
                    null,
                    List.of(new Requirement(condition, classifier.classify(condition))),
                    VISA_REGIME_URL));
        }
        return Optional.of(new VisaRequirementContent(VisaPolicy.VISA_REQUIRED, visas, List.of(VISA_REGIME_URL)));
    }

    private static Map<String, Row> rows(Document document) {
        Element table = document.select("table").stream()
                .filter(candidate -> candidate.select("th").text().contains(ORDINARY_COLUMN))
                .findFirst()
                .orElseThrow(() -> new ScrapingException("Visa table not found at " + VISA_REGIME_URL));
        List<String> headers = table.select("th").stream()
                .map(th -> HtmlSections.clean(th.text()))
                .toList();
        int ordinary = headers.indexOf(ORDINARY_COLUMN);
        int ave = headers.indexOf(AVE_COLUMN);
        if (ave < 0) {
            throw new ScrapingException("Column '" + AVE_COLUMN + "' not found at " + VISA_REGIME_URL);
        }

        Map<String, Row> rows = new HashMap<>();
        for (Element row : table.select("tr")) {
            Element flag = row.selectFirst("img[src]");
            List<Element> cells = row.select("> td");
            if (flag == null || cells.size() <= Math.max(ordinary, ave)) {
                continue; // letter separators
            }
            Matcher code = FLAG.matcher(flag.attr("src"));
            if (code.find()) {
                rows.put(
                        code.group(1),
                        new Row(
                                HtmlSections.clean(cells.get(ordinary).text()),
                                HtmlSections.clean(cells.get(ave).text()).equals("HABILITADO")));
            }
        }
        if (rows.size() < MIN_ROWS) {
            throw new ScrapingException(
                    "Only " + rows.size() + " nationalities in the visa table at " + VISA_REGIME_URL);
        }
        // An empty cell means "no visa", so a misread column would make every nationality visa free.
        long visaRequired = rows.values().stream()
                .filter(row -> row.ordinary().startsWith("REQUIERE VISA"))
                .count();
        if (visaRequired < MIN_VISA_REQUIRED) {
            throw new ScrapingException(
                    "Only " + visaRequired + " nationalities require a visa in the table at " + VISA_REGIME_URL);
        }
        return rows;
    }

    /**
     * @param ordinary   the ordinary passport column, e.g. "REQUIERE VISA" or empty
     * @param aveEnabled whether the nationality may use the AVE instead of a visa
     */
    private record Row(String ordinary, boolean aveEnabled) {}
}
