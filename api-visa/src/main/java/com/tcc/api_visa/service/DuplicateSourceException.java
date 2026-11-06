package com.tcc.api_visa.service;

public class DuplicateSourceException extends RuntimeException {

    public DuplicateSourceException(String iso3, String url) {
        super(iso3 + " already has the official source " + url);
    }
}
