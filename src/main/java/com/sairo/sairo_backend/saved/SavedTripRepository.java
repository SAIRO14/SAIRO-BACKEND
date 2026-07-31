package com.sairo.sairo_backend.saved;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * 저장 여행지 저장소.
 *
 * <p>커서 페이지(#31)에서 JPA로 어색한 쿼리가 이어지므로 JdbcTemplate으로 통일한다.
 */
@Repository
@RequiredArgsConstructor
class SavedTripRepository {

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
     *
     * <p>충돌 시 {@code region_key}와 {@code course_id}는 <b>기존 값을 유지한다.</b>
     * 저장 항목이 가리키는 것은 최초로 저장한 그 코스다.
     */
    SavedTrip save(String savedTripId,
                   String deviceId,
                   String courseId,
                   String regionKey,
                   String courseFingerprint) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO saved_trips (saved_trip_id, device_id, course_id, region_key, course_fingerprint)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (device_id, course_fingerprint)
                    DO UPDATE SET device_id = EXCLUDED.device_id
                RETURNING saved_trip_id, course_id, region_key, created_at
                """,
                ROW_MAPPER,
                savedTripId, deviceId, courseId, regionKey, courseFingerprint
        );
    }

    /**
     * 한 사용자의 저장 목록을 최근 저장순으로 읽는다.
     *
     * <p><b>소유자 조건은 쿼리에 있다.</b> 전부 읽어 애플리케이션에서 거르지 않는다.
     * 거르는 코드는 빠뜨리기 쉽고, 빠뜨린 순간 남의 저장 목록이 그대로 나간다. (AGENTS.md §1)
     *
     * <p>정렬 키는 {@code (created_at DESC, saved_trip_id DESC)}이고
     * {@code saved_trips_device_created_idx}가 그대로 이 순서다.
     *
     * <p>커서 비교에 행 값 비교 {@code (created_at, saved_trip_id) < (?, ?)}를 쓴다.
     * {@code created_at < ? OR (created_at = ? AND saved_trip_id < ?)}로 풀어 쓰면 같은 뜻이지만
     * 플래너가 인덱스를 한 번에 타지 못한다.
     *
     * <p>첫 페이지와 다음 페이지의 SQL을 나눈 것도 같은 이유다. {@code ? IS NULL OR ...}로 합치면
     * 첫 페이지에서도 조건이 붙어 인덱스 스캔 범위를 좁히지 못한다.
     *
     * @param limit 읽을 행 수. 호출자는 다음 페이지 유무를 알기 위해 필요한 수보다 하나 더 요청한다.
     */
    List<SavedTrip> findPage(String deviceId, SavedTripCursor cursor, int limit) {
        if (cursor == null) {
            return jdbcTemplate.query(
                    """
                    SELECT saved_trip_id, course_id, region_key, created_at
                    FROM saved_trips
                    WHERE device_id = ?
                    ORDER BY created_at DESC, saved_trip_id DESC
                    LIMIT ?
                    """,
                    ROW_MAPPER,
                    deviceId, limit
            );
        }

        return jdbcTemplate.query(
                """
                SELECT saved_trip_id, course_id, region_key, created_at
                FROM saved_trips
                WHERE device_id = ?
                  AND (created_at, saved_trip_id) < (?, ?)
                ORDER BY created_at DESC, saved_trip_id DESC
                LIMIT ?
                """,
                ROW_MAPPER,
                deviceId, Timestamp.valueOf(cursor.createdAt()), cursor.savedTripId(), limit
        );
    }
}
