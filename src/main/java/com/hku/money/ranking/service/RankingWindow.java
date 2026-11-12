package com.hku.money.ranking.service;

import com.hku.money.common.ApiException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;

public record RankingWindow(
    LocalDate startDate, LocalDate endDate, LocalDateTime startUtc, LocalDateTime endUtc) {
  public enum Period {
    DAY,
    WEEK,
    MONTH
  }

  public static RankingWindow of(Period period, LocalDate date, Clock clock) {
    ZoneId zone = ZoneId.of("Asia/Hong_Kong");
    LocalDate today = LocalDate.now(clock.withZone(zone));
    if (date == null) date = today;
    if (date.getYear() < 1000 || date.getYear() > 9999) throw ApiException.bad("日期超出数据库支持范围");
    LocalDate start =
        switch (period) {
          case DAY -> date;
          case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
          case MONTH -> date.withDayOfMonth(1);
        };
    if (start.isAfter(today)) throw ApiException.bad("不能查询未来周期");
    LocalDate end =
        switch (period) {
          case DAY -> start.plusDays(1);
          case WEEK -> start.plusWeeks(1);
          case MONTH -> start.plusMonths(1);
        };
    Instant startInstant = start.atStartOfDay(zone).toInstant(),
        endInstant = end.atStartOfDay(zone).toInstant();
    if (endInstant.isAfter(clock.instant())) endInstant = clock.instant();
    return new RankingWindow(
        start,
        end,
        LocalDateTime.ofInstant(startInstant, ZoneOffset.UTC),
        LocalDateTime.ofInstant(endInstant, ZoneOffset.UTC));
  }
}
