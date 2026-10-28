package com.tcc.api_visa.scraper.destination;

import com.tcc.api_visa.domain.VisaOption;
import com.tcc.api_visa.domain.VisaPolicy;
import com.tcc.api_visa.domain.VisaRequirementContent;
import com.tcc.api_visa.domain.VisaType;
import com.tcc.api_visa.scraper.ScrapedVisaRequirement;
import com.tcc.api_visa.scraper.ScrapingException;
import com.tcc.api_visa.scraper.VisaScraper;
import com.tcc.api_visa.scraper.fetch.HtmlFetcher;
import com.tcc.api_visa.scraper.support.CountryNames;
import com.tcc.api_visa.scraper.support.HtmlSections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Reads the Department of Home Affairs' visa exemption list: one row per country, with the days a
 * holder of each passport type may stay without a visa. Only the "Ordinary" column matters for
 * travellers. A country that is not on the list, or has no days for ordinary passports, needs a visa.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SouthAfricaScraper implements VisaScraper {

    static final String DESTINATION = "ZAF";
    static final String EXEMPT_COUNTRIES_URL = "https://www.dha.gov.za/index.php/immigration-services/exempt-countries";

    /** The list has well over 100 rows; far fewer means the page changed. */
    static final int MIN_ROWS = 50;

    /** Most listed countries are exempt for ordinary passports; almost none means the columns were misread. */
    static final int MIN_EXEMPT = 30;

    private static final String ORDINARY = "Ordinary";
    private static final Pattern DAYS = Pattern.compile("(\\d+)\\s*Days", Pattern.CASE_INSENSITIVE);

    private final HtmlFetcher fetcher;

    @Override
    public String destinationIso3() {
        return DESTINATION;
    }

    @Override
    public List<ScrapedVisaRequirement> scrape(Set<String> originIso3s) {
        Map<String, Optional<Integer>> ordinaryDays = ordinaryDaysByCountry(fetcher.fetch(EXEMPT_COUNTRIES_URL));

        return originIso3s.stream()
                .filter(origin -> !origin.equals(DESTINATION))
                .sorted()
                .map(origin -> new ScrapedVisaRequirement(
                        origin,
                        DESTINATION,
                        ordinaryDays
                                .getOrDefault(origin, Optional.empty())
                                .map(SouthAfricaScraper::exempt)
                                .orElseGet(SouthAfricaScraper::visaRequired)))
                .toList();
    }

    private static VisaRequirementContent exempt(int days) {
        VisaOption exemption = new VisaOption(
                VisaType.TOURIST, "Visa exemption (ordinary passport)", days, null, List.of(), EXEMPT_COUNTRIES_URL);
        return new VisaRequirementContent(VisaPolicy.VISA_FREE, List.of(exemption), List.of(EXEMPT_COUNTRIES_URL));
    }

    private static VisaRequirementContent visaRequired() {
        VisaOption visa =
                new VisaOption(VisaType.TOURIST, "Port of entry visa", null, null, List.of(), EXEMPT_COUNTRIES_URL);
        return new VisaRequirementContent(VisaPolicy.VISA_REQUIRED, List.of(visa), List.of(EXEMPT_COUNTRIES_URL));
    }

    /**
     * ISO code to the visa-free days for ordinary passports; empty when the country is listed but
     * ordinary passports are not exempt. Rows for organisations (laissez-passers) are skipped.
     */
    private static Map<String, Optional<Integer>> ordinaryDaysByCountry(Document document) {
        Element table = document.select("table").stream()
                .filter(candidate -> candidate.text().contains("Passport Type"))
                .findFirst()
                .orElseThrow(() -> new ScrapingException("Exemption table not found at " + EXEMPT_COUNTRIES_URL));
        List<Element> rows = table.select("tr");
        int ordinaryColumn = ordinaryColumn(rows);
        if (rows.size() < MIN_ROWS) {
            throw new ScrapingException(
                    "Only " + rows.size() + " rows in the exemption table at " + EXEMPT_COUNTRIES_URL);
        }

        Map<String, Optional<Integer>> days = new HashMap<>();
        List<String> unknown = new ArrayList<>();
        for (Element row : rows) {
            List<String> cells = expandedCells(row);
            if (cells.size() <= ordinaryColumn || !row.select("strong").isEmpty()) {
                continue; // title and header rows
            }
            String name = cells.getFirst();
            Matcher matcher = DAYS.matcher(cells.get(ordinaryColumn));
            Optional<Integer> ordinary =
                    matcher.find() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
            CountryNames.iso3(name, Locale.ENGLISH)
                    .ifPresentOrElse(iso3 -> days.put(iso3, ordinary), () -> unknown.add(name));
        }
        if (!unknown.isEmpty()) {
            // Organisations and names without an ISO code; a country here would be treated as visa required.
            log.debug("South African exemption list: names without an ISO code: {}", unknown);
        }
        // A misread column would leave every country without days, i.e. visa required.
        long exempt = days.values().stream().filter(Optional::isPresent).count();
        if (exempt < MIN_EXEMPT) {
            throw new ScrapingException(
                    "Only " + exempt + " countries exempt for ordinary passports at " + EXEMPT_COUNTRIES_URL);
        }
        return days;
    }

    /**
     * Position of "Ordinary" in a data row. The header has the country column, then "Passport Type"
     * split into Diplomatic, Official, Service, Ordinary and Special in a second header row.
     */
    private static int ordinaryColumn(List<Element> rows) {
        for (Element row : rows) {
            List<String> labels = row.select("> td").stream()
                    .map(Element::text)
                    .map(HtmlSections::clean)
                    .toList();
            int index = labels.indexOf(ORDINARY);
            if (index >= 0) {
                return index + 1; // the country column spans both header rows, so it is not in this one
            }
        }
        throw new ScrapingException("Column '" + ORDINARY + "' not found at " + EXEMPT_COUNTRIES_URL);
    }

    /** Cell texts with each {@code colspan} repeated, so every row has the same positions. */
    private static List<String> expandedCells(Element row) {
        List<String> cells = new ArrayList<>();
        for (Element cell : row.select("> td")) {
            String text = HtmlSections.clean(cell.text());
            int span = Math.max(1, parseOrOne(cell.attr("colspan")));
            for (int i = 0; i < span; i++) {
                cells.add(text);
            }
        }
        return cells;
    }

    private static int parseOrOne(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
