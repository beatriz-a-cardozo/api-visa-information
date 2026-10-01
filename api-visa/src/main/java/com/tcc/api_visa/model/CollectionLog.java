package com.tcc.api_visa.model;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "collection_logs")
public class CollectionLog {

    @Id
    private String id;

    private String destinationCountryIsoCode;
    private LocalDateTime timestamp;
    private Boolean status;
    private String errorMessage;
}
