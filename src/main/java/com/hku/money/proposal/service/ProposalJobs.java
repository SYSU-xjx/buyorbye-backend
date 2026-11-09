package com.hku.money.proposal.service;

import com.hku.money.exchange.ExchangeService;
import com.hku.money.proposal.mapper.ProposalMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "app.proposal.jobs-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ProposalJobs {
  private final ProposalMapper mapper;
  private final ProposalAccess access;
  private final SettlementService settlements;
  private final ExchangeService exchange;
  private int rateOffset;

  public ProposalJobs(
      ProposalMapper mapper,
      ProposalAccess access,
      SettlementService settlements,
      ExchangeService exchange) {
    this.mapper = mapper;
    this.access = access;
    this.settlements = settlements;
    this.exchange = exchange;
  }

  @Scheduled(fixedDelayString = "${app.proposal.settlement-delay-ms:15000}", initialDelay = 15000)
  public void settleDue() {
    for (long id : mapper.due(access.now()))
      try {
        settlements.settle(id);
      } catch (Exception e) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Settlement will retry for proposal {}", id, e);
      }
  }

  @Scheduled(fixedDelayString = "${app.proposal.exchange-delay-ms:60000}", initialDelay = 30000)
  public void fillRates() {
    if (rateOffset >= mapper.missingRateCount()) rateOffset = 0;
    var ids = mapper.missingRates(rateOffset);
    rateOffset += ids.size();
    for (long id : ids)
      try {
        exchange.fill(id);
      } catch (Exception e) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Rate snapshot will retry for proposal {}", id, e);
      }
  }
}
