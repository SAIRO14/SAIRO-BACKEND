package com.sairo.sairo_backend.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 오류 응답을 {@link ErrorResponse} 한 가지 형태로 통일한다.
 *
 * <p>요청 본문과 헤더 값은 로그에 남기지 않는다. 사진 ID 목록이나 디바이스 식별자가
 * 그대로 기록되면 안 되기 때문이다. 원인 추적은 {@code traceId}로 한다.
 *
 * <p>로그 메시지에 추적 ID를 직접 넣지 않는다. 로그 패턴이 MDC의 값을 이미 출력하므로
 * 넣으면 같은 값이 한 줄에 두 번 나온다.
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
            log.error("{} - {}", code.name(), ex.getMessage(), ex);
        } else {
            log.warn("{} - {}", code.name(), ex.getMessage());
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

    /** 요청 파라미터에 붙은 제약(@Min 등) 위반. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException ex) {
        String detail = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> r.getMethodParameter().getParameterName() + ": " + e.getDefaultMessage()))
                .findFirst()
                .orElse(ErrorCode.INVALID_REQUEST.getMessage());

        return build(ErrorCode.INVALID_REQUEST, detail);
    }

    // ─── 프로토콜 수준 오류 ──────────────────────────────────────────────────
    // 아래 예외들은 Spring이 기본 처리하던 것이다. 마지막 Exception 핸들러가 이를 가로채
    // 전부 500으로 만들어버렸던 회귀가 있었으므로, 각각 명시적으로 처리해 표준 상태 코드를 지킨다.

    /** 본문을 파싱할 수 없다. 깨진 JSON 등. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return build(ErrorCode.INVALID_REQUEST, "요청 본문을 해석할 수 없습니다.");
    }

    /** 지원하지 않는 Content-Type. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage());
    }

    /** 클라이언트가 요구한 응답 형식을 만들 수 없다. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return build(ErrorCode.NOT_ACCEPTABLE, ErrorCode.NOT_ACCEPTABLE.getMessage());
    }

    /** 경로는 있으나 메서드가 다르다. HTTP 규약상 Allow 헤더를 함께 준다. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        ErrorCode code = ErrorCode.METHOD_NOT_ALLOWED;
        String traceId = TraceIdFilter.currentTraceId();
        log.warn("[{}] {}", code.name(), ex.getMethod());

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.getStatus());
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            builder.allow(ex.getSupportedHttpMethods().toArray(new HttpMethod[0]));
        }
        return builder.body(ErrorResponse.of(code, code.getMessage(), traceId));
    }

    /** 매핑된 핸들러가 없다. 존재하지 않는 경로. */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNoHandler(Exception ex) {
        return build(ErrorCode.ENDPOINT_NOT_FOUND, ErrorCode.ENDPOINT_NOT_FOUND.getMessage());
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

        // 5xx는 error로 남긴다. 로그 정책이 상태에 따라 갈리므로 여기서도 동일하게 적용한다.
        if (ex.getStatusCode().is5xxServerError()) {
            log.error("표준화되지 않은 ResponseStatusException: {}", ex.getMessage(), ex);
        } else {
            log.warn("표준화되지 않은 ResponseStatusException: {}", ex.getMessage());
        }

        return ResponseEntity.status(ex.getStatusCode())
                .body(ErrorResponse.of(code, ex.getReason() != null ? ex.getReason() : code.getMessage(), traceId));
    }

    /**
     * 마지막 방어선. 내부 예외 메시지는 클라이언트에 노출하지 않는다.
     *
     * <p>여기에 걸리는 예외는 모두 서버 결함으로 간주한다. 클라이언트 잘못으로 생기는
     * 예외가 여기까지 오면 위에 전용 핸들러를 추가한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        String traceId = TraceIdFilter.currentTraceId();
        log.error("처리되지 않은 예외", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), traceId));
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message) {
        String traceId = TraceIdFilter.currentTraceId();
        log.warn("{} - {}", code.name(), message);
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, message, traceId));
    }
}
