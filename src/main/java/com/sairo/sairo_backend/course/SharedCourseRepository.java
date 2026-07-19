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

    public String save(String courseDataJson, String deviceId) {
        String shareId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        jdbcTemplate.update(
                "INSERT INTO shared_courses (share_id, course_data, device_id) VALUES (?, ?::jsonb, ?)",
                shareId, courseDataJson, deviceId
        );
        return shareId;
    }

    public List<SavedCourseItem> findByDeviceId(String deviceId) {
        return jdbcTemplate.query(
                "SELECT share_id, course_data::text, created_at FROM shared_courses WHERE device_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> new SavedCourseItem(
                        rs.getString("share_id"),
                        rs.getString("course_data"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ),
                deviceId
        );
    }

    public record SavedCourseItem(String shareId, String courseDataJson, java.time.LocalDateTime createdAt) {}

    public Optional<String> findCourseDataById(String shareId) {
        List<String> results = jdbcTemplate.query(
                "SELECT course_data::text FROM shared_courses WHERE share_id = ?",
                (rs, rowNum) -> rs.getString("course_data"),
                shareId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
