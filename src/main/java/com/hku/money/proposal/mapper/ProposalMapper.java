package com.hku.money.proposal.mapper;

import com.hku.money.plaza.dto.PlazaResponses.Proposal;
import com.hku.money.proposal.dto.ProposalModels.*;
import com.hku.money.proposal.entity.ProposalState;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProposalMapper {
  @Select("SELECT * FROM purchase_proposals WHERE id=#{id}")
  ProposalState find(long id);

  @Select("SELECT * FROM purchase_proposals WHERE id=#{id} FOR UPDATE")
  ProposalState lock(long id);

  @Select(
      "SELECT * FROM purchase_proposals WHERE creator_user_id=#{user} AND client_request_id=#{key}")
  ProposalState byKey(@Param("user") long user, @Param("key") String key);

  void insert(ProposalState p);

  void save(ProposalState p);

  @Delete("DELETE FROM purchase_proposals WHERE id=#{id} AND proposal_status='DRAFT'")
  void deleteDraft(long id);

  @Update(
      "UPDATE purchase_proposals SET"
          + " proposal_status='OPEN',published_at=#{now},deadline_at=#{deadline} WHERE id=#{id}")
  void publish(
      @Param("id") long id,
      @Param("now") LocalDateTime now,
      @Param("deadline") LocalDateTime deadline);

  @Update(
      "UPDATE purchase_proposals SET proposal_status='WITHDRAWN',withdrawn_at=#{now} WHERE"
          + " id=#{id}")
  void withdraw(@Param("id") long id, @Param("now") LocalDateTime now);

  @Update(
      "UPDATE purchase_proposals SET"
          + " proposal_status=#{status},decision_result=#{decision},winning_option_id=#{option},settled_at=#{now}"
          + " WHERE id=#{id}")
  void settle(
      @Param("id") long id,
      @Param("status") String status,
      @Param("decision") Integer decision,
      @Param("option") Long option,
      @Param("now") LocalDateTime now);

  @Select(
      "SELECT id FROM purchase_proposals WHERE proposal_status='OPEN' AND deadline_at<=#{now} ORDER"
          + " BY deadline_at,id LIMIT 100")
  List<Long> due(LocalDateTime now);

  @Select(
      "SELECT id FROM purchase_proposals p WHERE p.proposal_status='SETTLED' AND"
          + " p.participation_type='APPROVE_REJECT' AND p.decision_result=1 AND p.amount IS NOT"
          + " NULL AND NOT EXISTS(SELECT 1 FROM exchange_rate_snapshots e WHERE e.proposal_id=p.id)"
          + " ORDER BY p.settled_at DESC,p.id DESC LIMIT 100 OFFSET #{offset}")
  List<Long> missingRates(int offset);

  @Select(
      "SELECT COUNT(*) FROM purchase_proposals p WHERE p.proposal_status='SETTLED' AND"
          + " p.participation_type='APPROVE_REJECT' AND p.decision_result=1 AND p.amount IS NOT"
          + " NULL AND NOT EXISTS(SELECT 1 FROM exchange_rate_snapshots e WHERE"
          + " e.proposal_id=p.id)")
  long missingRateCount();

  @Select("SELECT COALESCE((SELECT score FROM reputation_accounts WHERE user_id=#{user}),0)")
  int score(long user);

  @Select("SELECT EXISTS(SELECT 1 FROM decision_votes WHERE proposal_id=#{id})")
  boolean hasVotes(long id);

  @Select("SELECT COUNT(DISTINCT voter_user_id) FROM decision_votes WHERE proposal_id=#{id}")
  int voterCount(long id);

  @Update("UPDATE purchase_proposals SET voter_count=#{count} WHERE id=#{id}")
  void updateVoterCount(@Param("id") long id, @Param("count") int count);

  final class NewVote {
    public Long id, optionId;
    public long proposalId, voterUserId;
    public String voteType, reason, clientRequestId;
    public Integer choice;
    public int reputationScoreSnapshot, weightSnapshot;
    public LocalDateTime castAt;
  }

  void insertVote(NewVote vote);

  @Select("SELECT * FROM decision_votes WHERE voter_user_id=#{user} AND client_request_id=#{key}")
  NewVote voteByKey(@Param("user") long user, @Param("key") String key);

  @Select(
      "SELECT * FROM decision_votes WHERE proposal_id=#{id} AND voter_user_id=#{user} ORDER BY id"
          + " DESC LIMIT 1")
  NewVote myVote(@Param("id") long id, @Param("user") long user);

  @Select(
      "SELECT v.* FROM decision_votes v JOIN (SELECT MAX(id) id FROM decision_votes WHERE"
          + " proposal_id=#{id} GROUP BY voter_user_id) latest ON v.id=latest.id ORDER BY"
          + " v.voter_user_id")
  List<NewVote> latestVotes(long id);

  @Insert(
      "INSERT INTO"
          + " reputation_ledger(user_id,proposal_id,source_vote_id,score_change,source_type,rule_version,created_at)"
          + " VALUES(#{user},#{proposal},#{vote},1,'VOTE_CORRECT','v1',#{now})")
  void reward(
      @Param("user") long user,
      @Param("proposal") long proposal,
      @Param("vote") long vote,
      @Param("now") LocalDateTime now);

  @Update("UPDATE reputation_accounts SET score=score+1 WHERE user_id=#{user}")
  void incrementScore(long user);

  @Select("SELECT score FROM reputation_accounts WHERE user_id=#{user} FOR UPDATE")
  Integer lockScore(long user);

  @Delete("DELETE FROM proposal_tags WHERE proposal_id=#{id}")
  void clearTags(long id);

  @Delete("DELETE FROM purchase_proposal_channels WHERE proposal_id=#{id}")
  void clearChannels(long id);

  @Delete("DELETE FROM poll_options WHERE proposal_id=#{id}")
  void clearOptions(long id);

  @Insert("INSERT INTO proposal_tags(proposal_id,tag_id) VALUES(#{id},#{tag})")
  void tag(@Param("id") long id, @Param("tag") long tag);

  @Insert("INSERT INTO purchase_proposal_channels(proposal_id,channel_id) VALUES(#{id},#{channel})")
  void channel(@Param("id") long id, @Param("channel") long channel);

  @Select("SELECT id FROM tags WHERE id=#{id} AND is_active=1 FOR SHARE")
  Long activeTag(long id);

  @Select("SELECT EXISTS(SELECT 1 FROM channels WHERE id=#{id} AND is_active=1)")
  boolean channelActive(long id);

  @Select("SELECT id FROM channels WHERE id=#{id} AND is_active=1 FOR SHARE")
  Long activeChannel(long id);

  @Select("SELECT tag_id FROM proposal_tags WHERE proposal_id=#{id} ORDER BY tag_id")
  List<Long> tags(long id);

  @Select(
      "SELECT channel_id FROM purchase_proposal_channels WHERE proposal_id=#{id} ORDER BY"
          + " channel_id")
  List<Long> channels(long id);

  @Select("SELECT id FROM poll_options WHERE proposal_id=#{id} ORDER BY display_order")
  List<Long> optionIds(long id);

  @Insert(
      "INSERT INTO poll_options(proposal_id,option_label,option_text,display_order)"
          + " VALUES(#{id},#{label},#{text},#{position})")
  void option(
      @Param("id") long id,
      @Param("label") String label,
      @Param("text") String text,
      @Param("position") int position);

  Proposal summary(@Param("id") long id, @Param("viewerId") long viewerId);

  List<Proposal> groupFeed(
      @Param("groupId") long groupId,
      @Param("viewerId") long viewerId,
      @Param("status") String status,
      @Param("offset") int offset,
      @Param("size") int size);

  long groupFeedCount(@Param("groupId") long groupId, @Param("status") String status);

  final class ImageRow {
    public Long id;
    public long proposalId;
    public String imageUrl;
    public int displayOrder;
  }

  @Select("SELECT * FROM purchase_proposal_images WHERE proposal_id=#{id} ORDER BY display_order")
  List<ImageRow> images(long id);

  @Select("SELECT * FROM purchase_proposal_images WHERE id=#{imageId} AND proposal_id=#{proposal}")
  ImageRow image(@Param("proposal") long proposal, @Param("imageId") long imageId);

  @Insert(
      "INSERT INTO purchase_proposal_images(proposal_id,image_url,display_order)"
          + " VALUES(#{proposalId},#{imageUrl},#{displayOrder})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void insertImage(ImageRow image);

  @Update("UPDATE purchase_proposal_images SET image_url=#{url} WHERE id=#{id}")
  void imageUrl(@Param("id") long id, @Param("url") String url);

  @Delete("DELETE FROM purchase_proposal_images WHERE id=#{id}")
  void deleteImage(long id);
}
