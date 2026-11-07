package com.hku.money.user.service;

import com.hku.money.auth.mapper.AuthMapper;
import com.hku.money.common.*;
import com.hku.money.security.AccountGuard;
import com.hku.money.user.dto.UserProfile;
import com.hku.money.user.entity.Streak;
import com.hku.money.user.mapper.UserMapper;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserMapper mapper;
  private final AuthMapper auth;
  private final Clock clock;
  private final AccountGuard guard;

  public UserService(UserMapper mapper, AuthMapper auth, Clock clock, AccountGuard guard) {
    this.mapper = mapper;
    this.auth = auth;
    this.clock = clock;
    this.guard = guard;
  }

  public record ProfilePatch(
      @Size(min = 1, max = 80) String displayName,
      @Size(max = 2048) String avatarUrl,
      @Pattern(regexp = "CNY|HKD|USD") String preferredCurrency) {}

  public record Reputation(int score, int level, int votingWeight) {}

  public record StreakView(
      int currentDays, int longestDays, LocalDate lastCountedDate, boolean checkedInToday) {}

  public UserProfile profile(long id) {
    var account = auth.byId(id);
    if (account == null || !"ACTIVE".equals(account.accountStatus))
      throw ApiException.unauthorized();
    return UserProfile.from(account);
  }

  @Transactional
  public UserProfile patch(long id, ProfilePatch req) {
    guard.lockActive(id);
    if (req.displayName() == null && req.avatarUrl() == null && req.preferredCurrency() == null)
      throw ApiException.bad("至少提供一个修改字段");
    if (req.displayName() != null && req.displayName().isBlank()) throw ApiException.bad("昵称不能为空白");
    if (req.avatarUrl() != null && !req.avatarUrl().isEmpty()) {
      try {
        var uri = java.net.URI.create(req.avatarUrl());
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
            || uri.getHost() == null) throw new IllegalArgumentException();
      } catch (IllegalArgumentException e) {
        throw ApiException.bad("头像须为HTTP或HTTPS URL");
      }
    }
    mapper.updateProfile(
        id,
        req.displayName() == null ? null : req.displayName().trim(),
        req.avatarUrl(),
        req.preferredCurrency());
    return profile(id);
  }

  public Reputation reputation(long id) {
    int score = mapper.score(id);
    int weight = weight(score);
    return new Reputation(score, weight, weight);
  }

  public static int weight(int score) {
    return score >= 30 ? 3 : score >= 10 ? 2 : 1;
  }

  public Map<String, Object> statistics(long id) {
    return mapper.statistics(id);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResult<Map<String, Object>> ledger(long id, int page, int size) {
    int offset = PageResult.offset(page, size);
    return new PageResult<>(mapper.ledger(id, offset, size), page, size, mapper.ledgerCount(id));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResult<Map<String, Object>> records(
      long id, String type, String status, int page, int size) {
    if (!Set.of("SUBMITTED", "VOTED", "WATCHED").contains(type)) throw ApiException.bad("无效的记录类型");
    if (status != null
        && !Set.of("DRAFT", "OPEN", "SETTLED", "WITHDRAWN", "INSUFFICIENT_PARTICIPATION")
            .contains(status)) throw ApiException.bad("无效的提案状态");
    int offset = PageResult.offset(page, size);
    return new PageResult<>(
        mapper.records(id, type, status, offset, size),
        page,
        size,
        mapper.recordsCount(id, type, status));
  }

  public StreakView streak(long id) {
    return view(mapper.streak(id), today());
  }

  @Transactional
  public StreakView checkIn(long id) {
    guard.lockActive(id);
    mapper.ensureStreak(id);
    var streak = mapper.lockStreak(id);
    var today = today();
    if (!today.equals(streak.lastCountedDate)) {
      streak.currentDays =
          today.minusDays(1).equals(streak.lastCountedDate) ? streak.currentDays + 1 : 1;
      streak.longestDays = Math.max(streak.longestDays, streak.currentDays);
      streak.lastCountedDate = today;
      mapper.updateStreak(id, streak);
      auth.touch(id, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
    return view(streak, today);
  }

  public static StreakView view(Streak s, LocalDate today) {
    if (s == null) return new StreakView(0, 0, null, false);
    int current =
        s.lastCountedDate == null || s.lastCountedDate.isBefore(today.minusDays(1))
            ? 0
            : s.currentDays;
    return new StreakView(
        current, s.longestDays, s.lastCountedDate, today.equals(s.lastCountedDate));
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneId.of("Asia/Hong_Kong")));
  }
}
