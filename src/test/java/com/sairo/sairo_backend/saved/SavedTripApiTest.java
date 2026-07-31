package com.sairo.sairo_backend.saved;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SavedTripApiTest extends IntegrationTestBase {

    private static final String DEVICE_A = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String DEVICE_B = "9b2d4e6f-1a3c-4b5d-8e7f-0a1b2c3d4e5f";

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM saved_trips");
        jdbcTemplate.update("DELETE FROM shared_courses");
        jdbcTemplate.update("DELETE FROM courses");
        jdbcTemplate.update("DELETE FROM spots");
        insertSpot("spot-a", "장소A", "제주도", 33.4, 126.5);
        insertSpot("spot-b", "장소B", "제주도", 33.5, 126.6);
        insertSpot("spot-c", "장소C", "제주도", 33.6, 126.7);
        insertSpot("spot-d", "장소D", "제주도", 33.7, 126.8);
        insertSpot("spot-e", "장소E", "강원", 37.8, 128.9);
        insertSpot("spot-f", "장소F", "강원", 37.9, 129.0);
    }

    @Test
    void save_withValidCourse_returns201() throws Exception {
        String courseId = createCourse("제주도", "\"spot-a\", \"spot-b\", \"spot-c\", \"spot-d\"");

        mockMvc.perform(saveRequest(DEVICE_A, courseId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.savedTripId").isNotEmpty())
                .andExpect(jsonPath("$.courseId").value(courseId))
                // 지역명은 요청 값이 아니라 코스 스냅샷에 담긴 값이다.
                .andExpect(jsonPath("$.regionName").value("제주도"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    // 네트워크 재시도나 연속 탭으로 같은 요청이 두 번 도착해도 항목은 하나여야 한다.
    @Test
    void save_calledTwiceWithSameCourse_returnsSameSavedTrip() throws Exception {
        String courseId = createCourse("제주도", "\"spot-a\", \"spot-b\", \"spot-c\", \"spot-d\"");

        String first = extract(saveResponseBody(DEVICE_A, courseId), "savedTripId");
        String second = extract(saveResponseBody(DEVICE_A, courseId), "savedTripId");

        assertThat(second).isEqualTo(first);
        assertThat(savedTripCount(DEVICE_A)).isEqualTo(1);
    }

    /**
     * 같은 장소로 코스를 다시 만들면 새 courseId가 나오지만 저장은 하나여야 한다. (Q-04)
     *
     * <p>courseId로 중복을 판정하면 사용자가 추천 화면을 다시 거칠 때마다 같은 코스가
     * 저장 목록에 쌓인다. 지문이 courseId가 아니라 장소 구성에서 나오는 이유다.
     */
    @Test
    void save_withDifferentCourseIdButSameSpots_returnsSameSavedTrip() throws Exception {
        String first = createCourse("제주도", "\"spot-a\", \"spot-b\", \"spot-c\", \"spot-d\"");
        String second = createCourse("제주도", "\"spot-a\", \"spot-b\", \"spot-c\", \"spot-d\"");
        assertThat(second).isNotEqualTo(first);

        String firstSaved = extract(saveResponseBody(DEVICE_A, first), "savedTripId");
        String secondBody = saveResponseBody(DEVICE_A, second);

        assertThat(extract(secondBody, "savedTripId")).isEqualTo(firstSaved);
        // 응답의 courseId는 처음 저장할 때의 값이다.
        assertThat(extract(secondBody, "courseId")).isEqualTo(first);
        assertThat(savedTripCount(DEVICE_A)).isEqualTo(1);
    }

    // 지역이 같아도 장소 구성이 다르면 별도 저장이다.
    @Test
    void save_withSameRegionButDifferentSpots_createsSeparateSavedTrips() throws Exception {
        String courseAbcd = createCourse("제주도", "\"spot-a\", \"spot-b\", \"spot-c\", \"spot-d\"");
        String courseAbc = createCourse("제주도", "\"spot-a\", \"spot-b\", \"spot-c\"");

        String firstSaved = extract(saveResponseBody(DEVICE_A, courseAbcd), "savedTripId");
        String secondSaved = extract(saveResponseBody(DEVICE_A, courseAbc), "savedTripId");

        assertThat(secondSaved).isNotEqualTo(firstSaved);
        assertThat(savedTripCount(DEVICE_A)).isEqualTo(2);
    }

    /**
     * 중복 판정은 기기별로 갈린다. 다른 기기의 저장이 내 저장을 막으면 안 된다.
     *
     * <p>코스도 기기별 소유물이므로 각자 자기 코스를 만들어 저장한다.
     * 장소 구성이 같으니 지문은 같지만 {@code device_id}가 달라 별도 항목이다.
     */
    @Test
    void save_withSameSpotsFromDifferentDevice_createsSeparateSavedTrip() throws Exception {
        String spots = "\"spot-a\", \"spot-b\", \"spot-c\", \"spot-d\"";
        String courseOfA = createCourse(DEVICE_A, "제주도", spots);
        String courseOfB = createCourse(DEVICE_B, "제주도", spots);

        String savedByA = extract(saveResponseBody(DEVICE_A, courseOfA), "savedTripId");
        String savedByB = extract(saveResponseBody(DEVICE_B, courseOfB), "savedTripId");

        assertThat(savedByB).isNotEqualTo(savedByA);
        assertThat(savedTripCount(DEVICE_A)).isEqualTo(1);
        assertThat(savedTripCount(DEVICE_B)).isEqualTo(1);
    }

    /**
     * 남의 코스는 저장할 수 없다. 403이 아니라 404다.
     *
     * <p>이 PR 이전에는 courseId만 알면 남의 코스를 내 저장 목록에 넣을 수 있었다. (ADR 0012)
     */
    @Test
    void save_withAnotherDevicesCourse_returns404() throws Exception {
        String courseOfA = createCourse(DEVICE_A, "제주도", "\"spot-a\", \"spot-b\", \"spot-c\", \"spot-d\"");

        mockMvc.perform(saveRequest(DEVICE_B, courseOfA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
        assertThat(savedTripCount(DEVICE_B)).isZero();
    }

    // 형식은 맞지만 대상이 없는 경우다. 형식 오류(아래)와 구분한다.
    @Test
    void save_withUnknownCourseId_returns404() throws Exception {
        mockMvc.perform(saveRequest(DEVICE_A, UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    /**
     * 본문에 담긴 리소스 ID는 형식을 검증하고 400을 낸다.
     *
     * <p>형식을 보지 않고 404로 답하는 예외는 <b>경로</b>의 리소스 ID에만 적용된다.
     * ({@code docs/api-contract.md} §2)
     */
    @Test
    void save_withMalformedCourseId_returns400() throws Exception {
        mockMvc.perform(saveRequest(DEVICE_A, "not-a-real-course"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void save_withoutDeviceIdHeader_returns400() throws Exception {
        String courseId = createCourse("제주도", "\"spot-a\", \"spot-b\", \"spot-c\", \"spot-d\"");

        mockMvc.perform(post("/saved-trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\": \"" + courseId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVICE_ID_REQUIRED"));
    }

    @Test
    void save_withBlankCourseId_returns400() throws Exception {
        mockMvc.perform(saveRequest(DEVICE_A, "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /**
     * 대문자 UUID는 400이다. 404가 아니다.
     *
     * <p>{@code courses.course_id}는 TEXT라 조회가 대소문자를 구분한다. 대문자를 통과시키면
     * 실재하는 코스에 {@code COURSE_NOT_FOUND}가 나가 "없는 코스"라고 잘못 답하게 된다.
     */
    @Test
    void save_withUppercaseCourseId_returns400() throws Exception {
        String courseId = createCourse("제주도", "\"spot-a\", \"spot-b\", \"spot-c\", \"spot-d\"");

        mockMvc.perform(saveRequest(DEVICE_A, courseId.toUpperCase(Locale.ROOT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /**
     * 저장된 코스 스냅샷을 읽지 못하면 500 INTERNAL_ERROR다.
     *
     * <p>컨트롤러가 이 500을 명세에 적었으므로 실제로 그 코드가 나오는지 고정한다.
     * JSONB라 문법이 깨진 값은 넣을 수 없다. 문법은 맞지만 {@code CourseSnapshot}으로
     * 매핑되지 않는 값을 넣는다.
     */
    @Test
    void save_withUnreadableCourseSnapshot_returns500() throws Exception {
        String courseId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO courses (course_id, device_id, course_data) VALUES (?, ?, ?::jsonb)",
                courseId, DEVICE_A, """
                        {"regionName":"제주도","day1":"배열이 아니다","day2":[]}
                        """);

        mockMvc.perform(saveRequest(DEVICE_A, courseId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    // ─── 목록 조회 (#31) ─────────────────────────────────────────────────────

    @Test
    void findPage_withNoSavedTrips_returnsEmptyListAndNullCursor() throws Exception {
        mockMvc.perform(listRequest(DEVICE_A, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void findPage_returnsSavedTripsInRecentFirstOrder() throws Exception {
        String courseId = insertCourse(DEVICE_A);
        insertSavedTrip("t1", DEVICE_A, courseId, "제주도", at(10, 0));
        insertSavedTrip("t2", DEVICE_A, courseId, "강원", at(11, 0));
        insertSavedTrip("t3", DEVICE_A, courseId, "경북", at(12, 0));

        mockMvc.perform(listRequest(DEVICE_A, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].savedTripId").value("t3"))
                .andExpect(jsonPath("$.items[1].savedTripId").value("t2"))
                .andExpect(jsonPath("$.items[2].savedTripId").value("t1"))
                .andExpect(jsonPath("$.items[0].regionName").value("경북"))
                .andExpect(jsonPath("$.items[0].courseId").value(courseId))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    /**
     * 커서로 끝까지 읽으면 모든 항목이 <b>정확히 한 번씩</b> 나온다.
     *
     * <p>커서 페이지의 실패는 대개 "빠짐"이나 "중복"으로 나타나므로 전체를 모아 검증한다.
     */
    @Test
    void findPage_readToEnd_returnsEveryItemExactlyOnce() throws Exception {
        String courseId = insertCourse(DEVICE_A);
        for (int i = 0; i < 5; i++) {
            insertSavedTrip("t" + i, DEVICE_A, courseId, "제주도", at(10, i));
        }

        assertThat(readAllIdsByPaging(DEVICE_A, 2))
                .containsExactly("t4", "t3", "t2", "t1", "t0");
    }

    /**
     * 저장 시각이 같아도 순서가 흔들리지 않는다.
     *
     * <p>{@code created_at}만으로 정렬하면 페이지 경계에서 같은 항목이 두 번 나오거나 빠진다.
     * {@code saved_trip_id}를 tie-breaker로 함께 쓰는 이유다.
     * ({@code docs/api-contract.md} §5)
     */
    @Test
    void findPage_withIdenticalCreatedAt_returnsEveryItemExactlyOnce() throws Exception {
        String courseId = insertCourse(DEVICE_A);
        LocalDateTime sameMoment = at(10, 0);
        for (int i = 0; i < 5; i++) {
            insertSavedTrip("t" + i, DEVICE_A, courseId, "제주도", sameMoment);
        }

        assertThat(readAllIdsByPaging(DEVICE_A, 2))
                .containsExactly("t4", "t3", "t2", "t1", "t0");
    }

    @Test
    void findPage_withMoreItemsThanSize_returnsCursor() throws Exception {
        String courseId = insertCourse(DEVICE_A);
        insertSavedTrip("t1", DEVICE_A, courseId, "제주도", at(10, 0));
        insertSavedTrip("t2", DEVICE_A, courseId, "강원", at(11, 0));

        mockMvc.perform(listRequest(DEVICE_A, null, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty());
    }

    /**
     * 남은 항목 수가 size와 정확히 같으면 다음 페이지는 없다.
     *
     * <p>size + 1을 읽어 판정하므로 이 경계에서 빈 페이지를 가리키는 커서가 나가기 쉽다.
     */
    @Test
    void findPage_withExactlySizeItems_returnsNullCursor() throws Exception {
        String courseId = insertCourse(DEVICE_A);
        insertSavedTrip("t1", DEVICE_A, courseId, "제주도", at(10, 0));
        insertSavedTrip("t2", DEVICE_A, courseId, "강원", at(11, 0));

        mockMvc.perform(listRequest(DEVICE_A, null, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    /** 다른 기기의 저장 항목은 목록에 섞이지 않는다. 소유자 조건은 쿼리에 있다. (AGENTS.md §1) */
    @Test
    void findPage_withAnotherDevicesSavedTrips_returnsOnlyOwn() throws Exception {
        String courseOfA = insertCourse(DEVICE_A);
        String courseOfB = insertCourse(DEVICE_B);
        insertSavedTrip("mine", DEVICE_A, courseOfA, "제주도", at(10, 0));
        insertSavedTrip("theirs", DEVICE_B, courseOfB, "강원", at(11, 0));

        mockMvc.perform(listRequest(DEVICE_A, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].savedTripId").value("mine"));
    }

    /** 남의 커서를 그대로 써도 남의 항목은 나오지 않는다. 커서는 위치일 뿐 권한이 아니다. */
    @Test
    void findPage_withAnotherDevicesCursor_stillReturnsOnlyOwn() throws Exception {
        String courseOfA = insertCourse(DEVICE_A);
        String courseOfB = insertCourse(DEVICE_B);
        insertSavedTrip("mine", DEVICE_A, courseOfA, "제주도", at(10, 0));
        insertSavedTrip("theirs-new", DEVICE_B, courseOfB, "강원", at(12, 0));
        insertSavedTrip("theirs-old", DEVICE_B, courseOfB, "경북", at(9, 0));

        String cursorOfB = extract(listBody(DEVICE_B, null, 1), "nextCursor");

        mockMvc.perform(listRequest(DEVICE_A, cursorOfB, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].savedTripId").value("mine"));
    }

    /**
     * 읽을 수 없는 커서는 조용히 첫 페이지로 되돌리지 않고 400으로 거절한다.
     *
     * <p>무시하면 클라이언트가 목록을 처음부터 다시 읽으며 같은 항목을 반복한다.
     */
    @Test
    void findPage_withMalformedCursor_returns400() throws Exception {
        mockMvc.perform(listRequest(DEVICE_A, "!!! not a cursor !!!", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    /**
     * 커서에 실린 시각이 {@code TIMESTAMP} 범위를 벗어나도 400이다. 500이 아니다.
     *
     * <p>여기서 500이 나가면 응답의 {@code retryable}이 {@code true}라 클라이언트는 계약상
     * 같은 커서로 재시도하게 되고, 몇 번을 해도 같은 500이라 목록이 그 자리에서 멈춘다.
     * 상태 코드만 보는 것이 아니라 {@code INVALID_CURSOR}까지 확인하는 이유다.
     */
    @Test
    void findPage_withCursorTimestampOutOfRange_returns400() throws Exception {
        String cursor = base64("v1|-9223372036854775807|" + UUID.randomUUID());

        mockMvc.perform(listRequest(DEVICE_A, cursor, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    /**
     * 빈 커서는 커서를 주지 않은 것과 같게 다룬다. (계약 §4의 빈 헤더 규칙과 같은 판단)
     *
     * <p>클라이언트 HTTP 라이브러리가 {@code null} 커서를 빈 문자열로 직렬화하는 일이 흔한데,
     * 그때 첫 페이지 요청이 400이 되면 목록을 아예 시작할 수 없다.
     */
    @Test
    void findPage_withEmptyCursor_returnsFirstPage() throws Exception {
        String courseId = insertCourse(DEVICE_A);
        insertSavedTrip("trip-1", DEVICE_A, courseId, "제주도", at(10, 0));

        mockMvc.perform(listRequest(DEVICE_A, "", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].savedTripId").value("trip-1"));
    }

    @Test
    void findPage_withoutDeviceIdHeader_returns400() throws Exception {
        mockMvc.perform(get("/saved-trips"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVICE_ID_REQUIRED"));
    }

    @Test
    void findPage_withSizeAboveMax_returns400() throws Exception {
        mockMvc.perform(listRequest(DEVICE_A, null, 51))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void findPage_withSizeBelowMin_returns400() throws Exception {
        mockMvc.perform(listRequest(DEVICE_A, null, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    /** 커서를 따라 끝까지 읽고 나온 순서대로 저장 항목 ID를 모은다. */
    private List<String> readAllIdsByPaging(String deviceId, int size) throws Exception {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        // 페이지 수 상한을 둔다. 커서가 전진하지 않는 버그가 무한 루프 대신 실패로 드러나게 한다.
        for (int page = 0; page < 20; page++) {
            String body = listBody(deviceId, cursor, size);
            objectMapper.readTree(body).path("items")
                    .forEach(item -> ids.add(item.path("savedTripId").asString()));
            cursor = objectMapper.readTree(body).path("nextCursor").asString(null);
            if (cursor == null) {
                return ids;
            }
        }
        throw new AssertionError("커서가 끝나지 않았다. 지금까지 읽은 항목: " + ids);
    }

    private String listBody(String deviceId, String cursor, Integer size) throws Exception {
        return mockMvc.perform(listRequest(deviceId, cursor, size))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private MockHttpServletRequestBuilder listRequest(
            String deviceId, String cursor, Integer size) {
        var request = get("/saved-trips").header("X-Device-Id", deviceId);
        if (cursor != null) {
            request = request.param("cursor", cursor);
        }
        if (size != null) {
            request = request.param("size", String.valueOf(size));
        }
        return request;
    }

    /** 정상 경로로는 만들 수 없는 커서를 손으로 짠다. 인코딩 형식은 {@code SavedTripCursor}와 같다. */
    private String base64(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 7, 31, hour, minute);
    }

    private String insertCourse(String deviceId) {
        String courseId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO courses (course_id, device_id, course_data) VALUES (?, ?, ?::jsonb)",
                courseId, deviceId, """
                        {"regionName":"제주도","day1":[],"day2":[]}
                        """);
        return courseId;
    }

    /**
     * 저장 행을 직접 넣는다.
     *
     * <p>API로는 저장 시각과 ID를 정할 수 없어 정렬·커서 경계를 만들 수 없다.
     * 지문은 유니크 인덱스에만 걸리므로 행마다 다르게 준다.
     */
    private void insertSavedTrip(String savedTripId, String deviceId, String courseId,
                                 String regionKey, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                        INSERT INTO saved_trips
                            (saved_trip_id, device_id, course_id, region_key, course_fingerprint, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                savedTripId, deviceId, courseId, regionKey,
                "fingerprint-" + savedTripId, Timestamp.valueOf(createdAt));
    }


    private void insertSpot(String id, String name, String region, double lat, double lng) {
        jdbcTemplate.update(
                "INSERT INTO spots (spot_id, name, region_name, lat, lng) VALUES (?, ?, ?, ?, ?)",
                id, name, region, lat, lng);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder saveRequest(
            String deviceId, String courseId) {
        return post("/saved-trips")
                .header("X-Device-Id", deviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\": \"" + courseId + "\"}");
    }

    private String saveResponseBody(String deviceId, String courseId) throws Exception {
        return mockMvc.perform(saveRequest(deviceId, courseId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String createCourse(String regionName, String spotIdsCsv) throws Exception {
        return createCourse(DEVICE_A, regionName, spotIdsCsv);
    }

    private String createCourse(String deviceId, String regionName, String spotIdsCsv) throws Exception {
        String body = mockMvc.perform(post("/courses")
                        .header("X-Device-Id", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regionName\": \"" + regionName + "\", \"spotIds\": [" + spotIdsCsv + "]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extract(body, "courseId");
    }

    private Integer savedTripCount(String deviceId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM saved_trips WHERE device_id = ?", Integer.class, deviceId);
    }

    private String extract(String json, String field) {
        return objectMapper.readTree(json).path(field).asString();
    }
}
