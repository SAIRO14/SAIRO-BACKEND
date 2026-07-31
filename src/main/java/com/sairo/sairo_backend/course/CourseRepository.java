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

    public void save(String courseId, String deviceId, String courseDataJson) {
        jdbcTemplate.update(
                "INSERT INTO courses (course_id, device_id, course_data) VALUES (?, ?, ?::jsonb)",
                courseId, deviceId, courseDataJson
        );
    }

    /**
     * 소유자가 만든 코스만 읽는다.
     *
     * <p><b>소유자 조건을 쿼리에 넣는다.</b> 애플리케이션 코드에서 걸러내지 않는다.
     * (docs/api-contract.md §4, ADR 0012)
     *
     * <p>소유자가 없는 옛 행({@code device_id IS NULL})은 어떤 기기로도 걸리지 않는다.
     * 코스 소유자 도입 이전에 만들어진 행이며 출시 전이라 사실상 없다.
     */
    public Optional<String> findCourseDataByIdAndDeviceId(String courseId, String deviceId) {
        List<String> results = jdbcTemplate.query(
                "SELECT course_data::text FROM courses WHERE course_id = ? AND device_id = ?",
                (rs, rowNum) -> rs.getString("course_data"),
                courseId, deviceId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
