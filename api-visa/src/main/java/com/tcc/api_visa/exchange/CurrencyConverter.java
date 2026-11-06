package com.tcc.api_visa.exchange;

import com.tcc.api_visa.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Converts fees to the traveller's currency at read time; stored amounts are never changed. */
@Service
@RequiredArgsConstructor
public class CurrencyConverter {

    static final String SOURCE = "European Central Bank (via Frankfurter)";

    private final ExchangeRateProvider exchangeRateProvider;

    /** Empty when the amount is already in {@code toCurrency} or no rate is available. */
    public Optional<ConvertedMoney> convert(Money money, String toCurrency) {
        if (money.currency().equals(toCurrency)) {
            return Optional.empty();
        }
        return exchangeRateProvider.rate(money.currency(), toCurrency).map(rate -> {
            int digits = Currency.getInstance(toCurrency).getDefaultFractionDigits();
            BigDecimal amount = money.amount().multiply(rate.rate()).setScale(digits, RoundingMode.HALF_EVEN);
            return new ConvertedMoney(amount, toCurrency, rate.rate(), rate.date(), SOURCE);
        });
    }
}
