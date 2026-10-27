package com.tcc.api_visa.exchange;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An amount converted to another currency, with the rate used, so the client can tell it is an
 * estimate and not an official price.
 *
 * @param rate     how much one unit of the original currency is worth in {@code currency}
 * @param rateDate day the rate was published
 * @param source   who published the rate
 */
public record ConvertedMoney(BigDecimal amount, String currency, BigDecimal rate, LocalDate rateDate, String source) {}
