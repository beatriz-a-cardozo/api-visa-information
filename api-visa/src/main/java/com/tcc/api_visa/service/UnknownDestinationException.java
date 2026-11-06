package com.tcc.api_visa.service;

public class UnknownDestinationException extends RuntimeException {

    public UnknownDestinationException(String destinationIso3) {
        super("No scraper for destination " + destinationIso3);
    }
}
