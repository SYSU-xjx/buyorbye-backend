package com.hku.money.plaza.controller;

import com.hku.money.common.PageResult;
import com.hku.money.common.Result;
import com.hku.money.plaza.dto.PlazaResponses.AuthorUpdate;
import com.hku.money.plaza.dto.PlazaResponses.Comment;
import com.hku.money.plaza.dto.PlazaResponses.Flower;
import com.hku.money.plaza.dto.PlazaResponses.Watch;
import com.hku.money.plaza.service.PlazaInteractionService;
import com.hku.money.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchase-proposals/{proposalId}")
public class PlazaInteractionController {
  public record CommentRequest(@NotBlank @Size(max = 1000) String content, @Positive Long parentCommentId) {}

  public record UpdateRequest(@NotBlank @Size(max = 1000) String content) {}

  private final PlazaInteractionService service;

  public PlazaInteractionController(PlazaInteractionService service) {
    this.service = service;
  }

  @PutMapping("/watches")
  public Result<Watch> watch(@PathVariable @Positive long proposalId) {
    return Result.success(service.setWatching(proposalId, CurrentUser.get().userId(), true));
  }

  @DeleteMapping("/watches")
  public Result<Watch> unwatch(@PathVariable @Positive long proposalId) {
    return Result.success(service.setWatching(proposalId, CurrentUser.get().userId(), false));
  }

  @GetMapping("/comments")
  public Result<PageResult<Comment>> comments(
      @PathVariable @Positive long proposalId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(service.comments(proposalId, CurrentUser.get().userId(), page, size));
  }

  @PostMapping("/comments")
  @ResponseStatus(HttpStatus.CREATED)
  public Result<Comment> addComment(
      @PathVariable @Positive long proposalId, @RequestBody @Valid CommentRequest request) {
    return Result.created(service.addComment(
        proposalId, CurrentUser.get().userId(), request.parentCommentId(), request.content()));
  }

  @DeleteMapping("/comments/{commentId}")
  public Result<Void> deleteComment(
      @PathVariable @Positive long proposalId, @PathVariable @Positive long commentId) {
    service.deleteComment(proposalId, CurrentUser.get().userId(), commentId);
    return Result.success();
  }

  @PutMapping("/flowers")
  public Result<Flower> sendFlower(@PathVariable @Positive long proposalId) {
    return Result.success(service.sendFlower(proposalId, CurrentUser.get().userId()));
  }

  @GetMapping("/updates")
  public Result<PageResult<AuthorUpdate>> updates(
      @PathVariable @Positive long proposalId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(service.updates(proposalId, page, size));
  }

  @PostMapping("/updates")
  @ResponseStatus(HttpStatus.CREATED)
  public Result<AuthorUpdate> addUpdate(
      @PathVariable @Positive long proposalId, @RequestBody @Valid UpdateRequest request) {
    return Result.created(service.addUpdate(proposalId, CurrentUser.get().userId(), request.content()));
  }
}
