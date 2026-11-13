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
class ProposalIntegrationTest {
  static final java.nio.file.Path AVATARS = avatarDirectory();

  static java.nio.file.Path avatarDirectory() {
    try {
      return java.nio.file.Files.createTempDirectory("buyorbye-avatar-test-");
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  @AfterAll
  static void cleanAvatars() throws Exception {
    try (var files = java.nio.file.Files.list(AVATARS)) {
      for (var path : files.toList()) {
        if (!path.getParent().equals(AVATARS))
          throw new IllegalStateException("Unexpected test file");
        java.nio.file.Files.deleteIfExists(path);
      }
    }
    java.nio.file.Files.deleteIfExists(AVATARS);
  }

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
    r.add("spring.datasource.username", MYSQL::getUsername);
    r.add("spring.datasource.password", MYSQL::getPassword);
    r.add("app.jwt-secret", () -> "test-only-secret-at-least-32-bytes-not-for-production");
    r.add("app.avatar.storage-dir", AVATARS::toString);
    r.add("app.proposal.image-dir", AVATARS::toString);
    r.add("app.proposal.jobs-enabled", () -> "false");
    r.add("app.avatar.public-base-url", () -> "");
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
  @Autowired com.hku.money.user.service.AvatarService avatars;
  @Autowired com.hku.money.user.service.AvatarStorage avatarStorage;

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

  @Autowired com.hku.money.proposal.service.SettlementService settlements;
  @Autowired com.hku.money.exchange.ExchangeService exchange;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  com.hku.money.exchange.RateProvider rates;

  Map<String, Object> body(String approval, String mode, int count, String type) {
    Map<String, Object> r = new HashMap<>();
    r.put("clientRequestId", UUID.randomUUID().toString());
    r.put("participationType", type);
    r.put("approvalMode", approval);
    r.put("settlementMode", mode);
    r.put("minimumVoterCount", count);
    r.put("publicationScope", "PLAZA");
    r.put("purchaseReason", "确实需要");
    r.put(
        "tagIds",
        jdbc.queryForList("SELECT id FROM tags WHERE is_active=1 ORDER BY id LIMIT 1", Long.class));
    r.put(
        "channelIds",
        jdbc.queryForList(
            "SELECT id FROM channels WHERE is_active=1 ORDER BY id LIMIT 1", Long.class));
    if (type.equals("BINARY")) {
      r.put("itemName", "耳机");
      r.put("amount", new BigDecimal("100.00"));
      r.put("currency", "HKD");
    } else {
      r.put("pollQuestion", "选哪一个");
      r.put("options", List.of(Map.of("text", "A"), Map.of("text", "B")));
    }
    return r;
  }

  long draft(Login author, Map<String, Object> body) throws Exception {
    return request(
            "POST", "/api/purchase-proposals", json.writeValueAsString(body), author.access, 201)
        .path("proposal")
        .path("id")
        .asLong();
  }

  long publish(Login author, Map<String, Object> body) throws Exception {
    long id = draft(author, body);
    request("POST", "/api/purchase-proposals/" + id + "/publish", "{}", author.access, 200);
    return id;
  }

  JsonNode vote(long id, Login voter, String choice, Long option, String key, int expected)
      throws Exception {
    Map<String, Object> r = new HashMap<>();
    r.put("clientRequestId", key);
    r.put("reason", "有充分理由");
    if (choice != null) r.put("choice", choice);
    if (option != null) r.put("optionId", option);
    return request(
        "POST",
        "/api/purchase-proposals/" + id + "/votes",
        json.writeValueAsString(r),
        voter.access,
        expected);
  }

  String state(long id) {
    return jdbc.queryForObject(
        "SELECT proposal_status FROM purchase_proposals WHERE id=?", String.class, id);
  }

  long score(Login user) {
    return jdbc.queryForObject(
        "SELECT score FROM reputation_accounts WHERE user_id=?", Long.class, user.id);
  }

  void expire(long id) {
    jdbc.update("UPDATE purchase_proposals SET deadline_at='2026-11-06 11:59:59' WHERE id=?", id);
  }

  @Test
  void groupLifecycleKeepsOneMembershipAndOwnerAuthority() throws Exception {
    var owner = account();
    var member = account();
    var outsider = account();
    var g = request("POST", "/api/groups", "{\"name\":\"讨论群\"}", owner.access, 201);
    long id = g.path("id").asLong();
    String code = g.path("inviteCode").asText();
    assertThat(code).matches("[0-9]{8}");
    request("GET", "/api/groups/" + id, null, outsider.access, 404);
    String join = json.writeValueAsString(Map.of("inviteCode", code));
    for (int i = 0; i < 2; i++)
      assertThat(
              request("POST", "/api/groups/join", join, member.access, 200)
                  .path("memberCount")
                  .asInt())
          .isEqualTo(2);
    long relationship =
        jdbc.queryForObject(
            "SELECT id FROM group_members WHERE group_id=? AND user_id=?",
            Long.class,
            id,
            member.id);
    request("DELETE", "/api/groups/" + id + "/members/me", null, member.access, 200);
    request("POST", "/api/groups/join", join, member.access, 200);
    assertThat(
            jdbc.queryForObject(
                "SELECT id FROM group_members WHERE group_id=? AND user_id=?",
                Long.class,
                id,
                member.id))
        .isEqualTo(relationship);
    request("DELETE", "/api/groups/" + id + "/members/me", null, owner.access, 409);
    request("PATCH", "/api/groups/" + id, "{\"name\":\"越权\"}", member.access, 403);
    request(
        "PUT",
        "/api/groups/" + id + "/owner",
        json.writeValueAsString(Map.of("userId", member.id)),
        owner.access,
        200);
    request("POST", "/api/groups/" + id + "/invite", null, owner.access, 403);
    request("DELETE", "/api/groups/" + id + "/members/me", null, owner.access, 200);
    request("DELETE", "/api/groups/" + id, null, member.access, 200);
    request("DELETE", "/api/groups/" + id, null, member.access, 200);
    request("POST", "/api/groups/join", join, outsider.access, 400);
  }

  @Test
  void draftPrivacyPublicationValidationAndIdempotency() throws Exception {
    var author = account();
    var viewer = account();
    var r = body("MULTI", "TIME", 3, "BINARY");
    r.remove("itemName");
    long id = draft(author, r);
    assertThat(draft(author, r)).isEqualTo(id);
    request("GET", "/api/purchase-proposals/" + id, null, viewer.access, 404);
    request("POST", "/api/purchase-proposals/" + id + "/publish", "{}", author.access, 400);
    r.put("itemName", "耳机");
    r.put("channelIds", List.of());
    request("PUT", "/api/purchase-proposals/" + id, json.writeValueAsString(r), author.access, 200);
    request("POST", "/api/purchase-proposals/" + id + "/publish", "{}", author.access, 400);
    r.put("channelIds", body("MULTI", "TIME", 3, "BINARY").get("channelIds"));
    r.put("tagIds", List.of());
    request("PUT", "/api/purchase-proposals/" + id, json.writeValueAsString(r), author.access, 200);
    request("POST", "/api/purchase-proposals/" + id + "/publish", "{}", author.access, 400);
    r.put("tagIds", body("MULTI", "TIME", 3, "BINARY").get("tagIds"));
    request("PUT", "/api/purchase-proposals/" + id, json.writeValueAsString(r), author.access, 200);
    request(
        "POST",
        "/api/purchase-proposals/" + id + "/publish",
        "{\"durationHours\":0}",
        author.access,
        400);
    request("POST", "/api/purchase-proposals/" + id + "/publish", "{}", author.access, 200);
    String deadline =
        jdbc.queryForObject(
            "SELECT CAST(deadline_at AS CHAR) FROM purchase_proposals WHERE id=?",
            String.class,
            id);
    request(
        "POST",
        "/api/purchase-proposals/" + id + "/publish",
        "{\"durationHours\":48}",
        author.access,
        200);
    assertThat(
            jdbc.queryForObject(
                "SELECT CAST(deadline_at AS CHAR) FROM purchase_proposals WHERE id=?",
                String.class,
                id))
        .isEqualTo(deadline);
    request("GET", "/api/plaza/" + id, null, viewer.access, 200);
    vote(id, author, "BUY", null, UUID.randomUUID().toString(), 403);
    r.put("itemName", "更新标题");
    request("PUT", "/api/purchase-proposals/" + id, json.writeValueAsString(r), author.access, 200);
    vote(id, viewer, "BUY", null, UUID.randomUUID().toString(), 200);
    request("PUT", "/api/purchase-proposals/" + id, json.writeValueAsString(r), author.access, 409);
    request("DELETE", "/api/purchase-proposals/" + id, null, author.access, 409);
  }

  @Test
  void singleFirstVoteSettlesAndRetryDoesNotRewardAgain() throws Exception {
    var author = account();
    var voter = account();
    var late = account();
    long id = publish(author, body("SINGLE", "QUORUM", 1, "BINARY"));
    String key = UUID.randomUUID().toString();
    long event = vote(id, voter, "BUY", null, key, 200).path("id").asLong();
    assertThat(state(id)).isEqualTo("SETTLED");
    assertThat(score(voter)).isEqualTo(1);
    assertThat(vote(id, voter, "BUY", null, key, 200).path("id").asLong()).isEqualTo(event);
    vote(id, voter, "NOT_BUY", null, key, 409);
    vote(id, late, "BUY", null, UUID.randomUUID().toString(), 409);
    vote(id, voter, "NOT_BUY", null, UUID.randomUUID().toString(), 409);
    request("POST", "/api/purchase-proposals/" + id + "/withdraw", null, author.access, 409);
    settlements.settle(id);
    settlements.settle(id);
    assertThat(score(voter)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM reputation_ledger WHERE proposal_id=?", Long.class, id))
        .isEqualTo(1);
    var invalid = body("SINGLE", "TIME", 3, "BINARY");
    draftRejected(author, invalid);
  }

  void draftRejected(Login author, Map<String, Object> r) throws Exception {
    request("POST", "/api/purchase-proposals", json.writeValueAsString(r), author.access, 400);
  }

  @Test
  void weightedSettlementUsesSnapshotsAndLatestVotes() throws Exception {
    var author = account();
    var strong = account();
    var weak = account();
    jdbc.update("UPDATE reputation_accounts SET score=30 WHERE user_id=?", strong.id);
    long id = publish(author, body("MULTI", "TIME", 2, "BINARY"));
    vote(id, strong, "BUY", null, UUID.randomUUID().toString(), 200);
    jdbc.update("UPDATE reputation_accounts SET score=0 WHERE user_id=?", strong.id);
    vote(id, weak, "BUY", null, UUID.randomUUID().toString(), 200);
    vote(id, weak, "NOT_BUY", null, UUID.randomUUID().toString(), 200);
    assertThat(
            jdbc.queryForObject(
                "SELECT voter_count FROM purchase_proposals WHERE id=?", Integer.class, id))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM decision_votes WHERE proposal_id=?", Integer.class, id))
        .isEqualTo(3);
    assertThat(state(id)).isEqualTo("OPEN");
    expire(id);
    settlements.settle(id);
    assertThat(
            jdbc.queryForObject(
                "SELECT decision_result FROM purchase_proposals WHERE id=?", Integer.class, id))
        .isEqualTo(1);
    assertThat(score(strong)).isEqualTo(1);
    assertThat(score(weak)).isZero();
    assertThat(
            request(
                    "GET",
                    "/api/purchase-proposals/" + id + "/vote-reasons",
                    null,
                    author.access,
                    200)
                .path("total")
                .asInt())
        .isEqualTo(2);
  }

  @Test
  void optionTieUsesDisplayOrderAndRewardsOnlyWinningOption() throws Exception {
    var author = account();
    var first = account();
    var second = account();
    long id = publish(author, body("MULTI", "QUORUM", 2, "OPTION"));
    var opts =
        request("GET", "/api/purchase-proposals/" + id, null, first.access, 200).path("options");
    long a = opts.get(0).path("id").asLong(), b = opts.get(1).path("id").asLong();
    // Vote for B first: insertion order must not win the tie.
    vote(id, second, null, b, UUID.randomUUID().toString(), 200);
    vote(id, first, null, a, UUID.randomUUID().toString(), 200);
    assertThat(state(id)).isEqualTo("SETTLED");
    assertThat(
            jdbc.queryForObject(
                "SELECT winning_option_id FROM purchase_proposals WHERE id=?", Long.class, id))
        .isEqualTo(a);
    assertThat(score(first)).isEqualTo(1);
    assertThat(score(second)).isZero();
  }

  @Test
  void deadlineInsufficiencyWithdrawalAndBinaryTie() throws Exception {
    var author = account();
    var voter = account();
    var other = account();
    long empty = publish(author, body("SINGLE", "QUORUM", 1, "BINARY"));
    expire(empty);
    settlements.settle(empty);
    assertThat(state(empty)).isEqualTo("INSUFFICIENT_PARTICIPATION");
    long insufficient = publish(author, body("MULTI", "QUORUM", 3, "BINARY"));
    vote(insufficient, voter, "BUY", null, UUID.randomUUID().toString(), 200);
    expire(insufficient);
    settlements.settle(insufficient);
    assertThat(state(insufficient)).isEqualTo("INSUFFICIENT_PARTICIPATION");
    assertThat(score(voter)).isZero();
    long withdrawn = publish(author, body("MULTI", "TIME", 1, "BINARY"));
    vote(withdrawn, voter, "BUY", null, UUID.randomUUID().toString(), 200);
    request("POST", "/api/purchase-proposals/" + withdrawn + "/withdraw", null, author.access, 200);
    request("POST", "/api/purchase-proposals/" + withdrawn + "/withdraw", null, author.access, 200);
    expire(withdrawn);
    settlements.settle(withdrawn);
    assertThat(state(withdrawn)).isEqualTo("WITHDRAWN");
    assertThat(score(voter)).isZero();
    request("POST", "/api/purchase-proposals/" + withdrawn + "/publish", "{}", author.access, 409);
    vote(withdrawn, other, "BUY", null, UUID.randomUUID().toString(), 404);
    long tie = publish(author, body("MULTI", "QUORUM", 2, "BINARY"));
    vote(tie, voter, "BUY", null, UUID.randomUUID().toString(), 200);
    vote(tie, other, "NOT_BUY", null, UUID.randomUUID().toString(), 200);
    assertThat(
            jdbc.queryForObject(
                "SELECT decision_result FROM purchase_proposals WHERE id=?", Integer.class, tie))
        .isZero();
    assertThat(score(other)).isEqualTo(1);
  }

  @Test
  void groupCloseEndsProposalsAndLeavingPreservesEarlierVote() throws Exception {
    var author = account();
    var member = account();
    var outsider = account();
    var g = request("POST", "/api/groups", "{\"name\":\"群\"}", author.access, 201);
    long group = g.path("id").asLong();
    String code = g.path("inviteCode").asText();
    request(
        "POST",
        "/api/groups/join",
        json.writeValueAsString(Map.of("inviteCode", code)),
        member.access,
        200);
    var r = body("MULTI", "TIME", 2, "BINARY");
    r.put("publicationScope", "GROUP");
    r.put("groupId", group);
    r.put("channelIds", List.of());
    long draft = draft(author, r);
    request("POST", "/api/purchase-proposals/" + draft + "/publish", "{}", author.access, 400);
    r.put("minimumVoterCount", 1);
    request(
        "PUT", "/api/purchase-proposals/" + draft, json.writeValueAsString(r), author.access, 200);
    request("POST", "/api/purchase-proposals/" + draft + "/publish", "{}", author.access, 200);
    request("GET", "/api/purchase-proposals/" + draft, null, outsider.access, 404);
    request("GET", "/api/plaza/" + draft, null, member.access, 404);
    vote(draft, member, "BUY", null, UUID.randomUUID().toString(), 200);
    request("DELETE", "/api/groups/" + group + "/members/me", null, member.access, 200);
    vote(draft, member, "NOT_BUY", null, UUID.randomUUID().toString(), 404);
    request("DELETE", "/api/groups/" + group, null, author.access, 200);
    assertThat(
            jdbc.queryForObject(
                "SELECT deadline_at<=? FROM purchase_proposals WHERE id=?",
                Boolean.class,
                "2026-11-06 12:00:00",
                draft))
        .isTrue();
    settlements.settle(draft);
    assertThat(state(draft)).isEqualTo("SETTLED");
    assertThat(score(member)).isEqualTo(1);
    request("GET", "/api/purchase-proposals/" + draft, null, member.access, 404);
    request("GET", "/api/purchase-proposals/" + draft, null, author.access, 200);
  }

  @Test
  void concurrentSingleVotesAdmitOnlyOne() throws Exception {
    var author = account();
    var a = account();
    var b = account();
    long id = publish(author, body("SINGLE", "QUORUM", 1, "BINARY"));
    var start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> fs = new ArrayList<>();
      for (var user : List.of(a, b))
        fs.add(
            pool.submit(
                () -> {
                  start.await();
                  return mvc.perform(
                          post("/api/purchase-proposals/" + id + "/votes")
                              .header("Authorization", "Bearer " + user.access)
                              .contentType("application/json")
                              .content(
                                  json.writeValueAsString(
                                      Map.of(
                                          "clientRequestId",
                                          UUID.randomUUID().toString(),
                                          "choice",
                                          "BUY",
                                          "reason",
                                          "充分理由"))))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      start.countDown();
      assertThat(List.of(fs.get(0).get(15, TimeUnit.SECONDS), fs.get(1).get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    } finally {
      pool.shutdownNow();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM decision_votes WHERE proposal_id=?", Long.class, id))
        .isEqualTo(1);
    assertThat(score(a) + score(b)).isEqualTo(1);
  }

  @Test
  void concurrentQuorumVotesDoNotOverrunTarget() throws Exception {
    var author = account();
    var a = account();
    var b = account();
    var c = account();
    long id = publish(author, body("MULTI", "QUORUM", 2, "BINARY"));
    var start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(3);
    try {
      List<Future<Integer>> fs = new ArrayList<>();
      for (var user : List.of(a, b, c))
        fs.add(
            pool.submit(
                () -> {
                  start.await();
                  return mvc.perform(
                          post("/api/purchase-proposals/" + id + "/votes")
                              .header("Authorization", "Bearer " + user.access)
                              .contentType("application/json")
                              .content(
                                  json.writeValueAsString(
                                      Map.of(
                                          "clientRequestId",
                                          UUID.randomUUID().toString(),
                                          "choice",
                                          "BUY",
                                          "reason",
                                          "充分理由"))))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (var f : fs) statuses.add(f.get(15, TimeUnit.SECONDS));
      assertThat(statuses).containsExactlyInAnyOrder(200, 200, 409);
    } finally {
      pool.shutdownNow();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT voter_count FROM purchase_proposals WHERE id=?", Integer.class, id))
        .isEqualTo(2);
    assertThat(score(a) + score(b) + score(c)).isEqualTo(2);
  }

  @Test
  void rateSnapshotsFallbackAndRemainImmutable() throws Exception {
    var author = account();
    var voter = account();
    var r = body("SINGLE", "QUORUM", 1, "BINARY");
    r.put("currency", "CNY");
    long id = publish(author, r);
    vote(id, voter, "BUY", null, UUID.randomUUID().toString(), 200);
    var date = LocalDate.of(2026, 11, 6);
    var rate =
        new com.hku.money.exchange.RateProvider.Rate(
            new BigDecimal("1.1000000000"), date.minusDays(1), "FRANKFURTER_ECB");
    org.mockito.Mockito.when(rates.fetch("CNY", date)).thenReturn(Optional.of(rate));
    exchange.fill(id);
    assertThat(
            jdbc.queryForObject(
                "SELECT converted_amount FROM exchange_rate_snapshots WHERE proposal_id=?",
                BigDecimal.class,
                id))
        .isEqualByComparingTo("110.00");
    org.mockito.Mockito.when(rates.fetch("CNY", date))
        .thenReturn(
            Optional.of(
                new com.hku.money.exchange.RateProvider.Rate(
                    new BigDecimal("2.0"), date, "FRANKFURTER_ECB")));
    exchange.fill(id);
    assertThat(
            jdbc.queryForObject(
                "SELECT converted_amount FROM exchange_rate_snapshots WHERE proposal_id=?",
                BigDecimal.class,
                id))
        .isEqualByComparingTo("110.00");
    r.put("clientRequestId", UUID.randomUUID().toString());
    long fallback = publish(author, r);
    vote(fallback, voter, "BUY", null, UUID.randomUUID().toString(), 200);
    org.mockito.Mockito.when(rates.fetch("CNY", date)).thenReturn(Optional.empty());
    exchange.fill(fallback);
    assertThat(
            jdbc.queryForObject(
                "SELECT converted_amount FROM exchange_rate_snapshots WHERE proposal_id=?",
                BigDecimal.class,
                fallback))
        .isEqualByComparingTo("110.00");
    r.put("clientRequestId", UUID.randomUUID().toString());
    r.put("currency", "ZZZ");
    long missing = publish(author, r);
    vote(missing, voter, "BUY", null, UUID.randomUUID().toString(), 200);
    exchange.fill(missing);
    assertThat(state(missing)).isEqualTo("SETTLED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM exchange_rate_snapshots WHERE proposal_id=?",
                Integer.class,
                missing))
        .isZero();
  }

  @Test
  void imagesArePrivateLimitedAndRemovedWithDraft() throws Exception {
    var author = account();
    var outsider = account();
    long id = draft(author, body("MULTI", "TIME", 3, "BINARY"));
    var pixels = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var output = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(pixels, "png", output);
    String path = null;
    long first = 0;
    for (int i = 0; i < 4; i++) {
      var res =
          mvc.perform(
                  multipart("/api/purchase-proposals/" + id + "/images")
                      .file(
                          new org.springframework.mock.web.MockMultipartFile(
                              "file", "photo.png", "image/png", output.toByteArray()))
                      .header("Authorization", "Bearer " + author.access))
              .andReturn();
      assertThat(res.getResponse().getStatus())
          .as(res.getResponse().getContentAsString())
          .isEqualTo(201);
      var image = json.readTree(res.getResponse().getContentAsString()).path("data");
      if (i == 0) {
        path = image.path("imageUrl").asText();
        first = image.path("id").asLong();
      }
    }
    assertThat(
            mvc.perform(
                    multipart("/api/purchase-proposals/" + id + "/images")
                        .file(
                            new org.springframework.mock.web.MockMultipartFile(
                                "file", "photo.png", "image/png", output.toByteArray()))
                        .header("Authorization", "Bearer " + author.access))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(400);
    mvc.perform(
            get("/api/purchase-proposals/" + id + "/images/" + first + "/content")
                .header("Authorization", "Bearer " + outsider.access))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isNotFound());
    mvc.perform(get(path).header("Authorization", "Bearer " + author.access))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    request("DELETE", "/api/purchase-proposals/" + id, null, author.access, 200);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM purchase_proposal_images WHERE proposal_id=?",
                Long.class,
                id))
        .isZero();
    try (var files = java.nio.file.Files.list(AVATARS)) {
      assertThat(files.count()).isZero();
    }
  }

  @Test
  void concurrentSettlementsSharingVotersKeepBothRewards() throws Exception {
    var author = account();
    var a = account();
    var b = account();
    long first = publish(author, body("MULTI", "QUORUM", 2, "BINARY"));
    long second = publish(author, body("MULTI", "QUORUM", 2, "BINARY"));
    vote(first, a, "BUY", null, UUID.randomUUID().toString(), 200);
    vote(second, b, "BUY", null, UUID.randomUUID().toString(), 200);
    var start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      var one =
          pool.submit(
              () -> {
                start.await();
                return vote(first, b, "BUY", null, UUID.randomUUID().toString(), 200);
              });
      var two =
          pool.submit(
              () -> {
                start.await();
                return vote(second, a, "BUY", null, UUID.randomUUID().toString(), 200);
              });
      start.countDown();
      one.get(20, TimeUnit.SECONDS);
      two.get(20, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
    assertThat(state(first)).isEqualTo("SETTLED");
    assertThat(state(second)).isEqualTo("SETTLED");
    assertThat(score(a)).isEqualTo(2);
    assertThat(score(b)).isEqualTo(2);
  }

  @Test
  void voteAndWithdrawalRaceNeverLeavesVotesOnAnAlreadyWithdrawnProposal() throws Exception {
    var author = account();
    var voter = account();
    long id = publish(author, body("SINGLE", "QUORUM", 1, "BINARY"));
    var start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      var one =
          pool.submit(
              () -> {
                start.await();
                return mvc.perform(
                        post("/api/purchase-proposals/" + id + "/withdraw")
                            .header("Authorization", "Bearer " + author.access))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      var two =
          pool.submit(
              () -> {
                start.await();
                return mvc.perform(
                        post("/api/purchase-proposals/" + id + "/votes")
                            .header("Authorization", "Bearer " + voter.access)
                            .contentType("application/json")
                            .content(
                                json.writeValueAsString(
                                    Map.of(
                                        "clientRequestId",
                                        UUID.randomUUID().toString(),
                                        "choice",
                                        "BUY",
                                        "reason",
                                        "充分理由"))))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      start.countDown();
      int withdrawal = one.get(20, TimeUnit.SECONDS), voting = two.get(20, TimeUnit.SECONDS);
      if (state(id).equals("WITHDRAWN")) {
        assertThat(withdrawal).isEqualTo(200);
        assertThat(voting).isIn(404, 409);
        assertThat(score(voter)).isZero();
      } else {
        assertThat(state(id)).isEqualTo("SETTLED");
        assertThat(voting).isEqualTo(200);
        assertThat(withdrawal).isEqualTo(409);
        assertThat(score(voter)).isEqualTo(1);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void singleOptionAndHkdSnapshotUseExistingFields() throws Exception {
    var author = account();
    var voter = account();
    var r = body("SINGLE", "QUORUM", 1, "OPTION");
    long id = publish(author, r);
    long option =
        request("GET", "/api/purchase-proposals/" + id, null, voter.access, 200)
            .path("options")
            .get(1)
            .path("id")
            .asLong();
    vote(id, voter, null, option, UUID.randomUUID().toString(), 200);
    assertThat(
            jdbc.queryForObject(
                "SELECT winning_option_id FROM purchase_proposals WHERE id=?", Long.class, id))
        .isEqualTo(option);
    assertThat(score(voter)).isEqualTo(1);
    long buy = publish(author, body("SINGLE", "QUORUM", 1, "BINARY"));
    vote(buy, voter, "BUY", null, UUID.randomUUID().toString(), 200);
    var date = LocalDate.of(2026, 11, 6);
    org.mockito.Mockito.when(rates.fetch("HKD", date))
        .thenReturn(
            Optional.of(
                new com.hku.money.exchange.RateProvider.Rate(
                    BigDecimal.ONE, date, "HKD_IDENTITY")));
    exchange.fill(buy);
    assertThat(
            jdbc.queryForObject(
                "SELECT rate FROM exchange_rate_snapshots WHERE proposal_id=?",
                BigDecimal.class,
                buy))
        .isEqualByComparingTo("1");
  }
}
