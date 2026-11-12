package com.hku.money.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Shared response envelope. code always equals the HTTP response status. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record Result<T>(int code, String message, T data) {
  public static <T> Result<T> success(T data) {
    return new Result<>(200, "操作成功", data);
  }

  public static <T> Result<T> created(T data) {
    return new Result<>(201, "创建成功", data);
  }

  public static Result<Void> success() {
    return success(null);
  }

  public static Result<Void> failure(int code, String message) {
    return new Result<>(code, message, null);
  }
}
