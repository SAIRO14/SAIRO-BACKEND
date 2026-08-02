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
    // area_name을 넣어 regionArea가 응답에 포함되는지 함께 검증한다.
    @Test
    void recommendations_withSpotsInDb_returnsRegionCardStructure() throws Exception {
        insertSpot("spot-jeju-1", "한라산", "제주도", "제주 제주시", "http://jeju1.jpg", 33.36, 126.53);
        insertSpot("spot-jeju-2", "성산일출봉", "제주도", "제주 서귀포", "http://jeju2.jpg", 33.46, 126.94);
        insertSpot("spot-gangwon-1", "설악산", "강원도", "강원 속초", "http://gw1.jpg", 38.12, 128.47);
        insertSpot("spot-gangwon-2", "남이섬", "강원도", "강원 춘천", "http://gw2.jpg", 37.79, 127.52);
        insertSpot("spot-gyeongju-1", "불국사", "경주", "경북 경주", "http://gj1.jpg", 35.79, 129.33);
        insertSpot("spot-gyeongju-2", "첨성대", "경주", "경북 경주", "http://gj2.jpg", 35.84, 129.22);
        insertSpot("spot-jeonbuk-1", "전주한옥마을", "전북", "전북 전주", "http://jb1.jpg", 35.82, 127.15);
        insertSpot("spot-jeonbuk-2", "마이산", "전북", "전북 진안", "http://jb2.jpg", 35.74, 127.39);
        insertSpot("spot-chungnam-1", "서해안", "충남", "충남 태안", "http://cn1.jpg", 36.55, 126.60);
        insertSpot("spot-chungnam-2", "태안", "충남", "충남 태안", "http://cn2.jpg", 36.74, 126.30);

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
                .andExpect(jsonPath("$.regions[0].regionArea").isNotEmpty())
                .andExpect(jsonPath("$.regions[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.regions[0].saved").value(false))
                .andExpect(jsonPath("$.regions[0].previewSpots[0].spotId").isNotEmpty())
                .andExpect(jsonPath("$.regions[0].previewSpots[0].name").isNotEmpty());
    }

    // ILIKE 부분 일치로 region_name이 "제주"/"제주도"로 혼재하는 6개 스팟(>SPOTS_PER_REGION)이 있을 때
    // 클러스터 샘플링 후 셔플 결과와 무관하게 카드가 항상 반환되어야 한다.
    // 수정 전: 셔플 후 get(0)이 "제주도" 스팟이 되면 consistent가 1개 → 카드 소멸
    // 수정 후: 풀 구성 시 center의 region_name("제주")으로 미리 정규화 → 카드 항상 존재
    @Test
    void recommendations_withMixedRegionNamesInCluster_alwaysReturnsCard() throws Exception {
        jdbcTemplate.update("DELETE FROM photos");
        for (int i = 1; i <= 5; i++) {
            insertPhoto("px" + i, "제주" + i, "http://px" + i + ".jpg", "제주", "바다");
        }
        // 5개("제주") + 1개("제주도") = 6 > SPOTS_PER_REGION(5) → 클러스터링 동작
        // 모두 한라산 기준 40km 이내. 밀도 최고 중심은 sj1(제주)이 선택된다.
        insertSpot("sj1", "한라산", "제주", "http://s1.jpg", 33.36, 126.53);
        insertSpot("sj2", "중문", "제주", "http://s2.jpg", 33.25, 126.41);
        insertSpot("sj3", "협재", "제주", "http://s3.jpg", 33.39, 126.24);
        insertSpot("sj4", "서귀포", "제주", "http://s4.jpg", 33.25, 126.56);
        insertSpot("sj5", "제주시", "제주", "http://s5.jpg", 33.50, 126.53);
        insertSpot("sjd1", "성산일출봉", "제주도", "http://sd1.jpg", 33.46, 126.94);

        String analysisId = extractAnalysisId(
                mockMvc.perform(post("/taste-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": ["px1","px2","px3","px4","px5"]}
                                """))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        mockMvc.perform(get("/recommendations").param("analysisId", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regions[0].regionName").value("제주"))
                .andExpect(jsonPath("$.regions[0].previewSpots").isArray());
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

    private void insertSpot(String spotId, String name, String regionName, String imageUrl, double lat, double lng) {
        insertSpot(spotId, name, regionName, null, imageUrl, lat, lng);
    }

    private void insertSpot(String spotId, String name, String regionName, String areaName, String imageUrl, double lat, double lng) {
        jdbcTemplate.update(
                "INSERT INTO spots (spot_id, name, region_name, area_name, image_url, lat, lng) VALUES (?, ?, ?, ?, ?, ?, ?)",
                spotId, name, regionName, areaName, imageUrl, lat, lng
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
