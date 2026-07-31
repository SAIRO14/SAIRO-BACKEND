package com.sairo.sairo_backend.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 오류 코드 정본.
 *
 * <p>클라이언트는 {@code code} 문자열로 분기하고 {@code message}는 그대로 노출하지 않는다.
 * {@code retryable}은 "같은 요청을 그대로 재시도했을 때 성공할 가능성이 있는가"를 뜻한다.
 * 입력이 잘못되었거나 리소스가 없는 경우는 재시도해도 결과가 같으므로 false다.
 *
 * <p>새 코드를 추가할 때는 화면 단위가 아니라 원인 단위로 만든다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ─── 공통 ────────────────────────────────────────────────────────────────
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", false),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.", false),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 완료하지 못했어요.", true),

    /**
     * 목록 커서를 읽을 수 없는 경우. {@code INVALID_REQUEST}와 나누는 이유는 복구 방법이 달라서다.
     * 커서를 버리고 첫 페이지부터 다시 읽으면 되므로 클라이언트가 이 코드로 분기할 수 있어야 한다.
     *
     * <p>커서 페이지네이션은 도메인이 아니라 전역 규칙이므로 여기 공통에 둔다.
     * ({@code docs/api-contract.md} §5)
     */
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "잘못된 커서입니다.", false),

    // ─── 프로토콜 수준 오류 ──────────────────────────────────────────────────
    // 표준 HTTP 의미를 유지한다. 400으로 뭉뚱그리면 프록시와 클라이언트가 원인을 구분할 수 없다.
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 경로입니다.", false),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 요청 방식입니다.", false),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다.", false),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "지원하지 않는 응답 형식 요청입니다.", false),

    // ─── 디바이스 식별 ───────────────────────────────────────────────────────
    DEVICE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "디바이스 식별자가 필요합니다.", false),
    DEVICE_ID_INVALID(HttpStatus.BAD_REQUEST, "디바이스 식별자 형식이 올바르지 않습니다.", false),

    // ─── 사진 풀 ─────────────────────────────────────────────────────────────
    PHOTO_POOL_UNAVAILABLE(HttpStatus.INTERNAL_SERVER_ERROR, "사진을 불러오지 못했어요.", true),

    // ─── 취향 분석 ───────────────────────────────────────────────────────────
    INVALID_PHOTO_SELECTION(HttpStatus.BAD_REQUEST, "선택한 사진이 올바르지 않습니다.", false),
    ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "취향 분석에 실패했어요.", true),

    /** 분석 ID가 없거나 TTL이 지나 만료된 경우. 만료와 부재를 구분하지 않는다. */
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "분석 결과를 찾을 수 없습니다.", false),

    // ─── 추천 ────────────────────────────────────────────────────────────────
    RECOMMENDATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "추천을 생성하지 못했어요.", true),

    // ─── 장소 ────────────────────────────────────────────────────────────────
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다.", false),

    // ─── 코스 ────────────────────────────────────────────────────────────────
    INSUFFICIENT_SPOTS(HttpStatus.BAD_REQUEST, "코스를 만들기에 장소가 부족합니다.", false),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다.", false),
    COURSE_REGION_MISMATCH(HttpStatus.BAD_REQUEST, "요청한 지역과 장소의 지역이 일치하지 않습니다.", false),

    // ─── 공유 ────────────────────────────────────────────────────────────────
    SHARE_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "공유 링크를 만들지 못했어요.", true),

    /** 공유 ID가 없거나 만료된 경우. 만료 정책 확정 전까지 부재와 동일하게 다룬다. */
    SHARED_COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "공유된 코스를 찾을 수 없습니다.", false),

    // ─── 저장 여행지 ─────────────────────────────────────────────────────────
    SAVED_TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "저장한 여행지를 찾을 수 없습니다.", false),
    SAVED_TRIP_CONFLICT(HttpStatus.CONFLICT, "이미 저장한 여행지입니다.", false),
    SAVED_TRIP_FORBIDDEN(HttpStatus.NOT_FOUND, "저장한 여행지를 찾을 수 없습니다.", false),

    // ─── 외부 연동 ───────────────────────────────────────────────────────────
    EXTERNAL_API_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "외부 정보를 불러오지 못했어요.", true);

    private final HttpStatus status;
    private final String message;
    private final boolean retryable;
}
