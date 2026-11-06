package com.tcc.api_visa.api.admin;

import com.tcc.api_visa.service.SourceChange;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * @param url      official page; must be https and resolve to a public address
 * @param language ISO 639-1 code of the page, e.g. {@code en}
 * @param verified {@code true} when you have just confirmed the page is official and works
 */
record SourceRequest(
        @Schema(example = "https://www.gov.uk/check-uk-visa") @NotBlank @Size(max = 2048)
        String url,

        @Schema(example = "GOV.UK - check if you need a UK visa") @NotBlank @Size(max = 300)
        String description,

        @Schema(example = "en") @Nullable @Pattern(regexp = "[a-z]{2,3}")
        String language,

        boolean verified) {

    SourceChange toChange() {
        return new SourceChange(url, description, language, verified);
    }
}
