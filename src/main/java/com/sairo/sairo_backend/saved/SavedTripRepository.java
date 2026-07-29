package com.sairo.sairo_backend.saved;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 저장 여행지 저장소.
 *
 * <p>커서 페이지(#31)에서 JPA로 어색한 쿼리가 이어지므로 JdbcTemplate으로 통일한다.
 */
@Repository
@RequiredArgsConstructor
public class SavedTripRepository {

    private static final RowMapper<SavedTrip> ROW_MAPPER = (rs, rowNum) -> new SavedTrip(
            rs.getString("saved_trip_id"),
            rs.getString("course_id"),
            rs.getString("region_key"),
            rs.getTimestamp("created_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    /**
     * 저장 항목을 만든다. 이미 같은 내용이 저장돼 있으면 새로 만들지 않고 기존 행을 돌려준다.
     *
     * <p>{@code DO UPDATE}는 {@code DO NOTHING}과 달리 충돌해도 **항상 행을 반환**하므로
     * 넣기와 읽기가 한 문장으로 끝난다. 동시에 도착한 두 요청은 유니크 인덱스에서 갈리고,
     * 진 쪽은 이긴 쪽이 커밋될 때까지 기다렸다가 그 행을 받는다.
     * {@code SharedCourseRepository.save}와 같은 방식이다.
     *
     * <p>{@code DO UPDATE}가 {@code device_id}를 자기 값으로 덮는 것은 갱신이 목적이 아니라
     * 행을 반환시키기 위한 것이다. 충돌한 행은 이미 같은 {@code device_id}를 갖고 있다.
     */
    public SavedTrip save(String savedTripId,
                          String deviceId,
                          String courseId,
                          String regionKey,
                          String courseFingerprint) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO saved_trips (saved_trip_id, device_id, course_id, region_key, course_fingerprint)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (device_id, region_key, course_fingerprint)
                    DO UPDATE SET device_id = EXCLUDED.device_id
                RETURNING saved_trip_id, course_id, region_key, created_at
                """,
                ROW_MAPPER,
                savedTripId, deviceId, courseId, regionKey, courseFingerprint
        );
    }
}
