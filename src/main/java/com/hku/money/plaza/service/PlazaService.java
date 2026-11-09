package com.hku.money.plaza.service;

import com.hku.money.common.ApiException;
import com.hku.money.common.PageResult;
import com.hku.money.plaza.dto.PlazaResponses.Channel;
import com.hku.money.plaza.dto.PlazaResponses.Detail;
import com.hku.money.plaza.dto.PlazaResponses.Proposal;
import com.hku.money.plaza.dto.PlazaResponses.Scope;
import com.hku.money.plaza.dto.PlazaResponses.Tag;
import com.hku.money.plaza.dto.PlazaResponses.VoteReason;
import com.hku.money.plaza.mapper.PlazaMapper;
import com.hku.money.plaza.mapper.PlazaMapper.FeedFilter;
import com.hku.money.security.AccountGuard;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlazaService {
  private final PlazaMapper mapper;
  private final AccountGuard accounts;

  public PlazaService(PlazaMapper mapper, AccountGuard accounts) {
    this.mapper = mapper;
    this.accounts = accounts;
  }

  public PageResult<Proposal> feed(
      long viewerId, Scope scope, Long channelId, Long tagId, int page, int size) {
    int offset = PageResult.offset(page, size);
    var filter = new FeedFilter(viewerId, scope == Scope.MINE, channelId, tagId);
    return new PageResult<>(mapper.feed(filter, offset, size), page, size, mapper.feedCount(filter));
  }

  public Detail detail(long proposalId, long viewerId) {
    Proposal proposal = mapper.detail(proposalId, viewerId);
    if (proposal == null) {
      throw new ApiException(404, "PLAZA_PROPOSAL_NOT_FOUND", "广场提案不存在");
    }
    return new Detail(
        proposal,
        mapper.proposalChannels(proposalId, viewerId),
        mapper.proposalTags(proposalId),
        mapper.proposalImages(proposalId),
        mapper.proposalOptions(proposalId),
        mapper.flowers(proposalId, viewerId),
        mapper.latestViewerVote(proposalId, viewerId),
        mapper.voteTally(proposalId));
  }

  public Proposal random(long viewerId, Scope scope, Long channelId, Long tagId, Long excludeId) {
    return mapper.random(new FeedFilter(viewerId, scope == Scope.MINE, channelId, tagId), excludeId);
  }

  public PageResult<VoteReason> voteReasons(long proposalId, long viewerId, int page, int size) {
    int offset = PageResult.offset(page, size);
    if (mapper.visibleProposalAuthor(proposalId, false) == null) {
      throw new ApiException(404, "PLAZA_PROPOSAL_NOT_FOUND", "广场提案不存在");
    }
    return new PageResult<>(mapper.voteReasons(proposalId, viewerId, offset, size), page, size,
        mapper.voteReasonCount(proposalId));
  }

  public List<Channel> channels(long viewerId, Scope scope) {
    return mapper.channels(viewerId, scope == Scope.MINE);
  }

  @Transactional
  public List<Channel> replaceSubscriptions(long viewerId, List<Long> channelIds) {
    if (new HashSet<>(channelIds).size() != channelIds.size()) {
      throw ApiException.bad("频道不能重复");
    }
    accounts.lockActive(viewerId);
    if (!channelIds.isEmpty() && mapper.lockActiveChannels(channelIds).size() != channelIds.size()) {
      throw ApiException.bad("请选择有效的频道");
    }
    if (!channelIds.isEmpty()) {
      mapper.saveSubscriptions(viewerId, channelIds);
    }
    mapper.removeSubscriptions(viewerId, channelIds);
    return mapper.channels(viewerId, true);
  }

  public List<Tag> tags() {
    return mapper.tags();
  }
}
