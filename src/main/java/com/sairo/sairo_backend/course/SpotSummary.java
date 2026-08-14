package com.sairo.sairo_backend.course;

import com.sairo.sairo_backend.spot.Spot;
import com.sairo.sairo_backend.spot.SpotText;
import io.swagger.v3.oas.annotations.media.Schema;

public record SpotSummary(
        String spotId,
        String name,
        Double lat,
        Double lng,
        String imageUrl,
        @Schema(nullable = true, description = "운영시간 원문. 줄바꿈은 \\n 하나로 정규화된 자유 텍스트") String operatingHours,
        @Schema(nullable = true, description = "휴무일 원문. 줄바꿈은 \\n 하나로 정규화된 자유 텍스트") String closedDays,
        @Schema(nullable = true, description = "주차 원문. 줄바꿈은 \\n 하나로 정규화된 자유 텍스트") String parking,
        @Schema(nullable = true, description = "문의처 원문. 줄바꿈은 \\n 하나로 정규화된 자유 텍스트") String contact,
        @Schema(nullable = true, description = "태그용 요약: \"상시 개방\" 또는 단일 HH:MM~HH:MM. null이면 요약 불가이므로 원문을 표시한다") String hoursSummary,
        @Schema(nullable = true, description = "태그용 요약: \"연중무휴\" 또는 \"X요일 휴무\". null이면 요약 불가이므로 원문을 표시한다") String closedDaysSummary,
        @Schema(nullable = true, description = "주차 가능 여부. null이면 판정 불가이므로 원문을 표시한다") Boolean parkingAvailable,
        @Schema(nullable = true, description = "원문에서 추출한 첫 전화번호") String contactPhone
) {
    // 요약 필드는 항상 원문에서 파생한다 — 과거 공유 스냅샷(JSONB) 역직렬화 시에도 자동 보완된다
    public SpotSummary {
        operatingHours = SpotText.normalize(operatingHours);
        closedDays = SpotText.normalize(closedDays);
        parking = SpotText.normalize(parking);
        contact = SpotText.normalize(contact);
        hoursSummary = SpotText.hoursSummary(operatingHours);
        closedDaysSummary = SpotText.closedDaysSummary(closedDays);
        parkingAvailable = SpotText.parkingAvailable(parking);
        contactPhone = SpotText.contactPhone(contact);
    }

    static SpotSummary from(Spot spot) {
        return new SpotSummary(
                spot.getSpotId(),
                spot.getName(),
                spot.getLat(),
                spot.getLng(),
                spot.getImageUrl(),
                spot.getOperatingHours(),
                spot.getClosedDays(),
                spot.getParking(),
                spot.getContact(),
                null, null, null, null
        );
    }
}
