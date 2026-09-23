package com.tcc.api_visa.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "visas")
public class Visa {

    @Id
    private String id;

    private String originCountryIsoCode;
    private String destinationCountryIsoCode;
    private VisaType visaType;
    private Integer stayOfDays;
    private Float cost;
    private List<Requirement> requirements = new ArrayList<>();

    public void addRequirement(Requirement requirement) {
        this.requirements.add(requirement);
    }
}
