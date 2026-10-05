package com.tcc.api_visa.scraper.impl;

import com.tcc.api_visa.model.Requirement;
import com.tcc.api_visa.model.RequirementType;
import com.tcc.api_visa.model.Visa;
import com.tcc.api_visa.model.VisaType;
import com.tcc.api_visa.scraper.ScraperStrategy;
import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
public class CanadaScraper implements ScraperStrategy {

    private static final String SOURCE_URL =
            "https://www.canada.ca/en/immigration-refugees-citizenship/services/visit-canada/eligibility.html";

    @Override
    public Visa scrape() throws IOException {

        Document document = Jsoup.connect(SOURCE_URL)
                .userAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                                + "Chrome/152.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .referrer("https://www.google.com/")
                .timeout(20000)
                .followRedirects(true)
                .get();

        Visa visa = new Visa();

        visa.setOriginCountryIsoCode("BR");
        visa.setDestinationCountryIsoCode("CA");
        visa.setVisaType(VisaType.TOURIST);

        extractRequirements(document, visa);

        return visa;
    }

    private void extractRequirements(Document document, Visa visa) {

        Element heading = document.select("h2").stream()
                .filter(element ->
                        element.text().trim().equalsIgnoreCase("Who can get a visa"))
                .findFirst()
                .orElse(null);

        if (heading == null) {
            return;
        }

        Element current = heading.nextElementSibling();

        while (current != null && !current.tagName().equals("h2")) {

            if (current.tagName().equals("ul")) {

                Elements listItems = current.select("> li");

                for (Element item : listItems) {

                    String text = item.text().trim();

                    if (text.isEmpty()) {
                        continue;
                    }

                    RequirementType type = classifyRequirement(text);

                    Requirement requirement = new Requirement();
                    requirement.setDescription(text);
                    requirement.setRequirementType(type);

                    visa.addRequirement(requirement);
                }
            }

            current = current.nextElementSibling();
        }
    }

    private RequirementType classifyRequirement(String text) {

        String lowerText = text.toLowerCase();

        if (lowerText.contains("passport")
                || lowerText.contains("travel document")) {
            return RequirementType.DOCUMENT;
        }

        if (lowerText.contains("money")
                || lowerText.contains("financial")) {
            return RequirementType.FINANCIAL_PROOF;
        }

        if (lowerText.contains("health")
                || lowerText.contains("medical")) {
            return RequirementType.HEALTH;
        }

        if (lowerText.contains("invitation")) {
            return RequirementType.INVITATION_LETTER;
        }

        if (lowerText.contains("photo")) {
            return RequirementType.PHOTO;
        }

        if (lowerText.contains("fee")
                || lowerText.contains("payment")) {
            return RequirementType.FEE_PAYMENT;
        }

        return RequirementType.OTHER;
    }
}