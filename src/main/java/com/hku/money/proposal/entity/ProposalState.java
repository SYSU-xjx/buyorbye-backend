package com.hku.money.proposal.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Internal row model. HTTP responses use ProposalModels, never expose this row directly. */
public class ProposalState {
  public Long id, groupId, winningOptionId;
  public long creatorUserId;
  public String participationType,
      approvalMode,
      publicationScope,
      itemName,
      pollQuestion,
      currency,
      purchaseReason,
      alternativeProductTip,
      proposalStatus,
      settlementMode,
      clientRequestId;
  public BigDecimal amount;
  public Integer decisionResult;
  public int minimumVoterCount, voterCount, watchCount, commentCount;
  public LocalDateTime publishedAt, deadlineAt, settledAt, withdrawnAt, createdAt, updatedAt;
}
