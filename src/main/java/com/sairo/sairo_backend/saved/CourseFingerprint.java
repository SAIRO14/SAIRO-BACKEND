package com.sairo.sairo_backend.saved;

import com.sairo.sairo_backend.course.CourseSnapshot;
import com.sairo.sairo_backend.course.SpotSummary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 코스가 어떤 장소들로 이루어졌는지를 하나의 문자열로 요약한다. 중복 저장 판정에 쓴다. (ADR 0011)
 *
 * <p><b>스냅샷 전체의 해시가 아니다.</b> 장소 ID만 넣으므로 Day 1·Day 2 배치나
 * 장소의 이름·좌표·이미지가 달라도 지문은 같다. 그 차이를 중복 판정에서 무시하겠다는 것이
 * 결정의 내용이다.
 *
 * <p>{@code courseId}로는 중복을 판정할 수 없다. 같은 장소로 {@code POST /courses}를
 * 다시 부르면 매번 새 {@code courseId}가 나오기 때문이다.
 *
 * <p><b>장소의 순서는 넣지 않는다.</b> 순서는 사용자가 고른 것이 아니라
 * {@code CourseService}의 좌표 정렬이 정하는 값이다. 지문에 순서를 넣으면 정렬 방식을
 * 바꾸는 순간 과거에 저장한 코스와 지문이 어긋나 같은 코스가 중복으로 쌓인다.
 * 사용자가 고른 것은 "어느 장소들인가"이므로 그것만 지문으로 삼는다.
 */
final class CourseFingerprint {

    private CourseFingerprint() {
    }

    /**
     * 장소 ID를 정렬해 이어 붙인 값의 SHA-256을 만든다.
     *
     * <p>구분자로 나누지 않고 <b>각 ID 앞에 길이를 붙인다.</b> {@code spots.spot_id}는 형식 제약이 없는
     * {@code TEXT}이고 값이 외부 TourAPI에서 들어오므로, 어떤 문자가 ID 안에 나타나지 않는다고
     * 단정할 수 없다. 구분자가 ID에 섞이면 {@code ["a,b", "c"]}와 {@code ["a", "b,c"]}가 같은 지문이 되어
     * <b>서로 다른 코스가 하나로 합쳐진다.</b> 지문은 정체성 키라 한 번 어긋나면 저장 데이터로 굳는다.
     */
    static String of(CourseSnapshot snapshot) {
        String joined = Stream.concat(spotIds(snapshot.day1()), spotIds(snapshot.day2()))
                .sorted()
                .map(id -> id.length() + ":" + id)
                .collect(Collectors.joining());
        return sha256(joined);
    }

    private static Stream<String> spotIds(List<SpotSummary> spots) {
        return spots == null ? Stream.empty() : spots.stream().map(SpotSummary::spotId);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM이 제공한다. 여기 오면 런타임이 깨진 것이다.
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
