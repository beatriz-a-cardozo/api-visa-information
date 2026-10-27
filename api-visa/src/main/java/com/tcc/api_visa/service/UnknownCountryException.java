package com.tcc.api_visa.service;

/** The code is not in the catalog, or the country is not covered in that role (origin or destination). */
public class UnknownCountryException extends RuntimeException {

    public UnknownCountryException(String role, String iso3) {
        super(iso3 + " is not a supported " + role + " country");
    }

    public UnknownCountryException(String iso3) {
        super(iso3 + " is not in the country catalog");
    }
}
