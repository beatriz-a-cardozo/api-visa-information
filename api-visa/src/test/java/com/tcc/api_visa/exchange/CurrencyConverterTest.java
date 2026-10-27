package com.tcc.api_visa.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.tcc.api_visa.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CurrencyConverterTest {

    private static final LocalDate RATE_DATE = LocalDate.parse("2026-10-26");

    private final CurrencyConverter converter = new CurrencyConverter((from, to) -> switch (from + ">" + to) {
        case "GBP>BRL" -> Optional.of(new ExchangeRate(from, to, new BigDecimal("7.0123"), RATE_DATE));
        case "EUR>JPY" -> Optional.of(new ExchangeRate(from, to, new BigDecimal("163.456"), RATE_DATE));
        default -> Optional.empty();
    });

    @Test
    void convertsWithTheRateAndItsDate() {
        assertThat(converter.convert(Money.of("135.50", "GBP"), "BRL"))
                .contains(new ConvertedMoney(
                        new BigDecimal("950.17"),
                        "BRL",
                        new BigDecimal("7.0123"),
                        RATE_DATE,
                        CurrencyConverter.SOURCE));
    }

    @Test
    void roundsToTheDecimalPlacesOfTheTargetCurrency() {
        assertThat(converter.convert(Money.of("75.00", "EUR"), "JPY"))
                .map(ConvertedMoney::amount)
                .contains(new BigDecimal("12259"));
    }

    @Test
    void doesNotConvertToTheSameCurrencyOrWithoutARate() {
        assertThat(converter.convert(Money.of("10.00", "BRL"), "BRL")).isEmpty();
        assertThat(converter.convert(Money.of("25.00", "EGP"), "BRL")).isEmpty();
    }
}
