package com.hku.money.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiErrors {
  private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Result<Void>> api(ApiException e) {
    return error(e.status, e.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    ConstraintViolationException.class,
    MissingServletRequestParameterException.class,
    HandlerMethodValidationException.class
  })
  ResponseEntity<Result<Void>> invalid(Exception e) {
    return error(400, "请求参数不合法");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Result<Void>> conflict(Exception e) {
    return error(409, "数据冲突，请检查用户名或重试");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Result<Void>> forbidden(Exception e) {
    return error(403, "没有操作权限");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<Result<Void>> notFound(Exception e) {
    return error(404, "资源不存在");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<Result<Void>> method(Exception e) {
    return error(405, "请求方法不支持");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<Result<Void>> media(Exception e) {
    return error(415, "请求内容类型不支持");
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Result<Void>> status(ResponseStatusException e) {
    return error(e.getStatusCode().value(), e.getReason() == null ? "请求失败" : e.getReason());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Result<Void>> unexpected(Exception e) {
    // Keep framework status errors from accidentally becoming 500 responses.
    if (e instanceof ErrorResponse response) {
      int status = response.getStatusCode().value();
      return error(status, "请求失败");
    }
    log.error("Unhandled API error", e);
    return error(500, "服务器内部错误");
  }

  private ResponseEntity<Result<Void>> error(int code, String message) {
    return ResponseEntity.status(code).body(Result.failure(code, message));
  }
}
