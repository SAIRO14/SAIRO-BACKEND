package com.sairo.sairo_backend.saved;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 저장 여행지 저장소.
 *
 * <p>커서 페이지(#31)에서 JPA로 어색한 쿼리가 이어지므로 JdbcTemplate으로 통일한다.
 */
@Repository
@RequiredArgsConstructor
class SavedTripRepository {

    /** 모든 조회가 같은 컬럼 집합을 읽는다. 한 곳만 고치면 ROW_MAPPER와 어긋나지 않는다. */
    private static final String COLUMNS = "saved_trip_id, course_id, region_key, region_area, "
            + "image_url, reason, spot_names, spot_image_urls, created_at";

    private static final RowMapper<SavedTrip> ROW_MAPPER = (rs, rowNum) -> new SavedTrip(
            rs.getString("saved_trip_id"),
            rs.getString("course_id"),
            rs.getString("region_key"),
            rs.getString("region_area"),
            rs.getString("image_url"),
            rs.getString("reason"),
            readTextArray(rs, "spot_names"),
            readTextArray(rs, "spot_image_urls"),
            rs.getTimestamp("created_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    /**
     * 카드용 배열 컬럼을 읽는다. 둘 다 {@code NOT NULL DEFAULT '{}'}라 값이 항상 있다.
     *
     * <p>그래도 {@code null}과 원소 {@code null}을 걸러 빈 목록으로 받는다. 이 값은 응답에 그대로
     * 나가므로, 제약이 깨진 행 하나가 목록 전체를 500으로 만들지 않게 한다.
     * (docs/api-contract.md §6)
     */
    private static List<String> readTextArray(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((String[]) array.getArray()).filter(Objects::nonNull).toList();
    }

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
     * {@code spot_names}도 같다. 지문이 같으면 장소 집합도 같지만 순서와 이름 표기는 다를 수 있고,
     * 남는 것은 최초 저장 시점의 것이다. (ADR 0011)
     */
    SavedTrip save(String savedTripId,
                   String deviceId,
                   String courseId,
                   String regionKey,
                   String courseFingerprint,
                   String regionArea,
                   String imageUrl,
                   String reason,
                   List<String> spotNames,
                   List<String> spotImageUrls) {
        String sql = """
                INSERT INTO saved_trips
                    (saved_trip_id, device_id, course_id, region_key, course_fingerprint,
                     region_area, image_url, reason, spot_names, spot_image_urls)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (device_id, course_fingerprint)
                    DO UPDATE SET device_id = EXCLUDED.device_id
                RETURNING""" + " " + COLUMNS;

        // TEXT[] 바인딩에는 커넥션이 필요해 PreparedStatementCreator를 쓴다.
        // JdbcTemplate의 가변 인자 경로는 String[]을 배열 파라미터로 넘기지 못한다.
        return jdbcTemplate.query(
                connection -> {
                    var ps = connection.prepareStatement(sql);
                    ps.setString(1, savedTripId);
                    ps.setString(2, deviceId);
                    ps.setString(3, courseId);
                    ps.setString(4, regionKey);
                    ps.setString(5, courseFingerprint);
                    ps.setString(6, regionArea);
                    ps.setString(7, imageUrl);
                    ps.setString(8, reason);
                    ps.setArray(9, connection.createArrayOf("text", spotNames.toArray(new String[0])));
                    ps.setArray(10, connection.createArrayOf("text", spotImageUrls.toArray(new String[0])));
                    return ps;
                },
                rs -> {
                    // RETURNING은 충돌 시에도 항상 한 행을 준다. 없으면 그 전제가 깨진 것이다.
                    if (!rs.next()) {
                        throw new IllegalStateException("저장 여행지 INSERT가 행을 반환하지 않았다.");
                    }
                    return ROW_MAPPER.mapRow(rs, 0);
                }
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
                    "SELECT " + COLUMNS + """
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
                "SELECT " + COLUMNS + """
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

    /** (device_id, course_fingerprint) 유니크 인덱스를 그대로 타는 단순 존재 확인이다. */
    boolean existsByDeviceIdAndFingerprint(String deviceId, String fingerprint) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM saved_trips WHERE device_id = ? AND course_fingerprint = ?)",
                Boolean.class, deviceId, fingerprint);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 저장 항목 하나를 지운다. (#32)
     *
     * <p><b>소유자 조건은 쿼리에 있다.</b> 읽어서 소유자를 확인한 뒤 지우는 방식은 쓰지 않는다.
     * 확인과 삭제 사이에 다른 요청이 끼면 조건이 어긋나고, 무엇보다 빠뜨리기 쉽다. (AGENTS.md §1)
     *
     * <p>지운 행 수를 돌려주지 않는다. 호출자가 그 값으로 분기하면 "없는 항목"과 "남의 항목"이
     * 응답에서 갈리는데, 그러면 ID를 바꿔가며 실재 여부를 알아낼 수 있다.
     * 404를 쓰기로 한 이유가 그대로 무너진다. ({@code docs/api-contract.md} §4)
     *
     * <p><b>이 {@code void}는 "응답에 담지 않는다"가 아니라 "이 정보를 존재하지 않게 한다"이다.</b>
     * 그래서 서버 쪽 관측 수단도 함께 없앤다. 기기 ID 정규화가 바뀌는 식으로 <b>모든</b> 해제가
     * 아무 행도 지우지 못하게 되면, 204만 나가고 서버에는 아무 신호도 남지 않는다.
     * 매치 0건은 정상적인 재시도에서도 늘 일어나므로 한 건씩 로그로 남길 값은 아니고,
     * 필요해지면 응답 계약은 그대로 둔 채 <b>비율</b>을 보는 메트릭으로 잡는다.
     */
    void deleteByIdAndDeviceId(String savedTripId, String deviceId) {
        jdbcTemplate.update(
                "DELETE FROM saved_trips WHERE saved_trip_id = ? AND device_id = ?",
                savedTripId, deviceId
        );
    }
}
