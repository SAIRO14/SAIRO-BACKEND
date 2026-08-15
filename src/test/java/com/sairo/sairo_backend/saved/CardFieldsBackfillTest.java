package com.sairo.sairo_backend.saved;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V7·V8·V9가 이미 저장된 행의 카드 필드를 코스 스냅샷에서 채우는지 확인한다. (#76)
 *
 * <p>테스트 컨테이너는 빈 DB로 뜨므로 마이그레이션이 실제로 도는 시점에는 채울 행이 없다.
 * 즉 평소에는 이 {@code UPDATE}들의 <b>문법</b>만 검증되고 동작은 검증되지 않는다.
 * 그래서 여기서 행을 만들어 두고 <b>마이그레이션 파일에 적힌 그 문장을 그대로</b> 실행한다.
 * SQL을 테스트에 옮겨 적으면 원본이 바뀌었을 때 이 테스트가 알아채지 못한다.
 *
 * <p>세 {@code UPDATE} 모두 몇 번을 실행해도 같은 결과라 이렇게 다시 돌려도 안전하다.
 */
class CardFieldsBackfillTest extends IntegrationTestBase {

    private static final String DEVICE = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

    private static final String SPOT_NAMES_MIGRATION = "V7__add_spot_names_to_saved_trips.sql";
    private static final String IMAGE_URLS_MIGRATION = "V8__add_spot_image_urls_to_saved_trips.sql";
    private static final String CARD_FIELDS_MIGRATION = "V9__backfill_card_fields_on_saved_trips.sql";

    /** 마이그레이션 파일에서 백필 문장이 시작하는 지점을 가리키는 주석. */
    private static final String BACKFILL_MARKER = "-- backfill:start";

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM saved_trips");
        jdbcTemplate.update("DELETE FROM shared_courses");
        jdbcTemplate.update("DELETE FROM courses");
    }

    @Test
    void backfill_fillsCardFieldsFromCourseSnapshot_inDayOrder() {
        String courseId = insertCourse("""
                {"regionName":"경상북도",
                 "day1":[{"spotId":"s1","name":"덕양서원(의성)","imageUrl":"https://example.com/a.jpg"},
                         {"spotId":"s2","name":"연일향교","imageUrl":"https://example.com/b.jpg"}],
                 "day2":[{"spotId":"s3","name":"장소C","imageUrl":"https://example.com/c.jpg"}]}
                """);
        insertPreBackfillSavedTrip("trip-1", courseId);

        runBackfill(SPOT_NAMES_MIGRATION);
        runBackfill(IMAGE_URLS_MIGRATION);

        // Day 1 다음 Day 2. 코스의 동선 순서다. 자르는 일은 응답을 만드는 쪽이 한다.
        assertThat(columnOf("trip-1", "spot_names"))
                .containsExactly("덕양서원(의성)", "연일향교", "장소C");
        assertThat(columnOf("trip-1", "spot_image_urls"))
                .containsExactly("https://example.com/a.jpg", "https://example.com/b.jpg",
                        "https://example.com/c.jpg");
    }

    /**
     * 사진이 없는 장소도 자리를 지킨다. 두 배열의 길이가 같고 i번째가 같은 장소다.
     *
     * <p>런타임 경로({@code SavedTripService.spotFieldOf})와 같은 규칙이어야 한다.
     * 백필된 옛 행과 새로 저장한 행이 다르게 굴면 같은 화면에서 카드가 두 가지로 보인다.
     *
     * <p>{@code imageUrl}이 JSON {@code null}인 경우와 키 자체가 없는 경우를 함께 넣는다.
     * 둘 다 자리를 유지해야 한다. 키가 없을 때 자리가 밀리는 것이
     * {@code jsonb_path_query_array('$.day1[*].imageUrl')}를 쓰지 않는 이유다.
     */
    @Test
    void backfill_keepsSlotForSpotsWithoutImage() {
        String courseId = insertCourse("""
                {"regionName":"제주도",
                 "day1":[{"spotId":"s1","name":"장소A","imageUrl":"https://example.com/a.jpg"},
                         {"spotId":"s2","name":"장소B","imageUrl":null},
                         {"spotId":"s3","name":"장소C"}],
                 "day2":[{"spotId":"s4","name":"장소D","imageUrl":"https://example.com/d.jpg"}]}
                """);
        insertPreBackfillSavedTrip("trip-1", courseId);

        runBackfill(SPOT_NAMES_MIGRATION);
        runBackfill(IMAGE_URLS_MIGRATION);

        assertThat(columnOf("trip-1", "spot_names"))
                .containsExactly("장소A", "장소B", "장소C", "장소D");
        assertThat(columnOf("trip-1", "spot_image_urls"))
                .containsExactly("https://example.com/a.jpg", null, null, "https://example.com/d.jpg");
    }

    /**
     * 이름이 없는 장소도 자리를 지킨다.
     *
     * <p>{@code spots.name}이 NOT NULL이라 실제로는 드물지만, 자리가 밀리면 사진과의 대응이
     * 조용히 어긋난다. 두 컬럼이 같은 규칙을 따르는지 확인한다.
     */
    @Test
    void backfill_keepsSlotForSpotsWithoutName() {
        String courseId = insertCourse("""
                {"regionName":"제주도",
                 "day1":[{"spotId":"s1","name":null,"imageUrl":"https://example.com/a.jpg"},
                         {"spotId":"s2","name":"장소B","imageUrl":"https://example.com/b.jpg"}],
                 "day2":[]}
                """);
        insertPreBackfillSavedTrip("trip-1", courseId);

        runBackfill(SPOT_NAMES_MIGRATION);
        runBackfill(IMAGE_URLS_MIGRATION);

        assertThat(columnOf("trip-1", "spot_names")).containsExactly(null, "장소B");
        assertThat(columnOf("trip-1", "spot_image_urls"))
                .containsExactly("https://example.com/a.jpg", "https://example.com/b.jpg");
    }

    /**
     * 스냅샷 모양이 어긋난 행이 섞여 있어도 마이그레이션이 멈추지 않는다.
     *
     * <p>여기서 예외가 나면 애플리케이션이 아예 뜨지 않는다. {@code jsonb_array_elements}를
     * 직접 쓰지 않고 {@code jsonb_path_query_array}(lax 모드)를 쓰는 이유다.
     * 그 행만 빈 배열로 남고 나머지는 정상적으로 채워져야 한다.
     */
    @Test
    void backfill_withMalformedSnapshot_leavesThatRowEmptyAndFillsOthers() {
        String broken = insertCourse("""
                {"regionName":"제주도","day1":"배열이 아니다","day2":[]}
                """);
        String healthy = insertCourse("""
                {"regionName":"제주도",
                 "day1":[{"spotId":"s1","name":"장소A","imageUrl":"https://example.com/a.jpg"}],
                 "day2":[]}
                """);
        insertPreBackfillSavedTrip("trip-broken", broken);
        insertPreBackfillSavedTrip("trip-healthy", healthy);

        runBackfill(SPOT_NAMES_MIGRATION);
        runBackfill(IMAGE_URLS_MIGRATION);

        assertThat(columnOf("trip-broken", "spot_names")).isEmpty();
        assertThat(columnOf("trip-broken", "spot_image_urls")).isEmpty();
        assertThat(columnOf("trip-healthy", "spot_names")).containsExactly("장소A");
        assertThat(columnOf("trip-healthy", "spot_image_urls"))
                .containsExactly("https://example.com/a.jpg");
    }

    /**
     * V6이 NULL로 남긴 지역 소재지·대표 이미지·추천 이유를 코스 스냅샷에서 채운다.
     *
     * <p>이 값들이 비면 저장 목록에서 옛 항목만 지역명뿐인 카드로 나온다.
     */
    @Test
    void backfill_fillsV6CardFieldsFromCourseSnapshot() {
        String courseId = insertCourse("""
                {"regionName":"경상북도","regionArea":"의성군",
                 "imageUrl":"https://example.com/cover.jpg","reason":"역사 속 고즈넉한 감성",
                 "day1":[],"day2":[]}
                """);
        insertPreBackfillSavedTrip("trip-1", courseId);

        runBackfill(CARD_FIELDS_MIGRATION);

        assertThat(cardFieldsOf("trip-1"))
                .containsExactly("의성군", "https://example.com/cover.jpg", "역사 속 고즈넉한 감성");
    }

    /**
     * 이미 값이 있는 행은 코스 쪽이 달라졌어도 덮지 않는다.
     *
     * <p>저장 항목이 보관하는 것은 최초 저장 시점의 표시값이다. (ADR 0011)
     */
    @Test
    void backfill_doesNotOverwriteExistingCardFields() {
        String courseId = insertCourse("""
                {"regionName":"경상북도","regionArea":"바뀐 소재지",
                 "imageUrl":"https://example.com/new.jpg","reason":"바뀐 이유",
                 "day1":[],"day2":[]}
                """);
        insertPreBackfillSavedTrip("trip-1", courseId);
        jdbcTemplate.update("""
                UPDATE saved_trips
                SET region_area = '의성군', image_url = 'https://example.com/old.jpg', reason = '원래 이유'
                WHERE saved_trip_id = 'trip-1'
                """);

        runBackfill(CARD_FIELDS_MIGRATION);

        assertThat(cardFieldsOf("trip-1"))
                .containsExactly("의성군", "https://example.com/old.jpg", "원래 이유");
    }

    /**
     * 스냅샷에 값이 없으면 NULL로 남는다. {@code POST /courses} 경유 코스가 그렇다.
     *
     * <p>채울 것이 없는 것이지 빠뜨린 것이 아니다. 이 경로는 지역 카드를 거치지 않아
     * 소재지도 추천 이유도 만들어지지 않는다.
     */
    @Test
    void backfill_withSnapshotLackingCardFields_leavesThemNull() {
        String courseId = insertCourse("""
                {"regionName":"제주도","day1":[],"day2":[]}
                """);
        insertPreBackfillSavedTrip("trip-1", courseId);

        runBackfill(CARD_FIELDS_MIGRATION);

        assertThat(cardFieldsOf("trip-1")).containsExactly(null, null, null);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    /**
     * 마이그레이션의 백필 문장만 떼어 실행한다. 앞의 {@code ALTER TABLE}은 이미 적용돼 있다.
     *
     * <p>자르는 기준은 {@code backfill:start} 마커다. {@code "UPDATE saved_trips"}를 찾는 방식은
     * 그 문자열이 주석에 들어가거나 백필문이 둘 이상이 되면 조용히 일부만 실행한다.
     * 마커는 마이그레이션 파일 쪽에도 "여기부터는 테스트가 다시 실행한다"는 의도를 남긴다.
     */
    private void runBackfill(String migrationFile) {
        String migration = readMigration(migrationFile);
        int marker = migration.indexOf(BACKFILL_MARKER);
        if (marker < 0) {
            throw new IllegalStateException(
                    migrationFile + "에 " + BACKFILL_MARKER + " 마커가 없다. 백필이 검증되지 않는다.");
        }
        jdbcTemplate.execute(migration.substring(marker + BACKFILL_MARKER.length()));
    }

    private String readMigration(String migrationFile) {
        try (var in = new ClassPathResource("db/migration/" + migrationFile).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(migrationFile + "을 읽지 못했다.", e);
        }
    }

    private String insertCourse(String courseDataJson) {
        String courseId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO courses (course_id, device_id, course_data) VALUES (?, ?, ?::jsonb)",
                courseId, DEVICE, courseDataJson);
        return courseId;
    }

    /** 백필 이전에 저장된 행을 흉내 낸다. 그 시점에는 카드 필드가 없었으므로 기본값 그대로 둔다. */
    private void insertPreBackfillSavedTrip(String savedTripId, String courseId) {
        jdbcTemplate.update("""
                        INSERT INTO saved_trips
                            (saved_trip_id, device_id, course_id, region_key, course_fingerprint)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                savedTripId, DEVICE, courseId, "제주도", "fingerprint-" + savedTripId);
        assertThat(columnOf(savedTripId, "spot_names")).isEmpty();
        assertThat(columnOf(savedTripId, "spot_image_urls")).isEmpty();
    }

    /** 지역 소재지·대표 이미지·추천 이유를 이 순서로 읽는다. 값이 없으면 {@code null}이 그대로 들어온다. */
    private List<String> cardFieldsOf(String savedTripId) {
        return jdbcTemplate.queryForObject(
                "SELECT region_area, image_url, reason FROM saved_trips WHERE saved_trip_id = ?",
                (rs, rowNum) -> java.util.Arrays.asList(
                        rs.getString("region_area"), rs.getString("image_url"), rs.getString("reason")),
                savedTripId);
    }

    /** 자리를 비운 원소가 {@code null}로 들어오므로 {@code List.of}가 아니라 {@code Arrays.asList}로 받는다. */
    private List<String> columnOf(String savedTripId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM saved_trips WHERE saved_trip_id = ?",
                (rs, rowNum) -> java.util.Arrays.asList((String[]) rs.getArray(column).getArray()),
                savedTripId);
    }
}
