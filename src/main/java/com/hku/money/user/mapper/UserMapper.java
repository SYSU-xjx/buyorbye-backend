package com.hku.money.user.mapper;

import com.hku.money.user.entity.Streak;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {
  void updateProfile(
      @Param("id") long id,
      @Param("displayName") String displayName,
      @Param("avatarUrl") String avatarUrl,
      @Param("preferredCurrency") String preferredCurrency);

  Streak streak(long id);

  void ensureStreak(long id);

  Streak lockStreak(long id);

  void updateStreak(@Param("id") long id, @Param("streak") Streak streak);

  int score(long id);

  Map<String, Object> statistics(long id);

  List<Map<String, Object>> ledger(
      @Param("id") long id, @Param("offset") int offset, @Param("size") int size);

  long ledgerCount(long id);

  List<Map<String, Object>> records(
      @Param("id") long id,
      @Param("type") String type,
      @Param("status") String status,
      @Param("offset") int offset,
      @Param("size") int size);

  long recordsCount(
      @Param("id") long id, @Param("type") String type, @Param("status") String status);
}
