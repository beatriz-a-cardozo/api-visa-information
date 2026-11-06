package com.tcc.api_visa.api.v1;

import com.tcc.api_visa.domain.Money;
import com.tcc.api_visa.domain.Requirement;
import com.tcc.api_visa.domain.VisaOption;
import com.tcc.api_visa.domain.VisaType;
import com.tcc.api_visa.exchange.ConvertedMoney;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * @param cost                  fee as published by the destination, or {@code null} when unknown
 * @param costInOriginCurrency  {@link #cost} converted at the latest ECB rate; {@code null} when the
 *                              fee is unknown, already in that currency, no rate exists, or in history
 */
public record VisaOptionResponse(
        VisaType type,
        String name,
        @Nullable Integer maxStayDays,
        @Nullable Money cost,
        @Nullable ConvertedMoney costInOriginCurrency,
        List<Requirement> requirements,
        String sourceUrl) {

    static VisaOptionResponse from(VisaOption visa, @Nullable ConvertedMoney costInOriginCurrency) {
        return new VisaOptionResponse(
                visa.type(),
                visa.name(),
                visa.maxStayDays(),
                visa.cost(),
                costInOriginCurrency,
                visa.requirements(),
                visa.sourceUrl());
    }
}
