package com.hku.money.auth.dto;

import jakarta.validation.constraints.*;

public final class AuthRequests {
  private AuthRequests() {}

  public record Register(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,64}") String username,
      @NotBlank @Size(min = 8, max = 72) String password,
      @NotBlank @Size(max = 80) String displayName) {}

  public record Login(
      @NotBlank @Size(max = 64) String username,
      @NotBlank @Size(max = 72) String password,
      @NotBlank @Size(max = 64) String deviceId) {}

  public record Refresh(@NotBlank @Size(max = 128) String refreshToken) {}

  public record Password(
      @NotBlank @Size(max = 72) String oldPassword,
      @NotBlank @Size(min = 8, max = 72) String newPassword) {}
}
