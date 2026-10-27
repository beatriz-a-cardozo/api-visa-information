package com.tcc.api_visa.service;

/** Both countries are supported, but the pair has not been collected yet. */
public class VisaDataNotFoundException extends RuntimeException {

    public VisaDataNotFoundException(String originIso3, String destinationIso3) {
        super("No visa data collected yet for " + originIso3 + " -> " + destinationIso3);
    }
}
