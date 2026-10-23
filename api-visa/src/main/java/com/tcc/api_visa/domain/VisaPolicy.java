package com.tcc.api_visa.domain;

/** What a citizen of the origin country needs, in general terms, to enter the destination. */
public enum VisaPolicy {
    VISA_FREE,
    ELECTRONIC_TRAVEL_AUTHORIZATION,
    E_VISA,
    VISA_ON_ARRIVAL,
    VISA_REQUIRED,
    /** The source was read, but it does not state the policy for this origin. */
    UNKNOWN
}
