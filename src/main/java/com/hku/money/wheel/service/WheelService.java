package com.hku.money.wheel.service;

import com.hku.money.common.ApiException;
import com.hku.money.security.AccountGuard;
import com.hku.money.wheel.mapper.WheelMapper;
import java.security.SecureRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WheelService {
  private final WheelMapper mapper;
  private final SecureRandom random = new SecureRandom();
  private final AccountGuard guard;

  public WheelService(WheelMapper mapper, AccountGuard guard) {
    this.mapper = mapper;
    this.guard = guard;
  }

  @Transactional
  public int spin(long userId, long proposalId) {
    guard.lockActive(userId);
    var p = mapper.proposal(proposalId);
    if (p == null) throw new ApiException(404, "PROPOSAL_NOT_FOUND", "提案不存在");
    if (p.creatorUserId != userId) throw new ApiException(403, "FORBIDDEN", "仅提案发起人可以使用转盘");
    if (!"APPROVE_REJECT".equals(p.participationType)
        || !java.util.Set.of("OPEN", "SETTLED", "INSUFFICIENT_PARTICIPATION")
            .contains(p.proposalStatus)
        || p.publishedAt == null)
      throw new ApiException(409, "WHEEL_UNAVAILABLE", "仅已发布且未撤回的二元购买提案可以使用转盘");
    return random.nextInt(3) + 1;
  }
}
