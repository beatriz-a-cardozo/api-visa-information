package com.tcc.api_visa.scraper;

/** A source could not be read, or its content no longer has the expected structure. */
public class ScrapingException extends RuntimeException {

    public ScrapingException(String message) {
        super(message);
    }

    public ScrapingException(String message, Throwable cause) {
        super(message, cause);
    }
}
