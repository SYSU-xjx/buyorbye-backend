package com.hku.money.proposal.service;

import com.hku.money.common.*;
import com.hku.money.group.mapper.GroupMapper;
import com.hku.money.proposal.dto.ProposalModels.*;
import com.hku.money.proposal.mapper.ProposalMapper;
import com.hku.money.proposal.mapper.ProposalMapper.NewVote;
import com.hku.money.security.AccountGuard;
import com.hku.money.user.service.UserService;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class VoteService {
  private final ProposalMapper mapper;
  private final ProposalAccess access;
  private final AccountGuard guard;
  private final GroupMapper groups;
  private final SettlementService settlements;

  public VoteService(
      ProposalMapper mapper,
      ProposalAccess access,
      AccountGuard guard,
      GroupMapper groups,
      SettlementService settlements) {
    this.mapper = mapper;
    this.access = access;
    this.guard = guard;
    this.groups = groups;
    this.settlements = settlements;
  }

  public static VoteEvent view(NewVote v) {
    return v == null
        ? null
        : new VoteEvent(
            v.id,
            v.proposalId,
            v.voterUserId,
            v.voteType,
            v.choice == null ? null : v.choice == 1 ? "BUY" : "NOT_BUY",
            v.optionId,
            v.reason,
            v.reputationScoreSnapshot,
            v.weightSnapshot,
            v.castAt);
  }

  public VoteEvent mine(long id, long user) {
    access.read(id, user);
    return view(mapper.myVote(id, user));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public VoteEvent vote(long id, long user, Vote r) {
    guard.lockActive(user);
    String key = ProposalService.uuid(r.clientRequestId());
    Integer choice = null;
    if (r.choice() != null) {
      if (!java.util.Set.of("BUY", "NOT_BUY").contains(r.choice()))
        throw ApiException.bad("投票选项无效");
      choice = "BUY".equals(r.choice()) ? 1 : 0;
    }
    NewVote existing = mapper.voteByKey(user, key);
    if (existing != null) {
      if (existing.proposalId != id
          || !Objects.equals(existing.choice, choice)
          || !Objects.equals(existing.optionId, r.optionId())
          || !existing.reason.equals(r.reason().strip()))
        throw new ApiException(409, "IDEMPOTENCY_CONFLICT", "同一请求标识不能用于不同投票");
      return view(existing);
    }
    var p = access.lock(id);
    access.read(id, user);
    access.open(p);
    if (p.creatorUserId == user) throw new ApiException(403, "SELF_VOTE", "作者不能给自己投票");
    if ("GROUP".equals(p.publicationScope) && !"ACTIVE".equals(groups.membership(p.groupId, user)))
      throw ProposalAccess.missing();
    if ("APPROVE_REJECT".equals(p.participationType)) {
      if (choice == null || r.optionId() != null)
        throw ApiException.bad("二元审批须提供choice，不能提供optionId");
    } else if (choice != null
        || r.optionId() == null
        || !mapper.optionIds(id).contains(r.optionId())) throw ApiException.bad("请选择该审批的有效候选项");
    NewVote v = new NewVote();
    v.proposalId = id;
    v.voterUserId = user;
    v.voteType = "APPROVE_REJECT".equals(p.participationType) ? "BINARY" : "OPTION";
    v.choice = choice;
    v.optionId = r.optionId();
    v.reason = r.reason().strip();
    v.clientRequestId = key;
    v.reputationScoreSnapshot = mapper.score(user);
    v.weightSnapshot = UserService.weight(v.reputationScoreSnapshot);
    v.castAt = access.now();
    mapper.insertVote(v);
    mapper.updateVoterCount(id, mapper.voterCount(id));
    settlements.settleLocked(p);
    return view(v);
  }
}
