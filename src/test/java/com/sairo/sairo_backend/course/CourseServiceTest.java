package com.sairo.sairo_backend.course;

import com.sairo.sairo_backend.common.BusinessException;
import com.sairo.sairo_backend.common.ErrorCode;
import com.sairo.sairo_backend.saved.SavedTripService;
import com.sairo.sairo_backend.spot.SpotRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 컨테이너 없이 오류 변환만 검증한다. DB로는 재현하기 어려운 경로다.
 */
class CourseServiceTest {

    private static final String DEVICE = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

    /**
     * share_id 재시도가 소진되면 500 SHARE_CREATION_FAILED로 나가야 한다.
     *
     * <p>리포지토리는 기술 예외({@code IllegalStateException})를 던지고 서비스가 오류 코드를
     * 붙인다. 이 변환이 빠지면 전역 핸들러의 마지막 방어선에 걸려 Swagger에 문서화한
     * SHARE_CREATION_FAILED가 아니라 INTERNAL_ERROR로 나간다.
     *
     * <p>share_id를 5회 연속 충돌시키는 건 실제 DB로는 재현할 수 없어 여기서 확인한다.
     */
    @Test
    void shareCourse_whenShareIdAttemptsExhausted_throwsShareCreationFailed() {
        CourseRepository courseRepository = mock(CourseRepository.class);
        SharedCourseRepository sharedCourseRepository = mock(SharedCourseRepository.class);

        when(courseRepository.findCourseDataByIdAndDeviceId("course-1", DEVICE))
                .thenReturn(Optional.of("{\"regionName\":\"제주도\",\"day1\":[],\"day2\":[]}"));
        when(sharedCourseRepository.save(any(), any()))
                .thenThrow(new IllegalStateException("공유 ID를 5회 시도 안에 만들지 못했습니다."));

        CourseService service = new CourseService(
                mock(SpotRepository.class), courseRepository, sharedCourseRepository,
                mock(SavedTripService.class), new ObjectMapper());

        assertThatThrownBy(() -> service.shareCourse(DEVICE, "course-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SHARE_CREATION_FAILED));
    }

    // 코스가 없거나 남의 코스면 공유 실패가 아니라 404다. 위 catch가 이걸 삼키면 안 된다.
    @Test
    void shareCourse_withUnknownCourseId_throwsCourseNotFound() {
        CourseRepository courseRepository = mock(CourseRepository.class);
        when(courseRepository.findCourseDataByIdAndDeviceId("nope", DEVICE)).thenReturn(Optional.empty());

        CourseService service = new CourseService(
                mock(SpotRepository.class), courseRepository, mock(SharedCourseRepository.class),
                mock(SavedTripService.class), new ObjectMapper());

        assertThatThrownBy(() -> service.shareCourse(DEVICE, "nope"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COURSE_NOT_FOUND));
    }
}
