package com.tcc.api_visa.api.admin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tcc.api_visa.domain.Money;
import com.tcc.api_visa.domain.Requirement;
import com.tcc.api_visa.domain.RequirementType;
import com.tcc.api_visa.domain.VisaOption;
import com.tcc.api_visa.domain.VisaPolicy;
import com.tcc.api_visa.domain.VisaRequirementContent;
import com.tcc.api_visa.domain.VisaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Visa information entered by an administrator. Same shape as the collected data, with limits on
 * every field so a request cannot store unbounded content.
 *
 * @param sourceUrls official pages the information was taken from (at least one)
 */
record ManualVisaRequest(
        @NotNull VisaPolicy policy,
        @NotNull @Size(max = 20) List<@NotNull @Valid Visa> visas,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 2048) String> sourceUrls) {

    VisaRequirementContent toContent() {
        return new VisaRequirementContent(
                policy,
                visas.stream().map(Visa::toOption).toList(),
                sourceUrls.stream().map(String::strip).toList());
    }

    /**
     * @param maxStayDays maximum stay, or {@code null} when the source does not say
     * @param cost        fee in the currency published by the destination, or {@code null} when unknown
     */
    record Visa(
            @NotNull VisaType type,
            @NotBlank @Size(max = 200) String name,
            @Nullable @Positive @Max(3650) Integer maxStayDays,
            @Nullable @Valid Fee cost,
            @NotNull @Size(max = 50) List<@NotNull @Valid Item> requirements,
            @NotBlank @Size(max = 2048) String sourceUrl) {

        VisaOption toOption() {
            return new VisaOption(
                    type,
                    name.strip(),
                    maxStayDays,
                    cost == null ? null : new Money(cost.amount(), cost.currency()),
                    requirements.stream().map(Item::toRequirement).toList(),
                    sourceUrl.strip());
        }
    }

    record Fee(
            @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 4)
            BigDecimal amount,

            @Schema(example = "GBP") @NotNull @Pattern(regexp = "[A-Z]{3}")
            String currency) {

        /** The pattern only checks the format; this rejects codes like {@code ZZZ} before they reach {@link Money}. */
        @JsonIgnore
        @AssertTrue(message = "must be an ISO 4217 currency code")
        boolean isKnownCurrency() {
            return currency == null
                    || Currency.getAvailableCurrencies().stream()
                            .anyMatch(known -> known.getCurrencyCode().equals(currency));
        }
    }

    record Item(
            @NotBlank @Size(max = 1000) String description,
            @NotNull RequirementType type) {

        Requirement toRequirement() {
            return new Requirement(description.strip(), type);
        }
    }
}
