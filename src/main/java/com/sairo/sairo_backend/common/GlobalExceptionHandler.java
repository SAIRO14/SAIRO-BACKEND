package com.sairo.sairo_backend.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 모든 오류 응답을 {@link ErrorResponse} 한 가지 형태로 통일한다.
 *
 * <p>요청 본문과 헤더 값은 로그에 남기지 않는다. 사진 ID 목록이나 디바이스 식별자가
 * 그대로 기록되면 안 되기 때문이다. 원인 추적은 {@code traceId}로 한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 서비스 계층이 의도적으로 던진 오류. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        String traceId = TraceIdFilter.currentTraceId();

        if (code.getStatus().is5xxServerError()) {
            log.error("[{}] {} - {}", traceId, code.name(), ex.getMessage(), ex);
        } else {
            log.warn("[{}] {} - {}", traceId, code.name(), ex.getMessage());
        }

        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, ex.getMessage(), traceId));
    }

    /** {@code @Valid} 위반. 어떤 필드가 왜 틀렸는지까지 전달한다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse(ErrorCode.INVALID_REQUEST.getMessage());

        return build(ErrorCode.INVALID_REQUEST, detail);
    }

    /** 필수 쿼리 파라미터 누락. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(ErrorCode.INVALID_REQUEST, "필수 파라미터가 없습니다: " + ex.getParameterName());
    }

    /** 필수 헤더 누락. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return build(ErrorCode.INVALID_REQUEST, "필수 헤더가 없습니다: " + ex.getHeaderName());
    }

    /** 타입이 맞지 않는 경로 변수나 파라미터. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(ErrorCode.INVALID_REQUEST, "값의 형식이 올바르지 않습니다: " + ex.getName());
    }

    /**
     * 프레임워크가 던지는 {@code ResponseStatusException} 방어선.
     *
     * <p>서비스 코드에서는 사용하지 않는다. 새로 발견되면 해당 지점을
     * {@link BusinessException}으로 옮기고 {@link ErrorCode}를 추가한다.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        ErrorCode code = ex.getStatusCode().is4xxClientError()
                ? ErrorCode.INVALID_REQUEST
                : ErrorCode.INTERNAL_ERROR;
        String traceId = TraceIdFilter.currentTraceId();

        log.warn("[{}] 표준화되지 않은 ResponseStatusException: {}", traceId, ex.getMessage());

        return ResponseEntity.status(ex.getStatusCode())
                .body(ErrorResponse.of(code, ex.getReason() != null ? ex.getReason() : code.getMessage(), traceId));
    }

    /** 마지막 방어선. 내부 예외 메시지는 클라이언트에 노출하지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        String traceId = TraceIdFilter.currentTraceId();
        log.error("[{}] 처리되지 않은 예외", traceId, ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), traceId));
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message) {
        String traceId = TraceIdFilter.currentTraceId();
        log.warn("[{}] {} - {}", traceId, code.name(), message);
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, message, traceId));
    }
}
