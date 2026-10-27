package com.tcc.api_visa.api.v1;

import com.tcc.api_visa.domain.Continent;
import com.tcc.api_visa.domain.Country;
import com.tcc.api_visa.domain.OfficialSource;
import java.util.List;

/**
 * @param origin      whether the API has data for citizens of this country
 * @param destination whether the API has data for travelling to this country
 */
public record CountryResponse(
        String iso3,
        String iso2,
        String name,
        Continent continent,
        String currency,
        List<String> languages,
        boolean origin,
        boolean destination,
        List<OfficialSource> officialSources) {

    static CountryResponse from(Country country) {
        return new CountryResponse(
                country.getIso3(),
                country.getIso2(),
                country.getName(),
                country.getContinent(),
                country.getCurrency(),
                List.copyOf(country.getLanguages()),
                country.isOrigin(),
                country.isDestination(),
                List.copyOf(country.getOfficialSources()));
    }
}
