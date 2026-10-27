package com.tcc.api_visa.api.v1;

import com.tcc.api_visa.domain.VisaOption;
import com.tcc.api_visa.domain.VisaRequirement;
import com.tcc.api_visa.domain.VisaRequirementContent;
import com.tcc.api_visa.exchange.ConvertedMoney;
import com.tcc.api_visa.exchange.CurrencyConverter;
import com.tcc.api_visa.service.VisaQueryService;
import com.tcc.api_visa.service.VisaQueryService.CurrentVisa;
import com.tcc.api_visa.service.VisaQueryService.VisaChanges;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Visas")
@RestController
@RequestMapping("/api/v1/visas")
@RequiredArgsConstructor
class VisaController {

    private static final String ISO3 = "[A-Z]{3}";

    private final VisaQueryService visaQueryService;
    private final CurrencyConverter currencyConverter;

    @Operation(
            summary = "What a citizen of the origin needs to enter the destination",
            description = "Fees come in the destination's currency and, when the ECB publishes a rate, also in"
                    + " the origin's currency (an estimate, with the rate and its date).")
    @GetMapping
    VisaResponse visa(
            @Parameter(description = "ISO alpha-3 of the traveller's country", example = "BRA")
                    @RequestParam
                    @Pattern(regexp = ISO3)
                    String origin,
            @Parameter(description = "ISO alpha-3 of the country to visit", example = "GBR")
                    @RequestParam
                    @Pattern(regexp = ISO3)
                    String destination) {
        CurrentVisa current = visaQueryService.current(origin, destination);
        VisaRequirement requirement = current.requirement();
        VisaRequirementContent content = requirement.getContent();
        String originCurrency = current.origin().getCurrency();
        return new VisaResponse(
                CountrySummary.from(current.origin()),
                CountrySummary.from(current.destination()),
                content.policy(),
                content.visas().stream()
                        .map(visa -> withOriginCurrency(visa, originCurrency))
                        .toList(),
                content.sourceUrls(),
                requirement.getDataOrigin(),
                requirement.getCollectedAt(),
                requirement.getUpdatedAt());
    }

    @Operation(summary = "How the information changed in the last year, newest first")
    @GetMapping("/history")
    VisaHistoryResponse history(
            @Parameter(example = "BRA") @RequestParam @Pattern(regexp = ISO3) String origin,
            @Parameter(example = "GBR") @RequestParam @Pattern(regexp = ISO3) String destination) {
        VisaChanges changes = visaQueryService.history(origin, destination);
        return new VisaHistoryResponse(
                CountrySummary.from(changes.origin()),
                CountrySummary.from(changes.destination()),
                changes.changes().stream().map(VisaHistoryResponse.Change::from).toList());
    }

    private VisaOptionResponse withOriginCurrency(VisaOption visa, String originCurrency) {
        ConvertedMoney converted = visa.cost() == null
                ? null
                : currencyConverter.convert(visa.cost(), originCurrency).orElse(null);
        return VisaOptionResponse.from(visa, converted);
    }
}
