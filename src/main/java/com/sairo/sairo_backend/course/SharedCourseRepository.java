package com.sairo.sairo_backend.course;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SharedCourseRepository {

    private final JdbcTemplate jdbcTemplate;

    public String save(String courseDataJson) {
        String shareId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        jdbcTemplate.update(
                "INSERT INTO shared_courses (share_id, course_data) VALUES (?, ?::jsonb)",
                shareId, courseDataJson
        );
        return shareId;
    }

    public Optional<String> findCourseDataById(String shareId) {
        List<String> results = jdbcTemplate.query(
                "SELECT course_data::text FROM shared_courses WHERE share_id = ?",
                (rs, rowNum) -> rs.getString("course_data"),
                shareId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
