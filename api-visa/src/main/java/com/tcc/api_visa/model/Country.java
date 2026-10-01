package com.tcc.api_visa.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "countries")
public class Country {

    @Indexed(unique = true)
    private String isoCode;

    private String name;
    private String officialSourceUrl;
    private Continent continent;

}
