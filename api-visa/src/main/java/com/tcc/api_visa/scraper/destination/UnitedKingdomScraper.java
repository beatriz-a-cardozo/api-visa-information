package com.tcc.api_visa.scraper.destination;

import com.tcc.api_visa.domain.Money;
import com.tcc.api_visa.domain.Requirement;
import com.tcc.api_visa.domain.RequirementType;
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
 * Reads the visa national list of the UK Immigration Rules (Appendix Visitor). Listed nationalities
 * need a Standard Visitor visa; every other visitor needs an electronic travel authorisation (ETA).
 * The details of each come from their GOV.UK pages, fetched only when some origin needs them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnitedKingdomScraper implements VisaScraper {

    static final String DESTINATION = "GBR";
    static final String VISA_NATIONAL_LIST_URL =
            "https://www.gov.uk/guidance/immigration-rules/immigration-rules-appendix-visitor-visa-national-list";
    static final String STANDARD_VISITOR_URL = "https://www.gov.uk/standard-visitor/overview";
    static final String ETA_URL = "https://www.gov.uk/eta";
    static final String ETA_APPLY_URL = "https://www.gov.uk/eta/apply";

    /** Irish citizens need neither a visa nor an ETA (Common Travel Area); the list does not say so. */
    static final Set<String> NO_ETA = Set.of("IRL");

    /** The list has over 100 nationalities; far fewer means the page changed. */
    static final int MIN_LIST_SIZE = 50;

    private static final Pattern VISA_FEE = Pattern.compile("Standard Visitor visa costs £(\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern ETA_FEE = Pattern.compile("It costs £(\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern STAY_MONTHS =
            Pattern.compile("(?:stay in the UK|Isle of Man) for up to (\\d+) months");
    private static final int DAYS_PER_MONTH = 30;

    private final HtmlFetcher fetcher;
    private final RequirementClassifier classifier;

    @Override
    public String destinationIso3() {
        return DESTINATION;
    }

    @Override
    public List<ScrapedVisaRequirement> scrape(Set<String> originIso3s) {
        Set<String> visaNationals = visaNationals(fetcher.fetch(VISA_NATIONAL_LIST_URL));

        List<String> origins = originIso3s.stream()
                .filter(origin -> !origin.equals(DESTINATION) && !NO_ETA.contains(origin))
                .sorted()
                .toList();
        VisaOption standardVisitor = origins.stream().anyMatch(visaNationals::contains) ? standardVisitor() : null;
        VisaOption eta = origins.stream().anyMatch(origin -> !visaNationals.contains(origin)) ? eta() : null;

        return origins.stream()
                .map(origin -> new ScrapedVisaRequirement(
                        origin,
                        DESTINATION,
                        visaNationals.contains(origin)
                                ? new VisaRequirementContent(
                                        VisaPolicy.VISA_REQUIRED,
                                        List.of(standardVisitor),
                                        List.of(VISA_NATIONAL_LIST_URL, STANDARD_VISITOR_URL))
                                : new VisaRequirementContent(
                                        VisaPolicy.ELECTRONIC_TRAVEL_AUTHORIZATION,
                                        List.of(eta),
                                        List.of(VISA_NATIONAL_LIST_URL, ETA_URL, ETA_APPLY_URL))))
                .toList();
    }

    /**
     * Item VN 1.1 (a) of the rules: "Nationals or citizens of the following countries", followed by
     * one country per list item ("*" marks exceptions explained further down the page).
     */
    private static Set<String> visaNationals(Document document) {
        List<Element> items = document.select("li").stream()
                .filter(item -> item.ownText().contains("Nationals or citizens of the following countries"))
                .findFirst()
                .map(item -> item.select("> ol > li"))
                .orElseThrow(() -> new ScrapingException("Visa national list not found at " + VISA_NATIONAL_LIST_URL));
        if (items.size() < MIN_LIST_SIZE) {
            throw new ScrapingException(
                    "Only " + items.size() + " nationalities in the visa national list at " + VISA_NATIONAL_LIST_URL);
        }

        Set<String> codes = new HashSet<>();
        List<String> unknown = new ArrayList<>();
        for (Element item : items) {
            CountryNames.iso3(item.text(), Locale.ENGLISH).ifPresentOrElse(codes::add, () -> unknown.add(item.text()));
        }
        if (!unknown.isEmpty()) {
            // Those nationalities would be treated as ETA; none of them is an origin in scope today.
            log.debug("Visa national list: names without an ISO code: {}", unknown);
        }
        return codes;
    }

    private VisaOption standardVisitor() {
        Document document = fetcher.fetch(STANDARD_VISITOR_URL);

        List<Element> eligibility = HtmlSections.section(
                        document, "h2", text -> text.equalsIgnoreCase("Check you meet the eligibility requirements"))
                .orElseThrow(() -> new ScrapingException(
                        "Section 'Check you meet the eligibility requirements' not found at " + STANDARD_VISITOR_URL));

        List<Requirement> requirements = new ArrayList<>();
        eligibility.stream()
                .filter(element -> element.tagName().equals("p"))
                .map(element -> HtmlSections.clean(element.text()))
                .filter(text -> text.toLowerCase(Locale.ROOT).contains("passport"))
                .forEach(text -> requirements.add(new Requirement(text, RequirementType.DOCUMENT)));
        HtmlSections.listItemsIntroducedBy(eligibility, "must").stream()
                .map(text -> new Requirement(text, classifier.classify(text)))
                .forEach(requirements::add);

        if (requirements.isEmpty()) {
            throw new ScrapingException("No requirements found at " + STANDARD_VISITOR_URL);
        }

        String pageText = HtmlSections.clean(document.text());
        return new VisaOption(
                VisaType.TOURIST,
                "Standard Visitor visa",
                stayDays(pageText),
                firstGroup(VISA_FEE, pageText)
                        .map(amount -> Money.of(amount, "GBP"))
                        .orElse(null),
                requirements,
                STANDARD_VISITOR_URL);
    }

    /** Stay from the ETA overview; fee and what you need from the "Apply" page. */
    private VisaOption eta() {
        Integer stayDays = stayDays(HtmlSections.clean(fetcher.fetch(ETA_URL).text()));
        Document apply = fetcher.fetch(ETA_APPLY_URL);

        // The first "You'll need:" list: the documents and means of payment for the application.
        List<Requirement> requirements = apply.select("p").stream()
                .filter(paragraph -> HtmlSections.clean(paragraph.text()).endsWith("need:"))
                .findFirst()
                .map(Element::nextElementSibling)
                .filter(list -> list.tagName().equals("ul"))
                .map(list -> list.select("> li").eachText())
                .orElseThrow(() -> new ScrapingException("ETA requirements not found at " + ETA_APPLY_URL))
                .stream()
                .map(HtmlSections::clean)
                .map(text -> new Requirement(text, classifier.classify(text)))
                .toList();

        return new VisaOption(
                VisaType.TOURIST,
                "Electronic travel authorisation (ETA)",
                stayDays,
                firstGroup(ETA_FEE, HtmlSections.clean(apply.text()))
                        .map(amount -> Money.of(amount, "GBP"))
                        .orElse(null),
                requirements,
                ETA_APPLY_URL);
    }

    private static Integer stayDays(String pageText) {
        return firstGroup(STAY_MONTHS, pageText)
                .map(months -> Integer.parseInt(months) * DAYS_PER_MONTH)
                .orElse(null);
    }

    private static Optional<String> firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
