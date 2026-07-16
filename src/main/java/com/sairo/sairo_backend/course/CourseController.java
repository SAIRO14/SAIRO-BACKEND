package com.sairo.sairo_backend.course;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    public CourseResponse buildCourse(@Valid @RequestBody CourseRequest request) {
        return courseService.buildCourse(request);
    }

    @PostMapping("/{courseId}/share")
    @ResponseStatus(HttpStatus.CREATED)
    public ShareCourseResponse shareCourse(
            @PathVariable String courseId,
            @RequestBody ShareCourseRequest request
    ) {
        return courseService.shareCourse(request);
    }

    @GetMapping("/shared/{shareId}")
    public SharedCourseViewResponse getSharedCourse(@PathVariable String shareId) {
        return courseService.getSharedCourse(shareId);
    }
}
