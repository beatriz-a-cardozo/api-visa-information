package com.tcc.api_visa.service;

import com.tcc.api_visa.domain.Country;
import com.tcc.api_visa.repository.CountryRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the initial country catalog. Only countries missing from the database are inserted, so
 * corrections made by an administrator are never overwritten on restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CountryCatalogSeeder implements ApplicationRunner {

    private final CountryRepository countryRepository;
    private final JsonMapper jsonMapper;

    @Value("classpath:seed/countries.json")
    private Resource catalog;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        List<Country> countries;
        try (InputStream input = catalog.getInputStream()) {
            countries = jsonMapper.readValue(input, new TypeReference<>() {});
        }

        List<Country> missing = countries.stream()
                .filter(country -> !countryRepository.existsById(country.getIso3()))
                .toList();

        countryRepository.saveAll(missing);
        log.info("Country catalog: {} in seed, {} inserted", countries.size(), missing.size());
    }
}
