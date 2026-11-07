package com.hku.money.auth.entity;

import java.time.LocalDateTime;

public class LoginSession {
  public Long id, userId;
  public String refreshTokenHash, deviceId;
  public LocalDateTime expiresAt, revokedAt;
}
