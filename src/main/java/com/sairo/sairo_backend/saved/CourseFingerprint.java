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
 * 코스의 내용을 하나의 문자열로 요약한다. 중복 저장 판정에 쓴다. (Q-04)
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

    static String of(CourseSnapshot snapshot) {
        String joined = Stream.concat(spotIds(snapshot.day1()), spotIds(snapshot.day2()))
                .sorted()
                .collect(Collectors.joining(","));
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
