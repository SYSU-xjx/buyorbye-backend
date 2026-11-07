package com.hku.money;

import static org.assertj.core.api.Assertions.*;

import com.hku.money.ranking.service.RankingWindow;
import com.hku.money.user.entity.Streak;
import com.hku.money.user.service.UserService;
import java.time.*;
import org.junit.jupiter.api.Test;

class RankingWindowTest {
  final Clock clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void naturalWeekAndHistoricalMonth() {
    var w = RankingWindow.of(RankingWindow.Period.WEEK, LocalDate.of(2026, 11, 5), clock);
    assertThat(w.startUtc()).isEqualTo(LocalDateTime.of(2026, 11, 1, 16, 0));
    assertThat(w.endUtc()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    var m = RankingWindow.of(RankingWindow.Period.MONTH, LocalDate.of(2024, 2, 15), clock);
    assertThat(m.startUtc()).isEqualTo(LocalDateTime.of(2024, 1, 31, 16, 0));
    assertThat(m.endUtc()).isEqualTo(LocalDateTime.of(2024, 2, 29, 16, 0));
  }

  @Test
  void crossYearWeekAndFuturePeriod() {
    var w = RankingWindow.of(RankingWindow.Period.WEEK, LocalDate.of(2025, 1, 1), clock);
    assertThat(w.startDate()).isEqualTo(LocalDate.of(2024, 12, 30));
    assertThat(w.endDate()).isEqualTo(LocalDate.of(2025, 1, 6));
    assertThatThrownBy(
            () -> RankingWindow.of(RankingWindow.Period.DAY, LocalDate.of(2030, 1, 1), clock))
        .isInstanceOf(com.hku.money.common.ApiException.class);
  }

  @Test
  void streakExpiresButLongestSurvives() {
    var s = new Streak();
    s.currentDays = 4;
    s.longestDays = 7;
    s.lastCountedDate = LocalDate.of(2026, 11, 4);
    assertThat(UserService.view(s, LocalDate.of(2026, 11, 5)).currentDays()).isEqualTo(4);
    assertThat(UserService.view(s, LocalDate.of(2026, 11, 6)).currentDays()).isZero();
    assertThat(UserService.view(s, LocalDate.of(2026, 11, 6)).longestDays()).isEqualTo(7);
    assertThat(UserService.weight(9)).isEqualTo(1);
    assertThat(UserService.weight(10)).isEqualTo(2);
    assertThat(UserService.weight(29)).isEqualTo(2);
    assertThat(UserService.weight(30)).isEqualTo(3);
  }
}
