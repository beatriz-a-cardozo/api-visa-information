package com.tcc.api_visa.exchange;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * How much one unit of {@code from} is worth in {@code to}.
 *
 * @param date day the rate was published by the source
 */
public record ExchangeRate(String from, String to, BigDecimal rate, LocalDate date) {}
