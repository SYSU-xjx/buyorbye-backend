package com.hku.money.security;

import com.hku.money.auth.entity.Account;
import com.hku.money.auth.mapper.AuthMapper;
import com.hku.money.common.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Called inside a write transaction. Lock order: user, session, then business rows. */
@Component
public class AccountGuard {
  private final AuthMapper mapper;
  private final Clock clock;

  public AccountGuard(AuthMapper mapper, Clock clock) {
    this.mapper = mapper;
    this.clock = clock;
  }

  public Account lockActive(long id) {
    Account account = mapper.lockUser(id);
    if (account == null || !"ACTIVE".equals(account.accountStatus))
      throw ApiException.unauthorized();
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof CurrentUser caller) {
      if (caller.userId() != id) throw new ApiException(403, "FORBIDDEN", "没有操作权限");
      var session = mapper.lockSession(caller.sessionId());
      if (session == null
          || session.userId != id
          || session.revokedAt != null
          || !session.expiresAt.isAfter(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))) {
        throw ApiException.unauthorized();
      }
    }
    return account;
  }
}
