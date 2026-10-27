package com.tcc.api_visa.api.v1;

import com.tcc.api_visa.domain.Country;

public record CountrySummary(String iso3, String name, String currency) {

    static CountrySummary from(Country country) {
        return new CountrySummary(country.getIso3(), country.getName(), country.getCurrency());
    }
}
