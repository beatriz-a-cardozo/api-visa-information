package com.tcc.api_visa.exchange;

import java.util.Optional;

public interface ExchangeRateProvider {

    /**
     * @return empty when the source does not publish this pair or cannot be reached; callers then
     *         show only the original amount
     */
    Optional<ExchangeRate> rate(String fromCurrency, String toCurrency);
}
