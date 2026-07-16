package com.sairo.sairo_backend.place;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlaceApiTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    TourApiClient tourApiClient;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM spots");
        // 정보 완전한 spot
        jdbcTemplate.update(
                "INSERT INTO spots (spot_id, name, region_name, lat, lng, operating_hours, closed_days, parking, contact) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "spot-full", "완전한 장소", "제주", 33.4, 126.5,
                "09:00~18:00", "연중무휴", "가능", "064-000-0000"
        );
        // 정보 없는 spot (info_incomplete 테스트용)
        jdbcTemplate.update(
                "INSERT INTO spots (spot_id, name, region_name, lat, lng) VALUES (?, ?, ?, ?, ?)",
                "spot-empty", "정보없는 장소", "제주", 33.5, 126.6
        );
    }

    @Test
    void getPlace_withCompleteInfo_returns200WithoutTourApi() throws Exception {
        mockMvc.perform(get("/places/spot-full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spotId").value("spot-full"))
                .andExpect(jsonPath("$.name").value("완전한 장소"))
                .andExpect(jsonPath("$.infoIncomplete").value(false));
    }

    @Test
    void getPlace_withMissingInfo_returnsInfoIncompleteTrue_whenTourApiFails() throws Exception {
        when(tourApiClient.fetchDetail(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/places/spot-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spotId").value("spot-empty"))
                .andExpect(jsonPath("$.infoIncomplete").value(true));
    }

    @Test
    void getPlace_withMissingInfo_mergesTourApiData() throws Exception {
        when(tourApiClient.fetchDetail("spot-empty")).thenReturn(
                Optional.of(new TourApiClient.TourDetail("10:00~17:00", "매주 월요일", "불가", "064-111-1111"))
        );

        mockMvc.perform(get("/places/spot-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatingHours").value("10:00~17:00"))
                .andExpect(jsonPath("$.infoIncomplete").value(false));
    }

    @Test
    void getPlace_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/places/not-exist"))
                .andExpect(status().isNotFound());
    }
}
