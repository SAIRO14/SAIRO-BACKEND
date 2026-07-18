package com.sairo.sairo_backend.analysis;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TasteAnalysisApiTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertTestData() {
        jdbcTemplate.update("DELETE FROM photos");
        insertPhoto("photo-1", "테스트1", "http://img1.jpg", "제주", "자연,힐링");
        insertPhoto("photo-2", "테스트2", "http://img2.jpg", "제주", "바다,여유");
    }

    @Test
    void tasteAnalysis_withValidPhotoIds_returns200() throws Exception {
        mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["photo-1", "photo-2"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").isNotEmpty())
                .andExpect(jsonPath("$.moodTags").isArray())
                .andExpect(jsonPath("$.summary").isNotEmpty());
    }

    @Test
    void tasteAnalysis_withInvalidPhotoIds_returns400() throws Exception {
        mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["no-such-id"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tasteAnalysis_withEmptyPhotoIds_returns400() throws Exception {
        mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": []}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recommendations_withValidAnalysisId_returns200() throws Exception {
        MvcResult result = mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["photo-1"]}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        // analysisId 추출: JSON에서 직접 파싱
        String body = result.getResponse().getContentAsString();
        String analysisId = body.split("\"analysisId\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/recommendations").param("analysisId", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moodTags").isArray())
                .andExpect(jsonPath("$.spots").isArray());
    }

    @Test
    void recommendations_withInvalidAnalysisId_returns400() throws Exception {
        mockMvc.perform(get("/recommendations").param("analysisId", "invalid-id"))
                .andExpect(status().isBadRequest());
    }

    private void insertPhoto(String id, String title, String imageUrl, String location, String keywords) {
        String vecStr = "[" + IntStream.range(0, 512)
                .mapToObj(i -> "0.1")
                .collect(Collectors.joining(",")) + "]";
        jdbcTemplate.update(
                "INSERT INTO photos (id, title, image_url, location, keywords, embedding) " +
                "VALUES (?, ?, ?, ?, ?, ?::vector)",
                id, title, imageUrl, location, keywords, vecStr
        );
    }
}
