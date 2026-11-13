package com.hku.money.proposal.controller;

import com.hku.money.common.Result;
import com.hku.money.common.WriteRetry;
import com.hku.money.proposal.dto.ProposalModels.Image;
import com.hku.money.proposal.service.ProposalImageService;
import com.hku.money.security.CurrentUser;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/purchase-proposals/{id}/images")
public class ProposalImageController {
  private final ProposalImageService images;

  public ProposalImageController(ProposalImageService images) {
    this.images = images;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Result<Image> upload(
      @PathVariable @Positive long id, @RequestPart("file") MultipartFile file) {
    String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    return Result.created(
        WriteRetry.run(() -> images.upload(id, CurrentUser.get().userId(), file, base)));
  }

  @DeleteMapping("/{imageId}")
  public Result<Void> delete(
      @PathVariable @Positive long id, @PathVariable @Positive long imageId) {
    WriteRetry.run(
        () -> {
          images.delete(id, imageId, CurrentUser.get().userId());
          return null;
        });
    return Result.success();
  }

  @GetMapping("/{imageId}/content")
  public ResponseEntity<Resource> content(
      @PathVariable @Positive long id, @PathVariable @Positive long imageId) {
    var file = images.content(id, imageId, CurrentUser.get().userId());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(file.toString().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .body(new FileSystemResource(file));
  }
}
