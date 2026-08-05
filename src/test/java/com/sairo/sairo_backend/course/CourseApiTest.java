package com.sairo.sairo_backend.course;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseApiTest extends IntegrationTestBase {

    private static final String DEVICE_A = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String DEVICE_B = "9b2d4e6f-1a3c-4b5d-8e7f-0a1b2c3d4e5f";

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // saved_trips가 courses를 FK(RESTRICT)로 참조한다. 반드시 먼저 지운다.
        jdbcTemplate.update("DELETE FROM saved_trips");
        jdbcTemplate.update("DELETE FROM shared_courses");
        jdbcTemplate.update("DELETE FROM courses");
        jdbcTemplate.update("DELETE FROM spots");
        jdbcTemplate.update(
                "INSERT INTO spots (spot_id, name, region_name, lat, lng) VALUES (?, ?, ?, ?, ?)",
                "spot-a", "장소A", "제주", 33.4, 126.5);
        jdbcTemplate.update(
                "INSERT INTO spots (spot_id, name, region_name, lat, lng) VALUES (?, ?, ?, ?, ?)",
                "spot-b", "장소B", "제주", 33.5, 126.6);
        jdbcTemplate.update(
                "INSERT INTO spots (spot_id, name, region_name, lat, lng) VALUES (?, ?, ?, ?, ?)",
                "spot-c", "장소C", "제주", 33.6, 126.7);
        jdbcTemplate.update(
                """
                INSERT INTO spots (
                    spot_id, name, region_name, lat, lng, image_url,
                    operating_hours, closed_days, parking, contact
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "spot-d", "장소D", "제주", 33.7, 126.8, "https://example.com/spot-d.jpg",
                "09:00~18:00", "연중무휴", "가능", "064-000-0000");
        jdbcTemplate.update(
                "INSERT INTO spots (spot_id, name, region_name, lat, lng) VALUES (?, ?, ?, ?, ?)",
                "spot-gangwon", "장소E", "강원", 37.8, 128.9);
    }

    @Test
    void buildCourse_withValidSpots_returns200WithDay1AndDay2() throws Exception {
        mockMvc.perform(post("/courses")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "제주", "spotIds": ["spot-a", "spot-b", "spot-c", "spot-d"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").isNotEmpty())
                .andExpect(jsonPath("$.regionName").value("제주"))
                .andExpect(jsonPath("$.day1").isArray())
                .andExpect(jsonPath("$.day2").isArray())
                .andExpect(jsonPath("$.day1.length()").value(2))
                .andExpect(jsonPath("$.day2.length()").value(2))
                .andExpect(jsonPath("$.day1[0].spotId").value("spot-d"))
                .andExpect(jsonPath("$.day1[0].operatingHours").value("09:00~18:00"))
                .andExpect(jsonPath("$.day1[0].closedDays").value("연중무휴"))
                .andExpect(jsonPath("$.day1[0].parking").value("가능"))
                .andExpect(jsonPath("$.day1[0].contact").value("064-000-0000"));
    }

    /**
     * 같은 장소 집합이면 요청 순서와 무관하게 같은 코스가 나와야 한다.
     *
     * <p>{@code findAllById()}는 반환 순서를 보장하지 않는다. 정렬 시작점이나 동점 처리가
     * 목록 순서에 의존하면 같은 요청에도 다른 코스가 나온다.
     */
    @Test
    void buildCourse_isDeterministic_regardlessOfRequestOrder() throws Exception {
        String forward = courseSpotOrder("[\"spot-a\", \"spot-b\", \"spot-c\", \"spot-d\"]");
        String reversed = courseSpotOrder("[\"spot-d\", \"spot-c\", \"spot-b\", \"spot-a\"]");
        String shuffled = courseSpotOrder("[\"spot-c\", \"spot-a\", \"spot-d\", \"spot-b\"]");

        assertThat(reversed).isEqualTo(forward);
        assertThat(shuffled).isEqualTo(forward);
    }

    // 시작점은 가장 북쪽 장소다. spot-d(33.7)가 가장 북쪽이므로 항상 첫 방문지가 된다.
    @Test
    void buildCourse_startsFromNorthernmostSpot() throws Exception {
        mockMvc.perform(post("/courses")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "제주", "spotIds": ["spot-a", "spot-b", "spot-c", "spot-d"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.day1[0].spotId").value("spot-d"));
    }

    private String courseSpotOrder(String spotIdsJson) throws Exception {
        String body = mockMvc.perform(post("/courses")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regionName\": \"제주\", \"spotIds\": " + spotIdsJson + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // courseId는 매번 새로 발급되므로 장소 순서만 비교한다.
        Matcher matcher = Pattern.compile("\"spotId\":\"([^\"]+)\"").matcher(body);
        StringBuilder order = new StringBuilder();
        while (matcher.find()) {
            order.append(matcher.group(1)).append(',');
        }
        return order.toString();
    }

    @Test
    void buildCourse_withTooFewSpots_returns400() throws Exception {
        mockMvc.perform(post("/courses")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "제주", "spotIds": ["spot-a"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    // 요청한 지역 밖의 장소가 섞이면 코스도 스냅샷도 틀린 지역을 갖게 된다.
    @Test
    void buildCourse_withSpotOutsideRegion_returns400() throws Exception {
        mockMvc.perform(post("/courses")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "제주", "spotIds": ["spot-a", "spot-b", "spot-gangwon"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_REGION_MISMATCH"));
    }

    @Test
    void shareCourse_returns201WithShareIdAndUrl() throws Exception {
        String courseId = createCourse();

        MvcResult result = mockMvc.perform(post("/courses/" + courseId + "/share").header("X-Device-Id", DEVICE_A))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shareId").isNotEmpty())
                .andExpect(jsonPath("$.shareUrl").isNotEmpty())
                .andReturn();

        // 저장된 shareId로 조회 검증
        String shareId = extract(result.getResponse().getContentAsString(), "shareId");

        mockMvc.perform(get("/courses/shared/" + shareId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareId").value(shareId))
                .andExpect(jsonPath("$.regionName").value("제주"))
                .andExpect(jsonPath("$.day1.length()").value(2))
                .andExpect(jsonPath("$.day2.length()").value(2))
                .andExpect(jsonPath("$.day1[0].spotId").value("spot-d"))
                .andExpect(jsonPath("$.day1[0].imageUrl").value("https://example.com/spot-d.jpg"))
                .andExpect(jsonPath("$.day1[0].lat").value(33.7))
                .andExpect(jsonPath("$.day1[0].lng").value(126.8))
                .andExpect(jsonPath("$.day1[0].operatingHours").value("09:00~18:00"))
                .andExpect(jsonPath("$.day1[0].closedDays").value("연중무휴"))
                .andExpect(jsonPath("$.day1[0].parking").value("가능"))
                .andExpect(jsonPath("$.day1[0].contact").value("064-000-0000"));
    }

    /**
     * 남의 코스는 공유할 수 없다. 403이 아니라 404다.
     *
     * <p>403이면 "그 courseId는 존재한다"는 사실을 알려주게 된다. (docs/api-contract.md §4)
     */
    @Test
    void shareCourse_withAnotherDevicesCourse_returns404() throws Exception {
        String courseId = createCourse();

        mockMvc.perform(post("/courses/" + courseId + "/share").header("X-Device-Id", DEVICE_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    @Test
    void buildCourse_withoutDeviceIdHeader_returns400() throws Exception {
        mockMvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "제주", "spotIds": ["spot-a", "spot-b"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVICE_ID_REQUIRED"));
    }

    // 공유 링크 조회는 소유자를 보지 않는다. 남에게 보내라고 만든 것이다.
    @Test
    void getSharedCourse_isPublicRegardlessOfDevice() throws Exception {
        String shareId = extract(shareResponseBody(createCourse()), "shareId");

        mockMvc.perform(get("/courses/shared/" + shareId).header("X-Device-Id", DEVICE_B))
                .andExpect(status().isOk());
        mockMvc.perform(get("/courses/shared/" + shareId))
                .andExpect(status().isOk());
    }

    // 형식은 맞지만 대상이 없는 경우다. 형식 오류(아래)와 구분한다.
    @Test
    void shareCourse_withUnknownCourseId_returns404() throws Exception {
        mockMvc.perform(post("/courses/" + UUID.randomUUID() + "/share").header("X-Device-Id", DEVICE_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    /**
     * 경로에 있는 {@code courseId}도 형식을 검증한다. 404가 아니라 400이다.
     *
     * <p>계약 §2의 기준은 "경로냐 본문이냐"가 아니라 <b>"틀린 형식이 거짓 부재를 만드는가"</b>다.
     * ({@code docs/decisions/0013-id-format-validation.md})
     */
    @Test
    void shareCourse_withMalformedCourseId_returns400() throws Exception {
        mockMvc.perform(post("/courses/not-a-real-course/share").header("X-Device-Id", DEVICE_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /**
     * 이 변경이 실제로 막는 것. 대문자 UUID로 <b>실재하는 자기 코스</b>를 공유하려 했을 때다.
     *
     * <p>이전에는 404 {@code COURSE_NOT_FOUND}가 나갔다. {@code courses.course_id}가 TEXT라
     * 조회가 대소문자를 구분해 빗나가는데, 응답은 "그런 코스가 없다"고 말한다.
     * 코스는 실재하므로 거짓말이다.
     */
    @Test
    void shareCourse_withUppercaseCourseIdOfExistingCourse_returns400() throws Exception {
        String courseId = createCourse();

        mockMvc.perform(post("/courses/" + courseId.toUpperCase(Locale.ROOT) + "/share")
                        .header("X-Device-Id", DEVICE_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // 소문자로는 그대로 공유된다. 코스가 실재한다는 근거다.
        mockMvc.perform(post("/courses/" + courseId + "/share").header("X-Device-Id", DEVICE_A))
                .andExpect(status().isCreated());
    }

    // 네트워크 재시도나 연속 탭으로 같은 요청이 두 번 도착해도 링크는 하나여야 한다.
    @Test
    void shareCourse_calledTwice_returnsSameShareId() throws Exception {
        String courseId = createCourse();

        String first = extract(shareResponseBody(courseId), "shareId");
        String second = extract(shareResponseBody(courseId), "shareId");

        assertThat(second).isEqualTo(first);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared_courses WHERE course_id = ?", Integer.class, courseId);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void getSharedCourse_withInvalidShareId_returns404() throws Exception {
        mockMvc.perform(get("/courses/shared/not-exist"))
                .andExpect(status().isNotFound());
    }

    /**
     * 공유 스냅샷을 읽지 못하면 500 INTERNAL_ERROR다.
     *
     * <p>컨트롤러가 이 500을 명세에 적었으므로 실제로 그 코드가 나오는지 고정한다.
     * JSONB라 문법이 깨진 값은 넣을 수 없어, 문법은 맞지만 {@code CourseSnapshot}으로
     * 매핑되지 않는 값을 넣는다.
     */
    @Test
    void getSharedCourse_withUnreadableSnapshot_returns500() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO shared_courses (share_id, course_data) VALUES (?, ?::jsonb)",
                "broken0001", """
                        {"regionName":"제주도","day1":"배열이 아니다","day2":[]}
                        """);

        mockMvc.perform(get("/courses/shared/broken0001"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    /**
     * 코스 영속화 이전에 만들어진 공유 링크도 계속 열려야 한다.
     *
     * <p>옛 스냅샷은 {@code day1}·{@code day2}만 담고 있어 지역을 복원할 수 없다.
     * 조회가 실패하지 않고 {@code regionName}만 null로 나가는 것이 정해진 동작이다.
     */
    @Test
    void getSharedCourse_withLegacySnapshot_returnsNullRegionName() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO shared_courses (share_id, course_data) VALUES (?, ?::jsonb)
                """,
                "legacy0001", """
                {"day1":[{"spotId":"spot-a","name":"장소A","lat":33.4,"lng":126.5,"imageUrl":null}],
                 "day2":[{"spotId":"spot-b","name":"장소B","lat":33.5,"lng":126.6,"imageUrl":null}]}
                """);

        mockMvc.perform(get("/courses/shared/legacy0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionName").value(nullValue()))
                .andExpect(jsonPath("$.day1.length()").value(1))
                .andExpect(jsonPath("$.day2.length()").value(1))
                .andExpect(jsonPath("$.day1[0].operatingHours").value(nullValue()))
                .andExpect(jsonPath("$.day1[0].closedDays").value(nullValue()))
                .andExpect(jsonPath("$.day1[0].parking").value(nullValue()))
                .andExpect(jsonPath("$.day1[0].contact").value(nullValue()));
    }

    /**
     * 지역 검증은 장소를 고를 때와 같은 부분 일치 규칙이어야 한다.
     *
     * <p>장소 조회가 {@code ILIKE '%지역%'}이므로 "제주"로 조회된 "제주도" 장소가
     * 검증에서 막히면 추천에서 코스 생성으로 이어지는 흐름이 끊긴다.
     *
     * <p>스냅샷에 저장되는 값은 요청한 "제주"가 아니라 장소에서 유도한 "제주도"다.
     * 요청 값을 그대로 믿으면 "주" 같은 값이 그대로 저장된다.
     */
    @Test
    void buildCourse_withPartiallyMatchingRegion_storesSpotRegion() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO spots (spot_id, name, region_name, lat, lng) VALUES (?, ?, ?, ?, ?)",
                "spot-jeju-1", "제주장소1", "제주도", 33.2, 126.3);
        jdbcTemplate.update(
                "INSERT INTO spots (spot_id, name, region_name, lat, lng) VALUES (?, ?, ?, ?, ?)",
                "spot-jeju-2", "제주장소2", "제주도", 33.3, 126.4);

        String body = mockMvc.perform(post("/courses")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "제주", "spotIds": ["spot-jeju-1", "spot-jeju-2"]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String shareId = extract(shareResponseBody(extract(body, "courseId")), "shareId");
        mockMvc.perform(get("/courses/shared/" + shareId))
                .andExpect(jsonPath("$.regionName").value("제주도"));
    }

    // 부분 일치라 "주"는 경주와 제주 장소 모두에 걸린다. 지역이 갈리면 코스를 만들지 않는다.
    @Test
    void buildCourse_withSpotsFromDifferentRegions_returns400() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO spots (spot_id, name, region_name, lat, lng) VALUES (?, ?, ?, ?, ?)",
                "spot-gyeongju", "경주장소", "경주", 35.8, 129.2);
        jdbcTemplate.update(
                "INSERT INTO spots (spot_id, name, region_name, lat, lng) VALUES (?, ?, ?, ?, ?)",
                "spot-jeju", "제주장소", "제주", 33.2, 126.3);

        mockMvc.perform(post("/courses")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "주", "spotIds": ["spot-gyeongju", "spot-jeju"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_REGION_MISMATCH"));
    }

    @Test
    void getCourse_withValidOwner_returnsRegionNameAndDays() throws Exception {
        String courseId = createCourse();

        mockMvc.perform(get("/courses/" + courseId).header("X-Device-Id", DEVICE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(courseId))
                .andExpect(jsonPath("$.regionName").value("제주"))
                .andExpect(jsonPath("$.day1").isArray())
                .andExpect(jsonPath("$.day2").isArray())
                .andExpect(jsonPath("$.day1.length()").value(2))
                .andExpect(jsonPath("$.day2.length()").value(2))
                .andExpect(jsonPath("$.day1[0].spotId").value("spot-d"))
                .andExpect(jsonPath("$.day1[0].imageUrl").value("https://example.com/spot-d.jpg"))
                .andExpect(jsonPath("$.day1[0].lat").value(33.7))
                .andExpect(jsonPath("$.day1[0].lng").value(126.8))
                .andExpect(jsonPath("$.day1[0].operatingHours").value("09:00~18:00"))
                .andExpect(jsonPath("$.day1[0].closedDays").value("연중무휴"))
                .andExpect(jsonPath("$.day1[0].parking").value("가능"))
                .andExpect(jsonPath("$.day1[0].contact").value("064-000-0000"));
    }

    /**
     * 상세 필드가 추가되기 전에 저장된 코스도 계속 조회할 수 있어야 한다.
     * 신규 필드는 복원할 원본이 스냅샷에 없으므로 null로 반환한다.
     */
    @Test
    void getCourse_withLegacySnapshot_returnsNullDetailFields() throws Exception {
        String courseId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO courses (course_id, device_id, course_data) VALUES (?, ?, ?::jsonb)",
                courseId, DEVICE_A, """
                {"regionName":"제주",
                 "day1":[{"spotId":"spot-a","name":"장소A","lat":33.4,"lng":126.5,"imageUrl":null}],
                 "day2":[{"spotId":"spot-b","name":"장소B","lat":33.5,"lng":126.6,"imageUrl":null}]}
                """);

        mockMvc.perform(get("/courses/" + courseId).header("X-Device-Id", DEVICE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(courseId))
                .andExpect(jsonPath("$.regionArea").value(nullValue()))
                .andExpect(jsonPath("$.imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.reason").value(nullValue()))
                .andExpect(jsonPath("$.day1[0].operatingHours").value(nullValue()))
                .andExpect(jsonPath("$.day1[0].closedDays").value(nullValue()))
                .andExpect(jsonPath("$.day1[0].parking").value(nullValue()))
                .andExpect(jsonPath("$.day1[0].contact").value(nullValue()));
    }

    /**
     * 코스는 생성 후 불변 스냅샷이다. 장소 마스터가 바뀌어도 기존 코스 조회는
     * 생성 시점의 장소 정보를 그대로 반환해야 한다. (ADR 0010)
     */
    @Test
    void getCourse_afterSpotDetailsChange_returnsCreationTimeSnapshot() throws Exception {
        String courseId = createCourse();
        jdbcTemplate.update(
                """
                UPDATE spots
                SET operating_hours = ?, closed_days = ?, parking = ?, contact = ?
                WHERE spot_id = ?
                """,
                "10:00~17:00", "매주 월요일", "불가", "064-111-1111", "spot-d");

        mockMvc.perform(get("/courses/" + courseId).header("X-Device-Id", DEVICE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.day1[0].spotId").value("spot-d"))
                .andExpect(jsonPath("$.day1[0].operatingHours").value("09:00~18:00"))
                .andExpect(jsonPath("$.day1[0].closedDays").value("연중무휴"))
                .andExpect(jsonPath("$.day1[0].parking").value("가능"))
                .andExpect(jsonPath("$.day1[0].contact").value("064-000-0000"));
    }

    /**
     * 남의 코스는 없는 것과 같게 404다. 403이면 courseId가 존재한다는 사실을 알려주게 된다.
     * (docs/api-contract.md §4)
     */
    @Test
    void getCourse_withAnotherDevicesCourse_returns404() throws Exception {
        String courseId = createCourse();

        mockMvc.perform(get("/courses/" + courseId).header("X-Device-Id", DEVICE_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    @Test
    void getCourse_withUnknownCourseId_returns404() throws Exception {
        mockMvc.perform(get("/courses/not-a-real-course").header("X-Device-Id", DEVICE_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    @Test
    void getCourse_withoutDeviceIdHeader_returns400() throws Exception {
        mockMvc.perform(get("/courses/some-course-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVICE_ID_REQUIRED"));
    }

    // 저장하지 않은 코스는 saved=false다.
    @Test
    void getCourse_withUnsavedCourse_returnsSavedFalse() throws Exception {
        String courseId = createCourse();

        mockMvc.perform(get("/courses/" + courseId).header("X-Device-Id", DEVICE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false));
    }

    /**
     * 저장한 코스는 saved=true다.
     *
     * <p>판정 키는 courseId가 아니라 코스 지문이다. 같은 장소 구성으로 만든 새 코스를
     * 조회해도 이미 저장된 코스가 있으면 saved=true가 나온다. (ADR 0011)
     */
    @Test
    void getCourse_withSavedCourse_returnsSavedTrue() throws Exception {
        String courseId = createCourse();
        saveCourse(courseId);

        mockMvc.perform(get("/courses/" + courseId).header("X-Device-Id", DEVICE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
    }

    /**
     * 판정 키가 courseId가 아니라 장소 구성(지문)이라는 것을 실제로 검증한다. (ADR 0011)
     *
     * <p>같은 장소로 새 코스를 만들어도 이미 저장된 코스가 있으면 saved=true다.
     * courseId로 판정하는 구현으로 바꾸면 이 테스트가 즉시 실패한다.
     */
    @Test
    void getCourse_withSameSpotsAsSavedCourse_returnsSavedTrue() throws Exception {
        saveCourse(createCourse());
        String recreated = createCourse(); // 같은 장소, 새 courseId

        mockMvc.perform(get("/courses/" + recreated).header("X-Device-Id", DEVICE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
    }

    // 다른 기기가 같은 장소 구성을 저장해도 내 코스는 saved=false다. (AGENTS.md §1)
    @Test
    void getCourse_otherDeviceSave_doesNotAffectOwnerSavedStatus() throws Exception {
        String courseIdA = createCourse();

        String deviceBBody = mockMvc.perform(post("/courses")
                        .header("X-Device-Id", DEVICE_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "제주", "spotIds": ["spot-a", "spot-b", "spot-c", "spot-d"]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/saved-trips")
                        .header("X-Device-Id", DEVICE_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\": \"" + extract(deviceBBody, "courseId") + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/courses/" + courseIdA).header("X-Device-Id", DEVICE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false));
    }

    // 저장 해제 후에는 saved=false로 돌아온다.
    @Test
    void getCourse_afterDelete_returnsSavedFalse() throws Exception {
        String courseId = createCourse();
        String savedTripId = saveCourse(courseId);

        mockMvc.perform(delete("/saved-trips")
                        .header("X-Device-Id", DEVICE_A)
                        .param("savedTripId", savedTripId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/courses/" + courseId).header("X-Device-Id", DEVICE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false));
    }

    private String createCourse() throws Exception {
        String body = mockMvc.perform(post("/courses")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "제주", "spotIds": ["spot-a", "spot-b", "spot-c", "spot-d"]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extract(body, "courseId");
    }

    private String saveCourse(String courseId) throws Exception {
        String body = mockMvc.perform(post("/saved-trips")
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\": \"" + courseId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extract(body, "savedTripId");
    }

    private String shareResponseBody(String courseId) throws Exception {
        return mockMvc.perform(post("/courses/" + courseId + "/share").header("X-Device-Id", DEVICE_A))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String extract(String json, String field) {
        return objectMapper.readTree(json).path(field).asString();
    }
}
