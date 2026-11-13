package com.hku.money.proposal.service;

import com.hku.money.common.*;
import com.hku.money.group.mapper.GroupMapper;
import com.hku.money.group.service.GroupService;
import com.hku.money.plaza.dto.PlazaResponses.Proposal;
import com.hku.money.plaza.dto.PlazaResponses.VoteReason;
import com.hku.money.plaza.mapper.PlazaMapper;
import com.hku.money.proposal.dto.ProposalModels.*;
import com.hku.money.proposal.entity.ProposalState;
import com.hku.money.proposal.mapper.ProposalMapper;
import com.hku.money.security.AccountGuard;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class ProposalService {
  private final ProposalMapper mapper;
  private final PlazaMapper plaza;
  private final GroupService groups;
  private final GroupMapper groupMapper;
  private final AccountGuard guard;
  private final ProposalAccess access;
  private final ProposalImageStore files;

  public ProposalService(
      ProposalMapper mapper,
      PlazaMapper plaza,
      GroupService groups,
      GroupMapper groupMapper,
      AccountGuard guard,
      ProposalAccess access,
      ProposalImageStore files) {
    this.mapper = mapper;
    this.plaza = plaza;
    this.groups = groups;
    this.groupMapper = groupMapper;
    this.guard = guard;
    this.access = access;
    this.files = files;
  }

  private static String clean(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  private static String one(String s, String fallback, String... allowed) {
    String v = s == null ? fallback : s;
    if (v != null && !Arrays.asList(allowed).contains(v)) throw ApiException.bad("不支持的审批设置");
    return v;
  }

  public static String uuid(String s) {
    try {
      if (s == null || !UUID.fromString(s).toString().equalsIgnoreCase(s))
        throw new IllegalArgumentException();
      return s.toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException e) {
      throw ApiException.bad("clientRequestId须为UUID");
    }
  }

  private void assign(ProposalState p, Save r) {
    p.participationType =
        "OPTION".equals(one(r.participationType(), "BINARY", "BINARY", "OPTION"))
            ? "OPTION_VOTE"
            : "APPROVE_REJECT";
    p.approvalMode = one(r.approvalMode(), "MULTI", "SINGLE", "MULTI");
    p.publicationScope = one(r.publicationScope(), null, "GROUP", "PLAZA");
    p.groupId = r.groupId();
    p.itemName = clean(r.itemName());
    p.pollQuestion = clean(r.pollQuestion());
    p.amount = r.amount();
    p.currency = r.currency();
    if ("OPTION_VOTE".equals(p.participationType)) p.itemName = null;
    else p.pollQuestion = null;
    p.purchaseReason = clean(r.purchaseReason());
    p.alternativeProductTip = clean(r.alternativeProductTip());
    if ("SINGLE".equals(p.approvalMode)) {
      if ((r.minimumVoterCount() != null && r.minimumVoterCount() != 1)
          || (r.settlementMode() != null && !"QUORUM".equals(r.settlementMode())))
        throw ApiException.bad("单人审批固定为1人、QUORUM");
      p.minimumVoterCount = 1;
      p.settlementMode = "QUORUM";
    } else {
      p.minimumVoterCount = r.minimumVoterCount() == null ? 3 : r.minimumVoterCount();
      p.settlementMode = one(r.settlementMode(), "TIME", "TIME", "QUORUM");
    }
    if ((p.amount == null) != (p.currency == null)) throw ApiException.bad("金额与币种须同时填写或同时为空");
    if ("GROUP".equals(p.publicationScope) && p.groupId == null)
      throw ApiException.bad("群组发布须选择群组");
    if (!"GROUP".equals(p.publicationScope) && p.groupId != null)
      throw ApiException.bad("仅群组发布可以指定群组");
    if ("OPTION_VOTE".equals(p.participationType) && (p.amount != null || p.currency != null))
      throw ApiException.bad("当前选项审批不带价格和币种");
    if ("APPROVE_REJECT".equals(p.participationType)
        && r.options() != null
        && !r.options().isEmpty()) throw ApiException.bad("二元审批不能包含选项");
    p.updatedAt = access.now();
  }

  private List<Long> ids(List<Long> ids) {
    if (ids == null) return List.of();
    if (new HashSet<>(ids).size() != ids.size()) throw ApiException.bad("标签或频道不能重复");
    return ids.stream().sorted().toList();
  }

  private void references(ProposalState p, Save r) {
    var tags = ids(r.tagIds());
    var channels = ids(r.channelIds());
    if (!"PLAZA".equals(p.publicationScope) && !channels.isEmpty())
      throw ApiException.bad("仅广场发布可以关联频道");
    for (long id : tags) if (mapper.activeTag(id) == null) throw ApiException.bad("标签无效");
    for (long id : channels) if (mapper.activeChannel(id) == null) throw ApiException.bad("频道无效");
  }

  private void replaceLinks(ProposalState p, Save r) {
    mapper.clearTags(p.id);
    for (long t : ids(r.tagIds())) mapper.tag(p.id, t);
    mapper.clearChannels(p.id);
    for (long c : ids(r.channelIds())) mapper.channel(p.id, c);
    mapper.clearOptions(p.id);
    int pos = 0;
    if (r.options() != null) {
      Set<String> texts = new HashSet<>();
      for (var o : r.options()) {
        String text = o.text().strip();
        if (!texts.add(text.toLowerCase(Locale.ROOT))) throw ApiException.bad("选项内容不能重复");
        mapper.option(p.id, String.valueOf((char) ('A' + pos)), text, ++pos);
      }
    }
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Detail create(long user, Save r) {
    guard.lockActive(user);
    String key = uuid(r.clientRequestId());
    var existing = mapper.byKey(user, key);
    if (existing != null) return detail(existing.id, user);
    ProposalState p = new ProposalState();
    p.creatorUserId = user;
    p.clientRequestId = key;
    p.createdAt = access.now();
    assign(p, r);
    if (p.groupId != null) groups.require(p.groupId, user, true);
    references(p, r);
    mapper.insert(p);
    replaceLinks(p, r);
    return detail(p.id, user);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Detail save(long id, long user, Save r) {
    guard.lockActive(user);
    // Lock both old and requested groups in numeric order before locking the proposal.
    var hint = mapper.find(id);
    if (hint == null) throw ProposalAccess.missing();
    access.owner(hint, user);
    var groupIds = new TreeSet<Long>();
    if (hint.groupId != null) groupIds.add(hint.groupId);
    if (r.groupId() != null) groupIds.add(r.groupId());
    groupIds.forEach(groupMapper::lock);
    var old = mapper.lock(id);
    if (old == null) throw ProposalAccess.missing();
    access.owner(old, user);
    if (!Objects.equals(hint.groupId, old.groupId)) throw ProposalAccess.conflict("审批已变更，请重试");
    if (!access.editable(old)) throw ProposalAccess.conflict("已有投票、已截止或已结束，不能修改");
    ProposalState p = new ProposalState();
    p.id = id;
    assign(p, r);
    if (p.groupId != null) groups.require(p.groupId, user, false);
    references(p, r);
    if (!"DRAFT".equals(old.proposalStatus)) {
      if (!Objects.equals(old.participationType, p.participationType)
          || !Objects.equals(old.approvalMode, p.approvalMode)
          || !Objects.equals(old.publicationScope, p.publicationScope)
          || !Objects.equals(old.groupId, p.groupId)
          || !Objects.equals(old.settlementMode, p.settlementMode)
          || old.minimumVoterCount != p.minimumVoterCount
          || !mapper.tags(id).equals(ids(r.tagIds()))
          || !mapper.channels(id).equals(ids(r.channelIds())))
        throw ApiException.bad("发布后不能改变范围、模式、标签或人数");
      var options = plaza.proposalOptions(id);
      var input = r.options() == null ? List.<OptionInput>of() : r.options();
      if (options.size() != input.size()) throw ApiException.bad("发布后不能修改选项");
      for (int i = 0; i < options.size(); i++)
        if (!Objects.equals(options.get(i).id(), input.get(i).id())
            || !options.get(i).text().equals(input.get(i).text().strip()))
          throw ApiException.bad("发布后不能修改选项");
      validateContent(p);
    }
    mapper.save(p);
    if ("DRAFT".equals(old.proposalStatus)) replaceLinks(p, r);
    return detail(id, user);
  }

  private void validateContent(ProposalState p) {
    if (p.publicationScope == null || p.purchaseReason == null)
      throw ApiException.bad("请选择发布位置并填写理由");
    if ("APPROVE_REJECT".equals(p.participationType)) {
      if (p.itemName == null || p.amount == null || p.currency == null)
        throw ApiException.bad("请填写商品名、金额和币种");
      if (p.amount.signum() <= 0
          || p.amount.scale() > 2
          || p.amount.precision() - p.amount.scale() > 10) throw ApiException.bad("金额不合法");
    } else if (p.pollQuestion == null) throw ApiException.bad("请填写投票问题");
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Detail publish(long id, long user, Publish r) {
    guard.lockActive(user);
    var p = access.lock(id);
    access.owner(p, user);
    if ("OPEN".equals(p.proposalStatus)) return detail(id, user);
    if (!"DRAFT".equals(p.proposalStatus)) throw ProposalAccess.conflict("仅草稿可以发布");
    validateContent(p);
    var tags = mapper.tags(id);
    if (tags.isEmpty() || tags.size() > 3) throw ApiException.bad("发布须选择1至3个标签");
    for (long t : tags) if (mapper.activeTag(t) == null) throw ApiException.bad("标签已失效");
    var channels = mapper.channels(id);
    if ("PLAZA".equals(p.publicationScope) && channels.isEmpty())
      throw ApiException.bad("广场发布至少选择一个频道");
    for (long c : channels) if (mapper.activeChannel(c) == null) throw ApiException.bad("频道已失效");
    if ("GROUP".equals(p.publicationScope)) {
      groups.require(p.groupId, user, false);
      if (p.minimumVoterCount > groupMapper.eligible(p.groupId, user))
        throw ApiException.bad("最低人数超过群内可投票人数");
    }
    var options = mapper.optionIds(id);
    if ("OPTION_VOTE".equals(p.participationType) && (options.isEmpty() || options.size() > 6))
      throw ApiException.bad("选项须为1至6个");
    mapper.publish(
        id,
        access.now(),
        access.now().plusHours(r.durationHours() == null ? 24 : r.durationHours()));
    return detail(id, user);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Detail withdraw(long id, long user) {
    guard.lockActive(user);
    var p = access.lock(id);
    access.owner(p, user);
    if ("WITHDRAWN".equals(p.proposalStatus)) return detail(id, user);
    // Authors may withdraw after a group closes only if the proposal has not already reached its
    // deadline.
    if (!"OPEN".equals(p.proposalStatus)
        || p.deadlineAt == null
        || !p.deadlineAt.isAfter(access.now())) throw ProposalAccess.conflict("仅未截止的开放审批可以撤回");
    mapper.withdraw(id, access.now());
    return detail(id, user);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void delete(long id, long user) {
    guard.lockActive(user);
    var p = access.lock(id);
    access.owner(p, user);
    if (!"DRAFT".equals(p.proposalStatus)) throw ProposalAccess.conflict("仅草稿可以删除");
    for (var image : mapper.images(id)) files.deleteAfterCommit(image.imageUrl);
    mapper.deleteDraft(id);
  }

  public Detail detail(long id, long user) {
    var p = access.read(id, user);
    boolean own = p.creatorUserId == user;
    boolean withdraw =
        own
            && "OPEN".equals(p.proposalStatus)
            && p.deadlineAt != null
            && p.deadlineAt.isAfter(access.now());
    return new Detail(
        mapper.summary(id, user),
        p.publicationScope,
        p.groupId,
        p.approvalMode,
        p.settlementMode,
        p.itemName,
        p.pollQuestion,
        p.settledAt,
        p.withdrawnAt,
        own,
        own && access.editable(p),
        withdraw,
        plaza.proposalChannels(id, user),
        plaza.proposalTags(id),
        plaza.proposalImages(id),
        plaza.proposalOptions(id),
        plaza.flowers(id, user),
        plaza.latestViewerVote(id, user),
        plaza.voteTally(id));
  }

  public PageResult<Proposal> groupFeed(long group, long user, String status, int page, int size) {
    groups.require(group, user, false);
    int offset = PageResult.offset(page, size);
    if (status != null && !Set.of("OPEN", "SETTLED", "INSUFFICIENT_PARTICIPATION").contains(status))
      throw ApiException.bad("不支持的状态");
    return new PageResult<>(
        mapper.groupFeed(group, user, status, offset, size),
        page,
        size,
        mapper.groupFeedCount(group, status));
  }

  public PageResult<VoteReason> reasons(long id, long user, int page, int size) {
    access.read(id, user);
    int offset = PageResult.offset(page, size);
    return new PageResult<>(
        plaza.voteReasons(id, user, offset, size), page, size, plaza.voteReasonCount(id));
  }
}
