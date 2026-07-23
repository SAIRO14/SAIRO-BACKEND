package com.sairo.sairo_backend.common;

import lombok.Getter;

/**
 * 의도적으로 발생시키는 모든 도메인 오류의 단일 타입.
 *
 * <p>서비스 계층에서는 {@code ResponseStatusException} 대신 이 예외만 사용한다.
 * HTTP 상태 코드는 {@link ErrorCode}가 결정하므로 서비스는 상태 코드를 알 필요가 없다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 기본 메시지 대신 상황을 좁힌 메시지를 내보내야 할 때 사용한다. */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
