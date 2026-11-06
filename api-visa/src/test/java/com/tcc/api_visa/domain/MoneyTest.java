package com.tcc.api_visa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesTheCurrencyCode() {
        assertThat(Money.of("135.00", "gbp").currency()).isEqualTo("GBP");
    }

    @Test
    void rejectsUnknownCurrencies() {
        assertThatThrownBy(() -> Money.of("10", "XYZ1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-1"), "EUR")).isInstanceOf(IllegalArgumentException.class);
    }
}
