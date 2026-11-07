package com.hku.money;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiIntegrationTest {
  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
    r.add("spring.datasource.username", MYSQL::getUsername);
    r.add("spring.datasource.password", MYSQL::getPassword);
    r.add("app.jwt-secret", () -> "test-only-secret-at-least-32-bytes-not-for-production");
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    Clock testClock() {
      return Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired com.hku.money.user.service.UserService users;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Autowired jakarta.persistence.EntityManager entityManager;
  @Autowired com.hku.money.auth.service.AuthService auth;
  @Autowired com.hku.money.auth.mapper.AuthMapper authMapper;

  record Login(long id, String access, String refresh) {}

  JsonNode request(String method, String path, String body, String token, int expected)
      throws Exception {
    var req =
        switch (method) {
          case "POST" -> post(path);
          case "PATCH" -> patch(path);
          case "PUT" -> put(path);
          case "DELETE" -> delete(path);
          default -> get(path);
        };
    if (body != null) req.contentType("application/json").content(body);
    if (token != null) req.header("Authorization", "Bearer " + token);
    var result = mvc.perform(req).andReturn();
    assertThat(result.getResponse().getStatus())
        .as(method + " " + path + " " + result.getResponse().getContentAsString())
        .isEqualTo(expected);
    String text = result.getResponse().getContentAsString();
    JsonNode envelope = json.readTree(text);
    assertThat(envelope.path("code").isIntegralNumber()).isTrue();
    assertThat(envelope.path("code").asInt()).isEqualTo(expected);
    assertThat(envelope.path("message").isTextual()).isTrue();
    assertThat(envelope.path("message").asText()).isNotBlank();
    assertThat(envelope.has("data")).isTrue();
    assertThat(envelope.size()).isEqualTo(3);
    if (expected >= 400) assertThat(envelope.path("data").isNull()).isTrue();
    return envelope.get("data");
  }

  Login account() throws Exception {
    String name = "u" + UUID.randomUUID().toString().replace("-", "");
    var profile =
        request(
            "POST",
            "/api/auth/register",
            json.writeValueAsString(
                Map.of("username", name, "password", "Password123!", "displayName", "测试用户")),
            null,
            201);
    var result =
        request(
            "POST",
            "/api/auth/login",
            json.writeValueAsString(
                Map.of("username", name, "password", "Password123!", "deviceId", "test-device")),
            null,
            200);
    return new Login(
        profile.path("id").asLong(),
        result.path("accessToken").asText(),
        result.path("refreshToken").asText());
  }

  @Test
  void authRotationRevocationProfileAndAnonymization() throws Exception {
    var a = account();
    String stored =
        jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, a.id);
    assertThat(stored).startsWith("$2");
    assertThat(stored).doesNotContain("Password123!");
    var profile = request("GET", "/api/users/me", null, a.access, 200);
    assertThat(profile.has("passwordHash")).isFalse();
    request("GET", "/api/users/me", null, null, 401);
    request("GET", "/api/users/me", null, "bad-token", 401);
    request(
        "PATCH",
        "/api/users/me",
        "{\"displayName\":\"新昵称\",\"preferredCurrency\":\"HKD\"}",
        a.access,
        200);
    request("PATCH", "/api/users/me", "{\"preferredCurrency\":\"BAD\"}", a.access, 400);
    var rotated =
        request(
            "POST",
            "/api/auth/refresh",
            json.writeValueAsString(Map.of("refreshToken", a.refresh)),
            null,
            200);
    request(
        "POST",
        "/api/auth/refresh",
        json.writeValueAsString(Map.of("refreshToken", a.refresh)),
        null,
        401);
    assertThat(
            request("POST", "/api/auth/logout", null, rotated.path("accessToken").asText(), 200)
                .isNull())
        .isTrue();
    request("GET", "/api/users/me", null, a.access, 401);
    request(
        "POST",
        "/api/auth/refresh",
        json.writeValueAsString(Map.of("refreshToken", rotated.path("refreshToken").asText())),
        null,
        401);
    var b = account();
    request(
        "PUT",
        "/api/users/me/password",
        "{\"oldPassword\":\"Password123!\",\"newPassword\":\"NewPassword123!\"}",
        b.access,
        200);
    request("GET", "/api/users/me", null, b.access, 401);
    var c = account();
    assertThat(request("DELETE", "/api/users/me", null, c.access, 200).isNull()).isTrue();
    request("GET", "/api/users/me", null, c.access, 401);
    assertThat(
            jdbc.queryForObject("SELECT account_status FROM users WHERE id=?", String.class, c.id))
        .isEqualTo("ANONYMIZED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_streaks WHERE user_id=?", Integer.class, c.id))
        .isEqualTo(1);
  }

  @Test
  void checkInConcurrencyAndGap() throws Exception {
    var a = account();
    var executor = Executors.newFixedThreadPool(4);
    try {
      var calls = new ArrayList<Callable<Integer>>();
      for (int i = 0; i < 8; i++) calls.add(() -> users.checkIn(a.id).currentDays());
      for (var result : executor.invokeAll(calls)) assertThat(result.get()).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
    assertThat(
            request("GET", "/api/users/me/streak", null, a.access, 200).path("currentDays").asInt())
        .isEqualTo(1);
    jdbc.update(
        "UPDATE user_streaks SET current_days=4,longest_days=7,last_counted_date='2026-11-05' WHERE"
            + " user_id=?",
        a.id);
    assertThat(
            request("POST", "/api/users/me/check-in", null, a.access, 200)
                .path("currentDays")
                .asInt())
        .isEqualTo(5);
    jdbc.update(
        "UPDATE user_streaks SET current_days=5,last_counted_date='2026-11-04' WHERE user_id=?",
        a.id);
    var gap = request("GET", "/api/users/me/streak", null, a.access, 200);
    assertThat(gap.path("currentDays").asInt()).isZero();
    assertThat(gap.path("longestDays").asInt()).isEqualTo(7);
    assertThat(
            request("POST", "/api/users/me/check-in", null, a.access, 200)
                .path("currentDays")
                .asInt())
        .isEqualTo(1);
  }

  @Test
  void errorsAlsoUseNumericResultEnvelope() throws Exception {
    var a = account();
    var profile = request("GET", "/api/users/me", null, a.access, 200);
    request(
        "POST",
        "/api/auth/register",
        json.writeValueAsString(
            Map.of(
                "username",
                profile.path("username").asText(),
                "password",
                "Password123!",
                "displayName",
                "duplicate")),
        null,
        409);
    request("GET", "/api/users/me/records", null, a.access, 400);
    request("POST", "/api/auth/login", "{", null, 400);
    request("POST", "/api/purchase-proposals/9223372036854775807/wheel/spin", null, a.access, 404);
    request("GET", "/api/auth/logout", null, a.access, 405);
    request("GET", "/api/not-a-route", null, a.access, 404);
  }

  @Test
  void jpaAndMyBatisShareRollback() throws Exception {
    var a = account();
    var transaction =
        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    assertThatThrownBy(
            () ->
                transaction.execute(
                    status -> {
                      entityManager
                          .createNativeQuery(
                              "UPDATE users SET preferred_currency='USD' WHERE id=:id")
                          .setParameter("id", a.id)
                          .executeUpdate();
                      users.patch(
                          a.id,
                          new com.hku.money.user.service.UserService.ProfilePatch(
                              "rollback-name", null, null));
                      throw new IllegalStateException("rollback-test");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT preferred_currency FROM users WHERE id=?", String.class, a.id))
        .isEqualTo("CNY");
    assertThat(jdbc.queryForObject("SELECT display_name FROM users WHERE id=?", String.class, a.id))
        .isEqualTo("测试用户");
  }

  @Test
  void concurrentRefreshAndRegistrationAreAtomic() throws Exception {
    var a = account();
    var executor = Executors.newFixedThreadPool(4);
    try {
      var barrier = new CyclicBarrier(4);
      var refreshCalls = new ArrayList<Callable<Integer>>();
      for (int i = 0; i < 4; i++)
        refreshCalls.add(
            () -> {
              barrier.await(10, TimeUnit.SECONDS);
              try {
                auth.refresh(new com.hku.money.auth.dto.AuthRequests.Refresh(a.refresh));
                return 200;
              } catch (com.hku.money.common.ApiException e) {
                return e.status;
              }
            });
      var codes = new ArrayList<Integer>();
      for (var result : executor.invokeAll(refreshCalls))
        codes.add(result.get(30, TimeUnit.SECONDS));
      assertThat(codes).containsExactlyInAnyOrder(200, 401, 401, 401);

      String name = "race_" + UUID.randomUUID().toString().replace("-", "");
      var registrationBarrier = new CyclicBarrier(4);
      var registerCalls = new ArrayList<Callable<Integer>>();
      for (int i = 0; i < 4; i++)
        registerCalls.add(
            () -> {
              registrationBarrier.await(10, TimeUnit.SECONDS);
              try {
                auth.register(
                    new com.hku.money.auth.dto.AuthRequests.Register(name, "Password123!", "race"));
                return 201;
              } catch (com.hku.money.common.ApiException e) {
                return e.status;
              } catch (org.springframework.dao.DataIntegrityViolationException e) {
                return 409;
              }
            });
      codes.clear();
      for (var result : executor.invokeAll(registerCalls))
        codes.add(result.get(30, TimeUnit.SECONDS));
      assertThat(codes).containsExactlyInAnyOrder(201, 409, 409, 409);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM users WHERE username=?", Integer.class, name))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM user_streaks s JOIN users u ON u.id=s.user_id WHERE"
                      + " u.username=?",
                  Integer.class,
                  name))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM reputation_accounts s JOIN users u ON u.id=s.user_id WHERE"
                      + " u.username=?",
                  Integer.class,
                  name))
          .isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void deletionCannotBeOverwrittenByWaitingProfileUpdate() throws Exception {
    var a = account();
    var executor = Executors.newSingleThreadExecutor();
    var waiting = new CountDownLatch(1);
    var pending = new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
    var transaction =
        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    try {
      transaction.execute(
          status -> {
            authMapper.lockUser(a.id);
            pending.set(
                executor.submit(
                    () -> {
                      waiting.countDown();
                      try {
                        users.patch(
                            a.id,
                            new com.hku.money.user.service.UserService.ProfilePatch(
                                "must-not-return", null, null));
                        return 200;
                      } catch (com.hku.money.common.ApiException e) {
                        return e.status;
                      }
                    }));
            try {
              assertThat(waiting.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            }
            auth.delete(new com.hku.money.security.CurrentUser(a.id, 0));
            return null;
          });
      assertThat(pending.get().get(30, TimeUnit.SECONDS)).isEqualTo(401);
      assertThat(
              jdbc.queryForObject("SELECT display_name FROM users WHERE id=?", String.class, a.id))
          .isEqualTo("已注销用户");
      assertThatThrownBy(() -> users.checkIn(a.id))
          .isInstanceOf(com.hku.money.common.ApiException.class);
      assertThat(
              jdbc.queryForObject(
                  "SELECT current_days FROM user_streaks WHERE user_id=?", Integer.class, a.id))
          .isZero();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void paginationValidationAndLegacyStreakAreConsistent() throws Exception {
    var a = account();
    request("PATCH", "/api/users/me", "{\"avatarUrl\":\"relative/path\"}", a.access, 400);
    request(
        "PUT",
        "/api/users/me/password",
        json.writeValueAsString(
            Map.of("oldPassword", "中".repeat(30), "newPassword", "NewPassword123!")),
        a.access,
        400);
    for (int i = 0; i < 5; i++) proposal(a.id, 0, "2001-01-01 00:00:00");
    var first =
        request("GET", "/api/users/me/records?type=SUBMITTED&page=1&size=2", null, a.access, 200);
    var second =
        request("GET", "/api/users/me/records?type=SUBMITTED&page=2&size=2", null, a.access, 200);
    var third =
        request("GET", "/api/users/me/records?type=SUBMITTED&page=3&size=2", null, a.access, 200);
    assertThat(first.path("total").asInt()).isEqualTo(5);
    assertThat(second.path("total").asInt()).isEqualTo(5);
    assertThat(third.path("items").size()).isEqualTo(1);
    var ids = new HashSet<String>();
    for (var page : List.of(first, second, third))
      for (var row : page.path("items")) assertThat(ids.add(row.path("id").asText())).isTrue();
    for (int i = 0; i < 5; i++)
      jdbc.update(
          "INSERT INTO reputation_ledger(user_id,score_change,source_type,rule_version)"
              + " VALUES(?,1,'ADMIN_ADJUST','test')",
          a.id);
    var ledger =
        request("GET", "/api/users/me/reputation/ledger?page=2&size=2", null, a.access, 200);
    assertThat(ledger.path("total").asInt()).isEqualTo(5);
    assertThat(ledger.path("items").size()).isEqualTo(2);
    request("GET", "/api/users/me/records?type=SUBMITTED&size=101", null, a.access, 400);
    jdbc.update("DELETE FROM user_streaks WHERE user_id=?", a.id);
    assertThat(
            request("GET", "/api/users/me/streak", null, a.access, 200).path("currentDays").asInt())
        .isZero();
    assertThat(
            request("POST", "/api/users/me/check-in", null, a.access, 200)
                .path("currentDays")
                .asInt())
        .isEqualTo(1);
    long p =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM purchase_proposals WHERE creator_user_id=?", Long.class, a.id);
    jdbc.update("UPDATE purchase_proposals SET published_at=NULL WHERE id=?", p);
    request("POST", "/api/purchase-proposals/" + p + "/wheel/spin", null, a.access, 409);
    jdbc.update(
        "UPDATE purchase_proposals SET published_at='2001-01-01"
            + " 00:00:00',proposal_status='WITHDRAWN' WHERE id=?",
        p);
    request("POST", "/api/purchase-proposals/" + p + "/wheel/spin", null, a.access, 409);
  }

  long proposal(long owner, int decision, String time) {
    jdbc.update(
        "INSERT INTO"
            + " purchase_proposals(creator_user_id,item_name,participation_type,publication_scope,amount,currency,purchase_reason,proposal_status,decision_result,settled_at,published_at)"
            + " VALUES(?,'测试商品','APPROVE_REJECT','PLAZA',100,'HKD','测试','SETTLED',?,?, '2024-01-01"
            + " 00:00:00')",
        owner,
        decision,
        time);
    return jdbc.queryForObject(
        "SELECT MAX(id) FROM purchase_proposals WHERE creator_user_id=?", Long.class, owner);
  }

  void vote(long proposal, long voter, String time) {
    jdbc.update(
        "INSERT INTO"
            + " decision_votes(proposal_id,voter_user_id,vote_type,choice,reason,reputation_score_snapshot,weight_snapshot,client_request_id,cast_at)"
            + " VALUES(?,?,'BINARY',0,'reason',0,1,?,?)",
        proposal,
        voter,
        UUID.randomUUID().toString(),
        time);
  }

  @Test
  void rankingsAllPeriodsBoundariesDeduplicationAndWheelOwnership() throws Exception {
    var a = account();
    var b = account();
    var c = account();
    long p = proposal(a.id, 0, "2024-02-05 00:00:00");
    proposal(a.id, 0, "2024-02-04 15:59:59"); // before local Monday
    proposal(b.id, 0, "2024-02-04 16:00:00"); // exactly local Monday
    proposal(b.id, 0, "2024-02-11 16:00:00"); // exactly next Monday
    long buy = proposal(a.id, 1, "2024-02-05 00:00:00");
    jdbc.update(
        "INSERT INTO"
            + " exchange_rate_snapshots(proposal_id,source_currency,target_currency,rate,converted_amount,rate_source,rate_date)"
            + " VALUES(?,'HKD','HKD',1,100.50,'test','2024-02-05')",
        buy);
    proposal(c.id, 1, "2024-02-05 00:00:00"); // no rate, excluded
    vote(p, c.id, "2024-02-04 15:59:59");
    vote(p, c.id, "2024-02-04 16:00:00");
    vote(p, c.id, "2024-02-05 00:00:00");
    for (String period : List.of("DAY", "WEEK", "MONTH"))
      for (String type : List.of("MOST_REJECTED", "HIGHEST_APPROVED_AMOUNT", "TOP_JUDGE")) {
        var result =
            request(
                "GET",
                "/api/rankings?type=" + type + "&period=" + period + "&date=2024-02-05",
                null,
                a.access,
                200);
        assertThat(result.path("items").isArray()).isTrue();
      }
    var weekly =
        request(
            "GET",
            "/api/rankings?type=MOST_REJECTED&period=WEEK&date=2024-02-05",
            null,
            a.access,
            200);
    assertThat(weekly.path("total").asInt()).isEqualTo(2);
    assertThat(weekly.path("items").get(0).path("metricValue").asInt()).isEqualTo(1);
    var votes =
        request(
            "GET",
            "/api/rankings/me?type=TOP_JUDGE&period=WEEK&date=2024-02-05",
            null,
            c.access,
            200);
    assertThat(votes.path("entry").path("metricValue").asInt()).isEqualTo(1);
    var prev =
        request(
            "GET",
            "/api/rankings/me?type=TOP_JUDGE&period=WEEK&date=2024-02-04",
            null,
            c.access,
            200);
    assertThat(prev.path("entry").path("metricValue").asInt()).isEqualTo(1);
    var money =
        request(
            "GET",
            "/api/rankings?type=HIGHEST_APPROVED_AMOUNT&period=MONTH&date=2024-02-01",
            null,
            a.access,
            200);
    assertThat(money.path("total").asInt()).isEqualTo(1);
    assertThat(money.path("items").get(0).path("metricValue").decimalValue())
        .isEqualByComparingTo(new BigDecimal("100.50"));
    request("GET", "/api/rankings?type=INVALID&period=DAY", null, a.access, 400);
    request("GET", "/api/rankings?type=TOP_JUDGE&period=DAY&date=2030-01-01", null, a.access, 400);
    request("GET", "/api/rankings?type=TOP_JUDGE&period=DAY&page=0", null, a.access, 400);
    for (int i = 0; i < 6; i++) {
      var spin =
          request("POST", "/api/purchase-proposals/" + p + "/wheel/spin", null, a.access, 200);
      assertThat(spin.isIntegralNumber()).isTrue();
      assertThat(spin.asInt()).isBetween(1, 3);
    }
    request("POST", "/api/purchase-proposals/" + p + "/wheel/spin", null, b.access, 403);
    assertThat(
            jdbc.queryForObject(
                "SELECT decision_result FROM purchase_proposals WHERE id=?", Integer.class, p))
        .isZero();
    vote(p, c.id, "2024-02-05 01:00:00");
    assertThat(
            request("GET", "/api/users/me/records?type=VOTED", null, c.access, 200)
                .path("total")
                .asInt())
        .isEqualTo(1);
    request("GET", "/api/users/me/statistics", null, a.access, 200);
    request("GET", "/api/users/me/reputation", null, a.access, 200);
    request("GET", "/api/users/me/reputation/ledger", null, a.access, 200);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM weekly_rankings", Integer.class)).isZero();
  }
}
