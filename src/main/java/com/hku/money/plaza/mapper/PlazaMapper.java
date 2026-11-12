package com.hku.money.plaza.mapper;

import com.hku.money.plaza.dto.PlazaResponses.AuthorUpdate;
import com.hku.money.plaza.dto.PlazaResponses.Channel;
import com.hku.money.plaza.dto.PlazaResponses.Comment;
import com.hku.money.plaza.dto.PlazaResponses.Flower;
import com.hku.money.plaza.dto.PlazaResponses.Option;
import com.hku.money.plaza.dto.PlazaResponses.Proposal;
import com.hku.money.plaza.dto.PlazaResponses.Tag;
import com.hku.money.plaza.dto.PlazaResponses.VoteReason;
import com.hku.money.plaza.dto.PlazaResponses.VoteTally;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlazaMapper {
  record FeedFilter(long viewerId, boolean mine, Long channelId, Long tagId) {}

  record CommentState(long authorUserId, Long parentCommentId, String status) {}

  final class NewComment {
    public Long id;
    public final long proposalId;
    public final long authorId;
    public final Long parentId;
    public final String content;

    public NewComment(long proposalId, long authorId, Long parentId, String content) {
      this.proposalId = proposalId;
      this.authorId = authorId;
      this.parentId = parentId;
      this.content = content;
    }
  }

  List<Proposal> feed(
      @Param("filter") FeedFilter filter, @Param("offset") int offset, @Param("size") int size);

  long feedCount(@Param("filter") FeedFilter filter);

  Proposal random(@Param("filter") FeedFilter filter, @Param("excludeId") Long excludeId);

  Proposal detail(@Param("proposalId") long proposalId, @Param("viewerId") long viewerId);

  List<Channel> channels(@Param("viewerId") long viewerId, @Param("mine") boolean mine);

  List<Long> lockActiveChannels(@Param("channelIds") List<Long> channelIds);

  void removeSubscriptions(@Param("viewerId") long viewerId, @Param("channelIds") List<Long> channelIds);

  void saveSubscriptions(@Param("viewerId") long viewerId, @Param("channelIds") List<Long> channelIds);

  List<Tag> tags();

  List<Channel> proposalChannels(@Param("proposalId") long proposalId, @Param("viewerId") long viewerId);

  List<Tag> proposalTags(@Param("proposalId") long proposalId);

  List<String> proposalImages(@Param("proposalId") long proposalId);

  List<Option> proposalOptions(@Param("proposalId") long proposalId);

  Long visibleProposalAuthor(@Param("proposalId") long proposalId, @Param("lock") boolean lock);

  List<VoteReason> voteReasons(
      @Param("proposalId") long proposalId, @Param("viewerId") long viewerId,
      @Param("offset") int offset, @Param("size") int size);

  long voteReasonCount(@Param("proposalId") long proposalId);

  VoteReason latestViewerVote(@Param("proposalId") long proposalId, @Param("viewerId") long viewerId);

  List<VoteTally> voteTally(@Param("proposalId") long proposalId);

  Flower flowers(@Param("proposalId") long proposalId, @Param("viewerId") long viewerId);

  void insertFlower(@Param("proposalId") long proposalId, @Param("viewerId") long viewerId,
      @Param("receiverId") long receiverId);

  List<AuthorUpdate> updates(
      @Param("proposalId") long proposalId, @Param("offset") int offset, @Param("size") int size);

  long updateCount(@Param("proposalId") long proposalId);

  void insertUpdate(NewUpdate update);

  AuthorUpdate updateById(@Param("id") long id);

  final class NewUpdate {
    public Long id;
    public final long proposalId;
    public final String content;

    public NewUpdate(long proposalId, String content) {
      this.proposalId = proposalId;
      this.content = content;
    }
  }

  boolean isWatching(@Param("proposalId") long proposalId, @Param("viewerId") long viewerId);

  void insertWatch(@Param("proposalId") long proposalId, @Param("viewerId") long viewerId);

  void deleteWatch(@Param("proposalId") long proposalId, @Param("viewerId") long viewerId);

  void changeWatchCount(@Param("proposalId") long proposalId, @Param("delta") int delta);

  int watchCount(@Param("proposalId") long proposalId);

  List<Comment> comments(
      @Param("proposalId") long proposalId, @Param("viewerId") long viewerId,
      @Param("offset") int offset, @Param("size") int size);

  long commentListCount(@Param("proposalId") long proposalId);

  CommentState commentState(@Param("proposalId") long proposalId, @Param("commentId") long commentId);

  void insertComment(NewComment comment);

  Comment comment(@Param("commentId") long commentId, @Param("viewerId") long viewerId);

  void deleteComment(@Param("commentId") long commentId);

  void changeCommentCount(@Param("proposalId") long proposalId, @Param("delta") int delta);
}
