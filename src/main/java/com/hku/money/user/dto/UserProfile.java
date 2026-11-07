package com.hku.money.user.dto;

import com.hku.money.auth.entity.Account;
import java.time.*;

public record UserProfile(
    String id,
    String username,
    String displayName,
    String avatarUrl,
    String publicNo,
    String preferredCurrency,
    Instant createdAt) {
  public static UserProfile from(Account a) {
    return new UserProfile(
        a.id.toString(),
        a.username,
        a.displayName,
        a.avatarUrl,
        a.publicNo == null ? null : a.publicNo.toString(),
        a.preferredCurrency,
        a.createdAt.toInstant(ZoneOffset.UTC));
  }
}
