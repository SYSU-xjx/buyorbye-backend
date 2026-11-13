package com.hku.money.group.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class GroupModels {
  private GroupModels() {}

  public record Name(@NotBlank @Size(max = 100) String name) {}

  public record Join(@NotBlank @Pattern(regexp = "[0-9]{8}") String inviteCode) {}

  public record Owner(@Positive long userId) {}

  public static class Group {
    public Long id;
    public String name, inviteCode, groupStatus, myRole;
    public long ownerUserId;
    public int memberCount;

    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    public LocalDateTime inviteCodeExpiresAt;

    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    public LocalDateTime createdAt;
  }

  public record Member(
      long userId,
      String displayName,
      String avatarUrl,
      String role,
      @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'") LocalDateTime joinedAt) {}
}
