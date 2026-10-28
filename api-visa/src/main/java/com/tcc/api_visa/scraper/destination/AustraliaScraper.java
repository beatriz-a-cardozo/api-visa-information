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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the Department of Home Affairs visa pages. Their content is not in the HTML body: each page
 * carries it as JSON in a hidden field, which the browser renders. The scraper reads that field,
 * so it sees the same text a visitor sees, without running JavaScript.
 *
 * <p>Passports on the Electronic Travel Authority (601) list get the ETA, those on the eVisitor
 * (651) list get the eVisitor, and everyone else needs a Visitor visa (600, tourist stream).
 */
@Component
@RequiredArgsConstructor
public class AustraliaScraper implements VisaScraper {

    static final String DESTINATION = "AUS";
    private static final String LISTING = "https://immi.homeaffairs.gov.au/visas/getting-a-visa/visa-listing/";
    static final String ETA_URL = LISTING + "electronic-travel-authority-601";
    static final String EVISITOR_URL = LISTING + "evisitor-651";
    static final String VISITOR_URL = LISTING + "visitor-600/tourist-stream-overseas";

    /** Each list has more than 30 passports; far fewer means the page changed. */
    static final int MIN_LIST_SIZE = 20;

    private static final String SCHEMA_FIELD = "input[id$=PageSchemaHiddenField_Input]";
    private static final String PASSPORT_CRITERION = "eligible passport";
    private static final Pattern STAY_MONTHS = Pattern.compile("(\\d+)\\s*months?", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUD = Pattern.compile("AUD\\s*(\\d+(?:\\.\\d{2})?)");
    private static final int DAYS_PER_MONTH = 30;

    // Own mapper: the page's JSON has nothing to do with the application's JSON settings.
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final HtmlFetcher fetcher;
    private final RequirementClassifier classifier;

    @Override
    public String destinationIso3() {
        return DESTINATION;
    }

    @Override
    public List<ScrapedVisaRequirement> scrape(Set<String> originIso3s) {
        Page eta = page(ETA_URL);
        Page evisitor = page(EVISITOR_URL);
        Set<String> etaPassports = eligiblePassports(eta);
        Set<String> evisitorPassports = eligiblePassports(evisitor);

        List<String> origins = originIso3s.stream()
                .filter(origin -> !origin.equals(DESTINATION))
                .sorted()
                .toList();
        boolean anyVisitorVisa = origins.stream()
                .anyMatch(origin -> !etaPassports.contains(origin) && !evisitorPassports.contains(origin));
        VisaOption visitor = anyVisitorVisa ? option(page(VISITOR_URL)) : null;
        VisaOption etaOption = option(eta);
        VisaOption evisitorOption = option(evisitor);

        List<ScrapedVisaRequirement> results = new ArrayList<>();
        for (String origin : origins) {
            List<VisaOption> visas = new ArrayList<>();
            List<String> sources = new ArrayList<>();
            if (etaPassports.contains(origin)) {
                visas.add(etaOption);
                sources.add(ETA_URL);
            }
            if (evisitorPassports.contains(origin)) {
                visas.add(evisitorOption);
                sources.add(EVISITOR_URL);
            }
            VisaPolicy policy = etaPassports.contains(origin)
                    ? VisaPolicy.ELECTRONIC_TRAVEL_AUTHORIZATION
                    : evisitorPassports.contains(origin) ? VisaPolicy.E_VISA : VisaPolicy.VISA_REQUIRED;
            if (visas.isEmpty()) {
                visas.add(visitor);
                sources.add(VISITOR_URL);
            }
            results.add(new ScrapedVisaRequirement(
                    origin, DESTINATION, new VisaRequirementContent(policy, visas, sources)));
        }
        return results;
    }

    private Page page(String url) {
        Document document = fetcher.fetch(url);
        Element field = document.selectFirst(SCHEMA_FIELD);
        if (field == null) {
            throw new ScrapingException("Page content field not found at " + url);
        }
        try {
            JsonNode schema = mapper.readTree(field.attr("value"));
            if (!schema.path("applicant").isObject()) {
                throw new ScrapingException("Unexpected page content at " + url);
            }
            return new Page(url, schema);
        } catch (JacksonException e) {
            throw new ScrapingException("Page content at " + url + " is not valid JSON", e);
        }
    }

    /** The countries in the "Have an eligible passport" criterion. */
    private static Set<String> eligiblePassports(Page page) {
        JsonNode criterion = criteria(page).stream()
                .filter(node ->
                        text(node.path("title")).toLowerCase(Locale.ROOT).contains(PASSPORT_CRITERION))
                .findFirst()
                .orElseThrow(() -> new ScrapingException("Eligible passport list not found at " + page.url()));
        List<String> names = Jsoup.parseBodyFragment(
                        criterion.path("description").asString(""))
                .select("li")
                .stream()
                .map(item -> HtmlSections.clean(item.text()))
                .toList();
        if (names.size() < MIN_LIST_SIZE) {
            throw new ScrapingException("Only " + names.size() + " passports in the list at " + page.url());
        }
        Set<String> codes = new HashSet<>();
        names.forEach(name -> CountryNames.iso3(name, Locale.ENGLISH).ifPresent(codes::add));
        return codes;
    }

    /** Name, stay, cost and the criteria (other than the passport list) as requirements. */
    private VisaOption option(Page page) {
        JsonNode overview = page.schema().path("applicant").path("overview");
        List<Requirement> requirements = criteria(page).stream()
                .map(node -> text(node.path("title")))
                .filter(title ->
                        !title.isEmpty() && !title.toLowerCase(Locale.ROOT).contains(PASSPORT_CRITERION))
                .map(title -> new Requirement(title, classifier.classify(title)))
                .toList();
        if (requirements.isEmpty()) {
            throw new ScrapingException("No eligibility criteria found at " + page.url());
        }
        String title = text(page.schema().path("title"));
        return new VisaOption(
                VisaType.TOURIST,
                title.isEmpty() ? page.url() : title,
                stayDays(text(overview.path("visaStay"))),
                cost(text(overview.path("visaCost"))),
                requirements,
                page.url());
    }

    private static List<JsonNode> criteria(Page page) {
        JsonNode criteria = page.schema().path("applicant").path("eligibility").path("criteria");
        List<JsonNode> list = new ArrayList<>();
        criteria.forEach(list::add);
        return list;
    }

    private static Integer stayDays(String stay) {
        Matcher months = STAY_MONTHS.matcher(stay);
        return months.find() ? Integer.parseInt(months.group(1)) * DAYS_PER_MONTH : null;
    }

    /** "Free" is zero; "AUD20" is 20; anything else (the page fills some amounts in by script) is unknown. */
    private static Money cost(String cost) {
        if (cost.equalsIgnoreCase("Free")) {
            return new Money(BigDecimal.ZERO, "AUD");
        }
        Matcher amount = AUD.matcher(cost);
        return amount.find() ? Money.of(amount.group(1), "AUD") : null;
    }

    /** Plain text of a JSON string that holds an HTML fragment. */
    private static String text(JsonNode node) {
        return HtmlSections.clean(Jsoup.parseBodyFragment(node.asString("")).text());
    }

    private record Page(String url, JsonNode schema) {}
}
