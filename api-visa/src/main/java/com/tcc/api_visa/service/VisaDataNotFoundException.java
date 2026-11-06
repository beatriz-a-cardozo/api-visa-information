package com.tcc.api_visa.service;

import com.tcc.api_visa.domain.TravelPurpose;

/** Both countries are supported, but the pair has no data for that purpose yet. */
public class VisaDataNotFoundException extends RuntimeException {

    public VisaDataNotFoundException(String originIso3, String destinationIso3, TravelPurpose purpose) {
        super("No " + purpose + " visa data collected yet for " + originIso3 + " -> " + destinationIso3);
    }
}
