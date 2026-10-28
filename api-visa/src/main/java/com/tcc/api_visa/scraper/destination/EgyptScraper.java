package com.tcc.api_visa.scraper.destination;

import com.tcc.api_visa.domain.Money;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Reads the FAQ of Egypt's e-Visa portal (Ministry of Interior): the nationalities that may get an
 * e-Visa, the tourism visa fees and the entry requirements. Origins not on the list are left out:
 * the page does not say which of them are exempt and which must apply at a consulate.
 */
@Component
@RequiredArgsConstructor
public class EgyptScraper implements VisaScraper {

    static final String DESTINATION = "EGY";
    static final String FAQ_URL = "https://visa2egypt.gov.eg/eVisa/FAQ";

    /** The list has about 80 nationalities; far fewer means the page changed. */
    static final int MIN_COUNTRIES = 30;

    private static final Pattern USD = Pattern.compile("(\\d+(?:\\.\\d{1,2})?)\\s*\\$");

    private final HtmlFetcher fetcher;
    private final RequirementClassifier classifier;

    @Override
    public String destinationIso3() {
        return DESTINATION;
    }

    @Override
    public List<ScrapedVisaRequirement> scrape(Set<String> originIso3s) {
        Document document = fetcher.fetch(FAQ_URL);
        Set<String> eligible = eligibleNationalities(document);
        List<String> origins = originIso3s.stream()
                .filter(origin -> !origin.equals(DESTINATION) && eligible.contains(origin))
                .sorted()
                .toList();
        if (origins.isEmpty()) {
            return List.of();
        }

        List<Requirement> requirements = requirements(document);
        List<VisaOption> visas = feeRows(document).stream()
                .map(row -> new VisaOption(
                        VisaType.TOURIST, "e-Visa: " + row.name(), null, row.fee(), requirements, FAQ_URL))
                .toList();
        VisaRequirementContent content = new VisaRequirementContent(VisaPolicy.E_VISA, visas, List.of(FAQ_URL));
        return origins.stream()
                .map(origin -> new ScrapedVisaRequirement(origin, DESTINATION, content))
                .toList();
    }

    private static Set<String> eligibleNationalities(Document document) {
        List<String> names = document.select(".countries-grid label").eachText();
        if (names.size() < MIN_COUNTRIES) {
            throw new ScrapingException("Only " + names.size() + " nationalities in the e-Visa list at " + FAQ_URL);
        }
        Set<String> codes = new HashSet<>();
        names.forEach(name -> CountryNames.iso3(name, Locale.ENGLISH).ifPresent(codes::add));
        return codes;
    }

    /** Rows of the "Visa type / Visa fees" table, e.g. "Single entry visa (Tourism)" and "30$". */
    private static List<FeeRow> feeRows(Document document) {
        List<FeeRow> rows = new ArrayList<>();
        for (Element row : document.select("tr")) {
            List<String> cells = row.select("> td").stream()
                    .map(Element::text)
                    .map(HtmlSections::clean)
                    .toList();
            if (cells.size() != 2) {
                continue;
            }
            Matcher fee = USD.matcher(cells.get(1));
            if (fee.matches()) {
                rows.add(new FeeRow(cells.get(0), Money.of(fee.group(1), "USD")));
            }
        }
        if (rows.isEmpty()) {
            throw new ScrapingException("No visa fees found at " + FAQ_URL);
        }
        return rows;
    }

    /** The "- ..." paragraphs under the "Requirements" heading. */
    private List<Requirement> requirements(Document document) {
        Element heading = document.select("h3").stream()
                .filter(element -> HtmlSections.clean(element.text()).equalsIgnoreCase("Requirements"))
                .findFirst()
                .orElseThrow(() -> new ScrapingException("Section 'Requirements' not found at " + FAQ_URL));
        Element section = heading.nextElementSibling();
        List<Requirement> requirements = section == null
                ? List.of()
                : section.select("p").stream()
                        .map(paragraph -> HtmlSections.clean(paragraph.text()))
                        .filter(text -> text.startsWith("-"))
                        .map(text -> text.substring(1).strip())
                        .map(text -> new Requirement(text, classifier.classify(text)))
                        .toList();
        if (requirements.isEmpty()) {
            throw new ScrapingException("No requirements found at " + FAQ_URL);
        }
        return requirements;
    }

    private record FeeRow(String name, Money fee) {}
}
