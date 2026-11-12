package com.hku.money.auth.entity;

import java.time.LocalDateTime;

public class Account {
  public Long id;
  public String username, passwordHash, displayName, avatarUrl, preferredCurrency, accountStatus;
  public Long publicNo;
  public LocalDateTime createdAt;
}
