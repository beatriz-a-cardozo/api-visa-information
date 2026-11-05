package com.tcc.api_visa.scraper.destination;

import com.tcc.api_visa.domain.Requirement;
import com.tcc.api_visa.domain.TravelPurpose;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Reads the Federal Foreign Office's pages on stays over 90 days. Studying needs a national visa
 * (type D) applied for before travelling, except for the nationalities the "nationale Visa" page
 * lists as allowed to request the residence permit after entering Germany. The requirements come
 * from the "Studium" section of the study page, in German, as published.
 */
@Component
@RequiredArgsConstructor
public class GermanyStudyScraper implements VisaScraper {

    static final String DESTINATION = "DEU";
    static final String NATIONAL_VISA_URL =
            "https://www.auswaertiges-amt.de/de/service/visa-und-aufenthalt/nationale-visa";
    static final String STUDY_URL =
            "https://www.auswaertiges-amt.de/de/service/visa-und-aufenthalt/schulbesuch-studium-sprachkurs-in-deutschland-2230586";

    /** The page lists eight nationalities today; fewer than this means the sentence changed. */
    static final int MIN_LIST_SIZE = 5;

    /**
     * The list is a German sentence with names in the genitive ("Staatsangehörige Australiens,
     * Israels, ..."), which country-name lookup does not cover. A name missing here fails the
     * collection, so a country added to the page is noticed instead of being treated as visa required.
     */
    static final Map<String, String> GENITIVE_NAMES = new LinkedHashMap<>();

    static {
        // Longest first: "Vereinigten Staaten" must not be read as part of another name.
        GENITIVE_NAMES.put("Vereinigten Königreichs Großbritannien und Nordirland", "GBR");
        GENITIVE_NAMES.put("Vereinigten Staaten von Amerika", "USA");
        GENITIVE_NAMES.put("Republik Korea", "KOR");
        GENITIVE_NAMES.put("Australiens", "AUS");
        GENITIVE_NAMES.put("Neuseelands", "NZL");
        GENITIVE_NAMES.put("Israels", "ISR");
        GENITIVE_NAMES.put("Japans", "JPN");
        GENITIVE_NAMES.put("Kanadas", "CAN");
    }

    private static final Pattern PERMIT_AFTER_ENTRY_SENTENCE = Pattern.compile(
            "Staatsangehörige (.+?) können darüber hinaus einen erforderlichen Aufenthaltstitel auch nach der Einreise");

    /** Words left between the names once they are removed: separators and articles. */
    private static final Set<String> FILLER = Set.of("und", "der", "des");

    private static final Pattern REQUIREMENTS_SENTENCE = Pattern.compile("Dazu müssen sie (.+?)\\.");

    private final HtmlFetcher fetcher;
    private final RequirementClassifier classifier;

    @Override
    public String destinationIso3() {
        return DESTINATION;
    }

    @Override
    public TravelPurpose purpose() {
        return TravelPurpose.STUDY;
    }

    @Override
    public List<ScrapedVisaRequirement> scrape(Set<String> originIso3s) {
        List<String> origins = originIso3s.stream()
                .filter(origin -> !origin.equals(DESTINATION))
                .sorted()
                .toList();
        if (origins.isEmpty()) {
            return List.of();
        }

        Set<String> permitAfterEntry = permitAfterEntry(fetcher.fetch(NATIONAL_VISA_URL));
        List<Requirement> requirements = requirements(fetcher.fetch(STUDY_URL));
        List<String> sources = List.of(NATIONAL_VISA_URL, STUDY_URL);

        return origins.stream()
                .map(origin -> new ScrapedVisaRequirement(
                        origin,
                        DESTINATION,
                        TravelPurpose.STUDY,
                        permitAfterEntry.contains(origin)
                                ? new VisaRequirementContent(
                                        VisaPolicy.PERMIT_AFTER_ENTRY,
                                        List.of(new VisaOption(
                                                VisaType.STUDENT,
                                                "Aufenthaltserlaubnis zum Studium nach der Einreise",
                                                null,
                                                null,
                                                requirements,
                                                NATIONAL_VISA_URL)),
                                        sources)
                                : new VisaRequirementContent(
                                        VisaPolicy.VISA_REQUIRED,
                                        List.of(new VisaOption(
                                                VisaType.STUDENT,
                                                "Nationales Visum (D) zum Studium",
                                                null,
                                                null,
                                                requirements,
                                                STUDY_URL)),
                                        sources)))
                .toList();
    }

    /** Paragraph by paragraph: the intro above it also mentions "Staatsangehörige der Schweiz". */
    private static Set<String> permitAfterEntry(Document document) {
        String remaining = document.select("p").stream()
                .map(paragraph -> PERMIT_AFTER_ENTRY_SENTENCE.matcher(HtmlSections.clean(paragraph.text())))
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .findFirst()
                .orElseThrow(() -> new ScrapingException(
                        "List of nationalities that may apply after entry not found at " + NATIONAL_VISA_URL));
        Set<String> codes = new HashSet<>();
        for (Map.Entry<String, String> name : GENITIVE_NAMES.entrySet()) {
            if (remaining.contains(name.getKey())) {
                codes.add(name.getValue());
                remaining = remaining.replace(name.getKey(), " ");
            }
        }
        List<String> unknown = Arrays.stream(remaining.split("[,\\s]+"))
                .filter(word -> !word.isEmpty() && !FILLER.contains(word))
                .toList();
        if (!unknown.isEmpty()) {
            throw new ScrapingException("Unknown nationalities " + unknown + " at " + NATIONAL_VISA_URL);
        }
        if (codes.size() < MIN_LIST_SIZE) {
            throw new ScrapingException(
                    "Only " + codes.size() + " nationalities may apply after entry at " + NATIONAL_VISA_URL);
        }
        return codes;
    }

    /**
     * "Dazu müssen sie sich ... bewerben und über ausreichende Mittel ... verfügen.": each condition
     * joined by "und" becomes one requirement.
     */
    private List<Requirement> requirements(Document document) {
        String section = HtmlSections.section(document, "h2", "Studium"::equals)
                .map(elements -> elements.stream()
                        .map(element -> HtmlSections.clean(element.text()))
                        .reduce("", (left, right) -> left + " " + right))
                .orElseThrow(() -> new ScrapingException("Section 'Studium' not found at " + STUDY_URL));
        Matcher matcher = REQUIREMENTS_SENTENCE.matcher(section);
        if (!matcher.find()) {
            throw new ScrapingException("Study requirements not found at " + STUDY_URL);
        }
        List<Requirement> requirements = Arrays.stream(matcher.group(1).split(" und "))
                .map(String::strip)
                .map(text -> new Requirement(text, classifier.classify(text)))
                .toList();
        if (requirements.size() < 2) {
            throw new ScrapingException("Only one study requirement found at " + STUDY_URL);
        }
        return requirements;
    }
}
