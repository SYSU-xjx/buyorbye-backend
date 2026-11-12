package com.hku.money.plaza.service;

import com.hku.money.common.ApiException;
import com.hku.money.common.PageResult;
import com.hku.money.plaza.dto.PlazaResponses.AuthorUpdate;
import com.hku.money.plaza.dto.PlazaResponses.Comment;
import com.hku.money.plaza.dto.PlazaResponses.Flower;
import com.hku.money.plaza.dto.PlazaResponses.Watch;
import com.hku.money.plaza.mapper.PlazaMapper;
import com.hku.money.plaza.mapper.PlazaMapper.NewComment;
import com.hku.money.plaza.mapper.PlazaMapper.NewUpdate;
import com.hku.money.security.AccountGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlazaInteractionService {
  private final PlazaMapper mapper;
  private final AccountGuard accounts;

  public PlazaInteractionService(PlazaMapper mapper, AccountGuard accounts) {
    this.mapper = mapper;
    this.accounts = accounts;
  }

  @Transactional
  public Watch setWatching(long proposalId, long viewerId, boolean watching) {
    accounts.lockActive(viewerId);
    requireVisibleProposal(proposalId, true);
    if (mapper.isWatching(proposalId, viewerId) != watching) {
      if (watching) {
        mapper.insertWatch(proposalId, viewerId);
      } else {
        mapper.deleteWatch(proposalId, viewerId);
      }
      mapper.changeWatchCount(proposalId, watching ? 1 : -1);
    }
    return new Watch(watching, mapper.watchCount(proposalId));
  }

  @Transactional(readOnly = true)
  public PageResult<Comment> comments(long proposalId, long viewerId, int page, int size) {
    int offset = PageResult.offset(page, size);
    requireVisibleProposal(proposalId, false);
    return new PageResult<>(
        mapper.comments(proposalId, viewerId, offset, size), page, size,
        mapper.commentListCount(proposalId));
  }

  @Transactional
  public Comment addComment(long proposalId, long viewerId, Long parentId, String content) {
    accounts.lockActive(viewerId);
    requireVisibleProposal(proposalId, true);
    if (parentId != null) {
      var parent = mapper.commentState(proposalId, parentId);
      if (parent == null || parent.parentCommentId() != null || !"VISIBLE".equals(parent.status())) {
        throw ApiException.bad("只能回复该提案下可见的一级评论");
      }
    }
    var comment = new NewComment(proposalId, viewerId, parentId, content.strip());
    mapper.insertComment(comment);
    mapper.changeCommentCount(proposalId, 1);
    return mapper.comment(comment.id, viewerId);
  }

  @Transactional
  public void deleteComment(long proposalId, long viewerId, long commentId) {
    accounts.lockActive(viewerId);
    requireVisibleProposal(proposalId, true);
    var comment = mapper.commentState(proposalId, commentId);
    if (comment == null || "HIDDEN".equals(comment.status())) {
      throw new ApiException(404, "COMMENT_NOT_FOUND", "评论不存在");
    }
    if (comment.authorUserId() != viewerId) {
      throw new ApiException(403, "COMMENT_NOT_OWNED", "只能删除自己的评论");
    }
    if ("VISIBLE".equals(comment.status())) {
      mapper.deleteComment(commentId);
      mapper.changeCommentCount(proposalId, -1);
    }
  }

  @Transactional
  public Flower sendFlower(long proposalId, long viewerId) {
    accounts.lockActive(viewerId);
    long authorId = requireVisibleProposal(proposalId, true);
    if (authorId == viewerId) {
      throw new ApiException(403, "SELF_FLOWER", "不能给自己的提案送花");
    }
    Flower flowers = mapper.flowers(proposalId, viewerId);
    if (flowers.sent()) {
      return flowers;
    }
    mapper.insertFlower(proposalId, viewerId, authorId);
    return new Flower(true, flowers.flowerCount() + 1);
  }

  @Transactional(readOnly = true)
  public PageResult<AuthorUpdate> updates(long proposalId, int page, int size) {
    int offset = PageResult.offset(page, size);
    requireVisibleProposal(proposalId, false);
    return new PageResult<>(mapper.updates(proposalId, offset, size), page, size,
        mapper.updateCount(proposalId));
  }

  @Transactional
  public AuthorUpdate addUpdate(long proposalId, long viewerId, String content) {
    accounts.lockActive(viewerId);
    if (requireVisibleProposal(proposalId, true) != viewerId) {
      throw new ApiException(403, "NOT_PROPOSAL_AUTHOR", "只有提案作者可以补充说明");
    }
    var update = new NewUpdate(proposalId, content.strip());
    mapper.insertUpdate(update);
    return mapper.updateById(update.id);
  }

  private long requireVisibleProposal(long proposalId, boolean lock) {
    // Serialize writes to the same proposal so interactions and counts change together.
    Long authorId = mapper.visibleProposalAuthor(proposalId, lock);
    if (authorId == null) {
      throw new ApiException(404, "PLAZA_PROPOSAL_NOT_FOUND", "广场提案不存在");
    }
    return authorId;
  }
}
