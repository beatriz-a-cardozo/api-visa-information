package com.tcc.api_visa.domain;

public enum CollectionStatus {
    SUCCESS,
    /** Some origins were collected and others failed. */
    PARTIAL,
    FAILED
}
