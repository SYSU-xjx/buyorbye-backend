package com.hku.money.wheel.controller;

import com.hku.money.common.Result;
import com.hku.money.security.CurrentUser;
import com.hku.money.wheel.service.WheelService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-proposals/{proposalId}/wheel")
public class WheelController {
  private final WheelService service;

  public WheelController(WheelService service) {
    this.service = service;
  }

  @PostMapping("/spin")
  public Result<Integer> spin(@PathVariable long proposalId) {
    return Result.success(service.spin(CurrentUser.get().userId(), proposalId));
  }
}
