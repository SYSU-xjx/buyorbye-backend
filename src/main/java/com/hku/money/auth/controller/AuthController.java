package com.hku.money.auth.controller;

import com.hku.money.auth.dto.AuthRequests.*;
import com.hku.money.auth.service.AuthService;
import com.hku.money.common.Result;
import com.hku.money.security.CurrentUser;
import com.hku.money.user.dto.UserProfile;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @io.swagger.v3.oas.annotations.security.SecurityRequirements
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Result<UserProfile> register(@Valid @RequestBody Register req) {
    return Result.created(service.register(req));
  }

  @io.swagger.v3.oas.annotations.security.SecurityRequirements
  @PostMapping("/login")
  public Result<AuthService.Tokens> login(@Valid @RequestBody Login req) {
    return Result.success(service.login(req));
  }

  @io.swagger.v3.oas.annotations.security.SecurityRequirements
  @PostMapping("/refresh")
  public Result<AuthService.Tokens> refresh(@Valid @RequestBody Refresh req) {
    return Result.success(service.refresh(req));
  }

  @PostMapping("/logout")
  public Result<Void> logout() {
    service.logout(CurrentUser.get());
    return Result.success();
  }
}
