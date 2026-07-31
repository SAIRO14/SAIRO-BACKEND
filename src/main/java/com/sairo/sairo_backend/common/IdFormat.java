package com.sairo.sairo_backend.common;

/**
 * 서버가 발급하는 리소스 ID의 형식.
 *
 * <p>{@code courseId}와 {@code savedTripId}는 모두 {@code UUID.randomUUID()}로 만들어지므로
 * 소문자 UUID v4다. 요청 본문이나 쿼리 파라미터에 실려 온 값을 Bean Validation으로 검증할 때 쓴다.
 *
 * <p>형식을 검증하는 대상은 <b>본문과 쿼리 파라미터</b>다. 경로의 리소스 ID는 형식을 보지 않고
 * 404로 답한다. ({@code docs/api-contract.md} §2)
 *
 * <p>클라이언트가 만드는 {@code X-Device-Id}는 성격이 달라 여기 해당하지 않는다.
 * 그쪽은 거절하지 않고 소문자로 정규화한다. ({@link DeviceIdArgumentResolver})
 */
public final class IdFormat {

    /**
     * 소문자 UUID v4 정규형. 버전 자리(4)와 IETF variant(8·9·a·b)까지 본다.
     *
     * <p><b>대문자를 받지 않는다.</b> 이 ID들이 담긴 컬럼은 {@code TEXT}라 조회가 대소문자를
     * 구분하므로, 대문자를 통과시키면 실재하는 리소스를 두고 "없다"고 답하게 된다.
     * 서버가 발급한 적 없는 형식이므로 400으로 거절하는 편이 정직하다.
     */
    public static final String UUID_V4 =
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    private IdFormat() {
    }
}
