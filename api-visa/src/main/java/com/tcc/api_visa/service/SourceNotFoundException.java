package com.tcc.api_visa.service;

public class SourceNotFoundException extends RuntimeException {

    public SourceNotFoundException(String iso3, String url) {
        super(iso3 + " has no official source " + url);
    }
}
