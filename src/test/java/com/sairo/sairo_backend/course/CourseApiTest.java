package com.sairo.sairo_backend.course;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseApiTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM shared_courses");
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

    @Test
    void buildCourse_withTooFewSpots_returns400() throws Exception {
        mockMvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "제주", "spotIds": ["spot-a"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shareCourse_returns201WithShareIdAndUrl() throws Exception {
        MvcResult result = mockMvc.perform(post("/courses/test-course-id/share")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "day1": [{"spotId":"spot-a","name":"장소A","lat":33.4,"lng":126.5,"imageUrl":null}],
                                  "day2": [{"spotId":"spot-b","name":"장소B","lat":33.5,"lng":126.6,"imageUrl":null}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shareId").isNotEmpty())
                .andExpect(jsonPath("$.shareUrl").isNotEmpty())
                .andReturn();

        // 저장된 shareId로 조회 검증
        String body = result.getResponse().getContentAsString();
        String shareId = body.split("\"shareId\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/courses/shared/" + shareId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareId").value(shareId))
                .andExpect(jsonPath("$.day1").isArray())
                .andExpect(jsonPath("$.day2").isArray());
    }

    @Test
    void getSharedCourse_withInvalidShareId_returns404() throws Exception {
        mockMvc.perform(get("/courses/shared/not-exist"))
                .andExpect(status().isNotFound());
    }
}
