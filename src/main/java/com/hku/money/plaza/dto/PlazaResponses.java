package com.hku.money.plaza.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PlazaResponses {
  private PlazaResponses() {}

  private static final String UTC_TIMESTAMP = "uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'";

  public enum Scope {
    ALL,
    MINE
  }

  public enum ParticipationType {
    BINARY,
    OPTION
  }

  public enum Decision {
    BUY,
    NOT_BUY
  }

  public record Channel(long id, String slug, String name, String description, boolean subscribed) {}

  public record Tag(long id, String slug, String name) {}

  public record Proposal(
      long id,
      String title,
      ParticipationType participationType,
      BigDecimal amount,
      String currency,
      String purchaseReason,
      String alternativeProductTip,
      String status,
      @JsonFormat(pattern = UTC_TIMESTAMP) LocalDateTime publishedAt,
      @JsonFormat(pattern = UTC_TIMESTAMP) LocalDateTime deadlineAt,
      int minimumVoterCount,
      int voterCount,
      int watchCount,
      int commentCount,
      boolean watching,
      Decision decisionResult,
      Long winningOptionId,
      String authorName,
      String authorAvatarUrl) {}

  public record Option(long id, String label, String text, BigDecimal amount) {}

  public record Detail(
      Proposal proposal,
      List<Channel> channels,
      List<Tag> tags,
      List<String> images,
      List<Option> options,
      Flower flowers,
      VoteReason myVote,
      List<VoteTally> voteTally) {}

  public record Watch(boolean watching, int watchCount) {}

  public record Flower(boolean sent, long flowerCount) {}

  public record AuthorUpdate(
      long id, String content, @JsonFormat(pattern = UTC_TIMESTAMP) LocalDateTime createdAt) {}

  public record VoteReason(
      ParticipationType voteType,
      Decision choice,
      Long optionId,
      String reason,
      String voterName,
      String voterAvatarUrl,
      boolean ownedByViewer,
      @JsonFormat(pattern = UTC_TIMESTAMP) LocalDateTime castAt) {}

  public record VoteTally(Decision choice, Long optionId, long voteCount) {}

  public record Comment(
      long id,
      Long parentCommentId,
      String authorName,
      String authorAvatarUrl,
      String content,
      boolean deleted,
      boolean ownedByViewer,
      @JsonFormat(pattern = UTC_TIMESTAMP) LocalDateTime createdAt) {}
}
