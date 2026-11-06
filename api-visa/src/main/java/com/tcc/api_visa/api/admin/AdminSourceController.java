package com.tcc.api_visa.api.admin;

import com.tcc.api_visa.domain.OfficialSource;
import com.tcc.api_visa.service.OfficialSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Official sources of each country. A source is identified by its URL, which is unique within the
 * country. Every route here requires the {@code X-Admin-Key} header.
 */
@Tag(name = "Admin: official sources")
@SecurityRequirement(name = "adminKey")
@RestController
@RequestMapping("/admin/countries/{iso3}/sources")
@RequiredArgsConstructor
class AdminSourceController {

    private static final String ISO3 = "[A-Z]{3}";

    private final OfficialSourceService sourceService;

    @GetMapping
    List<OfficialSource> list(@PathVariable @Pattern(regexp = ISO3) String iso3) {
        return sourceService.list(iso3);
    }

    @Operation(summary = "Adds a source (409 if the country already has this URL)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OfficialSource add(@PathVariable @Pattern(regexp = ISO3) String iso3, @RequestBody @Valid SourceRequest request) {
        return sourceService.add(iso3, request.toChange());
    }

    @Operation(summary = "Replaces the source registered under ?url= (the URL itself may change)")
    @PutMapping
    OfficialSource replace(
            @PathVariable @Pattern(regexp = ISO3) String iso3,
            @RequestParam @Size(max = 2048) String url,
            @RequestBody @Valid SourceRequest request) {
        return sourceService.replace(iso3, url, request.toChange());
    }

    @Operation(summary = "Removes the source registered under ?url=")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable @Pattern(regexp = ISO3) String iso3, @RequestParam @Size(max = 2048) String url) {
        sourceService.remove(iso3, url);
    }
}
