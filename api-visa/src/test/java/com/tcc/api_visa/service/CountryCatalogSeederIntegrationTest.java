package com.tcc.api_visa.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tcc.api_visa.domain.Country;
import com.tcc.api_visa.repository.CountryRepository;
import com.tcc.api_visa.support.MongoTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MongoTestcontainersConfiguration.class)
class CountryCatalogSeederIntegrationTest {

    @Autowired
    private CountryCatalogSeeder seeder;

    @Autowired
    private CountryRepository countryRepository;

    @Test
    void loadsTheInitialScopeOnStartup() {
        assertThat(countryRepository.findByOriginTrue())
                .extracting(Country::getIso3)
                .containsExactlyInAnyOrder("BRA", "JPN", "DEU", "CAN", "ZAF");
        assertThat(countryRepository.findByDestinationTrue()).hasSize(10);
        assertThat(countryRepository.findAll())
                .allSatisfy(country -> assertThat(country.getOfficialSources()).isNotEmpty());
    }

    @Test
    void doesNotOverwriteCorrectionsMadeByAnAdmin() throws Exception {
        Country germany = countryRepository.findById("DEU").orElseThrow();
        germany.setName("Deutschland");
        countryRepository.save(germany);

        seeder.run(new DefaultApplicationArguments());

        assertThat(countryRepository.findById("DEU").orElseThrow().getName()).isEqualTo("Deutschland");
        assertThat(countryRepository.count()).isEqualTo(10);

        germany.setName("Germany");
        countryRepository.save(germany);
    }
}
