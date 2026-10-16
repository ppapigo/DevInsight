package com.portfolio.devinsight.common;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
  public record Error(String code, String message, String requestId, Instant timestamp) {}

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Error> api(ApiException e) {
    var builder = ResponseEntity.status(e.getStatus());
    if (e.getRetryAfter() != null) builder.header("Retry-After", e.getRetryAfter().toString());
    return builder.body(
        new Error(e.getCode(), e.getMessage(), UUID.randomUUID().toString(), Instant.now()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.HandlerMethodValidationException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Error> validation(Exception e) {
    return api(ApiException.badRequest("입력값의 형식 또는 길이를 확인해 주세요."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<Error> upload(Exception e) {
    return api(
        new ApiException(
            org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
            "FILE_TOO_LARGE",
            "파일은 최대 10MB까지 업로드할 수 있습니다."));
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<Error> conflict(Exception e) {
    return api(new ApiException(org.springframework.http.HttpStatus.CONFLICT, "DATA_CONFLICT",
        "이미 등록되었거나 다른 요청에서 변경된 항목입니다. 목록을 새로고침해 주세요."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Error> unknown(Exception e) {
    String id = UUID.randomUUID().toString();
    // Exception messages may contain credentials, prompts, uploaded content, or upstream bodies.
    log.error("Unhandled error requestId={} type={}", id, e.getClass().getSimpleName());
    return ResponseEntity.internalServerError()
        .body(new Error("INTERNAL_ERROR", "처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", id, Instant.now()));
  }
}
