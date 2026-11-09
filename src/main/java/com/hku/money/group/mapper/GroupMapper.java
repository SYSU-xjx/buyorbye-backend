package com.hku.money.group.mapper;

import com.hku.money.group.dto.GroupModels.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface GroupMapper {
  @Select("SELECT * FROM app_groups WHERE id=#{id}")
  Group find(long id);

  @Select("SELECT * FROM app_groups WHERE id=#{id} FOR UPDATE")
  Group lock(long id);

  @Select("SELECT id FROM app_groups WHERE invite_code=#{code}")
  Long byCode(String code);

  @Select("SELECT member_status FROM group_members WHERE group_id=#{groupId} AND user_id=#{userId}")
  String membership(@Param("groupId") long groupId, @Param("userId") long userId);

  @Select(
      "SELECT COUNT(*) FROM group_members m JOIN users u ON u.id=m.user_id WHERE"
          + " m.group_id=#{groupId} AND m.member_status='ACTIVE' AND u.account_status='ACTIVE' AND"
          + " m.user_id<>#{author}")
  int eligible(@Param("groupId") long groupId, @Param("author") long author);

  @Insert(
      "INSERT INTO"
          + " app_groups(name,owner_user_id,invite_code,invite_code_expires_at,member_count,created_at)"
          + " VALUES(#{name},#{ownerUserId},#{inviteCode},#{inviteCodeExpiresAt},1,#{createdAt})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void insert(Group group);

  @Insert(
      "INSERT INTO group_members(group_id,user_id,member_role,member_status,joined_at)"
          + " VALUES(#{groupId},#{userId},#{role},'ACTIVE',#{now}) AS incoming ON DUPLICATE KEY"
          + " UPDATE"
          + " member_status='ACTIVE',member_role=incoming.member_role,joined_at=incoming.joined_at,left_at=NULL")
  void join(
      @Param("groupId") long groupId,
      @Param("userId") long userId,
      @Param("role") String role,
      @Param("now") LocalDateTime now);

  @Update(
      "UPDATE group_members SET member_status=#{status},left_at=#{now} WHERE group_id=#{groupId}"
          + " AND user_id=#{userId} AND member_status='ACTIVE'")
  int leave(
      @Param("groupId") long groupId,
      @Param("userId") long userId,
      @Param("status") String status,
      @Param("now") LocalDateTime now);

  @Update("UPDATE app_groups SET member_count=member_count+#{delta} WHERE id=#{id}")
  void count(@Param("id") long id, @Param("delta") int delta);

  @Update("UPDATE app_groups SET name=#{name} WHERE id=#{id}")
  void rename(@Param("id") long id, @Param("name") String name);

  @Update(
      "UPDATE app_groups SET invite_code=#{code},invite_code_expires_at=#{expires} WHERE id=#{id}")
  void invite(
      @Param("id") long id, @Param("code") String code, @Param("expires") LocalDateTime expires);

  @Update("UPDATE app_groups SET owner_user_id=#{userId} WHERE id=#{id}")
  void owner(@Param("id") long id, @Param("userId") long userId);

  @Update(
      "UPDATE group_members SET member_role=CASE WHEN user_id=#{owner} THEN 'OWNER' ELSE 'MEMBER'"
          + " END WHERE group_id=#{id}")
  void roles(@Param("id") long id, @Param("owner") long owner);

  @Update(
      "UPDATE app_groups SET group_status='CLOSED',invite_code=NULL,invite_code_expires_at=NULL"
          + " WHERE id=#{id}")
  void close(long id);

  @Update(
      "UPDATE purchase_proposals SET deadline_at=#{now} WHERE group_id=#{id} AND"
          + " proposal_status='OPEN' AND (deadline_at IS NULL OR deadline_at>#{now})")
  void endProposals(@Param("id") long id, @Param("now") LocalDateTime now);

  @Select(
      "SELECT g.*,CASE WHEN g.owner_user_id=#{userId} THEN 'OWNER' ELSE 'MEMBER' END AS my_role"
          + " FROM app_groups g JOIN group_members m ON m.group_id=g.id WHERE m.user_id=#{userId}"
          + " AND m.member_status='ACTIVE' AND g.group_status='ACTIVE' ORDER BY g.created_at"
          + " DESC,g.id DESC LIMIT #{size} OFFSET #{offset}")
  List<Group> list(
      @Param("userId") long userId, @Param("offset") int offset, @Param("size") int size);

  @Select(
      "SELECT COUNT(*) FROM app_groups g JOIN group_members m ON m.group_id=g.id WHERE"
          + " m.user_id=#{userId} AND m.member_status='ACTIVE' AND g.group_status='ACTIVE'")
  long listCount(long userId);

  @Select(
      "SELECT m.user_id,u.display_name,u.avatar_url,CASE WHEN g.owner_user_id=m.user_id THEN"
          + " 'OWNER' ELSE 'MEMBER' END AS role,m.joined_at FROM group_members m JOIN app_groups g"
          + " ON g.id=m.group_id JOIN users u ON u.id=m.user_id WHERE m.group_id=#{id} AND"
          + " m.member_status='ACTIVE' ORDER BY m.user_id LIMIT #{size} OFFSET #{offset}")
  List<Member> members(@Param("id") long id, @Param("offset") int offset, @Param("size") int size);

  @Select("SELECT COUNT(*) FROM group_members WHERE group_id=#{id} AND member_status='ACTIVE'")
  long memberTotal(long id);
}
