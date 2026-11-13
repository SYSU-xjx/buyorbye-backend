package com.hku.money.group.controller;

import com.hku.money.common.*;
import com.hku.money.group.dto.GroupModels.*;
import com.hku.money.group.service.GroupService;
import com.hku.money.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups")
public class GroupController {
  private final GroupService groups;

  public GroupController(GroupService groups) {
    this.groups = groups;
  }

  private long user() {
    return CurrentUser.get().userId();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Result<Group> create(@Valid @RequestBody Name req) {
    return Result.created(WriteRetry.run(() -> groups.create(user(), req.name())));
  }

  @GetMapping
  public Result<PageResult<Group>> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return Result.success(groups.list(user(), page, size));
  }

  @GetMapping("/{id}")
  public Result<Group> detail(@PathVariable @Positive long id) {
    return Result.success(groups.detail(id, user()));
  }

  @PatchMapping("/{id}")
  public Result<Group> rename(@PathVariable @Positive long id, @Valid @RequestBody Name req) {
    return Result.success(WriteRetry.run(() -> groups.rename(id, user(), req.name())));
  }

  @GetMapping("/{id}/members")
  public Result<PageResult<Member>> members(
      @PathVariable @Positive long id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(groups.members(id, user(), page, size));
  }

  @PostMapping("/join")
  public Result<Group> join(@Valid @RequestBody Join req) {
    return Result.success(WriteRetry.run(() -> groups.join(user(), req.inviteCode())));
  }

  @DeleteMapping("/{id}/members/me")
  public Result<Void> leave(@PathVariable @Positive long id) {
    WriteRetry.run(
        () -> {
          groups.leave(id, user());
          return null;
        });
    return Result.success();
  }

  @DeleteMapping("/{id}/members/{userId}")
  public Result<Void> remove(@PathVariable @Positive long id, @PathVariable @Positive long userId) {
    WriteRetry.run(
        () -> {
          groups.remove(id, user(), userId);
          return null;
        });
    return Result.success();
  }

  @PutMapping("/{id}/owner")
  public Result<Group> owner(@PathVariable @Positive long id, @Valid @RequestBody Owner req) {
    return Result.success(WriteRetry.run(() -> groups.transfer(id, user(), req.userId())));
  }

  @PostMapping("/{id}/invite")
  public Result<Group> invite(@PathVariable @Positive long id) {
    return Result.success(WriteRetry.run(() -> groups.rotate(id, user())));
  }

  @DeleteMapping("/{id}")
  public Result<Void> close(@PathVariable @Positive long id) {
    WriteRetry.run(
        () -> {
          groups.close(id, user());
          return null;
        });
    return Result.success();
  }
}
