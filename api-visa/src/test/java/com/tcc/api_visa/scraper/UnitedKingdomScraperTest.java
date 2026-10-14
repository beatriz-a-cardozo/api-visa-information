package com.tcc.api_visa.scraper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tcc.api_visa.model.Visa;
import com.tcc.api_visa.model.VisaType;
import com.tcc.api_visa.scraper.impl.UnitedKingdomScraper;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class UnitedKingdomScraperTest {

    @Test
    void shouldExtractUnitedKingdomVisaRequirements() throws IOException {

        UnitedKingdomScraper scraper = new UnitedKingdomScraper();

        Visa visa = scraper.scrape();

        assertNotNull(visa);
        assertEquals("BR", visa.getOriginCountryIsoCode());
        assertEquals("GB", visa.getDestinationCountryIsoCode());
        assertEquals(VisaType.TOURIST, visa.getVisaType());

        assertNotNull(visa.getRequirements());
        assertFalse(visa.getRequirements().isEmpty());
    }
}