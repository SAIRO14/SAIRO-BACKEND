package com.sairo.sairo_backend.saved;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    // 중복 판정은 기기별로 갈린다. 다른 기기의 저장이 내 저장을 막으면 안 된다.
    @Test
    void save_withSameCourseFromDifferentDevice_createsSeparateSavedTrip() throws Exception {
        String courseId = createCourse("제주도", "\"spot-a\", \"spot-b\", \"spot-c\", \"spot-d\"");

        String savedByA = extract(saveResponseBody(DEVICE_A, courseId), "savedTripId");
        String savedByB = extract(saveResponseBody(DEVICE_B, courseId), "savedTripId");

        assertThat(savedByB).isNotEqualTo(savedByA);
        assertThat(savedTripCount(DEVICE_A)).isEqualTo(1);
        assertThat(savedTripCount(DEVICE_B)).isEqualTo(1);
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

    // ─── helpers ────────────────────────────────────────────────────────────

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
        String body = mockMvc.perform(post("/courses")
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
