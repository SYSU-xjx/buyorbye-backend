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

  @Test
  void plazaFiltersAndDetailsRespectPublicationScope() throws Exception {
    var author = account();
    var viewer = account();
    long milkTea = referenceId("channels", "milk-tea");
    long digital = referenceId("channels", "digital");
    long food = referenceId("channels", "food");
    long foodTag = referenceId("tags", "food");
    jdbc.update("INSERT INTO user_channels(user_id,channel_id) VALUES (?,?)", viewer.id, milkTea);
    long multiChannel = plazaFixture(author.id, "PLAZA", "OPEN", null, milkTea, digital);
    long otherChannel = plazaFixture(author.id, "PLAZA", "OPEN", null, food);
    long draft = plazaFixture(author.id, "PLAZA", "DRAFT", null, milkTea);
    long withdrawn = plazaFixture(author.id, "PLAZA", "WITHDRAWN", null, milkTea);
    String invite = UUID.randomUUID().toString().substring(0, 8);
    jdbc.update("INSERT INTO app_groups(owner_user_id,name,invite_code) VALUES (?,?,?)",
        author.id, "Private group", invite);
    long group = jdbc.queryForObject("SELECT id FROM app_groups WHERE invite_code=?", Long.class, invite);
    long groupProposal = plazaFixture(author.id, "GROUP", "OPEN", group, milkTea);
    jdbc.update("INSERT INTO proposal_tags(proposal_id,tag_id) VALUES (?,?)", multiChannel, foodTag);
    jdbc.update("INSERT INTO purchase_proposal_images(proposal_id,image_url,display_order) VALUES (?,?,1)",
        multiChannel, "https://example.test/milk-tea.jpg");

    String feed = "/api/plaza";
    request("GET", feed, null, null, 401);
    assertThat(itemIds(request("GET", feed, null, viewer.access, 200)))
        .containsSequence(otherChannel, multiChannel)
        .doesNotHaveDuplicates()
        .doesNotContain(draft, withdrawn, groupProposal);
    var mine = request("GET", feed + "?scope=MINE", null, viewer.access, 200);
    assertThat(itemIds(mine)).containsExactly(multiChannel);
    assertThat(mine.path("total").asLong()).isEqualTo(1);
    assertThat(itemIds(request("GET", feed + "?scope=MINE", null, author.access, 200))).isEmpty();
    assertThat(itemIds(request("GET", feed + "?channelId=" + digital, null, viewer.access, 200)))
        .contains(multiChannel);
    assertThat(itemIds(request("GET", feed + "?scope=MINE&channelId=" + digital, null, viewer.access, 200)))
        .isEmpty();
    assertThat(itemIds(request("GET", feed + "?scope=MINE&channelId=" + milkTea + "&tagId=" + foodTag,
        null, viewer.access, 200))).containsExactly(multiChannel);
    assertThat(itemIds(request("GET", feed + "?scope=MINE&size=1&page=2", null, viewer.access, 200)))
        .isEmpty();
    request("GET", feed + "?size=0", null, viewer.access, 400);

    var subscriptions = request("GET", "/api/channels?scope=MINE", null, viewer.access, 200);
    assertThat(subscriptions).hasSize(1);
    assertThat(subscriptions.get(0).path("id").asLong()).isEqualTo(milkTea);
    assertThat(subscriptions.get(0).path("subscribed").asBoolean()).isTrue();
    assertThat(request("GET", "/api/tags", null, viewer.access, 200)).isNotEmpty();
    var detail = request("GET", feed + "/" + multiChannel, null, viewer.access, 200);
    assertThat(detail.path("proposal").path("participationType").asText()).isEqualTo("BINARY");
    assertThat(detail.path("proposal").path("amount").decimalValue()).isEqualByComparingTo("24.00");
    assertThat(detail.path("channels")).hasSize(2);
    assertThat(detail.path("tags").get(0).path("id").asLong()).isEqualTo(foodTag);
    assertThat(detail.path("images").get(0).asText()).endsWith("milk-tea.jpg");
    for (long id : List.of(draft, withdrawn, groupProposal)) {
      request("GET", feed + "/" + id, null, viewer.access, 404);
    }
  }

  private long referenceId(String table, String slug) {
    return jdbc.queryForObject("SELECT id FROM " + table + " WHERE slug=?", Long.class, slug);
  }

  private List<Long> itemIds(JsonNode page) {
    List<Long> ids = new ArrayList<>();
    page.path("items").forEach(item -> ids.add(item.path("id").asLong()));
    return ids;
  }

  private long plazaFixture(long creator, String scope, String status, Long groupId, long... channels) {
    String key = UUID.randomUUID().toString();
    jdbc.update("""
        INSERT INTO purchase_proposals(creator_user_id, publication_scope, proposal_status, group_id,
          item_name, amount, currency, purchase_reason, client_request_id, published_at, deadline_at)
        VALUES (?, ?, ?, ?, 'Milk tea', 24.00, 'HKD', 'Trying a new flavor', ?,
          '2026-11-06 10:00:00', '2026-11-07 10:00:00')
        """, creator, scope, status, groupId, key);
    long id = jdbc.queryForObject(
        "SELECT id FROM purchase_proposals WHERE creator_user_id=? AND client_request_id=?",
        Long.class, creator, key);
    for (long channel : channels) {
      jdbc.update("INSERT INTO purchase_proposal_channels(proposal_id,channel_id) VALUES (?,?)", id, channel);
    }
    return id;
  }

  @Test
  void plazaWatchesAndCommentsUpdateCountsWithoutCastingVotes() throws Exception {
    var author = account();
    var viewer = account();
    long channel = referenceId("channels", "milk-tea");
    long proposal = plazaFixture(author.id, "PLAZA", "OPEN", null, channel);
    long draft = plazaFixture(author.id, "PLAZA", "DRAFT", null, channel);
    String path = "/api/purchase-proposals/" + proposal;
    String comments = path + "/comments";
    String watches = path + "/watches";
    request("PUT", watches, null, null, 401);
    request("PUT", "/api/purchase-proposals/" + draft + "/watches", null, viewer.access, 404);
    request("GET", "/api/purchase-proposals/" + draft + "/comments", null, viewer.access, 404);

    var executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(() -> request("PUT", watches, null, viewer.access, 200));
      var retry = executor.submit(() -> request("PUT", watches, null, viewer.access, 200));
      for (var result : List.of(first.get(10, TimeUnit.SECONDS), retry.get(10, TimeUnit.SECONDS))) {
        assertThat(result.path("watching").asBoolean()).isTrue();
        assertThat(result.path("watchCount").asInt()).isEqualTo(1);
      }
    } finally {
      executor.shutdownNow();
    }
    assertThat(request("GET", "/api/plaza/" + proposal, null, viewer.access, 200)
        .path("proposal").path("watching").asBoolean()).isTrue();
    for (int attempt = 0; attempt < 2; attempt++) {
      var result = request("DELETE", watches, null, viewer.access, 200);
      assertThat(result.path("watching").asBoolean()).isFalse();
      assertThat(result.path("watchCount").asInt()).isZero();
    }

    request("POST", comments, "{\"content\":\"  \"}", viewer.access, 400);
    var root = request("POST", comments, "{\"content\":\" Good idea \"}", viewer.access, 201);
    long rootId = root.path("id").asLong();
    assertThat(root.path("content").asText()).isEqualTo("Good idea");
    assertThat(root.path("ownedByViewer").asBoolean()).isTrue();
    var reply = request("POST", comments,
        "{\"content\":\"Thanks!\",\"parentCommentId\":" + rootId + "}", author.access, 201);
    long replyId = reply.path("id").asLong();
    assertThat(reply.path("parentCommentId").asLong()).isEqualTo(rootId);
    request("POST", comments, "{\"content\":\"Nested\",\"parentCommentId\":" + replyId + "}",
        viewer.access, 400);
    long otherProposal = plazaFixture(author.id, "PLAZA", "OPEN", null, channel);
    request("POST", "/api/purchase-proposals/" + otherProposal + "/comments",
        "{\"content\":\"Wrong thread\",\"parentCommentId\":" + rootId + "}", viewer.access, 400);
    request("DELETE", comments + "/" + rootId, null, author.access, 403);
    request("DELETE", comments + "/" + rootId, null, viewer.access, 200);
    request("DELETE", comments + "/" + rootId, null, viewer.access, 200);
    var afterDelete = request("GET", comments, null, author.access, 200);
    assertThat(itemIds(afterDelete)).containsExactly(rootId, replyId);
    assertThat(afterDelete.path("items").get(0).path("deleted").asBoolean()).isTrue();
    assertThat(afterDelete.path("items").get(0).path("content").isNull()).isTrue();
    var detail = request("GET", "/api/plaza/" + proposal, null, author.access, 200).path("proposal");
    assertThat(detail.path("commentCount").asInt()).isEqualTo(1);
    assertThat(detail.path("watchCount").asInt()).isZero();
    assertThat(detail.path("voterCount").asInt()).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM decision_votes WHERE proposal_id=?", Integer.class, proposal)).isZero();
    request("DELETE", comments + "/" + replyId, null, author.access, 200);
    assertThat(itemIds(request("GET", comments, null, author.access, 200))).isEmpty();
  }

  @Test
  void plazaSubscriptionsAndRandomUseTheSameFiltersAsTheFeed() throws Exception {
    var author = account();
    var viewer = account();
    long milkTea = referenceId("channels", "milk-tea");
    long digital = referenceId("channels", "digital");
    String slug = "test-" + UUID.randomUUID();
    jdbc.update("INSERT INTO tags(slug,name) VALUES (?, 'Random test')", slug);
    long tag = referenceId("tags", slug);
    long visible = plazaFixture(author.id, "PLAZA", "OPEN", null, milkTea, digital);
    long draft = plazaFixture(author.id, "PLAZA", "DRAFT", null, milkTea);
    long withdrawn = plazaFixture(author.id, "PLAZA", "WITHDRAWN", null, milkTea);
    for (long id : List.of(visible, draft, withdrawn)) {
      jdbc.update("INSERT INTO proposal_tags(proposal_id,tag_id) VALUES (?,?)", id, tag);
    }

    String subscriptions = "/api/channels/subscriptions";
    String mine = "/api/channels?scope=MINE";
    String random = "/api/plaza/random?scope=MINE&tagId=" + tag;
    assertThat(request("GET", random, null, viewer.access, 200).isNull()).isTrue();
    String channels = "{\"channelIds\":[" + digital + "," + milkTea + "]}";
    request("PUT", subscriptions, channels, null, 401);
    request("PUT", subscriptions, channels, viewer.access, 200);
    var before = jdbc.queryForObject("""
        SELECT subscribed_at FROM user_channels WHERE user_id=? AND channel_id=?
        """, LocalDateTime.class, viewer.id, milkTea);
    request("PUT", subscriptions, "{\"channelIds\":[" + milkTea + "," + digital + "]}",
        viewer.access, 200);
    var updated = request("GET", mine, null, viewer.access, 200);
    assertThat(updated.get(0).path("id").asLong()).isEqualTo(milkTea);
    assertThat(updated.get(1).path("id").asLong()).isEqualTo(digital);
    assertThat(jdbc.queryForObject("""
        SELECT subscribed_at FROM user_channels WHERE user_id=? AND channel_id=?
        """, LocalDateTime.class, viewer.id, milkTea)).isEqualTo(before);
    request("PUT", subscriptions, "{\"channelIds\":[" + milkTea + "," + milkTea + "]}",
        viewer.access, 400);
    request("PUT", subscriptions, "{\"channelIds\":[" + milkTea + ",99999999]}",
        viewer.access, 400);
    assertThat(request("GET", mine, null, viewer.access, 200)).isEqualTo(updated);
    assertThat(request("GET", mine, null, author.access, 200)).isEmpty();

    assertThat(request("GET", random + "&channelId=" + milkTea, null, viewer.access, 200)
        .path("id").asLong()).isEqualTo(visible);
    assertThat(request("GET", random + "&excludeProposalId=" + visible, null, viewer.access, 200)
        .isNull()).isTrue();
    request("PUT", subscriptions, "{\"channelIds\":[]}", viewer.access, 200);
    assertThat(request("GET", mine, null, viewer.access, 200)).isEmpty();
    assertThat(request("GET", random, null, viewer.access, 200).isNull()).isTrue();
  }

  @Test
  void plazaFlowersAndAuthorUpdatesStayOnVisibleProposals() throws Exception {
    var author = account();
    var viewer = account();
    long channel = referenceId("channels", "milk-tea");
    long proposal = plazaFixture(author.id, "PLAZA", "OPEN", null, channel);
    long draft = plazaFixture(author.id, "PLAZA", "DRAFT", null, channel);
    String path = "/api/purchase-proposals/" + proposal;
    String draftPath = "/api/purchase-proposals/" + draft;
    request("PUT", path + "/flowers", null, author.access, 403);
    request("PUT", draftPath + "/flowers", null, viewer.access, 404);
    for (int attempt = 0; attempt < 2; attempt++) {
      var result = request("PUT", path + "/flowers", null, viewer.access, 200);
      assertThat(result.path("sent").asBoolean()).isTrue();
      assertThat(result.path("flowerCount").asLong()).isEqualTo(1);
    }
    var flowers = request("GET", "/api/plaza/" + proposal, null, author.access, 200).path("flowers");
    assertThat(flowers.path("sent").asBoolean()).isFalse();
    assertThat(flowers.path("flowerCount").asLong()).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT receiver_user_id FROM flowers WHERE proposal_id=?", Long.class, proposal))
        .isEqualTo(author.id);

    String updateBody = "{\"content\":\" Price dropped this morning \"}";
    request("POST", path + "/updates", updateBody, viewer.access, 403);
    request("POST", draftPath + "/updates", updateBody, author.access, 404);
    var update = request("POST", path + "/updates", updateBody, author.access, 201);
    assertThat(update.path("content").asText()).isEqualTo("Price dropped this morning");
    var updates = request("GET", path + "/updates", null, viewer.access, 200);
    assertThat(updates.path("total").asLong()).isEqualTo(1);
    assertThat(updates.path("items").get(0)).isEqualTo(update);
    assertThat(jdbc.queryForObject(
        "SELECT voter_count FROM purchase_proposals WHERE id=?", Integer.class, proposal)).isZero();
  }

  @Test
  void plazaShowsOnlyLatestVoteReasonsWithoutChangingVotes() throws Exception {
    var author = account();
    var viewer = account();
    var other = account();
    long channel = referenceId("channels", "milk-tea");
    long proposal = plazaFixture(author.id, "PLAZA", "OPEN", null, channel);
    long draft = plazaFixture(author.id, "PLAZA", "DRAFT", null, channel);
    String path = "/api/plaza/" + proposal + "/vote-reasons";
    request("GET", path, null, null, 401);
    request("GET", "/api/plaza/" + draft + "/vote-reasons", null, viewer.access, 404);
    for (int i = 0; i < 3; i++) {
      jdbc.update("""
          INSERT INTO decision_votes(proposal_id, voter_user_id, choice, reason,
            reputation_score_snapshot, weight_snapshot, client_request_id, cast_at)
          VALUES (?,?,?,?,0,1,?,?)
          """, proposal, i == 2 ? other.id : viewer.id, i == 1 ? 0 : 1,
          List.of("Original reason", "Revised reason", "Another voter").get(i),
          UUID.randomUUID().toString(), "2002-01-01 00:00:0" + i);
    }
    var first = request("GET", path + "?size=1", null, viewer.access, 200);
    var second = request("GET", path + "?size=1&page=2", null, viewer.access, 200);
    assertThat(first.path("total").asLong()).isEqualTo(2);
    assertThat(first.path("items").get(0).path("reason").asText()).isEqualTo("Another voter");
    var latest = second.path("items").get(0);
    assertThat(latest.path("choice").asText()).isEqualTo("NOT_BUY");
    assertThat(latest.path("reason").asText()).isEqualTo("Revised reason");
    assertThat(latest.path("ownedByViewer").asBoolean()).isTrue();
    String detailPath = "/api/plaza/" + proposal;
    var detail = request("GET", detailPath, null, viewer.access, 200);
    assertThat(detail.path("myVote")).isEqualTo(latest);
    assertThat(detail.path("voteTally")).isEqualTo(json.readTree("""
        [{"choice":"BUY","optionId":null,"voteCount":1},
         {"choice":"NOT_BUY","optionId":null,"voteCount":1}]
        """));
    assertThat(request("GET", detailPath, null, author.access, 200).path("myVote").isNull()).isTrue();
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM decision_votes WHERE proposal_id=?", Integer.class, proposal)).isEqualTo(3);
  }

  @Test
  void plazaTallyCountsOptionVotesByOptionId() throws Exception {
    var author = account();
    var viewer = account();
    long channel = referenceId("channels", "digital");
    long proposal = plazaFixture(author.id, "PLAZA", "OPEN", null, channel);
    jdbc.update("""
        UPDATE purchase_proposals SET participation_type='OPTION_VOTE',
          item_name=NULL, poll_question='Which one?' WHERE id=?
        """, proposal);
    jdbc.update("""
        INSERT INTO poll_options(proposal_id,option_label,option_text,display_order)
        VALUES (?,'A','First',1), (?,'B','Second',2)
        """, proposal, proposal);
    long selected = jdbc.queryForObject(
        "SELECT id FROM poll_options WHERE proposal_id=? AND display_order=2", Long.class, proposal);
    jdbc.update("""
        INSERT INTO decision_votes(proposal_id, voter_user_id, vote_type, option_id, reason,
          reputation_score_snapshot, weight_snapshot, client_request_id, cast_at)
        VALUES (?,?,'OPTION',?,'Second fits',0,1,?,'2002-01-01 00:00:00')
        """, proposal, viewer.id, selected, UUID.randomUUID().toString());
    var detail = request("GET", "/api/plaza/" + proposal, null, viewer.access, 200);
    assertThat(detail.path("proposal").path("participationType").asText()).isEqualTo("OPTION");
    assertThat(detail.path("options")).hasSize(2);
    var tally = detail.path("voteTally");
    assertThat(tally).hasSize(1);
    assertThat(tally.get(0).path("optionId").asLong()).isEqualTo(selected);
    assertThat(tally.get(0).path("choice").isNull()).isTrue();
    assertThat(tally.get(0).path("voteCount").asLong()).isEqualTo(1);
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

  byte[] picture(String format) throws Exception {
    var image = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var out = new java.io.ByteArrayOutputStream();
    assertThat(javax.imageio.ImageIO.write(image, format, out)).isTrue();
    return out.toByteArray();
  }

  JsonNode upload(Login user, org.springframework.mock.web.MockMultipartFile file, int code)
      throws Exception {
    var req = multipart("/api/users/me/avatar").file(file);
    if (user != null) req.header("Authorization", "Bearer " + user.access);
    var response = mvc.perform(req).andReturn().getResponse();
    assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(code);
    var envelope = json.readTree(response.getContentAsString());
    assertThat(envelope.path("code").asInt()).isEqualTo(code);
    assertThat(envelope.has("data")).isTrue();
    return envelope.get("data");
  }

  @Test
  void avatarUploadStoresPixelsAndUpdatesProfile() throws Exception {
    var a = account();
    var file =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "../../evil.svg", "text/plain", picture("png"));
    upload(null, file, 401);
    var data = upload(a, file, 200);
    String url = data.path("avatarUrl").asText();
    assertThat(url).matches("http://localhost/uploads/avatars/[0-9a-f]{32}\\.png");
    assertThat(request("GET", "/api/users/me", null, a.access, 200).path("avatarUrl").asText())
        .isEqualTo(url);
    assertThat(jdbc.queryForObject("SELECT avatar_url FROM users WHERE id=?", String.class, a.id))
        .isEqualTo(url);
    String path = java.net.URI.create(url).getPath();
    var image = mvc.perform(get(path)).andReturn().getResponse();
    assertThat(image.getStatus()).isEqualTo(200);
    assertThat(image.getContentType()).isEqualTo("image/png");
    assertThat(
            javax.imageio.ImageIO.read(
                    new java.io.ByteArrayInputStream(image.getContentAsByteArray()))
                .getWidth())
        .isEqualTo(4);
    assertThat(mvc.perform(head(path)).andReturn().getResponse().getStatus()).isEqualTo(200);
    request("GET", "/uploads/avatars/not-an-image.txt", null, null, 404);
    assertThatThrownBy(() -> avatarStorage.find("../.env"))
        .isInstanceOf(com.hku.money.common.ApiException.class);

    var jpeg =
        upload(
            a,
            new org.springframework.mock.web.MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", picture("jpeg")),
            200);
    assertThat(jpeg.path("avatarUrl").asText()).endsWith(".jpg").isNotEqualTo(url);
    assertThat(request("GET", "/api/users/me", null, a.access, 200).path("avatarUrl").asText())
        .isEqualTo(jpeg.path("avatarUrl").asText());
  }

  @Test
  void invalidAvatarsAndRolledBackUploadsDoNotChangeProfile() throws Exception {
    var a = account();
    upload(
        a,
        new org.springframework.mock.web.MockMultipartFile(
            "file", "x.png", "image/png", new byte[0]),
        400);
    upload(
        a,
        new org.springframework.mock.web.MockMultipartFile(
            "file",
            "x.png",
            "image/png",
            "not a picture".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        400);
    upload(
        a,
        new org.springframework.mock.web.MockMultipartFile(
            "file", "x.gif", "image/gif", picture("gif")),
        415);
    upload(
        a,
        new org.springframework.mock.web.MockMultipartFile(
            "file", "x.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]),
        413);
    var missing =
        mvc.perform(multipart("/api/users/me/avatar").header("Authorization", "Bearer " + a.access))
            .andReturn()
            .getResponse();
    assertThat(missing.getStatus()).isEqualTo(400);
    assertThat(json.readTree(missing.getContentAsString()).path("code").asInt()).isEqualTo(400);
    long before;
    try (var files = java.nio.file.Files.list(AVATARS)) {
      before = files.count();
    }
    var transaction =
        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    var valid =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "photo.png", "image/png", picture("png"));
    assertThatThrownBy(
            () ->
                transaction.execute(
                    status -> {
                      avatars.upload(a.id, valid, "http://localhost");
                      throw new IllegalStateException("force rollback");
                    }))
        .isInstanceOf(IllegalStateException.class);
    try (var files = java.nio.file.Files.list(AVATARS)) {
      assertThat(files.count()).isEqualTo(before);
    }
    assertThat(jdbc.queryForObject("SELECT avatar_url FROM users WHERE id=?", String.class, a.id))
        .isNull();
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
