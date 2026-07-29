package com.sairo.sairo_backend.saved;

import java.time.LocalDateTime;

/**
 * {@code saved_trips} 한 행이다. 리포지토리와 서비스 사이에서만 쓰고 응답으로 내보내지 않는다.
 */
record SavedTrip(
        String savedTripId,
        String courseId,
        String regionKey,
        LocalDateTime createdAt
) {}
