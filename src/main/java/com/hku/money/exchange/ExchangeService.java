package com.hku.money.exchange;

import com.hku.money.proposal.mapper.ProposalMapper;
import java.math.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExchangeService {
  private final ProposalMapper proposals;
  private final ExchangeMapper mapper;
  private final RateProvider provider;
  private final TransactionTemplate transactions;

  public ExchangeService(
      ProposalMapper proposals,
      ExchangeMapper mapper,
      RateProvider provider,
      PlatformTransactionManager manager) {
    this.proposals = proposals;
    this.mapper = mapper;
    this.provider = provider;
    this.transactions = new TransactionTemplate(manager);
  }

  /** Fetch outside any DB transaction, then atomically insert an immutable snapshot. */
  public void fill(long id) {
    if (org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive())
      throw new IllegalStateException("Rate fetch must run outside a transaction");
    if (mapper.exists(id)) return;
    var p = proposals.find(id);
    if (p == null
        || !"SETTLED".equals(p.proposalStatus)
        || !"APPROVE_REJECT".equals(p.participationType)
        || !Integer.valueOf(1).equals(p.decisionResult)
        || p.settledAt == null
        || p.currency == null
        || p.amount == null) return;
    var date = p.settledAt.toLocalDate();
    var rate = provider.fetch(p.currency, date).orElseGet(() -> mapper.fallback(p.currency, date));
    if (rate == null || rate.rate().signum() <= 0 || rate.date().isAfter(date)) return;
    BigDecimal amount = p.amount.multiply(rate.rate()).setScale(2, RoundingMode.HALF_UP);
    if (amount.precision() > 16 || rate.rate().scale() > 10 || rate.rate().precision() > 20) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn("Conversion outside schema precision for proposal {}", id);
      return;
    }
    transactions.executeWithoutResult(
        status -> {
          var locked = proposals.lock(id);
          if (locked != null
              && "SETTLED".equals(locked.proposalStatus)
              && Integer.valueOf(1).equals(locked.decisionResult))
            mapper.snapshot(id, p.currency, rate, amount);
        });
  }
}
