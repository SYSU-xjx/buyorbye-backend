package com.hku.money.exchange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface RateProvider {
  record Rate(BigDecimal rate, LocalDate date, String source) {}

  Optional<Rate> fetch(String currency, LocalDate date);
}
