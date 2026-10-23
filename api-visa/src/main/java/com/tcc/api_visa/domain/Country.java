package com.tcc.api_visa.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "countries")
public class Country {

    /** ISO 3166-1 alpha-3 code. */
    @Id
    private String iso3;

    /** ISO 3166-1 alpha-2 code, used by some official sources. */
    private String iso2;

    private String name;
    private Continent continent;

    /** ISO 4217 currency code. */
    private String currency;

    private List<String> languages = new ArrayList<>();
    private boolean origin;
    private boolean destination;
    private List<OfficialSource> officialSources = new ArrayList<>();
}
