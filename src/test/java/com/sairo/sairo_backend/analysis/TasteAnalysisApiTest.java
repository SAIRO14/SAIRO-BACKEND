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
        insertPhoto("photo-3", "테스트3", "http://img3.jpg", "강원", "산,숲");
        insertPhoto("photo-4", "테스트4", "http://img4.jpg", "강원", "계곡,청정");
        insertPhoto("photo-5", "테스트5", "http://img5.jpg", "경주", "역사,고즈넉");
        insertPhoto("photo-6", "테스트6", "http://img6.jpg", "경주", "전통,문화");
        insertPhoto("photo-7", "테스트7", "http://img7.jpg", "전북", "고요,자연");
        insertPhoto("photo-8", "테스트8", "http://img8.jpg", "전북", "숲,산책");
        insertPhoto("photo-9", "테스트9", "http://img9.jpg", "충남", "바다,노을");
        insertPhoto("photo-10", "테스트10", "http://img10.jpg", "충남", "갯벌,체험");
        insertPhoto("photo-11", "테스트11", "http://img11.jpg", "서울", "도시,야경");
    }

    @Test
    void tasteAnalysis_withValidPhotoIds_returns200() throws Exception {
        mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["photo-1", "photo-2", "photo-3", "photo-4", "photo-5"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").isNotEmpty())
                .andExpect(jsonPath("$.moodTags").isArray())
                .andExpect(jsonPath("$.summary").isNotEmpty());
    }

    // 5장 미만은 분석 기준을 충족하지 못한다.
    @Test
    void tasteAnalysis_withTooFewPhotoIds_returns400() throws Exception {
        mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["photo-1", "photo-2", "photo-3", "photo-4"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // 10장은 상한 경계로 허용된다.
    @Test
    void tasteAnalysis_withMaxPhotoIds_returns200() throws Exception {
        mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["photo-1", "photo-2", "photo-3", "photo-4", "photo-5",
                                              "photo-6", "photo-7", "photo-8", "photo-9", "photo-10"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").isNotEmpty());
    }

    // 10장 초과는 허용하지 않는다. photo-1~11은 모두 DB에 존재하며, 개수(11)가 거절 원인이다.
    @Test
    void tasteAnalysis_withTooManyPhotoIds_returns400() throws Exception {
        mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["photo-1", "photo-2", "photo-3", "photo-4", "photo-5",
                                              "photo-6", "photo-7", "photo-8", "photo-9", "photo-10", "photo-11"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void tasteAnalysis_withInvalidPhotoIds_returns400() throws Exception {
        mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["no-1", "no-2", "no-3", "no-4", "no-5"]}
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
                                {"photoIds": ["photo-1", "photo-2", "photo-3", "photo-4", "photo-5"]}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String analysisId = body.split("\"analysisId\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/recommendations").param("analysisId", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moodTags").isArray())
                .andExpect(jsonPath("$.spots").isArray());
    }

    // 없거나 만료된 analysisId는 리소스 부재이므로 404다. (docs/api-contract.md 상태 코드)
    @Test
    void recommendations_withInvalidAnalysisId_returns404() throws Exception {
        mockMvc.perform(get("/recommendations").param("analysisId", "invalid-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"))
                .andExpect(jsonPath("$.retryable").value(false));
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
