package com.tcc.api_visa.domain;

/** What a citizen of the origin country needs, in general terms, to enter the destination. */
public enum VisaPolicy {
    VISA_FREE,
    ELECTRONIC_TRAVEL_AUTHORIZATION,
    E_VISA,
    VISA_ON_ARRIVAL,
    /** Entry without a visa; the residence permit is requested in the destination after arrival. */
    PERMIT_AFTER_ENTRY,
    VISA_REQUIRED,
    /** The source was read, but it does not state the policy for this origin. */
    UNKNOWN
}
