package com.sairo.sairo_backend.saved;

import com.sairo.sairo_backend.common.DeviceId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "저장 여행지", description = "추천 지역과 그 시점 코스의 저장")
@RestController
@RequestMapping("/saved-trips")
@RequiredArgsConstructor
public class SavedTripController {

    private final SavedTripService savedTripService;

    @Operation(
            summary = "저장 여행지 생성",
            description = """
                    `POST /courses`가 저장해둔 코스를 저장 목록에 담는다. 요청은 `courseId`만 받는다.

                    저장 단위는 **추천 지역 + 그 시점의 코스**다. 지역명은 코스 스냅샷에 들어 있는
                    값(서버가 장소에서 유도한 값)을 쓴다.

                    중복 요청에 안전하다. 같은 기기가 **같은 장소 구성**을 다시 저장하면
                    새 항목을 만들지 않고 기존 항목을 그대로 반환한다. 이때도 응답은 201이다.
                    장소가 같으면 `POST /courses`를 다시 불러 받은 새 `courseId`로 저장해도
                    같은 항목으로 본다. 응답의 `courseId`와 `regionName`은 처음 저장할 때의 값이다.

                    장소 구성이 다르면 지역이 같아도 별도 항목으로 저장된다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "저장됨 (이미 저장된 코스면 기존 항목)"),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST — courseId 누락 또는 UUID 형식 아님"),
            @ApiResponse(responseCode = "404", description = "COURSE_NOT_FOUND — 해당 코스가 없거나 다른 기기의 코스임"),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR — 코스 스냅샷 역직렬화 실패")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedTripResponse save(
            @DeviceId String deviceId,
            @Valid @RequestBody SavedTripRequest request
    ) {
        return savedTripService.save(deviceId, request);
    }
}
