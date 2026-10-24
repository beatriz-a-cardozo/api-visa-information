package com.tcc.api_visa.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

/** An amount in a given ISO 4217 currency. */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        // Throws IllegalArgumentException for codes that are not ISO 4217.
        currency = Currency.getInstance(currency.toUpperCase(Locale.ROOT)).getCurrencyCode();
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }
}
