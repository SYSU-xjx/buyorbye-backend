package com.hku.money.wheel.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WheelMapper {
  Proposal proposal(long id);

  class Proposal {
    public long creatorUserId;
    public String participationType, proposalStatus;
    public java.time.LocalDateTime publishedAt;
  }
}
