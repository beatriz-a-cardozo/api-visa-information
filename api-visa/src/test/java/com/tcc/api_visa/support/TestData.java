package com.tcc.api_visa.support;

import com.tcc.api_visa.domain.Money;
import com.tcc.api_visa.domain.Requirement;
import com.tcc.api_visa.domain.RequirementType;
import com.tcc.api_visa.domain.VisaOption;
import com.tcc.api_visa.domain.VisaPolicy;
import com.tcc.api_visa.domain.VisaRequirementContent;
import com.tcc.api_visa.domain.VisaType;
import com.tcc.api_visa.scraper.ScrapedVisaRequirement;
import java.util.List;

public final class TestData {

    public static final String SOURCE = "https://www.gov.uk/standard-visitor";

    private TestData() {}

    public static VisaRequirementContent content(String fee) {
        VisaOption visa = new VisaOption(
                VisaType.TOURIST,
                "Standard Visitor visa",
                180,
                Money.of(fee, "GBP"),
                List.of(new Requirement("Valid passport", RequirementType.DOCUMENT)),
                SOURCE);
        return new VisaRequirementContent(VisaPolicy.VISA_REQUIRED, List.of(visa), List.of(SOURCE));
    }

    public static ScrapedVisaRequirement scraped(String origin, String destination, String fee) {
        return new ScrapedVisaRequirement(origin, destination, content(fee));
    }
}
