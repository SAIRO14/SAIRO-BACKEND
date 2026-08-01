package com.sairo.sairo_backend.common;

/**
 * 서버가 발급하는 리소스 ID의 형식.
 *
 * <p>{@code courseId}와 {@code savedTripId}는 모두 {@code UUID.randomUUID()}로 만들어지므로
 * 소문자 UUID v4다. 서버 발급 ID의 형식 검증이 필요한 자리에서 Bean Validation으로 검증할 때 쓴다.
 *
 * <p>형식을 검증할지는 값의 위치가 아니라, 검증하지 않았을 때 <b>거짓 응답이 나가는지</b>로 정한다.
 * 경로의 {@code courseId}도 대문자 UUID를 통과시키면 실재하는 코스를 두고 404로 답하므로 검증한다.
 * ({@code docs/api-contract.md} §2, ADR 0013)
 *
 * <p>클라이언트가 만드는 {@code X-Device-Id}는 성격이 달라 여기 해당하지 않는다.
 * 대문자는 허용해 소문자로 정규화하지만, 잘못된 형식과 비-v4 UUID는 별도 정규식으로 거절한다.
 * ({@link DeviceIdArgumentResolver})
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
