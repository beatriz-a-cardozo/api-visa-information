package com.tcc.api_visa.service;

public class SameCountryException extends RuntimeException {

    public SameCountryException() {
        super("Origin and destination must be different countries");
    }
}
