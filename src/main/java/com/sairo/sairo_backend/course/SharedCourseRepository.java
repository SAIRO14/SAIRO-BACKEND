package com.sairo.sairo_backend.course;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SharedCourseRepository {

    /** share_id가 10자라 규모가 커지면 충돌할 수 있다. 충돌하면 새 ID로 다시 시도한다. */
    private static final int MAX_ID_ATTEMPTS = 5;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 코스의 공유 스냅샷을 만든다.
     *
     * <p>중복 요청에 안전하다. 이미 공유된 코스면 새 링크를 만들지 않고 기존 share_id를
     * 돌려준다. 동시에 도착한 요청은 course_id 유니크 인덱스에서 갈리므로,
     * 진 쪽은 이긴 쪽이 넣은 행을 다시 읽는다.
     */
    public String save(String courseId, String courseDataJson) {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String shareId = generateShareId();
            try {
                List<String> inserted = jdbcTemplate.query(
                        """
                        INSERT INTO shared_courses (share_id, course_id, course_data)
                        VALUES (?, ?, ?::jsonb)
                        ON CONFLICT (course_id) DO NOTHING
                        RETURNING share_id
                        """,
                        (rs, rowNum) -> rs.getString("share_id"),
                        shareId, courseId, courseDataJson
                );
                if (!inserted.isEmpty()) {
                    return inserted.get(0);
                }
                // course_id가 이미 있다. 그 코스의 기존 공유 링크를 그대로 쓴다.
                Optional<String> existing = findShareIdByCourseId(courseId);
                if (existing.isPresent()) {
                    return existing.get();
                }
                // 여기까지 오면 그 사이에 코스가 지워져 course_id가 NULL이 된 경우다. 다시 시도한다.
            } catch (DuplicateKeyException e) {
                // share_id 충돌이다. 루프를 돌아 새 ID로 다시 넣는다.
            }
        }
        throw new IllegalStateException("공유 ID를 %d회 시도 안에 만들지 못했습니다.".formatted(MAX_ID_ATTEMPTS));
    }

    public Optional<String> findShareIdByCourseId(String courseId) {
        List<String> results = jdbcTemplate.query(
                "SELECT share_id FROM shared_courses WHERE course_id = ?",
                (rs, rowNum) -> rs.getString("share_id"),
                courseId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<String> findCourseDataById(String shareId) {
        List<String> results = jdbcTemplate.query(
                "SELECT course_data::text FROM shared_courses WHERE share_id = ?",
                (rs, rowNum) -> rs.getString("course_data"),
                shareId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    private String generateShareId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
