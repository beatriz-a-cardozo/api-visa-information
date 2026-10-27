package com.tcc.api_visa.service;

import com.tcc.api_visa.domain.Country;
import com.tcc.api_visa.domain.VisaHistory;
import com.tcc.api_visa.domain.VisaRequirement;
import com.tcc.api_visa.repository.CountryRepository;
import com.tcc.api_visa.repository.VisaHistoryRepository;
import com.tcc.api_visa.repository.VisaRequirementRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Read-only queries behind the public API. */
@Service
@RequiredArgsConstructor
public class VisaQueryService {

    /** History older than this is not returned, even before MongoDB's TTL monitor removes it. */
    static final Duration HISTORY_WINDOW = Duration.ofDays(365);

    private final CountryRepository countryRepository;
    private final VisaRequirementRepository visaRequirementRepository;
    private final VisaHistoryRepository visaHistoryRepository;
    private final Clock clock;

    public List<Country> countries() {
        return countryRepository.findAll().stream()
                .sorted(Comparator.comparing(Country::getIso3))
                .toList();
    }

    /** Current data for a pair. */
    public CurrentVisa current(String originIso3, String destinationIso3) {
        Pair pair = pair(originIso3, destinationIso3);
        VisaRequirement requirement = visaRequirementRepository
                .findByOriginIso3AndDestinationIso3(originIso3, destinationIso3)
                .orElseThrow(() -> new VisaDataNotFoundException(originIso3, destinationIso3));
        return new CurrentVisa(pair.origin(), pair.destination(), requirement);
    }

    /** Changes of the last year, newest first. Empty when the content never changed. */
    public VisaChanges history(String originIso3, String destinationIso3) {
        Pair pair = pair(originIso3, destinationIso3);
        Instant since = clock.instant().minus(HISTORY_WINDOW);
        List<VisaHistory> changes =
                visaHistoryRepository.findByOriginIso3AndDestinationIso3AndChangedAtAfterOrderByChangedAtDesc(
                        originIso3, destinationIso3, since);
        return new VisaChanges(pair.origin(), pair.destination(), changes);
    }

    /** Both countries exist in their role and are different; otherwise the matching exception. */
    Pair pair(String originIso3, String destinationIso3) {
        if (originIso3.equals(destinationIso3)) {
            throw new SameCountryException();
        }
        Country origin = country(originIso3, "origin", Country::isOrigin);
        Country destination = country(destinationIso3, "destination", Country::isDestination);
        return new Pair(origin, destination);
    }

    private Country country(String iso3, String role, Predicate<Country> coveredInRole) {
        return countryRepository
                .findById(iso3)
                .filter(coveredInRole)
                .orElseThrow(() -> new UnknownCountryException(role, iso3));
    }

    record Pair(Country origin, Country destination) {}

    public record CurrentVisa(Country origin, Country destination, VisaRequirement requirement) {}

    public record VisaChanges(Country origin, Country destination, List<VisaHistory> changes) {}
}
