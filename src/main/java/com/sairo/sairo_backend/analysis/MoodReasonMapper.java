package com.sairo.sairo_backend.analysis;

import java.util.List;
import java.util.Map;
import java.util.Objects;

class MoodReasonMapper {

    private static final Map<String, String> REASON_MAP = Map.of(
            "자연", "자연에서의 고요한 힐링",
            "바다", "바다에서의 여유로운 쉼",
            "역사", "역사 속 고즈넉한 감성",
            "도시", "도시에서 즐기는 감성 여행",
            "힐링", "일상을 벗어난 조용한 힐링",
            "계절", "계절의 빛을 담은 감성 여행",
            "체험", "오감으로 즐기는 생생한 체험"
    );

    private static final String FALLBACK = "당신의 취향을 담은 여행";

    static String from(List<String> moodTags) {
        return moodTags.stream()
                .map(REASON_MAP::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(FALLBACK);
    }

    private MoodReasonMapper() {}
}
