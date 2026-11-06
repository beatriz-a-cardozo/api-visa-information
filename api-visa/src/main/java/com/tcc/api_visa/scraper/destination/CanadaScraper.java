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
 * Reads IRCC's "What you need to enter Canada" page, which lists the nationalities that need a
 * visitor visa, those that need an eTA, and the visa-required ones that may get an eTA instead when
 * flying. The visa and eTA details come from their own pages, fetched only when some origin needs them.
 */
@Component
@RequiredArgsConstructor
public class CanadaScraper implements VisaScraper {

    static final String DESTINATION = "CAN";
    static final String ENTRY_REQUIREMENTS_URL =
            "https://www.canada.ca/en/immigration-refugees-citizenship/services/visit-canada/entry-requirements-country.html";
    static final String VISITOR_ELIGIBILITY_URL =
            "https://www.canada.ca/en/immigration-refugees-citizenship/services/visit-canada/eligibility.html";
    static final String ETA_URL =
            "https://www.canada.ca/en/immigration-refugees-citizenship/services/visit-canada/eta.html";

    static final String VISA_LIST = "Visa-required countries or territories";
    static final String ETA_LIST = "eTA-required countries or territories";
    static final String ETA_INSTEAD_OF_VISA_LIST = "Eligible visa-required countries";

    /** Far fewer entries than this means the page changed, not that most countries stopped needing a visa. */
    static final int MIN_LIST_SIZE = 20;

    private static final Pattern ETA_FEE = Pattern.compile("costs CAN\\$(\\d+(?:\\.\\d{2})?)");

    private final HtmlFetcher fetcher;
    private final RequirementClassifier classifier;

    @Override
    public String destinationIso3() {
        return DESTINATION;
    }

    @Override
    public List<ScrapedVisaRequirement> scrape(Set<String> originIso3s) {
        Document entry = fetcher.fetch(ENTRY_REQUIREMENTS_URL);
        Element visaList = list(entry, VISA_LIST);
        Element etaList = list(entry, ETA_LIST);
        Element etaInsteadOfVisaList = list(entry, ETA_INSTEAD_OF_VISA_LIST);
        Set<String> visaRequired = countries(visaList, MIN_LIST_SIZE);
        Set<String> etaRequired = countries(etaList, MIN_LIST_SIZE);
        Set<String> etaInsteadOfVisa = countries(etaInsteadOfVisaList, 1);

        List<String> origins = originIso3s.stream()
                .filter(origin -> !origin.equals(DESTINATION))
                .filter(origin -> visaRequired.contains(origin) || etaRequired.contains(origin))
                .sorted()
                .toList();
        boolean anyVisa = origins.stream().anyMatch(visaRequired::contains);
        boolean anyEta = origins.stream()
                .anyMatch(origin -> !visaRequired.contains(origin) || etaInsteadOfVisa.contains(origin));

        VisaOption visitorVisa = anyVisa ? visitorVisa() : null;
        VisaOption eta = anyEta ? eta() : null;
        VisaOption etaForEligible = eta == null ? null : insteadOfVisa(eta, conditions(etaInsteadOfVisaList));

        List<ScrapedVisaRequirement> results = new ArrayList<>();
        for (String origin : origins) {
            VisaRequirementContent content;
            if (visaRequired.contains(origin)) {
                List<VisaOption> visas = new ArrayList<>(List.of(visitorVisa));
                List<String> sources = new ArrayList<>(List.of(ENTRY_REQUIREMENTS_URL, VISITOR_ELIGIBILITY_URL));
                if (etaInsteadOfVisa.contains(origin)) {
                    visas.add(etaForEligible);
                    sources.add(ETA_URL);
                }
                content = new VisaRequirementContent(VisaPolicy.VISA_REQUIRED, visas, sources);
            } else {
                content = new VisaRequirementContent(
                        VisaPolicy.ELECTRONIC_TRAVEL_AUTHORIZATION,
                        List.of(eta),
                        List.of(ENTRY_REQUIREMENTS_URL, ETA_URL));
            }
            results.add(new ScrapedVisaRequirement(origin, DESTINATION, content));
        }
        return results;
    }

