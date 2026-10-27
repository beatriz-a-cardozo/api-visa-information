package com.tcc.api_visa.api.v1;

import com.tcc.api_visa.service.VisaQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Countries")
@RestController
@RequestMapping("/api/v1/countries")
@RequiredArgsConstructor
class CountryController {

    private final VisaQueryService visaQueryService;

    @Operation(summary = "Countries covered, with their currency and official sources")
    @GetMapping
    List<CountryResponse> countries() {
        return visaQueryService.countries().stream().map(CountryResponse::from).toList();
    }
}
