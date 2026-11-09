package com.hku.money.proposal.service;

import com.hku.money.common.ApiException;
import com.hku.money.user.service.AvatarStorage;
import java.net.URI;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

/** Reuses the existing safe PNG/JPEG decoder, with a separate private directory. */
@Component
public class ProposalImageStore {
  private final AvatarStorage storage;

  public ProposalImageStore(
      @Value("${app.proposal.image-dir:./uploads/proposals}") String directory) {
    storage = new AvatarStorage(directory);
  }

  public AvatarStorage.Stored save(MultipartFile file) {
    try {
      return storage.save(file);
    } catch (ApiException e) {
      throw new ApiException(e.status, e.code, e.getMessage().replace("头像", "审批图片"));
    }
  }

  public Path find(String url) {
    return storage.find(filename(url));
  }

  private String filename(String url) {
    String query = URI.create(url).getRawQuery();
    if (query == null || !query.matches("name=[0-9a-f]{32}\\.(png|jpg)"))
      throw new ApiException(404, "IMAGE_NOT_FOUND", "图片不存在");
    return query.substring(5);
  }

  public void rollbackCleanup(Path path) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) storage.discard(path);
          }
        });
  }

  public void deleteAfterCommit(String url) {
    Path path;
    try {
      path = find(url);
    } catch (ApiException e) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            storage.discard(path);
          }
        });
  }
}
