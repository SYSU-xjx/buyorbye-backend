package com.hku.money;

import static org.assertj.core.api.Assertions.*;

import com.hku.money.security.TokenService;
import io.jsonwebtoken.ExpiredJwtException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TokenServiceTest {
  @Test
  void expiredAndWrongKeyTokensAreRejected() {
    String secret = "test-only-secret-at-least-32-bytes-not-for-production";
    Instant instant = Instant.parse("2026-11-06T12:00:00Z");
    var service = new TokenService(secret, 60, Clock.fixed(instant, ZoneOffset.UTC));
    String token = service.access(1, 2);
    assertThat(service.parse(token).sessionId()).isEqualTo(2);
    var later = new TokenService(secret, 60, Clock.fixed(instant.plusSeconds(61), ZoneOffset.UTC));
    assertThatThrownBy(() -> later.parse(token)).isInstanceOf(ExpiredJwtException.class);
    var other =
        new TokenService(
            "a-different-test-secret-of-at-least-32-bytes",
            60,
            Clock.fixed(instant, ZoneOffset.UTC));
    assertThatThrownBy(() -> other.parse(token)).isInstanceOf(io.jsonwebtoken.JwtException.class);
  }
}
