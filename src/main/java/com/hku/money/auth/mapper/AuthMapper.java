package com.hku.money.auth.mapper;

import com.hku.money.auth.entity.*;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuthMapper {
  Account byUsername(String username);

  Account byId(long id);

  Account lockUser(long id);

  int insertUser(Account user);

  void initReputation(long id);

  void initStreak(long id);

  void insertSession(LoginSession session);

  LoginSession byRefresh(String hash);

  LoginSession lockSession(long id);

  LoginSession activeSession(
      @Param("id") long id, @Param("userId") long userId, @Param("now") LocalDateTime now);

  void rotate(@Param("id") long id, @Param("hash") String hash, @Param("now") LocalDateTime now);

  void revoke(@Param("id") long id, @Param("now") LocalDateTime now);

  void revokeAll(@Param("userId") long userId, @Param("now") LocalDateTime now);

  void password(@Param("id") long id, @Param("hash") String hash);

  void touch(@Param("id") long id, @Param("now") LocalDateTime now);

  void anonymize(@Param("id") long id, @Param("hash") String hash);
}
