package com.sairo.sairo_backend.saved;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 유니크 키의 각 컬럼이 실제로 정체성에 참여하는지 확인한다.
 *
 * <p>{@code region_key}는 API를 거쳐서는 검증할 수 없다. 지역은 장소에서 유도되므로
 * 같은 장소 구성에 다른 지역을 만들 수 없고, 지역이 다르면 지문도 함께 갈린다.
 * 즉 API 테스트만으로는 유니크 인덱스에서 {@code region_key}를 빼도 전부 통과한다.
 * 리포지토리를 직접 불러 그 한 축만 다르게 만든다.
 */
class SavedTripRepositoryTest extends IntegrationTestBase {

    private static final String DEVICE = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String FINGERPRINT = "same-fingerprint";

    @Autowired
    SavedTripRepository savedTripRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String courseId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM saved_trips");
        jdbcTemplate.update("DELETE FROM shared_courses");
        jdbcTemplate.update("DELETE FROM courses");

        courseId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO courses (course_id, course_data) VALUES (?, ?::jsonb)",
                courseId, """
                        {"regionName":"제주도","day1":[],"day2":[]}
                        """);
    }

    // 지역이 다르면 장소 구성이 같아도 별도 저장이다.
    @Test
    void save_withSameFingerprintButDifferentRegion_createsSeparateRows() {
        SavedTrip jeju = save("제주도", FINGERPRINT);
        SavedTrip gangwon = save("강원", FINGERPRINT);

        assertThat(gangwon.savedTripId()).isNotEqualTo(jeju.savedTripId());
        assertThat(rowCount()).isEqualTo(2);
    }

    // 세 축이 모두 같으면 기존 행을 돌려준다.
    @Test
    void save_withIdenticalKey_returnsExistingRow() {
        SavedTrip first = save("제주도", FINGERPRINT);
        SavedTrip second = save("제주도", FINGERPRINT);

        assertThat(second.savedTripId()).isEqualTo(first.savedTripId());
        assertThat(second.createdAt()).isEqualTo(first.createdAt());
        assertThat(rowCount()).isEqualTo(1);
    }

    private SavedTrip save(String regionKey, String fingerprint) {
        return savedTripRepository.save(
                UUID.randomUUID().toString(), DEVICE, courseId, regionKey, fingerprint);
    }

    private Integer rowCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM saved_trips", Integer.class);
    }
}
