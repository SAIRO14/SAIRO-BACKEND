package com.sairo.sairo_backend.device;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DeviceRepository {

    private final JdbcTemplate jdbcTemplate;

    public void upsert(String deviceId) {
        jdbcTemplate.update(
                "INSERT INTO devices (device_id) VALUES (?) ON CONFLICT DO NOTHING",
                deviceId
        );
    }

    public void saveAnalysisHistory(String deviceId, String analysisId, String moodTags) {
        jdbcTemplate.update(
                "INSERT INTO analysis_history (device_id, analysis_id, mood_tags) VALUES (?, ?, ?)",
                deviceId, analysisId, moodTags
        );
    }

    public List<AnalysisHistoryItem> findAnalysisHistory(String deviceId) {
        return jdbcTemplate.query(
                "SELECT analysis_id, mood_tags, created_at FROM analysis_history WHERE device_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> new AnalysisHistoryItem(
                        rs.getString("analysis_id"),
                        rs.getString("mood_tags"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ),
                deviceId
        );
    }

    public record AnalysisHistoryItem(String analysisId, String moodTags, java.time.LocalDateTime createdAt) {}
}
