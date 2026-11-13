package com.hku.money;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hku.money.auth.dto.AuthRequests.*;
import com.hku.money.auth.entity.Account;
import com.hku.money.auth.mapper.AuthMapper;
import com.hku.money.auth.service.AuthService;
import com.hku.money.common.ApiException;
import com.hku.money.security.AccountGuard;
import com.hku.money.security.CurrentUser;
import com.hku.money.security.TokenService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthErrorTest {
  @Test
  void credentialErrorsAreDistinctFromExpiredSessions() {
    var mapper = mock(AuthMapper.class);
    var encoder = mock(PasswordEncoder.class);
    var guard = mock(AccountGuard.class);
    var auth = new AuthService(mapper, encoder, mock(TokenService.class), Clock.systemUTC(), 30, guard);

    assertThatThrownBy(() -> auth.login(new Login("missing", "wrong-password", "device")))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> {
              assertThat(error.status).isEqualTo(401);
              assertThat(error.getMessage()).isEqualTo("用户名或密码错误");
            });

    var user = new Account();
    user.id = 1L;
    user.accountStatus = "ACTIVE";
    user.passwordHash = "stored-hash";
    when(mapper.byUsername("known")).thenReturn(user);
    when(mapper.lockUser(1)).thenReturn(user);
    when(guard.lockActive(1)).thenReturn(user);

    assertThatThrownBy(() -> auth.login(new Login("known", "wrong-password", "device")))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> {
              assertThat(error.status).isEqualTo(401);
              assertThat(error.getMessage()).isEqualTo("用户名或密码错误");
            });

    assertThatThrownBy(
            () -> auth.changePassword(new CurrentUser(1, 2), new Password("wrong", "new-password")))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> {
              assertThat(error.status).isEqualTo(400);
              assertThat(error.getMessage()).isEqualTo("原密码错误");
            });

    assertThatThrownBy(() -> auth.refresh(new Refresh("invalid-refresh-token")))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> {
              assertThat(error.status).isEqualTo(401);
              assertThat(error.getMessage()).isEqualTo("登录已失效，请重新登录");
            });
  }
}
