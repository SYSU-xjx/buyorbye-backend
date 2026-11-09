package com.hku.money.proposal.service;

import com.hku.money.proposal.entity.ProposalState;
import com.hku.money.proposal.mapper.ProposalMapper;
import com.hku.money.proposal.mapper.ProposalMapper.NewVote;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class SettlementService {
  private final ProposalMapper mapper;
  private final ProposalAccess access;

  public SettlementService(ProposalMapper mapper, ProposalAccess access) {
    this.mapper = mapper;
    this.access = access;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void settle(long id) {
    var p = mapper.lock(id);
    if (p != null) settleLocked(p);
  }

  /** Caller holds the proposal lock. Never fetch external rates in this transaction. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void settleLocked(ProposalState p) {
    if (!"OPEN".equals(p.proposalStatus)) return;
    var votes =
        mapper.latestVotes(p.id).stream().filter(v -> v.voterUserId != p.creatorUserId).toList();
    boolean due = p.deadlineAt != null && !p.deadlineAt.isAfter(access.now());
    boolean quorum = "QUORUM".equals(p.settlementMode) && votes.size() >= p.minimumVoterCount;
    if (!due && !quorum) return;
    mapper.updateVoterCount(p.id, votes.size());
    if (votes.size() < p.minimumVoterCount) {
      mapper.settle(p.id, "INSUFFICIENT_PARTICIPATION", null, null, access.now());
      return;
    }
    Integer decision = null;
    Long winner = null;
    if ("APPROVE_REJECT".equals(p.participationType)) {
      long total = votes.stream().mapToLong(v -> v.weightSnapshot).sum();
      long buy =
          votes.stream()
              .filter(v -> Integer.valueOf(1).equals(v.choice))
              .mapToLong(v -> v.weightSnapshot)
              .sum();
      decision = buy * 2 > total ? 1 : 0;
    } else {
      Map<Long, Long> totals = new HashMap<>();
      votes.forEach(v -> totals.merge(v.optionId, (long) v.weightSnapshot, Long::sum));
      long maximum = -1;
      // IDs are loaded by display_order: strictly greater preserves the first tied option.
      for (long option : mapper.optionIds(p.id)) {
        long total = totals.getOrDefault(option, 0L);
        if (total > maximum) {
          maximum = total;
          winner = option;
        }
      }
      if (winner == null)
        throw new IllegalStateException("Published option proposal has no options");
    }
    mapper.settle(p.id, "SETTLED", decision, winner, access.now());
    // Sorted user IDs provide a consistent order for reputation-account locks across proposals.
    for (NewVote v : votes) {
      boolean correct =
          decision != null
              ? Objects.equals(v.choice, decision)
              : Objects.equals(v.optionId, winner);
      if (correct) {
        if (mapper.lockScore(v.voterUserId) == null)
          throw new IllegalStateException("Missing reputation account");
        mapper.reward(v.voterUserId, p.id, v.id, access.now());
        mapper.incrementScore(v.voterUserId);
      }
    }
  }
}
