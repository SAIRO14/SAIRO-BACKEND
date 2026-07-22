package com.sairo.sairo_backend.common;

/**
 * 모든 API 오류의 공통 응답 본문.
 *
 * <pre>
 * {
 *   "code": "ANALYSIS_NOT_FOUND",
 *   "message": "분석 결과를 찾을 수 없습니다.",
 *   "retryable": false,
 *   "traceId": "3f8c1a20"
 * }
 * </pre>
 */
public record ErrorResponse(
        String code,
        String message,
        boolean retryable,
        String traceId
) {
    public static ErrorResponse of(ErrorCode errorCode, String message, String traceId) {
        return new ErrorResponse(errorCode.name(), message, errorCode.isRetryable(), traceId);
    }
}
