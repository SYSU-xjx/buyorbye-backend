package com.hku.money.ranking.service;

import com.hku.money.common.PageResult;
import com.hku.money.ranking.dto.RankingEntry;
import com.hku.money.ranking.mapper.RankingMapper;
import java.time.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RankingService {
  public enum Type {
    MOST_REJECTED,
    HIGHEST_APPROVED_AMOUNT,
    TOP_JUDGE
  }

  public record RankingPage(
      Type type,
      RankingWindow.Period period,
      LocalDate periodStart,
      LocalDate periodEnd,
      Instant calculatedAt,
      String unit,
      int page,
      int size,
      long total,
      List<RankingEntry> items) {}

  public record MyRanking(
      Type type,
      RankingWindow.Period period,
      LocalDate periodStart,
      LocalDate periodEnd,
      String unit,
      RankingEntry entry) {}

  private final RankingMapper mapper;
  private final Clock clock;

  public RankingService(RankingMapper mapper, Clock clock) {
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public RankingPage list(
      Type type, RankingWindow.Period period, LocalDate date, int page, int size) {
    int offset = PageResult.offset(page, size);
    var w = RankingWindow.of(period, date, clock);
    return new RankingPage(
        type,
        period,
        w.startDate(),
        w.endDate(),
        clock.instant(),
        unit(type),
        page,
        size,
        mapper.count(type.name(), w.startUtc(), w.endUtc()),
        mapper.entries(type.name(), w.startUtc(), w.endUtc(), offset, size));
  }

  public MyRanking me(long uid, Type type, RankingWindow.Period period, LocalDate date) {
    var w = RankingWindow.of(period, date, clock);
    return new MyRanking(
        type,
        period,
        w.startDate(),
        w.endDate(),
        unit(type),
        mapper.me(type.name(), w.startUtc(), w.endUtc(), uid));
  }

  private String unit(Type type) {
    return type == Type.HIGHEST_APPROVED_AMOUNT ? "HKD" : "COUNT";
  }
}
