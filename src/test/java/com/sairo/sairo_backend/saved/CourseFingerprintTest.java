package com.sairo.sairo_backend.saved;

import com.sairo.sairo_backend.course.CourseSnapshot;
import com.sairo.sairo_backend.course.SpotSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0011이 정한 지문의 성질을 고정한다.
 *
 * <p>API를 거쳐서는 검증할 수 없다. 코스 정렬이 결정론적이라 같은 장소 집합이면
 * 스냅샷 자체가 애초에 동일하게 나오기 때문이다. 배치가 다른 스냅샷을 직접 만들어
 * 비교해야 "무엇을 같은 코스로 보는가"가 실제로 고정된다.
 */
class CourseFingerprintTest {

    private static SpotSummary spot(String id, String name) {
        return new SpotSummary(id, name, 33.4, 126.5, "https://example.com/" + id + ".jpg");
    }

    // 순서는 사용자가 고른 것이 아니라 좌표 정렬이 정하는 값이다. 지문에 넣지 않는다.
    @Test
    void of_withSameSpotsInDifferentOrder_producesSameFingerprint() {
        CourseSnapshot forward = new CourseSnapshot("제주도",
                List.of(spot("a", "장소A"), spot("b", "장소B")),
                List.of(spot("c", "장소C"), spot("d", "장소D")));
        CourseSnapshot reversed = new CourseSnapshot("제주도",
                List.of(spot("d", "장소D"), spot("c", "장소C")),
                List.of(spot("b", "장소B"), spot("a", "장소A")));

        assertThat(CourseFingerprint.of(reversed)).isEqualTo(CourseFingerprint.of(forward));
    }

    // Day 분할도 정렬 결과에서 나오는 값이므로 정체성에 넣지 않는다.
    @Test
    void of_withSameSpotsInDifferentDaySplit_producesSameFingerprint() {
        CourseSnapshot even = new CourseSnapshot("제주도",
                List.of(spot("a", "장소A"), spot("b", "장소B")),
                List.of(spot("c", "장소C"), spot("d", "장소D")));
        CourseSnapshot skewed = new CourseSnapshot("제주도",
                List.of(spot("a", "장소A")),
                List.of(spot("b", "장소B"), spot("c", "장소C"), spot("d", "장소D")));

        assertThat(CourseFingerprint.of(skewed)).isEqualTo(CourseFingerprint.of(even));
    }

    /**
     * 장소의 표시 정보는 지문에 들어가지 않는다.
     *
     * <p>이름·좌표·이미지는 코스를 만든 시점의 {@code spots} 값이 복사된 것이라 마스터가 바뀌면 달라진다.
     * 그 차이까지 지문에 넣으면 장소 정보가 수정될 때마다 같은 코스가 또 저장된다.
     */
    @Test
    void of_ignoresSpotDisplayFields() {
        CourseSnapshot before = new CourseSnapshot("제주도",
                List.of(new SpotSummary("a", "옛 이름", 33.4, 126.5, "https://example.com/old.jpg")),
                List.of(spot("b", "장소B")));
        CourseSnapshot after = new CourseSnapshot("제주도",
                List.of(new SpotSummary("a", "새 이름", 35.1, 129.0, "https://example.com/new.jpg")),
                List.of(spot("b", "장소B")));

        assertThat(CourseFingerprint.of(after)).isEqualTo(CourseFingerprint.of(before));
    }

    // 장소 구성이 다르면 다른 코스다. 위 검증들이 공허하게 통과하지 않는지 함께 확인한다.
    @Test
    void of_withDifferentSpots_producesDifferentFingerprint() {
        CourseSnapshot four = new CourseSnapshot("제주도",
                List.of(spot("a", "장소A"), spot("b", "장소B")),
                List.of(spot("c", "장소C"), spot("d", "장소D")));
        CourseSnapshot three = new CourseSnapshot("제주도",
                List.of(spot("a", "장소A"), spot("b", "장소B")),
                List.of(spot("c", "장소C")));

        assertThat(CourseFingerprint.of(three)).isNotEqualTo(CourseFingerprint.of(four));
    }

    // 지역은 지문이 아니라 region_key 컬럼이 맡는다. 지문만 보면 지역이 달라도 같다.
    @Test
    void of_ignoresRegionName() {
        CourseSnapshot jeju = new CourseSnapshot("제주도",
                List.of(spot("a", "장소A")), List.of(spot("b", "장소B")));
        CourseSnapshot gangwon = new CourseSnapshot("강원",
                List.of(spot("a", "장소A")), List.of(spot("b", "장소B")));

        assertThat(CourseFingerprint.of(gangwon)).isEqualTo(CourseFingerprint.of(jeju));
    }
}
