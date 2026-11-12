package com.hku.money.plaza.controller;

import com.hku.money.common.PageResult;
import com.hku.money.common.Result;
import com.hku.money.plaza.dto.PlazaResponses.Channel;
import com.hku.money.plaza.dto.PlazaResponses.Detail;
import com.hku.money.plaza.dto.PlazaResponses.Proposal;
import com.hku.money.plaza.dto.PlazaResponses.Scope;
import com.hku.money.plaza.dto.PlazaResponses.Tag;
import com.hku.money.plaza.dto.PlazaResponses.VoteReason;
import com.hku.money.plaza.service.PlazaService;
import com.hku.money.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlazaController {
  public record SubscriptionRequest(
      @NotNull @Size(max = 100) List<@NotNull @Positive Long> channelIds) {}

  private final PlazaService service;

  public PlazaController(PlazaService service) {
    this.service = service;
  }

  @GetMapping("/plaza")
  public Result<PageResult<Proposal>> feed(
      @RequestParam(defaultValue = "ALL") Scope scope,
      @RequestParam(required = false) @Positive Long channelId,
      @RequestParam(required = false) @Positive Long tagId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(service.feed(CurrentUser.get().userId(), scope, channelId, tagId, page, size));
  }

  @GetMapping("/plaza/{proposalId}")
  public Result<Detail> detail(@PathVariable @Positive long proposalId) {
    return Result.success(service.detail(proposalId, CurrentUser.get().userId()));
  }

  @GetMapping("/plaza/random")
  public Result<Proposal> random(
      @RequestParam(defaultValue = "ALL") Scope scope,
      @RequestParam(required = false) @Positive Long channelId,
      @RequestParam(required = false) @Positive Long tagId,
      @RequestParam(required = false) @Positive Long excludeProposalId) {
    return Result.success(service.random(
        CurrentUser.get().userId(), scope, channelId, tagId, excludeProposalId));
  }

  @GetMapping("/plaza/{proposalId}/vote-reasons")
  public Result<PageResult<VoteReason>> voteReasons(
      @PathVariable @Positive long proposalId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(service.voteReasons(proposalId, CurrentUser.get().userId(), page, size));
  }

  @GetMapping("/channels")
  public Result<List<Channel>> channels(@RequestParam(defaultValue = "ALL") Scope scope) {
    return Result.success(service.channels(CurrentUser.get().userId(), scope));
  }

  @PutMapping("/channels/subscriptions")
  public Result<List<Channel>> replaceSubscriptions(@RequestBody @Valid SubscriptionRequest request) {
    return Result.success(service.replaceSubscriptions(CurrentUser.get().userId(), request.channelIds()));
  }

  @GetMapping("/tags")
  public Result<List<Tag>> tags() {
    return Result.success(service.tags());
  }
}
