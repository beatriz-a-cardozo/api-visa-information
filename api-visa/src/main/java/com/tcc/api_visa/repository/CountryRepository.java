package com.tcc.api_visa.repository;

import com.tcc.api_visa.domain.Country;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CountryRepository extends MongoRepository<Country, String> {

    List<Country> findByOriginTrue();

    List<Country> findByDestinationTrue();
}
