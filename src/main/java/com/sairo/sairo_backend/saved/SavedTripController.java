package com.sairo.sairo_backend.saved;

import com.sairo.sairo_backend.common.DeviceId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @Operation(
            summary = "저장 여행지 목록 조회",
            description = """
                    이 기기가 저장한 여행지를 **최근 저장순**으로 한 페이지씩 준다.
                    다른 기기의 저장 항목은 나오지 않는다.

                    페이지는 커서로 넘긴다. 응답의 `nextCursor`를 그대로 다음 요청의 `cursor`에 실으면 된다.
                    `nextCursor`가 `null`이면 마지막 페이지다.
                    커서 문자열은 **해석하지 않는다.** 형식은 예고 없이 바뀔 수 있고,
                    읽을 수 없는 커서는 `INVALID_CURSOR`로 거절한다. 이때는 커서를 버리고 첫 페이지부터 다시 읽는다.

                    항목에는 코스 내용이 들어 있지 않다. `courseId`로 코스를 따로 조회한다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회됨 (저장한 항목이 없으면 빈 목록)"),
            @ApiResponse(responseCode = "400", description = "INVALID_CURSOR — 커서를 읽을 수 없음 / INVALID_REQUEST — size가 범위 밖")
    })
    @GetMapping
    public SavedTripListResponse findPage(
            @DeviceId String deviceId,

            @Parameter(description = "이전 응답의 nextCursor. 첫 페이지에서는 생략한다. 빈 값은 생략과 같다.")
            @RequestParam(required = false) String cursor,

            @Parameter(description = "한 페이지 항목 수", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return savedTripService.findPage(deviceId, cursor, size);
    }
}