    private VisaOption visitorVisa() {
        Document document = fetcher.fetch(VISITOR_ELIGIBILITY_URL);

        List<Requirement> requirements = HtmlSections.section(
                        document, "h2", text -> text.equalsIgnoreCase("Who can get a visa"))
                .map(section -> HtmlSections.listItemsIntroducedBy(section, "must"))
                .orElseThrow(() ->
                        new ScrapingException("Section 'Who can get a visa' not found at " + VISITOR_ELIGIBILITY_URL))
                .stream()
                .map(text -> new Requirement(text, classifier.classify(text)))
                .toList();

        if (requirements.isEmpty()) {
            throw new ScrapingException("No requirements found at " + VISITOR_ELIGIBILITY_URL);
        }
        return new VisaOption(
                VisaType.TOURIST,
                "Visitor visa (temporary resident visa)",
                null,
                null,
                requirements,
                VISITOR_ELIGIBILITY_URL);
    }

    private VisaOption eta() {
        Document document = fetcher.fetch(ETA_URL);

        List<Requirement> requirements = document.select("p").stream()
                .map(paragraph -> HtmlSections.clean(paragraph.text()))
                .filter(text -> text.toLowerCase(Locale.ROOT).contains("you must have"))
                .map(text -> new Requirement(text, classifier.classify(text)))
                .toList();
        if (requirements.isEmpty()) {
            throw new ScrapingException("No eTA requirements found at " + ETA_URL);
        }

        Matcher fee = ETA_FEE.matcher(HtmlSections.clean(document.text()));
        return new VisaOption(
                VisaType.TOURIST,
                "Electronic travel authorization (eTA)",
                null,
                fee.find() ? Money.of(fee.group(1), "CAD") : null,
                requirements,
                ETA_URL);
    }

    /** The eTA for visa-required travellers who qualify: the same eTA, with the conditions first. */
    private VisaOption insteadOfVisa(VisaOption eta, String conditions) {
        List<Requirement> requirements = new ArrayList<>();
        requirements.add(new Requirement(conditions, classifier.classify(conditions)));
        requirements.addAll(eta.requirements());
        return new VisaOption(
                eta.type(),
                "Electronic travel authorization (eTA) instead of a visa, when flying",
                eta.maxStayDays(),
                eta.cost(),
                requirements,
                eta.sourceUrl());
    }

    /** The {@code <ul>} inside the {@code <details>} whose summary is {@code summary}. */
    private static Element list(Document document, String summary) {
        return document.select("details > summary").stream()
                .filter(element -> HtmlSections.clean(element.text()).equalsIgnoreCase(summary))
                .findFirst()
                .map(element -> element.parent().selectFirst("> ul"))
                .orElseThrow(
                        () -> new ScrapingException("List '" + summary + "' not found at " + ENTRY_REQUIREMENTS_URL));
    }

    /**
     * ISO codes of the list's countries. Each item starts with the name, sometimes followed by a note
     * in parentheses; items that are not a country (territory citizens, special passports) are ignored.
     */
    private static Set<String> countries(Element list, int minSize) {
        List<Element> items = list.select("> li");
        if (items.size() < minSize) {
            throw new ScrapingException("Only " + items.size() + " countries in a list at " + ENTRY_REQUIREMENTS_URL);
        }
        Set<String> codes = new HashSet<>();
        items.forEach(item -> CountryNames.iso3(item.ownText(), Locale.ENGLISH).ifPresent(codes::add));
        return codes;
    }

    /**
     * The sentence before the list and its bullet points, e.g. "... may be eligible to apply for an
     * eTA, instead of a visa, if you meet certain requirements and are travelling by air".
     */
    private static String conditions(Element list) {
        List<String> parts = new ArrayList<>();
        Element details = list.parent();
        for (Element previous = details.previousElementSibling();
                previous != null && !previous.tagName().matches("h[1-6]");
                previous = previous.previousElementSibling()) {
            if (previous.tagName().equals("ul")) {
                parts.addFirst(String.join(" and ", previous.select("> li").eachText()));
            } else if (previous.tagName().equals("p")) {
                parts.addFirst(previous.text());
            }
        }
        if (parts.isEmpty()) {
            throw new ScrapingException("Conditions for '" + ETA_INSTEAD_OF_VISA_LIST + "' not found");
        }
        return HtmlSections.clean(String.join(" ", parts));
    }
}
