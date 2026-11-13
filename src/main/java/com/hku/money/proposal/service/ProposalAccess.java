package com.hku.money.proposal.service;

import com.hku.money.common.*;
import com.hku.money.group.mapper.GroupMapper;
import com.hku.money.proposal.entity.ProposalState;
import com.hku.money.proposal.mapper.ProposalMapper;
import java.time.*;
import org.springframework.stereotype.Component;

@Component
public class ProposalAccess {
  private final ProposalMapper mapper;
  private final GroupMapper groups;
  private final Clock clock;

  public ProposalAccess(ProposalMapper mapper, GroupMapper groups, Clock clock) {
    this.mapper = mapper;
    this.groups = groups;
    this.clock = clock;
  }

  public LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  public ProposalState read(long id, long viewer) {
    ProposalState p = mapper.find(id);
    if (p == null) throw missing();
    if (p.creatorUserId == viewer) return p;
    if (!java.util.Set.of("OPEN", "SETTLED", "INSUFFICIENT_PARTICIPATION")
            .contains(p.proposalStatus)
        || p.publishedAt == null) throw missing();
    if ("GROUP".equals(p.publicationScope)) {
      var g = groups.find(p.groupId);
      if (g == null
          || !"ACTIVE".equals(g.groupStatus)
          || !"ACTIVE".equals(groups.membership(p.groupId, viewer))) throw missing();
    } else if (!"PLAZA".equals(p.publicationScope)
        || mapper.channels(id).stream().noneMatch(c -> activeChannelRead(c))) throw missing();
    return p;
  }

  private boolean activeChannelRead(long id) {
    return mapper.channelActive(id);
  }

  /** Lock order after AccountGuard: group (if any), proposal. Scope cannot change after publish. */
  public ProposalState lock(long id) {
    ProposalState hint = mapper.find(id);
    if (hint == null) throw missing();
    if (hint.groupId != null) groups.lock(hint.groupId);
    ProposalState p = mapper.lock(id);
    if (p == null) throw missing();
    if (!java.util.Objects.equals(hint.groupId, p.groupId)) throw conflict("审批已被修改，请重试");
    return p;
  }

  public boolean groupActive(ProposalState p) {
    if (!"GROUP".equals(p.publicationScope)) return true;
    var g = groups.find(p.groupId);
    return g != null && "ACTIVE".equals(g.groupStatus);
  }

  public boolean editable(ProposalState p) {
    return "DRAFT".equals(p.proposalStatus)
        || ("OPEN".equals(p.proposalStatus)
            && p.deadlineAt != null
            && p.deadlineAt.isAfter(now())
            && groupActive(p)
            && !mapper.hasVotes(p.id));
  }

  public void owner(ProposalState p, long user) {
    if (p.creatorUserId != user) throw new ApiException(403, "NOT_AUTHOR", "仅作者可以执行此操作");
  }

  public void open(ProposalState p) {
    if (!"OPEN".equals(p.proposalStatus)
        || p.deadlineAt == null
        || !p.deadlineAt.isAfter(now())
        || !groupActive(p)) throw conflict("审批已截止、撤回或结束");
  }

  public static ApiException missing() {
    return new ApiException(404, "PROPOSAL_NOT_FOUND", "审批不存在或没有访问权限");
  }

  public static ApiException conflict(String message) {
    return new ApiException(409, "PROPOSAL_CONFLICT", message);
  }
}
