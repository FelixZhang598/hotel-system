package com.example.hotel.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> business(ApiException e, HttpServletRequest r) {
    return ResponseEntity.status(e.status)
        .body(new ApiError(e.code, e.getMessage(), RequestIdFilter.id(r)));
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    MissingRequestHeaderException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class,
    HttpRequestMethodNotSupportedException.class
  })
  ResponseEntity<ApiError> invalid(Exception e, HttpServletRequest r) {
    return ResponseEntity.badRequest()
        .body(new ApiError("VALIDATION_ERROR", "请求参数无效", RequestIdFilter.id(r)));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> missing(NoResourceFoundException e, HttpServletRequest r) {
    return ResponseEntity.status(404)
        .body(new ApiError("NOT_FOUND", "资源不存在", RequestIdFilter.id(r)));
  }

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  ResponseEntity<ApiError> database(RuntimeException e, HttpServletRequest r) {
    Throwable t = e;
    while (t != null) {
      if (t instanceof java.sql.SQLException s && (s.getErrorCode() == 5 || s.getErrorCode() == 6))
        return ResponseEntity.status(503)
            .body(new ApiError("DATABASE_BUSY", "数据库繁忙，请稍后重试", RequestIdFilter.id(r)));
      t = t.getCause();
    }
    org.slf4j.LoggerFactory.getLogger(getClass())
        .error("Database request failed: {}", RequestIdFilter.id(r), e);
    return ResponseEntity.internalServerError()
        .body(new ApiError("INTERNAL_ERROR", "服务暂时无法完成请求", RequestIdFilter.id(r)));
  }
}
