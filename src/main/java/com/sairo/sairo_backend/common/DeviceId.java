package com.sairo.sairo_backend.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청 헤더 {@code X-Device-Id}에서 익명 사용자 식별자를 받는다.
 *
 * <p>로그인이 없으므로 사용자는 기기가 만든 UUID v4로만 구분한다.
 * 근거는 ADR 0007, 계약은 docs/api-contract.md §4에 있다.
 *
 * <p><b>필수 여부는 파라미터 타입이 정한다.</b> 별도 플래그를 두지 않는다.
 *
 * <pre>{@code
 * // 소유자가 있는 데이터를 다루는 요청 — 헤더가 없으면 400 DEVICE_ID_REQUIRED
 * public SavedTripResponse save(@DeviceId String deviceId) { ... }
 *
 * // 저장 여부만 함께 내려주는 요청 — 헤더가 없으면 비어 있는 값이 들어온다
 * public RecommendationResponse recommend(@DeviceId Optional<String> deviceId) { ... }
 * }</pre>
 *
 * <p>넘어오는 값은 소문자로 정규화된 UUID 문자열이다. 대소문자만 다른 같은 기기가
 * 서로 다른 사용자로 갈리지 않게 하기 위해서다.
 *
 * <p>OpenAPI 문서에는 {@code SwaggerConfig}가 헤더 파라미터를 자동으로 추가한다.
 * 컨트롤러에서 {@code @Parameter}로 다시 적지 않는다.
 *
 * @see DeviceIdArgumentResolver
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeviceId {
}
