package com.hku.money.user.controller;

import com.hku.money.common.Result;
import com.hku.money.security.CurrentUser;
import com.hku.money.user.service.AvatarService;
import com.hku.money.user.service.AvatarStorage;
import java.time.Duration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class AvatarController {
  private final AvatarService service;
  private final AvatarStorage storage;

  public AvatarController(AvatarService service, AvatarStorage storage) {
    this.service = service;
    this.storage = storage;
  }

  @PostMapping(value = "/api/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Result<AvatarService.AvatarResponse> upload(@RequestPart("file") MultipartFile file) {
    String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    return Result.success(service.upload(CurrentUser.get().userId(), file, base));
  }

  // Image bytes, not a JSON Result: usable directly by Android image loaders and <img>.
  @io.swagger.v3.oas.annotations.security.SecurityRequirements
  @GetMapping("/uploads/avatars/{filename}")
  public ResponseEntity<Resource> image(@PathVariable String filename) {
    var file = storage.find(filename);
    return ResponseEntity.ok()
        .contentType(filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable())
        .body(new FileSystemResource(file));
  }
}
