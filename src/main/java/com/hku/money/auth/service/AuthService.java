package com.hku.money.auth.service;

import com.hku.money.auth.dto.AuthRequests.*;
import com.hku.money.auth.entity.*;
import com.hku.money.auth.mapper.AuthMapper;
import com.hku.money.common.ApiException;
import com.hku.money.security.*;
import com.hku.money.user.dto.UserProfile;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final AuthMapper mapper;
  private final PasswordEncoder encoder;
  private final TokenService tokens;
  private final Clock clock;
  private final int refreshDays;
  private final String dummy;
  private final AccountGuard guard;

  public AuthService(
      AuthMapper mapper,
      PasswordEncoder encoder,
      TokenService tokens,
      Clock clock,
      @Value("${app.refresh-token-days}") int refreshDays,
      AccountGuard guard) {
    this.mapper = mapper;
    this.encoder = encoder;
    this.tokens = tokens;
    this.clock = clock;
    this.refreshDays = refreshDays;
    this.guard = guard;
    dummy = encoder.encode(TokenService.random());
  }

  public record Tokens(
      String accessToken,
      String refreshToken,
      String tokenType,
      long expiresIn,
      UserProfile user) {}

  public static void validatePassword(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiException.bad("密码UTF-8编码不得超过72字节");
  }

  @Transactional
  public UserProfile register(Register req) {
    validatePassword(req.password());
    String name = req.username().toLowerCase(Locale.ROOT);
    if (name.startsWith("deleted_")) throw ApiException.bad("用户名使用了保留前缀");
    if (mapper.byUsername(name) != null) throw new ApiException(409, "USERNAME_EXISTS", "用户名已存在");
    var user = new Account();
    user.username = name;
    user.passwordHash = encoder.encode(req.password());
    user.displayName = req.displayName().trim();
    user.publicNo = 1_000_000_000L + new SecureRandom().nextLong(8_000_000_000_000_000L);
    mapper.insertUser(user);
    mapper.initReputation(user.id);
    mapper.initStreak(user.id);
    return UserProfile.from(mapper.byId(user.id));
  }

  @Transactional
  public Tokens login(Login req) {
    validatePassword(req.password());
    var found = mapper.byUsername(req.username().toLowerCase(Locale.ROOT));
    if (found == null) {
      encoder.matches(req.password(), dummy);
      throw ApiException.unauthorized();
    }
    var user = mapper.lockUser(found.id);
    if (user == null
        || !"ACTIVE".equals(user.accountStatus)
        || !encoder.matches(req.password(), user.passwordHash)) throw ApiException.unauthorized();
    String raw = TokenService.random();
    var session = new LoginSession();
    session.userId = user.id;
    session.deviceId = req.deviceId();
    session.refreshTokenHash = TokenService.hash(raw);
    session.expiresAt = now().plusDays(refreshDays);
    mapper.insertSession(session);
    mapper.touch(user.id, now());
    return issue(user, session, raw);
  }

  @Transactional
  public Tokens refresh(Refresh req) {
    String hash = TokenService.hash(req.refreshToken());
    var found = mapper.byRefresh(hash);
    if (found == null) throw ApiException.unauthorized();
    var user = mapper.lockUser(found.userId);
    var session = mapper.lockSession(found.id);
    if (user == null
        || session == null
        || !"ACTIVE".equals(user.accountStatus)
        || session.revokedAt != null
        || !session.expiresAt.isAfter(now())
        || !hash.equals(session.refreshTokenHash)) throw ApiException.unauthorized();
    String raw = TokenService.random();
    mapper.rotate(session.id, TokenService.hash(raw), now());
    return issue(user, session, raw);
  }

  @Transactional
  public void logout(CurrentUser principal) {
    guard.lockActive(principal.userId());
    mapper.revoke(principal.sessionId(), now());
  }

  @Transactional
  public void changePassword(CurrentUser principal, Password req) {
    validatePassword(req.oldPassword());
    validatePassword(req.newPassword());
    var user = guard.lockActive(principal.userId());
    if (!encoder.matches(req.oldPassword(), user.passwordHash)) throw ApiException.unauthorized();
    mapper.password(user.id, encoder.encode(req.newPassword()));
    mapper.revokeAll(user.id, now());
  }

  @Transactional
  public void delete(CurrentUser principal) {
    guard.lockActive(principal.userId());
    mapper.revokeAll(principal.userId(), now());
    mapper.anonymize(principal.userId(), encoder.encode(TokenService.random()));
  }

  private Tokens issue(Account user, LoginSession session, String raw) {
    return new Tokens(
        tokens.access(user.id, session.id),
        raw,
        "Bearer",
        tokens.accessSeconds,
        UserProfile.from(user));
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
