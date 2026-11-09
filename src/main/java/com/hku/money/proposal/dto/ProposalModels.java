package com.hku.money.proposal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hku.money.plaza.dto.PlazaResponses.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ProposalModels {
  private ProposalModels() {}

  public record OptionInput(@Positive Long id, @NotBlank @Size(max = 200) String text) {}

  public record Save(
      @Size(max = 36) String clientRequestId,
      String participationType,
      String approvalMode,
      String publicationScope,
      @Positive Long groupId,
      @Size(max = 200) String itemName,
      @Size(max = 300) String pollQuestion,
      @Digits(integer = 10, fraction = 2) @DecimalMin(value = "0", inclusive = false)
          BigDecimal amount,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      @Size(max = 10000) String purchaseReason,
      @Size(max = 10000) String alternativeProductTip,
      String settlementMode,
      @Min(1) @Max(1000000) Integer minimumVoterCount,
      @Size(max = 3) List<@NotNull @Positive Long> tagIds,
      @Size(max = 100) List<@NotNull @Positive Long> channelIds,
      @Size(max = 6) List<@NotNull @Valid OptionInput> options) {}

  public record Publish(@Min(1) @Max(168) Integer durationHours) {}

  public record Vote(
      @NotBlank String clientRequestId,
      String choice,
      @Positive Long optionId,
      @NotBlank @Size(max = 10000) String reason) {}

  public record VoteEvent(
      long id,
      long proposalId,
      long voterUserId,
      String voteType,
      String choice,
      Long optionId,
      String reason,
      int reputationScoreSnapshot,
      int weightSnapshot,
      @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'") LocalDateTime castAt) {}

  public record Image(long id, String imageUrl, int displayOrder) {}

  public record Detail(
      Proposal proposal,
      String publicationScope,
      Long groupId,
      String approvalMode,
      String settlementMode,
      String itemName,
      String pollQuestion,
      @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'") LocalDateTime settledAt,
      @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'") LocalDateTime withdrawnAt,
      boolean ownedByViewer,
      boolean canEdit,
      boolean canWithdraw,
      List<Channel> channels,
      List<Tag> tags,
      List<String> images,
      List<Option> options,
      Flower flowers,
      VoteReason myVote,
      List<VoteTally> voteTally) {}
}
