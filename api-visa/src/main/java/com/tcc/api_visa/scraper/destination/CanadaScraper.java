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
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Reads the visitor visa requirements published by IRCC. The page does not say which
 * nationalities need the visa, so the policy stays {@link VisaPolicy#UNKNOWN} until the
 * entry-requirements-by-country page is parsed (see docs/ROADMAP.md).
 */
@Component
@RequiredArgsConstructor
public class CanadaScraper implements VisaScraper {

    static final String DESTINATION = "CAN";
    static final String VISITOR_ELIGIBILITY_URL =
            "https://www.canada.ca/en/immigration-refugees-citizenship/services/visit-canada/eligibility.html";

    private final HtmlFetcher fetcher;
    private final RequirementClassifier classifier;

    @Override
    public String destinationIso3() {
        return DESTINATION;
    }

    @Override
    public List<ScrapedVisaRequirement> scrape(Set<String> originIso3s) {
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

        VisaOption visitorVisa = new VisaOption(
                VisaType.TOURIST,
                "Visitor visa (temporary resident visa)",
                null,
                null,
                requirements,
                VISITOR_ELIGIBILITY_URL);
        VisaRequirementContent content =
                new VisaRequirementContent(VisaPolicy.UNKNOWN, List.of(visitorVisa), List.of(VISITOR_ELIGIBILITY_URL));

        return originIso3s.stream()
                .filter(origin -> !origin.equals(DESTINATION))
                .sorted()
                .map(origin -> new ScrapedVisaRequirement(origin, DESTINATION, content))
                .toList();
    }
}
