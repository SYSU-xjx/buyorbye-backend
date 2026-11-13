package com.hku.money.common;

import java.util.function.Supplier;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Retry only after the proxied service has rolled back its entire transaction. */
public final class WriteRetry {
  private WriteRetry() {}

  public static <T> T run(Supplier<T> operation) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Write retries must wrap, not join, a transaction");
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        return operation.get();
      } catch (PessimisticLockingFailureException e) {
        if (attempt == 2) throw new ApiException(409, "CONCURRENT_WRITE", "数据正在被修改，请稍后重试");
        try {
          Thread.sleep(20L * (attempt + 1));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new ApiException(409, "CONCURRENT_WRITE", "请求已中断，请重试");
        }
      }
    }
    throw new IllegalStateException("Unreachable");
  }
}
