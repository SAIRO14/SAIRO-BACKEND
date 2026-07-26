package com.sairo.sairo_backend.course;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseApiTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // shared_courses가 courses를 참조하므로 순서를 지킨다.
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "제주", "spotIds": ["spot-a", "spot-b", "spot-c", "spot-d"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").isNotEmpty())
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "제주", "spotIds": ["spot-a", "spot-b", "spot-c", "spot-d"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.day1[0].spotId").value("spot-d"));
    }

    private String courseSpotOrder(String spotIdsJson) throws Exception {
        String body = mockMvc.perform(post("/courses")
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

        MvcResult result = mockMvc.perform(post("/courses/" + courseId + "/share"))
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
     * 공유 스냅샷은 서버가 저장한 코스에서만 나온다.
     *
     * <p>이전에는 경로의 courseId를 무시하고 요청 본문을 그대로 저장해,
     * 서버가 만들지 않은 코스로도 공유 링크를 받을 수 있었다.
     */
    @Test
    void shareCourse_withUnknownCourseId_returns404() throws Exception {
        mockMvc.perform(post("/courses/not-a-real-course/share"))
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

    private String createCourse() throws Exception {
        String body = mockMvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "제주", "spotIds": ["spot-a", "spot-b", "spot-c", "spot-d"]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extract(body, "courseId");
    }

    private String shareResponseBody(String courseId) throws Exception {
        return mockMvc.perform(post("/courses/" + courseId + "/share"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String extract(String json, String field) {
        return json.split("\"" + field + "\":\"")[1].split("\"")[0];
    }
}
