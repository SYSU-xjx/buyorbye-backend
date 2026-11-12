package com.hku.money.user.service;

import com.hku.money.security.AccountGuard;
import com.hku.money.user.mapper.UserMapper;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarService {
  private final AvatarStorage storage;
  private final UserMapper users;
  private final AccountGuard guard;
  private final String publicBase;

  public AvatarService(
      AvatarStorage storage,
      UserMapper users,
      AccountGuard guard,
      @Value("${app.avatar.public-base-url:}") String publicBase) {
    this.storage = storage;
    this.users = users;
    this.guard = guard;
    this.publicBase = publicBase.strip().replaceAll("/+$", "");
    if (!this.publicBase.isEmpty()) {
      URI uri = URI.create(this.publicBase);
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || uri.getUserInfo() != null)
        throw new IllegalArgumentException("AVATAR_PUBLIC_BASE_URL must be an HTTP(S) base URL");
    }
  }

  public record AvatarResponse(String avatarUrl) {}

  @Transactional
  public AvatarResponse upload(long userId, MultipartFile file, String requestBase) {
    guard.lockActive(userId);
    var saved = storage.save(file);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) storage.discard(saved.path());
          }
        });
    String base = publicBase.isEmpty() ? requestBase : publicBase;
    String url = base + "/uploads/avatars/" + saved.filename();
    if (url.length() > 2048) throw com.hku.money.common.ApiException.bad("头像访问地址过长");
    users.updateProfile(userId, null, url, null);
    return new AvatarResponse(url);
  }
}
