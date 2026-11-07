package com.hku.money.ranking.mapper;

import com.hku.money.ranking.dto.RankingEntry;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RankingMapper {
  List<RankingEntry> entries(
      @Param("type") String type,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      @Param("offset") int offset,
      @Param("size") int size);

  RankingEntry me(
      @Param("type") String type,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      @Param("userId") long userId);

  long count(
      @Param("type") String type,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);
}
