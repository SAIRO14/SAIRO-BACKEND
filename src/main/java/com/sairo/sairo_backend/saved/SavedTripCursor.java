package com.sairo.sairo_backend.saved;

import com.sairo.sairo_backend.common.BusinessException;
import com.sairo.sairo_backend.common.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * 저장 목록 커서. 마지막으로 읽은 행의 정렬 키를 담는다.
 *
 * <p>정렬은 {@code (created_at DESC, saved_trip_id DESC)}다. 생성 시각만으로는 같은 시각에 만들어진
 * 항목의 순서가 흔들려 페이지 경계에서 항목이 빠지거나 두 번 나온다. 그래서 커서도 두 값을 함께 담는다.
 * ({@code docs/api-contract.md} §5)
 *
 * <p><b>클라이언트는 이 문자열을 해석하지 않는다.</b> 받은 값을 그대로 다음 요청에 돌려주기만 한다.
 * 형식은 예고 없이 바뀔 수 있고, 그때 옛 커서는 {@code INVALID_CURSOR}가 된다.
 * 앞에 붙은 버전 표시가 그 판별을 맡는다.
 *
 * <p>인코딩은 base64url이다. 담기는 두 값은 모두 {@code POST /saved-trips} 응답으로 이미 나간
 * 자기 자신의 데이터라, 이 커서가 새로 노출하는 정보는 없다. 다른 사용자의 행을 가리키게 고쳐도
 * 조회 쿼리에 {@code device_id} 조건이 있어 남의 항목이 나오지 않는다.
 */
record SavedTripCursor(LocalDateTime createdAt, String savedTripId) {

    /** 형식이 바뀌면 올린다. 옛 커서는 여기서 걸러진다. */
    private static final String VERSION = "v1";

    private static final String DELIMITER = "|";

    String encode() {
        // 마이크로초까지만 쓴다. TIMESTAMP 컬럼의 정밀도가 마이크로초라 그 아래는 DB에 남지 않고,
        // 나노초를 커서에 넣으면 왕복한 값이 원래 행보다 커져 같은 항목이 다시 나온다.
        Instant instant = createdAt.toInstant(ZoneOffset.UTC);
        long micros = instant.getEpochSecond() * 1_000_000 + instant.getNano() / 1_000;
        String raw = VERSION + DELIMITER + micros + DELIMITER + savedTripId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 커서를 되돌린다. 읽을 수 없으면 {@code INVALID_CURSOR}로 400을 낸다.
     *
     * <p>깨진 커서를 조용히 무시하고 첫 페이지를 주지 않는다. 클라이언트가 목록 끝까지 왔다고
     * 착각한 채 처음부터 다시 읽어 같은 항목을 반복하게 된다.
     */
    static SavedTripCursor decode(String encoded) {
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR, "커서를 읽을 수 없습니다.", e);
        }

        // limit -1: 값 안에 구분자가 들어가도 조각 수가 달라지지 않게 한다.
        String[] parts = raw.split("\\" + DELIMITER, -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR, "커서를 읽을 수 없습니다.");
        }

        long micros;
        try {
            micros = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR, "커서를 읽을 수 없습니다.", e);
        }
        if (parts[2].isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR, "커서를 읽을 수 없습니다.");
        }

        LocalDateTime createdAt = LocalDateTime.ofInstant(
                Instant.EPOCH.plus(micros, ChronoUnit.MICROS), ZoneOffset.UTC);
        return new SavedTripCursor(createdAt, parts[2]);
    }

    static SavedTripCursor from(SavedTrip savedTrip) {
        return new SavedTripCursor(savedTrip.createdAt(), savedTrip.savedTripId());
    }
}
