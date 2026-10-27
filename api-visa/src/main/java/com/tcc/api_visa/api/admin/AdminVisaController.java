package com.tcc.api_visa.api.admin;

import com.tcc.api_visa.service.ManualVisaService;
import com.tcc.api_visa.service.PairOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Manual corrections of visa data. Every route here requires the {@code X-Admin-Key} header. */
@Tag(name = "Admin: manual corrections")
@SecurityRequirement(name = "adminKey")
@RestController
@RequestMapping("/admin/visas")
@RequiredArgsConstructor
class AdminVisaController {

    private static final String ISO3 = "[A-Z]{3}";

    private final ManualVisaService manualVisaService;

    @Operation(
            summary = "Creates or corrects the data of a pair and marks it MANUAL",
            description = "Collections stop overwriting the pair until it is released. A real change goes to"
                    + " the history with changeOrigin MANUAL. Answers 201 when the pair had no data.")
    @PutMapping
    ResponseEntity<ManualVisaResponse> correct(
            @RequestParam @Pattern(regexp = ISO3) String origin,
            @RequestParam @Pattern(regexp = ISO3) String destination,
            @RequestBody @Valid ManualVisaRequest request) {
        PairOutcome outcome = manualVisaService.correct(origin, destination, request.toContent());
        HttpStatus status = outcome == PairOutcome.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(new ManualVisaResponse(origin, destination, outcome));
    }

    @Operation(summary = "Hands the pair back to the scraper; the next collection replaces the manual data")
    @DeleteMapping("/manual")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(
            @RequestParam @Pattern(regexp = ISO3) String origin,
            @RequestParam @Pattern(regexp = ISO3) String destination) {
        manualVisaService.release(origin, destination);
    }

    /** @param outcome {@code CREATED}, {@code CHANGED} or {@code UNCHANGED} (only marked as manual) */
    record ManualVisaResponse(String origin, String destination, PairOutcome outcome) {}
}
