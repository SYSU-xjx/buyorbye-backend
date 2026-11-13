package com.hku.money.proposal.service;

import com.hku.money.common.*;
import com.hku.money.proposal.dto.ProposalModels.Image;
import com.hku.money.proposal.mapper.ProposalMapper;
import com.hku.money.security.AccountGuard;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProposalImageService {
  private final ProposalMapper mapper;
  private final ProposalAccess access;
  private final AccountGuard guard;
  private final ProposalImageStore files;
  private final String publicBase;

  public ProposalImageService(
      ProposalMapper mapper,
      ProposalAccess access,
      AccountGuard guard,
      ProposalImageStore files,
      @Value("${app.proposal.image-base-url:}") String base) {
    this.mapper = mapper;
    this.access = access;
    this.guard = guard;
    this.files = files;
    this.publicBase = base.replaceAll("/+$", "");
    if (!publicBase.isEmpty()) {
      var uri = java.net.URI.create(publicBase);
      if (!java.util.Set.of("http", "https").contains(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null)
        throw new IllegalArgumentException("Invalid proposal image base URL");
    }
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Image upload(long id, long user, MultipartFile file, String requestBase) {
    guard.lockActive(user);
    var p = access.lock(id);
    access.owner(p, user);
    if (!access.editable(p)) throw ProposalAccess.conflict("当前不能修改配图");
    var existing = mapper.images(id);
    if (existing.size() >= 4) throw ApiException.bad("最多上传4张图片");
    int position = 1;
    var used = existing.stream().map(i -> i.displayOrder).toList();
    while (used.contains(position)) position++;
    var saved = files.save(file);
    files.rollbackCleanup(saved.path());
    var row = new ProposalMapper.ImageRow();
    row.proposalId = id;
    row.displayOrder = position;
    row.imageUrl = "pending";
    mapper.insertImage(row);
    String base = publicBase.isEmpty() ? requestBase : publicBase;
    row.imageUrl =
        base
            + "/api/purchase-proposals/"
            + id
            + "/images/"
            + row.id
            + "/content?name="
            + saved.filename();
    if (row.imageUrl.length() > 2048) throw ApiException.bad("图片访问地址过长");
    mapper.imageUrl(row.id, row.imageUrl);
    return new Image(row.id, row.imageUrl, position);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void delete(long id, long image, long user) {
    guard.lockActive(user);
    var p = access.lock(id);
    access.owner(p, user);
    if (!access.editable(p)) throw ProposalAccess.conflict("当前不能修改配图");
    var row = mapper.image(id, image);
    if (row == null) return;
    mapper.deleteImage(image);
    files.deleteAfterCommit(row.imageUrl);
  }

  @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
  public Path content(long id, long image, long user) {
    access.read(id, user);
    var row = mapper.image(id, image);
    if (row == null) throw new ApiException(404, "IMAGE_NOT_FOUND", "图片不存在");
    return files.find(row.imageUrl);
  }
}
