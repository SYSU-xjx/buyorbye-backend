package com.hku.money.group.service;

import com.hku.money.auth.mapper.AuthMapper;
import com.hku.money.common.*;
import com.hku.money.group.dto.GroupModels.*;
import com.hku.money.group.mapper.GroupMapper;
import com.hku.money.security.AccountGuard;
import java.security.SecureRandom;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class GroupService {
  private final GroupMapper mapper;
  private final AccountGuard guard;
  private final AuthMapper users;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public GroupService(GroupMapper mapper, AccountGuard guard, AuthMapper users, Clock clock) {
    this.mapper = mapper;
    this.guard = guard;
    this.users = users;
    this.clock = clock;
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  public Group require(long id, long viewer, boolean lock) {
    Group g = lock ? mapper.lock(id) : mapper.find(id);
    if (g == null
        || !"ACTIVE".equals(g.groupStatus)
        || !"ACTIVE".equals(mapper.membership(id, viewer)))
      throw new ApiException(404, "GROUP_NOT_FOUND", "群组不存在或没有访问权限");
    g.myRole = g.ownerUserId == viewer ? "OWNER" : "MEMBER";
    return g;
  }

  private Group owned(long id, long viewer) {
    Group g = require(id, viewer, true);
    if (g.ownerUserId != viewer) throw new ApiException(403, "NOT_GROUP_OWNER", "仅群主可以执行此操作");
    return g;
  }

  private String code() {
    for (int i = 0; i < 32; i++) {
      String code = String.format(java.util.Locale.ROOT, "%08d", random.nextInt(100000000));
      if (mapper.byCode(code) == null) return code;
    }
    throw new ApiException(409, "INVITE_BUSY", "邀请码生成冲突，请重试");
  }

  @Transactional
  public Group create(long user, String name) {
    guard.lockActive(user);
    Group g = new Group();
    g.name = name.strip();
    g.ownerUserId = user;
    g.inviteCode = code();
    g.createdAt = now();
    g.inviteCodeExpiresAt = g.createdAt.plusDays(7);
    mapper.insert(g);
    mapper.join(g.id, user, "OWNER", now());
    return require(g.id, user, false);
  }

  public Group detail(long id, long user) {
    return require(id, user, false);
  }

  public PageResult<Group> list(long user, int page, int size) {
    int offset = PageResult.offset(page, size);
    return new PageResult<>(mapper.list(user, offset, size), page, size, mapper.listCount(user));
  }

  public PageResult<Member> members(long id, long user, int page, int size) {
    require(id, user, false);
    int offset = PageResult.offset(page, size);
    return new PageResult<>(mapper.members(id, offset, size), page, size, mapper.memberTotal(id));
  }

  @Transactional
  public Group rename(long id, long user, String name) {
    guard.lockActive(user);
    owned(id, user);
    mapper.rename(id, name.strip());
    return require(id, user, false);
  }

  @Transactional
  public Group join(long user, String code) {
    guard.lockActive(user);
    Long id = mapper.byCode(code);
    Group g = id == null ? null : mapper.lock(id);
    if (g == null
        || !"ACTIVE".equals(g.groupStatus)
        || !code.equals(g.inviteCode)
        || g.inviteCodeExpiresAt == null
        || !g.inviteCodeExpiresAt.isAfter(now())) throw ApiException.bad("邀请码无效或已过期");
    if (!"ACTIVE".equals(mapper.membership(id, user))) {
      mapper.join(id, user, "MEMBER", now());
      mapper.count(id, 1);
    }
    return require(id, user, false);
  }

  @Transactional
  public void leave(long id, long user) {
    guard.lockActive(user);
    Group g = mapper.lock(id);
    if (g == null || !"ACTIVE".equals(g.groupStatus))
      throw new ApiException(404, "GROUP_NOT_FOUND", "群组不存在");
    if (g.ownerUserId == user) throw new ApiException(409, "OWNER_CANNOT_LEAVE", "群主须先转让群组");
    if (mapper.leave(id, user, "LEFT", now()) == 1) mapper.count(id, -1);
  }

  @Transactional
  public void remove(long id, long owner, long user) {
    guard.lockActive(owner);
    owned(id, owner);
    if (owner == user) throw ApiException.bad("不能移除自己");
    if (mapper.leave(id, user, "REMOVED", now()) == 1) mapper.count(id, -1);
  }

  @Transactional
  public Group transfer(long id, long owner, long user) {
    guard.lockActive(owner);
    owned(id, owner);
    var account = users.byId(user);
    if (!"ACTIVE".equals(mapper.membership(id, user))
        || account == null
        || !"ACTIVE".equals(account.accountStatus)) throw ApiException.bad("新群主须为有效群成员");
    mapper.owner(id, user);
    mapper.roles(id, user);
    return require(id, owner, false);
  }

  @Transactional
  public Group rotate(long id, long owner) {
    guard.lockActive(owner);
    owned(id, owner);
    mapper.invite(id, code(), now().plusDays(7));
    return require(id, owner, false);
  }

  @Transactional
  public void close(long id, long owner) {
    guard.lockActive(owner);
    Group g = mapper.lock(id);
    if (g == null) throw new ApiException(404, "GROUP_NOT_FOUND", "群组不存在");
    if (g.ownerUserId != owner) throw new ApiException(403, "NOT_GROUP_OWNER", "仅群主可以解散群组");
    if ("CLOSED".equals(g.groupStatus)) return;
    mapper.close(id);
    mapper.endProposals(id, now());
  }
}
