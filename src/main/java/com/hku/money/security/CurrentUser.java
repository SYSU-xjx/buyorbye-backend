package com.hku.money.security;

import com.hku.money.common.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;

public record CurrentUser(long userId, long sessionId) {
  public static CurrentUser get() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof CurrentUser user))
      throw ApiException.unauthorized();
    return user;
  }
}
