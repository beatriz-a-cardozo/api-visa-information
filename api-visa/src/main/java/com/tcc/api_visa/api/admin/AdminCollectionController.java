package com.tcc.api_visa.api.admin;

import com.tcc.api_visa.repository.CollectionLogRepository;
import com.tcc.api_visa.service.CollectionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Triggers and audits collections. Every route here requires the {@code X-Admin-Key} header. */
@RestController
@RequestMapping("/admin/collections")
@RequiredArgsConstructor
class AdminCollectionController {

    private static final String ISO3 = "[A-Z]{3}";

    private final CollectionService collectionService;
    private final CollectionLogRepository logRepository;

    /** Starts a collection in the background (202). Returns 409 if one is already running. */
    @PostMapping
    ResponseEntity<CollectionStartedResponse> start(
            @RequestParam(required = false) @Nullable @Pattern(regexp = ISO3) String destination) {
        String runId = collectionService.start(destination);
        return ResponseEntity.accepted()
                .location(URI.create("/admin/collections/" + runId))
                .body(new CollectionStartedResponse(runId, destination));
    }

    /** Logs of one run. A scraper's log appears when it finishes. */
    @GetMapping("/{runId}")
    CollectionRunResponse run(@PathVariable @Pattern(regexp = "[0-9a-f-]{36}") String runId) {
        List<CollectionLogResponse> logs = logRepository.findByRunIdOrderByStartedAtAsc(runId).stream()
                .map(CollectionLogResponse::from)
                .toList();
        return new CollectionRunResponse(runId, collectionService.isRunning(), logs);
    }

    /** Most recent logs, newest first. */
    @GetMapping("/logs")
    List<CollectionLogResponse> logs(
            @RequestParam(required = false) @Nullable @Pattern(regexp = ISO3) String destination,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        Pageable page = PageRequest.of(0, limit);
        var logs = destination == null
                ? logRepository.findByOrderByStartedAtDesc(page)
                : logRepository.findByDestinationIso3OrderByStartedAtDesc(destination, page);
        return logs.stream().map(CollectionLogResponse::from).toList();
    }

    record CollectionStartedResponse(String runId, @Nullable String destination) {}

    /**
     * @param collectionRunning whether any collection is running now (only one runs at a time)
     */
    record CollectionRunResponse(String runId, boolean collectionRunning, List<CollectionLogResponse> logs) {}
}
