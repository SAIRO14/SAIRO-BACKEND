package com.sairo.sairo_backend.saved;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code saved_trips} 한 행이다. 리포지토리와 서비스 사이에서만 쓰고 응답으로 내보내지 않는다.
 */
record SavedTrip(
        String savedTripId,
        String courseId,
        String regionKey,
        String regionArea,
        String imageUrl,
        String reason,
        /** 코스의 장소 이름 전부. 동선 순서(day1 다음 day2)다. 응답에 몇 개를 담을지는 {@link SavedTripResponse}가 정한다. */
        List<String> spotNames,
        /** 코스 장소의 사진 URL. 사진이 없는 장소는 빠지므로 {@code spotNames}와 길이가 다를 수 있다. */
        List<String> spotImageUrls,
        LocalDateTime createdAt
) {}
