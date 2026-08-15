package com.sairo.sairo_backend.saved;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record SavedTripResponse(
        @Schema(description = "저장 항목 ID", example = "1f0a2b3c-4d5e-4f70-8192-a3b4c5d6e7f8")
        String savedTripId,
        @Schema(description = "저장된 코스 ID. 같은 코스를 다시 저장하면 처음 저장할 때의 값이 그대로 나온다.")
        String courseId,
        @Schema(description = "저장된 지역명", example = "제주도")
        String regionName,
        @Schema(description = "지역 소재지. POST /courses 경유 코스나 이전 저장 항목에서는 null이다.", nullable = true)
        String regionArea,
        @Schema(
                description = """
                        코스 대표 이미지 URL 한 장. `POST /taste-analysis` 경유 코스에만 있어 `null`일 수 있다.
                        카드 썸네일에는 `imageUrls`를 쓴다.
                        """,
                nullable = true
        )
        String imageUrl,
        @Schema(description = "추천 이유 문구. null일 수 있다.", nullable = true)
        String reason,
        @Schema(
                description = """
                        코스 대표 장소 이름. 저장 목록 카드에 표시할 만큼만 담는다.
                        코스의 장소 전체가 아니며, 전체는 `courseId`로 코스를 조회해 얻는다.
                        장소가 없는 코스에서는 빈 배열이다. `null`은 아니다.
                        """,
                example = "[\"덕양서원(의성)\", \"연일향교\"]"
        )
        List<String> spotNames,
        @Schema(
                description = """
                        카드 썸네일 URL. 코스 장소의 사진 중 앞부분이다.
                        **`spotNames`와 짝이 아니다.** 사진이 없는 장소는 빠지므로 같은 자리의 이름과
                        다른 장소일 수 있고, 길이도 다를 수 있다.
                        사진이 있는 장소가 없으면 빈 배열이다. `null`은 아니다.
                        """,
                example = "[\"https://tong.visitkorea.or.kr/a.jpg\", \"https://tong.visitkorea.or.kr/b.jpg\"]"
        )
        List<String> imageUrls,
        @Schema(description = "저장 시각")
        LocalDateTime createdAt
) {
    /**
     * 카드에 표시할 장소 이름 수.
     *
     * <p>코스의 장소는 이보다 많다. 목록은 카드가 그리는 만큼만 담고 나머지는 코스 조회로 넘긴다.
     * 값은 추천 결과 카드가 표시하는 장소 수({@code TasteAnalysisService.PREVIEW_SPOT_COUNT})와 같다.
     */
    private static final int CARD_SPOT_NAME_COUNT = 2;

    /** 카드에 겹쳐 표시하는 썸네일 수. 이름 수와 우연히 같을 뿐 같은 값이어야 할 이유는 없다. */
    private static final int CARD_IMAGE_COUNT = 2;

    // regionKey → regionName으로 이름이 바뀐다. 같은 값이다.
    static SavedTripResponse from(SavedTrip savedTrip) {
        return new SavedTripResponse(
                savedTrip.savedTripId(),
                savedTrip.courseId(),
                savedTrip.regionKey(),
                savedTrip.regionArea(),
                savedTrip.imageUrl(),
                savedTrip.reason(),
                savedTrip.spotNames().stream().limit(CARD_SPOT_NAME_COUNT).toList(),
                savedTrip.spotImageUrls().stream().limit(CARD_IMAGE_COUNT).toList(),
                savedTrip.createdAt()
        );
    }
}
