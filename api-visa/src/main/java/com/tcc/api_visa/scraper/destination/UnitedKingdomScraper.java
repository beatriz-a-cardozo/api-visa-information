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
import com.tcc.api_visa.scraper.support.HtmlSections;
import com.tcc.api_visa.scraper.support.RequirementClassifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Reads the Standard Visitor visa page on GOV.UK: requirements, fee and maximum stay. Which
 * nationalities need the visa comes from the visa national list (see docs/ROADMAP.md), so the
 * policy stays {@link VisaPolicy#UNKNOWN} for now.
 */
@Component
@RequiredArgsConstructor
public class UnitedKingdomScraper implements VisaScraper {

    static final String DESTINATION = "GBR";
    static final String STANDARD_VISITOR_URL = "https://www.gov.uk/standard-visitor/overview";

    private static final Pattern FEE = Pattern.compile("Standard Visitor visa costs £(\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern STAY_MONTHS = Pattern.compile("stay in the UK for up to (\\d+) months");
    private static final int DAYS_PER_MONTH = 30;

    private final HtmlFetcher fetcher;
    private final RequirementClassifier classifier;

    @Override
    public String destinationIso3() {
        return DESTINATION;
    }

    @Override
    public List<ScrapedVisaRequirement> scrape(Set<String> originIso3s) {
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
        VisaOption standardVisitor = new VisaOption(
                VisaType.TOURIST,
                "Standard Visitor visa",
                firstGroup(STAY_MONTHS, pageText)
                        .map(months -> Integer.parseInt(months) * DAYS_PER_MONTH)
                        .orElse(null),
                firstGroup(FEE, pageText).map(amount -> Money.of(amount, "GBP")).orElse(null),
                requirements,
                STANDARD_VISITOR_URL);
        VisaRequirementContent content =
                new VisaRequirementContent(VisaPolicy.UNKNOWN, List.of(standardVisitor), List.of(STANDARD_VISITOR_URL));

        return originIso3s.stream()
                .filter(origin -> !origin.equals(DESTINATION))
                .sorted()
                .map(origin -> new ScrapedVisaRequirement(origin, DESTINATION, content))
                .toList();
    }

    private static Optional<String> firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
