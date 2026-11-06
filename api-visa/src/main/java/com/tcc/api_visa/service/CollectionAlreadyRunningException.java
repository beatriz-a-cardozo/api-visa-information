package com.tcc.api_visa.service;

public class CollectionAlreadyRunningException extends RuntimeException {

    public CollectionAlreadyRunningException() {
        super("A collection is already running");
    }
}
