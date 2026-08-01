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
        jdbcTemplate.update("DELETE FROM saved_trips");
        jdbcTemplate.update("DELETE FROM courses");
        jdbcTemplate.update("DELETE FROM spots");
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

    // 중복 ID를 제거하면 5장 미만이 되는 경우 — @Size는 원시 개수만 보므로 서비스에서 추가 검증한다.
    @Test
    void tasteAnalysis_withDuplicatePhotoIds_returns400() throws Exception {
        mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["photo-1", "photo-1", "photo-1", "photo-1", "photo-1"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHOTO_SELECTION"));
    }

    // 유효한 ID가 5장 미만인 경우 — 일부가 DB에 없어도 유효 장수 기준으로 실패한다.
    @Test
    void tasteAnalysis_withTooFewValidPhotoIds_returns400() throws Exception {
        mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["photo-1", "photo-2", "no-3", "no-4", "no-5"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHOTO_SELECTION"));
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
        String analysisId = extractAnalysisId(
                mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["photo-1", "photo-2", "photo-3", "photo-4", "photo-5"]}
                                """))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        mockMvc.perform(get("/recommendations").param("analysisId", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moodTags").isArray())
                .andExpect(jsonPath("$.regions").isArray());
    }

    // 5개 지역 모두 장소를 넣어 top 3 선정이 비결정적이어도 카드가 만들어지도록 한다.
    @Test
    void recommendations_withSpotsInDb_returnsRegionCardStructure() throws Exception {
        insertSpot("spot-jeju-1", "한라산", "제주", "http://jeju1.jpg");
        insertSpot("spot-jeju-2", "성산일출봉", "제주", "http://jeju2.jpg");
        insertSpot("spot-gangwon-1", "설악산", "강원", "http://gw1.jpg");
        insertSpot("spot-gangwon-2", "남이섬", "강원", "http://gw2.jpg");
        insertSpot("spot-gyeongju-1", "불국사", "경주", "http://gj1.jpg");
        insertSpot("spot-gyeongju-2", "첨성대", "경주", "http://gj2.jpg");
        insertSpot("spot-jeonbuk-1", "전주한옥마을", "전북", "http://jb1.jpg");
        insertSpot("spot-jeonbuk-2", "마이산", "전북", "http://jb2.jpg");
        insertSpot("spot-chungnam-1", "서해안", "충남", "http://cn1.jpg");
        insertSpot("spot-chungnam-2", "태안", "충남", "http://cn2.jpg");

        String analysisId = extractAnalysisId(
                mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["photo-1", "photo-2", "photo-3", "photo-4", "photo-5"]}
                                """))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        mockMvc.perform(get("/recommendations").param("analysisId", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regions[0].regionId").isNotEmpty())
                .andExpect(jsonPath("$.regions[0].regionName").isNotEmpty())
                .andExpect(jsonPath("$.regions[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.regions[0].saved").value(false))
                .andExpect(jsonPath("$.regions[0].previewSpots[0].spotId").isNotEmpty())
                .andExpect(jsonPath("$.regions[0].previewSpots[0].name").isNotEmpty());
    }

    // 기기가 저장한 지역이 추천 카드에 포함되면 saved=true여야 한다.
    // 사진을 제주도로만 채워 top 지역이 제주도로 고정되도록 한다.
    @Test
    void recommendations_withSavedRegion_returnsSavedTrue() throws Exception {
        jdbcTemplate.update("DELETE FROM photos");
        for (int i = 1; i <= 5; i++) {
            insertPhoto("p" + i, "제주" + i, "http://p" + i + ".jpg", "제주도", "바다,힐링");
        }
        insertSpot("j1", "한라산", "제주도", "http://j1.jpg");
        insertSpot("j2", "성산일출봉", "제주도", "http://j2.jpg");

        String deviceId = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11";

        String analysisId = extractAnalysisId(
                mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["p1","p2","p3","p4","p5"]}
                                """))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        String courseBody = mockMvc.perform(post("/courses")
                        .header("X-Device-Id", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "제주도", "spotIds": ["j1", "j2"]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = courseBody.split("\"courseId\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/saved-trips")
                        .header("X-Device-Id", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\": \"" + courseId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/recommendations")
                        .header("X-Device-Id", deviceId)
                        .param("analysisId", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regions[0].regionName").value("제주도"))
                .andExpect(jsonPath("$.regions[0].saved").value(true));
    }

    // 사진 location("제주")과 spot region_name("제주도")이 다를 때 saved=true가 올바르게 반환되어야 한다.
    // ILIKE 부분 일치로 검색어≠canonical인 상황에서 saved 조회가 canonical 기준으로 동작하는지 검증한다.
    @Test
    void recommendations_withSavedRegion_searchTermDiffersFromCanonical_returnsSavedTrue() throws Exception {
        jdbcTemplate.update("DELETE FROM photos");
        for (int i = 1; i <= 5; i++) {
            insertPhoto("pq" + i, "제주" + i, "http://pq" + i + ".jpg", "제주", "바다,힐링");
        }
        insertSpot("jq1", "한라산", "제주도", "http://jq1.jpg");
        insertSpot("jq2", "성산일출봉", "제주도", "http://jq2.jpg");

        String deviceId = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11";

        String analysisId = extractAnalysisId(
                mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["pq1","pq2","pq3","pq4","pq5"]}
                                """))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        // 코스 생성: regionName "제주", spot region_name "제주도" — "제주도".contains("제주") 검증 통과
        String courseBody = mockMvc.perform(post("/courses")
                        .header("X-Device-Id", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionName": "제주", "spotIds": ["jq1", "jq2"]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = courseBody.split("\"courseId\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/saved-trips")
                        .header("X-Device-Id", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\": \"" + courseId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/recommendations")
                        .header("X-Device-Id", deviceId)
                        .param("analysisId", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regions[0].regionName").value("제주"))
                .andExpect(jsonPath("$.regions[0].saved").value(true));
    }

    // 없거나 만료된 analysisId는 리소스 부재이므로 404다. (docs/api-contract.md 상태 코드)
    @Test
    void recommendations_withInvalidAnalysisId_returns404() throws Exception {
        mockMvc.perform(get("/recommendations").param("analysisId", "invalid-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    private String extractAnalysisId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return body.split("\"analysisId\":\"")[1].split("\"")[0];
    }

    private void insertSpot(String spotId, String name, String regionName, String imageUrl) {
        jdbcTemplate.update(
                "INSERT INTO spots (spot_id, name, region_name, image_url) VALUES (?, ?, ?, ?)",
                spotId, name, regionName, imageUrl
        );
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
