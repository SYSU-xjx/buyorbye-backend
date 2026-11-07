package com.hku.money.ranking.controller;

import com.hku.money.common.Result;
import com.hku.money.ranking.service.*;
import com.hku.money.security.CurrentUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rankings")
public class RankingController {
  private final RankingService service;

  public RankingController(RankingService service) {
    this.service = service;
  }

  @GetMapping
  public Result<RankingService.RankingPage> list(
      @RequestParam RankingService.Type type,
      @RequestParam RankingWindow.Period period,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(service.list(type, period, date, page, size));
  }

  @GetMapping("/me")
  public Result<RankingService.MyRanking> me(
      @RequestParam RankingService.Type type,
      @RequestParam RankingWindow.Period period,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return Result.success(service.me(CurrentUser.get().userId(), type, period, date));
  }
}
