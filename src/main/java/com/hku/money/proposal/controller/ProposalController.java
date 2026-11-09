package com.hku.money.proposal.controller;

import com.hku.money.common.*;
import com.hku.money.plaza.dto.PlazaResponses.Proposal;
import com.hku.money.plaza.dto.PlazaResponses.VoteReason;
import com.hku.money.proposal.dto.ProposalModels.*;
import com.hku.money.proposal.service.*;
import com.hku.money.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProposalController {
  private final ProposalService proposals;
  private final VoteService votes;

  public ProposalController(ProposalService proposals, VoteService votes) {
    this.proposals = proposals;
    this.votes = votes;
  }

  private long user() {
    return CurrentUser.get().userId();
  }

  @PostMapping("/purchase-proposals")
  @ResponseStatus(HttpStatus.CREATED)
  public Result<Detail> create(@Valid @RequestBody Save r) {
    return Result.created(WriteRetry.run(() -> proposals.create(user(), r)));
  }

  @PutMapping("/purchase-proposals/{id}")
  public Result<Detail> save(@PathVariable @Positive long id, @Valid @RequestBody Save r) {
    return Result.success(WriteRetry.run(() -> proposals.save(id, user(), r)));
  }

  @GetMapping("/purchase-proposals/{id}")
  public Result<Detail> detail(@PathVariable @Positive long id) {
    return Result.success(proposals.detail(id, user()));
  }

  @DeleteMapping("/purchase-proposals/{id}")
  public Result<Void> delete(@PathVariable @Positive long id) {
    WriteRetry.run(
        () -> {
          proposals.delete(id, user());
          return null;
        });
    return Result.success();
  }

  @PostMapping("/purchase-proposals/{id}/publish")
  public Result<Detail> publish(
      @PathVariable @Positive long id, @Valid @RequestBody(required = false) Publish r) {
    return Result.success(
        WriteRetry.run(() -> proposals.publish(id, user(), r == null ? new Publish(null) : r)));
  }

  @PostMapping("/purchase-proposals/{id}/withdraw")
  public Result<Detail> withdraw(@PathVariable @Positive long id) {
    return Result.success(WriteRetry.run(() -> proposals.withdraw(id, user())));
  }

  @GetMapping("/groups/{id}/purchase-proposals")
  public Result<PageResult<Proposal>> group(
      @PathVariable @Positive long id,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(proposals.groupFeed(id, user(), status, page, size));
  }

  @PostMapping("/purchase-proposals/{id}/votes")
  public Result<VoteEvent> vote(@PathVariable @Positive long id, @Valid @RequestBody Vote r) {
    return Result.success(WriteRetry.run(() -> votes.vote(id, user(), r)));
  }

  @GetMapping("/purchase-proposals/{id}/votes/me")
  public Result<VoteEvent> myVote(@PathVariable @Positive long id) {
    return Result.success(votes.mine(id, user()));
  }

  @GetMapping("/purchase-proposals/{id}/vote-reasons")
  public Result<PageResult<VoteReason>> reasons(
      @PathVariable @Positive long id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(proposals.reasons(id, user(), page, size));
  }
}
