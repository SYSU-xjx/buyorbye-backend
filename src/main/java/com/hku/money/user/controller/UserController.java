package com.hku.money.user.controller;

import com.hku.money.auth.dto.AuthRequests.Password;
import com.hku.money.auth.service.AuthService;
import com.hku.money.common.PageResult;
import com.hku.money.common.Result;
import com.hku.money.security.CurrentUser;
import com.hku.money.user.dto.UserProfile;
import com.hku.money.user.service.UserService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
  private final UserService users;
  private final AuthService auth;

  public UserController(UserService users, AuthService auth) {
    this.users = users;
    this.auth = auth;
  }

  private long id() {
    return CurrentUser.get().userId();
  }

  @GetMapping
  public Result<UserProfile> profile() {
    return Result.success(users.profile(id()));
  }

  @PatchMapping
  public Result<UserProfile> patch(@Valid @RequestBody UserService.ProfilePatch req) {
    return Result.success(users.patch(id(), req));
  }

  @PutMapping("/password")
  public Result<Void> password(@Valid @RequestBody Password req) {
    auth.changePassword(CurrentUser.get(), req);
    return Result.success();
  }

  @DeleteMapping
  public Result<Void> delete() {
    auth.delete(CurrentUser.get());
    return Result.success();
  }

  @GetMapping("/statistics")
  public Result<Map<String, Object>> statistics() {
    return Result.success(users.statistics(id()));
  }

  @GetMapping("/records")
  public Result<PageResult<Map<String, Object>>> records(
      @RequestParam String type,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(users.records(id(), type, status, page, size));
  }

  @GetMapping("/reputation")
  public Result<UserService.Reputation> reputation() {
    return Result.success(users.reputation(id()));
  }

  @GetMapping("/reputation/ledger")
  public Result<PageResult<Map<String, Object>>> ledger(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return Result.success(users.ledger(id(), page, size));
  }

  @GetMapping("/streak")
  public Result<UserService.StreakView> streak() {
    return Result.success(users.streak(id()));
  }

  @PostMapping("/check-in")
  public Result<UserService.StreakView> checkIn() {
    return Result.success(users.checkIn(id()));
  }
}
