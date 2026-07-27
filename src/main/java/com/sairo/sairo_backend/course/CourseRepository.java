package com.sairo.sairo_backend.course;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 코스 스냅샷 저장소. JSONB 컬럼을 다루므로 JPA 대신 JdbcTemplate을 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class CourseRepository {

    private final JdbcTemplate jdbcTemplate;

    public void save(String courseId, String courseDataJson) {
        jdbcTemplate.update(
                "INSERT INTO courses (course_id, course_data) VALUES (?, ?::jsonb)",
                courseId, courseDataJson
        );
    }

    public Optional<String> findCourseDataById(String courseId) {
        List<String> results = jdbcTemplate.query(
                "SELECT course_data::text FROM courses WHERE course_id = ?",
                (rs, rowNum) -> rs.getString("course_data"),
                courseId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
