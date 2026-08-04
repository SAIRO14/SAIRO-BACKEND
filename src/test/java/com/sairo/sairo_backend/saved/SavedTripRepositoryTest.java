package com.sairo.sairo_backend.saved;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정체성이 {@code (device_id, course_fingerprint)}라는 것을 리포지토리 층에서 고정한다.
 *
 * <p>{@code region_key}가 판정에 끼지 않는다는 것은 API를 거쳐서는 확인할 수 없다.
 * 지역명이 장소에서 유도되므로 같은 장소 구성에 다른 지역을 만들 수 없기 때문이다.
 * 리포지토리를 직접 불러 그 상태를 만든다.
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
                "INSERT INTO courses (course_id, device_id, course_data) VALUES (?, ?, ?::jsonb)",
                courseId, DEVICE, """
                        {"regionName":"제주도","day1":[],"day2":[]}
                        """);
    }

    /**
     * 지역명이 달라져도 장소 구성이 같으면 같은 저장 항목이다.
     *
     * <p>{@code spots.region_name}을 고친 뒤 같은 장소로 코스를 다시 만든 상황이다.
     * 지역명을 정체성에 넣으면 이때 같은 코스가 중복으로 쌓인다.
     * 남는 {@code region_key}는 <b>최초 저장 시점의 값</b>이다.
     */
    @Test
    void save_withSameFingerprintButDifferentRegion_returnsExistingRow() {
        SavedTrip first = save("경북", FINGERPRINT);
        SavedTrip renamed = save("경상북도", FINGERPRINT);

        assertThat(renamed.savedTripId()).isEqualTo(first.savedTripId());
        assertThat(renamed.regionKey()).isEqualTo("경북");
        assertThat(rowCount()).isEqualTo(1);
    }

    // 장소 구성이 다르면 별도 저장이다.
    @Test
    void save_withDifferentFingerprint_createsSeparateRows() {
        SavedTrip first = save("제주도", FINGERPRINT);
        SavedTrip other = save("제주도", "other-fingerprint");

        assertThat(other.savedTripId()).isNotEqualTo(first.savedTripId());
        assertThat(rowCount()).isEqualTo(2);
    }

    // 두 축이 모두 같으면 기존 행을 돌려준다. created_at도 바뀌지 않는다.
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
                UUID.randomUUID().toString(), DEVICE, courseId, regionKey, fingerprint, null, null, null);
    }

    private Integer rowCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM saved_trips", Integer.class);
    }
}
