package com.tcc.api_visa.service;

import com.tcc.api_visa.domain.DataOrigin;
import com.tcc.api_visa.domain.VisaHistory;
import com.tcc.api_visa.domain.VisaRequirement;
import com.tcc.api_visa.domain.VisaRequirementContent;
import com.tcc.api_visa.repository.VisaHistoryRepository;
import com.tcc.api_visa.repository.VisaRequirementRepository;
import com.tcc.api_visa.scraper.ScrapedVisaRequirement;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Saves a scraped result. The previous content goes to the history only when the hash changes, so
 * the history holds real changes and not one entry per collection.
 */
@Component
@RequiredArgsConstructor
public class VisaRequirementWriter {

    private final VisaRequirementRepository requirementRepository;
    private final VisaHistoryRepository historyRepository;
    private final ContentHasher hasher;

    public PairOutcome apply(ScrapedVisaRequirement scraped, Instant now) {
        String newHash = hasher.hash(scraped.content());
        Optional<VisaRequirement> existing = requirementRepository.findByOriginIso3AndDestinationIso3(
                scraped.originIso3(), scraped.destinationIso3());

        if (existing.isEmpty()) {
            VisaRequirement created = new VisaRequirement();
            created.setOriginIso3(scraped.originIso3());
            created.setDestinationIso3(scraped.destinationIso3());
            created.setContent(scraped.content());
            created.setContentHash(newHash);
            created.setDataOrigin(DataOrigin.SCRAPER);
            created.setCollectedAt(now);
            created.setUpdatedAt(now);
            requirementRepository.save(created);
            return PairOutcome.CREATED;
        }

        VisaRequirement current = existing.get();
        if (current.getDataOrigin() == DataOrigin.MANUAL) {
            return PairOutcome.SKIPPED_MANUAL;
        }

        if (newHash.equals(current.getContentHash())) {
            current.setCollectedAt(now);
            requirementRepository.save(current);
            return PairOutcome.UNCHANGED;
        }

        // History first: if saving the new content fails, the next collection retries the change.
        recordChange(current, newHash, DataOrigin.SCRAPER, now);
        current.setContent(scraped.content());
        current.setContentHash(newHash);
        current.setCollectedAt(now);
        current.setUpdatedAt(now);
        requirementRepository.save(current);
        return PairOutcome.CHANGED;
    }

    /**
     * Saves content entered by an administrator and marks the pair {@code MANUAL}, so collections
     * stop overwriting it. A real change goes to the history like a scraped one, with
     * {@code changeOrigin = MANUAL}. {@code collectedAt} keeps the last time the source was read.
     */
    public PairOutcome applyManual(
            String originIso3, String destinationIso3, VisaRequirementContent content, Instant now) {
        String newHash = hasher.hash(content);
        Optional<VisaRequirement> existing =
                requirementRepository.findByOriginIso3AndDestinationIso3(originIso3, destinationIso3);

        if (existing.isEmpty()) {
            VisaRequirement created = new VisaRequirement();
            created.setOriginIso3(originIso3);
            created.setDestinationIso3(destinationIso3);
            created.setContent(content);
            created.setContentHash(newHash);
            created.setDataOrigin(DataOrigin.MANUAL);
            created.setUpdatedAt(now);
            requirementRepository.save(created);
            return PairOutcome.CREATED;
        }

        VisaRequirement current = existing.get();
        current.setDataOrigin(DataOrigin.MANUAL);
        if (newHash.equals(current.getContentHash())) {
            requirementRepository.save(current);
            return PairOutcome.UNCHANGED;
        }

        recordChange(current, newHash, DataOrigin.MANUAL, now);
        current.setContent(content);
        current.setContentHash(newHash);
        current.setUpdatedAt(now);
        requirementRepository.save(current);
        return PairOutcome.CHANGED;
    }

    private void recordChange(VisaRequirement current, String newHash, DataOrigin changeOrigin, Instant now) {
        VisaHistory history = new VisaHistory();
        history.setOriginIso3(current.getOriginIso3());
        history.setDestinationIso3(current.getDestinationIso3());
        history.setPreviousContent(current.getContent());
        history.setPreviousContentHash(current.getContentHash());
        history.setNewContentHash(newHash);
        history.setChangeOrigin(changeOrigin);
        history.setChangedAt(now);
        historyRepository.save(history);
    }
}
