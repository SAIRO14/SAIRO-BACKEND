package com.sairo.sairo_backend.saved;

import com.sairo.sairo_backend.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 커서 인코딩은 DB 없이 검증할 수 있다. 페이지 경계 동작은 {@code SavedTripApiTest}가 본다.
 */
class SavedTripCursorTest {

    private static final String SAVED_TRIP_ID = "1f0a2b3c-4d5e-4f70-8192-a3b4c5d6e7f8";

    @Test
    void encode_thenDecode_restoresSameValues() {
        SavedTripCursor original =
                new SavedTripCursor(LocalDateTime.of(2026, 7, 31, 12, 34, 56, 789_012_000), SAVED_TRIP_ID);

        SavedTripCursor restored = SavedTripCursor.decode(original.encode());

        assertThat(restored).isEqualTo(original);
    }

    /**
     * 마이크로초 아래 자리는 버린다.
     *
     * <p>{@code TIMESTAMP} 컬럼이 마이크로초까지만 담으므로 나노초를 커서에 실으면
     * 왕복한 값이 DB의 행보다 커진다. 그러면 {@code (created_at, saved_trip_id) < (커서)} 비교에서
     * 방금 읽은 항목이 다시 걸려 다음 페이지 첫 줄로 나온다.
     */
    @Test
    void encode_withSubMicrosecondPrecision_truncatesToMicros() {
        LocalDateTime withNanos = LocalDateTime.of(2026, 7, 31, 12, 34, 56, 789_012_345);

        SavedTripCursor restored = SavedTripCursor.decode(new SavedTripCursor(withNanos, SAVED_TRIP_ID).encode());

        assertThat(restored.createdAt()).isEqualTo(LocalDateTime.of(2026, 7, 31, 12, 34, 56, 789_012_000));
    }

    // URL 쿼리 파라미터로 그대로 실을 수 있어야 한다. base64url이라 +, /, = 가 나오지 않는다.
    @Test
    void encode_producesUrlSafeString() {
        String encoded = new SavedTripCursor(LocalDateTime.now(), SAVED_TRIP_ID).encode();

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void decode_withNonBase64_throwsInvalidCursor() {
        assertThatThrownBy(() -> SavedTripCursor.decode("!!! not base64 !!!"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("커서");
    }

    @Test
    void decode_withWrongFieldCount_throwsInvalidCursor() {
        assertThatThrownBy(() -> SavedTripCursor.decode(base64("v1|123")))
                .isInstanceOf(BusinessException.class);
    }

    // 커서 형식이 바뀌면 옛 커서는 조용히 잘못 읽히지 않고 거절돼야 한다.
    @Test
    void decode_withUnknownVersion_throwsInvalidCursor() {
        assertThatThrownBy(() -> SavedTripCursor.decode(base64("v0|123|" + SAVED_TRIP_ID)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_withNonNumericTimestamp_throwsInvalidCursor() {
        assertThatThrownBy(() -> SavedTripCursor.decode(base64("v1|어제|" + SAVED_TRIP_ID)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_withBlankId_throwsInvalidCursor() {
        assertThatThrownBy(() -> SavedTripCursor.decode(base64("v1|123|   ")))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 파싱되는 {@code long}이라고 해서 쓸 수 있는 시각인 것은 아니다.
     *
     * <p>{@code TIMESTAMP} 컬럼 범위를 벗어난 값을 그대로 흘려보내면 PostgreSQL이
     * "timestamp out of range"로 실패해 500이 나간다. 그 코드는 {@code retryable: true}라
     * 클라이언트가 계약상 같은 커서로 재시도하게 되고, 커서를 버리라는 신호가 닿지 않는다.
     * 어차피 어떤 행도 가리키지 못하는 값이므로 읽을 수 없는 커서로 다룬다.
     */
    @Test
    void decode_withTimestampBelowSupportedRange_throwsInvalidCursor() {
        assertThatThrownBy(() -> SavedTripCursor.decode(base64("v1|-9223372036854775807|" + SAVED_TRIP_ID)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decode_withTimestampAboveSupportedRange_throwsInvalidCursor() {
        assertThatThrownBy(() -> SavedTripCursor.decode(base64("v1|9223372036854775807|" + SAVED_TRIP_ID)))
                .isInstanceOf(BusinessException.class);
    }

    private String base64(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
