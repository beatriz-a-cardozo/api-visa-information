
package com.tcc.api_visa.scraper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tcc.api_visa.model.RequirementType;
import com.tcc.api_visa.model.Visa;
import com.tcc.api_visa.model.VisaType;
import com.tcc.api_visa.scraper.impl.CanadaScraper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class CanadaScraperTest {

    @Test
    void shouldExtractCanadaVisaRequirements() {

        String html = """
                <html>
                    <body>
                        <h2>Who can get a visa</h2>
                        <ul>
                            <li>You need a valid passport.</li>
                            <li>You must have enough money for your stay.</li>
                            <li>You must be in good health.</li>
                        </ul>
                        <h2>Other information</h2>
                    </body>
                </html>
                """;

        Document document = Jsoup.parse(html);

        CanadaScraper scraper = new CanadaScraper();

        Visa visa = scraper.extractFromDocument(document);

        assertNotNull(visa);

        assertEquals("BR", visa.getOriginCountryIsoCode());
        assertEquals("CA", visa.getDestinationCountryIsoCode());
        assertEquals(VisaType.TOURIST, visa.getVisaType());

        assertNotNull(visa.getRequirements());
        assertFalse(visa.getRequirements().isEmpty());

        assertEquals(3, visa.getRequirements().size());

        assertEquals(
                RequirementType.DOCUMENT,
                visa.getRequirements().get(0).getRequirementType()
        );

        assertEquals(
                RequirementType.FINANCIAL_PROOF,
                visa.getRequirements().get(1).getRequirementType()
        );

        assertEquals(
                RequirementType.HEALTH,
                visa.getRequirements().get(2).getRequirementType()
        );
    }
}
