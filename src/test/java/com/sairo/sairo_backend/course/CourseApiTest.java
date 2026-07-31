package com.sairo.sairo_backend.course;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

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
        // FK가 ON DELETE SET NULL이라 순서에 제약은 없다. 읽는 순서대로 지운다.
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
                "INSERT INTO spots (spot_id, name, region_name, lat, lng) VALUES (?, ?, ?, ?, ?)",
                "spot-d", "장소D", "제주", 33.7, 126.8);
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
                .andExpect(jsonPath("$.day2.length()").value(2));
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
                .andExpect(jsonPath("$.day2.length()").value(2));
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

    @Test
    void shareCourse_withUnknownCourseId_returns404() throws Exception {
        mockMvc.perform(post("/courses/not-a-real-course/share").header("X-Device-Id", DEVICE_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
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
                .andExpect(jsonPath("$.day2.length()").value(1));
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
    void getCourse_returnsRegionNameAndDays() throws Exception {
        String courseId = createCourse();

        mockMvc.perform(get("/courses/" + courseId).header("X-Device-Id", DEVICE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(courseId))
                .andExpect(jsonPath("$.regionName").value("제주"))
                .andExpect(jsonPath("$.day1").isArray())
                .andExpect(jsonPath("$.day2").isArray())
                .andExpect(jsonPath("$.day1.length()").value(2))
                .andExpect(jsonPath("$.day2.length()").value(2));
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

    private String shareResponseBody(String courseId) throws Exception {
        return mockMvc.perform(post("/courses/" + courseId + "/share").header("X-Device-Id", DEVICE_A))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String extract(String json, String field) {
        return objectMapper.readTree(json).path(field).asString();
    }
}
