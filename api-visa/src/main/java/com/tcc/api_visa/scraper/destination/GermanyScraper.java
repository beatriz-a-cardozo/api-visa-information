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
import com.tcc.api_visa.scraper.support.CountryNames;
import com.tcc.api_visa.scraper.support.HtmlSections;
import com.tcc.api_visa.scraper.support.RequirementClassifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Reads the Federal Foreign Office's list of visa requirements for entering Germany: a table with
 * one row per country ("Ja" = visa required, "Nein" = visa free) and numbered notes. The notes a
 * row cites become its requirements, in German, as published.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GermanyScraper implements VisaScraper {

    static final String DESTINATION = "DEU";
    static final String VISA_LIST_URL =
            "https://www.auswaertiges-amt.de/de/service/visa-und-aufenthalt/staatenliste-zur-visumpflicht-207820";

    /** The table has close to 200 rows; far fewer means the page changed. */
    static final int MIN_ROWS = 100;

    private static final Locale GERMAN = Locale.GERMAN;
    private static final Pattern NOTE = Pattern.compile("^(\\d+)\\)\\s*(.+)$");
    private static final Pattern NOTE_REFERENCES = Pattern.compile("\\(([\\d,\\s]+)\\)");
    private static final Pattern VISA_FREE_STAY = Pattern.compile("nicht länger als (\\d+) Tage");

    private final HtmlFetcher fetcher;
    private final RequirementClassifier classifier;

    @Override
    public String destinationIso3() {
        return DESTINATION;
    }

    @Override
    public List<ScrapedVisaRequirement> scrape(Set<String> originIso3s) {
        Document document = fetcher.fetch(VISA_LIST_URL);
        Map<String, String> answers = answersByCountry(table(document));
        Map<Integer, String> notes = notes(document);
        Integer visaFreeStayDays = visaFreeStayDays(notes);

        List<ScrapedVisaRequirement> results = new ArrayList<>();
        originIso3s.stream()
                .filter(origin -> !origin.equals(DESTINATION) && answers.containsKey(origin))
                .sorted()
                .forEach(origin -> {
                    String answer = answers.get(origin);
                    List<Requirement> requirements = requirements(answer, notes);
                    VisaRequirementContent content = answer.startsWith("Nein")
                            ? new VisaRequirementContent(
                                    VisaPolicy.VISA_FREE,
                                    List.of(new VisaOption(
                                            VisaType.TOURIST,
                                            "Visumfreie Einreise",
                                            visaFreeStayDays,
                                            null,
                                            requirements,
                                            VISA_LIST_URL)),
                                    List.of(VISA_LIST_URL))
                            : new VisaRequirementContent(
                                    VisaPolicy.VISA_REQUIRED,
                                    List.of(new VisaOption(
                                            VisaType.TOURIST,
                                            "Visum für die Einreise nach Deutschland",
                                            null,
                                            null,
                                            requirements,
                                            VISA_LIST_URL)),
                                    List.of(VISA_LIST_URL));
                    results.add(new ScrapedVisaRequirement(origin, DESTINATION, content));
                });
        return results;
    }

    /** The table whose header row asks "Visumpflicht für Deutschland: Ja/Nein". */
    private static Element table(Document document) {
        return document.select("table").stream()
                .filter(table -> {
                    Element header = table.selectFirst("tr");
                    return header != null && header.text().contains("Visumpflicht");
                })
                .findFirst()
                .orElseThrow(() -> new ScrapingException("Visa table not found at " + VISA_LIST_URL));
    }

    /** ISO code to the answer cell, e.g. {@code "Nein (1, 4)"}. Rows that are not countries are skipped. */
    private static Map<String, String> answersByCountry(Element table) {
        List<Element> rows = table.select("tr");
        if (rows.size() < MIN_ROWS) {
            throw new ScrapingException("Only " + rows.size() + " rows in the visa table at " + VISA_LIST_URL);
        }
        Map<String, String> answers = new HashMap<>();
        List<String> unknown = new ArrayList<>();
        for (Element row : rows.subList(1, rows.size())) {
            List<Element> cells = row.select("> td");
            if (cells.size() < 2) {
                continue;
            }
            String name = HtmlSections.clean(cells.get(0).text());
            String answer = HtmlSections.clean(cells.get(1).text());
            if (!answer.startsWith("Ja") && !answer.startsWith("Nein")) {
                continue;
            }
            CountryNames.iso3(name, GERMAN).ifPresentOrElse(iso3 -> answers.put(iso3, answer), () -> unknown.add(name));
        }
        if (!unknown.isEmpty()) {
            log.debug("German visa table: names without an ISO code: {}", unknown);
        }
        return answers;
    }

    /** Numbered notes below the table, e.g. "1) Inhaber von Nationalpässen ...". Deleted notes are left out. */
    private static Map<Integer, String> notes(Document document) {
        Map<Integer, String> notes = new HashMap<>();
        for (Element paragraph : document.select("p")) {
            Matcher matcher = NOTE.matcher(HtmlSections.clean(paragraph.text()));
            if (matcher.matches() && !matcher.group(2).startsWith("Entfällt")) {
                notes.put(Integer.parseInt(matcher.group(1)), matcher.group(2));
            }
        }
        return notes;
    }

    private List<Requirement> requirements(String answer, Map<Integer, String> notes) {
        Matcher references = NOTE_REFERENCES.matcher(answer);
        if (!references.find()) {
            return List.of();
        }
        List<Requirement> requirements = new ArrayList<>();
        for (String number : references.group(1).split(",")) {
            String note = notes.get(Integer.parseInt(number.strip()));
            if (note == null) {
                throw new ScrapingException(
                        "Note " + number.strip() + " cited in the table not found at " + VISA_LIST_URL);
            }
            requirements.add(new Requirement(note, classifier.classify(note)));
        }
        return requirements;
    }

    /** Note 1 sets the visa-free stay for every "Nein" row (90 days in any 180). */
    private static Integer visaFreeStayDays(Map<Integer, String> notes) {
        String note = notes.get(1);
        Matcher matcher = note == null ? null : VISA_FREE_STAY.matcher(note);
        return matcher != null && matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }
}
