package com.tcc.api_visa.scraper.destination;

import com.tcc.api_visa.domain.Money;
import com.tcc.api_visa.domain.Requirement;
import com.tcc.api_visa.domain.RequirementType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Reads the GOV.UK guide to the Student visa. The rules are the same for every nationality in
 * scope, so each origin gets the same option: eligibility from the overview, the money needed to
 * live in the UK and the documents every applicant must provide.
 */
@Component
@RequiredArgsConstructor
public class UnitedKingdomStudyScraper implements VisaScraper {

    static final String DESTINATION = "GBR";
    static final String OVERVIEW_URL = "https://www.gov.uk/student-visa";
    static final String MONEY_URL = "https://www.gov.uk/student-visa/money";
    static final String DOCUMENTS_URL = "https://www.gov.uk/student-visa/documents-you-must-provide";

    /** Irish citizens can study in the UK without a visa (Common Travel Area). */
    static final Set<String> NO_VISA = Set.of("IRL");

    /** The overview lists four conditions and the documents page two mandatory documents. */
    static final int MIN_ELIGIBILITY = 3;

    static final int MIN_DOCUMENTS = 2;

    private static final Pattern FEE =
            Pattern.compile("£(\\d+(?:\\.\\d{1,2})?) to apply for a Student visa from outside");
    private static final Pattern STAY_YEARS =
            Pattern.compile("course is at degree level, you can usually stay in the UK for up to (\\d+) years");
    private static final Pattern MONTHLY_AMOUNT = Pattern.compile("£[\\d,]+ per month");
    private static final int DAYS_PER_YEAR = 365;

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
                .filter(origin -> !origin.equals(DESTINATION) && !NO_VISA.contains(origin))
                .sorted()
                .toList();
        if (origins.isEmpty()) {
            return List.of();
        }

        VisaRequirementContent content = new VisaRequirementContent(
                VisaPolicy.VISA_REQUIRED, List.of(studentVisa()), List.of(OVERVIEW_URL, MONEY_URL, DOCUMENTS_URL));
        return origins.stream()
                .map(origin -> new ScrapedVisaRequirement(origin, DESTINATION, TravelPurpose.STUDY, content))
                .toList();
    }

    private VisaOption studentVisa() {
        Document overview = fetcher.fetch(OVERVIEW_URL);
        String overviewText = HtmlSections.clean(overview.text());

        List<Requirement> requirements = new ArrayList<>();
        List<String> eligibility =
                listAfter(overview, "You can apply for a Student visa to study in the UK", OVERVIEW_URL);
        if (eligibility.size() < MIN_ELIGIBILITY) {
            throw new ScrapingException("Only " + eligibility.size() + " eligibility conditions at " + OVERVIEW_URL);
        }
        eligibility.forEach(text -> requirements.add(new Requirement(text, classifier.classify(text))));

        // "£1,529 per month (for up to 9 months) for courses in London", and the same outside London.
        List<String> amounts = listAfter(fetcher.fetch(MONEY_URL), "need either", MONEY_URL).stream()
                .filter(text -> MONTHLY_AMOUNT.matcher(text).find())
                .toList();
        if (amounts.isEmpty()) {
            throw new ScrapingException("Monthly amounts not found at " + MONEY_URL);
        }
        amounts.forEach(text -> requirements.add(new Requirement(text, RequirementType.FINANCIAL_PROOF)));

        List<String> documents = listAfter(
                fetcher.fetch(DOCUMENTS_URL), "When you apply for your Student visa you must provide", DOCUMENTS_URL);
        if (documents.size() < MIN_DOCUMENTS) {
            throw new ScrapingException("Only " + documents.size() + " mandatory documents at " + DOCUMENTS_URL);
        }
        documents.forEach(text -> requirements.add(new Requirement(text, RequirementType.DOCUMENT)));

        String fee = firstGroup(FEE, overviewText, "Student visa fee", OVERVIEW_URL);
        return new VisaOption(
                VisaType.STUDENT,
                "Student visa",
                Integer.parseInt(firstGroup(STAY_YEARS, overviewText, "Degree-level stay", OVERVIEW_URL))
                        * DAYS_PER_YEAR,
                Money.of(fee, "GBP"),
                requirements,
                OVERVIEW_URL);
    }

    /** Items of the list right after the first paragraph containing {@code intro}. */
    private static List<String> listAfter(Document document, String intro, String url) {
        return document.select("p").stream()
                .filter(paragraph -> HtmlSections.clean(paragraph.text()).contains(intro))
                .findFirst()
                .map(Element::nextElementSibling)
                .filter(list -> list.tagName().equals("ul"))
                .map(list -> list.select("> li").stream()
                        .map(item -> HtmlSections.clean(item.text()))
                        .filter(text -> !text.isEmpty())
                        .toList())
                .orElseThrow(() -> new ScrapingException("List after '" + intro + "' not found at " + url));
    }

    private static String firstGroup(Pattern pattern, String text, String what, String url) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            throw new ScrapingException(what + " not found at " + url);
        }
        return matcher.group(1);
    }
}
