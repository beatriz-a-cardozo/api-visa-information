package com.tcc.api_visa.service;

import com.tcc.api_visa.domain.DataOrigin;
import com.tcc.api_visa.domain.VisaOption;
import com.tcc.api_visa.domain.VisaRequirement;
import com.tcc.api_visa.domain.VisaRequirementContent;
import com.tcc.api_visa.repository.VisaRequirementRepository;
import com.tcc.api_visa.security.PublicUrlValidator;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Manual corrections by an administrator, for sources that block robots or that a scraper reads
 * wrong. The data stays traceable: it is marked {@code MANUAL} and must cite official URLs.
 */
@Service
@RequiredArgsConstructor
public class ManualVisaService {

    private final VisaQueryService visaQueryService;
    private final VisaRequirementWriter writer;
    private final VisaRequirementRepository requirementRepository;
    private final PublicUrlValidator urlValidator;
    private final Clock clock;

    public PairOutcome correct(String originIso3, String destinationIso3, VisaRequirementContent content) {
        visaQueryService.pair(originIso3, destinationIso3);
        content.sourceUrls().forEach(urlValidator::validate);
        content.visas().stream().map(VisaOption::sourceUrl).forEach(urlValidator::validate);
        return writer.applyManual(originIso3, destinationIso3, content, clock.instant());
    }

    /**
     * Hands the pair back to the scraper: the content stays until the next collection replaces it
     * (and, if it differs, moves the manual content to the history).
     */
    public void release(String originIso3, String destinationIso3) {
        visaQueryService.pair(originIso3, destinationIso3);
        VisaRequirement requirement = requirementRepository
                .findByOriginIso3AndDestinationIso3(originIso3, destinationIso3)
                .orElseThrow(() -> new VisaDataNotFoundException(originIso3, destinationIso3));
        if (requirement.getDataOrigin() == DataOrigin.MANUAL) {
            requirement.setDataOrigin(DataOrigin.SCRAPER);
            requirementRepository.save(requirement);
        }
    }
}
