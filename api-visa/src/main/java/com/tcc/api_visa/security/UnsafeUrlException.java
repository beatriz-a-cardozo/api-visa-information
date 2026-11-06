package com.tcc.api_visa.security;

/** A URL that the application must not store or fetch (not https, or pointing to a private network). */
public class UnsafeUrlException extends RuntimeException {

    public UnsafeUrlException(String url, String reason) {
        super("URL not allowed (" + reason + "): " + url);
    }
}
