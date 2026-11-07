package com.hku.money.common;

public class ApiException extends RuntimeException {
  public final int status;
  public final String code;

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public static ApiException bad(String message) {
    return new ApiException(400, "INVALID_REQUEST", message);
  }

  public static ApiException unauthorized() {
    return new ApiException(401, "UNAUTHORIZED", "登录已失效，请重新登录");
  }
}
