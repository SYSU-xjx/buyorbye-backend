package com.hku.money.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.Clock;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenService {
  private final SecretKey key;
  private final Clock clock;
  public final long accessSeconds;

  public TokenService(
      @Value("${app.jwt-secret}") String secret,
      @Value("${app.access-token-seconds}") long seconds,
      Clock clock) {
    key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.clock = clock;
    accessSeconds = seconds;
  }

  public String access(long uid, long sid) {
    Instant now = clock.instant();
    return Jwts.builder()
        .issuer("buyorbye")
        .subject(Long.toString(uid))
        .claim("sid", Long.toString(sid))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(accessSeconds)))
        .signWith(key)
        .compact();
  }

  public CurrentUser parse(String token) {
    var claims =
        Jwts.parser()
            .verifyWith(key)
            .requireIssuer("buyorbye")
            .clock(() -> Date.from(clock.instant()))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    return new CurrentUser(
        Long.parseLong(claims.getSubject()), Long.parseLong(claims.get("sid", String.class)));
  }

  public static String random() {
    byte[] b = new byte[48];
    new SecureRandom().nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public static String hash(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
