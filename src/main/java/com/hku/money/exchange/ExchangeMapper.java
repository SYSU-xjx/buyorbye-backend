package com.hku.money.exchange;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ExchangeMapper {
  @Select("SELECT EXISTS(SELECT 1 FROM exchange_rate_snapshots WHERE proposal_id=#{id})")
  boolean exists(long id);

  @Select(
      "SELECT rate,rate_date AS date,rate_source AS source FROM exchange_rate_snapshots WHERE"
          + " source_currency=#{currency} AND target_currency='HKD' AND rate_date<=#{date} ORDER BY"
          + " rate_date DESC,id DESC LIMIT 1")
  RateProvider.Rate fallback(@Param("currency") String currency, @Param("date") LocalDate date);

  @Insert(
      "INSERT INTO"
          + " exchange_rate_snapshots(proposal_id,source_currency,target_currency,rate,converted_amount,rate_source,rate_date)"
          + " VALUES(#{id},#{currency},'HKD',#{rate.rate},#{amount},#{rate.source},#{rate.date}) ON"
          + " DUPLICATE KEY UPDATE proposal_id=proposal_id")
  void snapshot(
      @Param("id") long id,
      @Param("currency") String currency,
      @Param("rate") RateProvider.Rate rate,
      @Param("amount") BigDecimal amount);
}
