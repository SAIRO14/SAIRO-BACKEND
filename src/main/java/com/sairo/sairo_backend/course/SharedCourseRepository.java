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
     * <p>중복 요청에 안전하다. 이미 공유된 코스면 새 링크를 만들지 않고 기존 share_id를 돌려준다.
     * {@code DO UPDATE}는 {@code DO NOTHING}과 달리 충돌해도 **항상 행을 반환**하므로,
     * 이겼든 졌든 한 문장으로 끝난다. 동시에 도착한 요청은 course_id 유니크 인덱스에서
     * 갈리고, 진 쪽은 이긴 쪽이 커밋될 때까지 기다렸다가 그 share_id를 받는다.
     *
     * <p><b>이 메서드에 트랜잭션을 걸지 않는다.</b> 각 호출이 auto-commit이라
     * share_id 충돌 후 재시도가 가능하다. {@code @Transactional}을 붙이면 첫 충돌에서
     * 트랜잭션이 abort되어 이후 재시도가 전부 실패한다.
     */
    public String save(String courseId, String courseDataJson) {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String shareId = generateShareId();
            try {
                return jdbcTemplate.queryForObject(
                        """
                        INSERT INTO shared_courses (share_id, course_id, course_data)
                        VALUES (?, ?, ?::jsonb)
                        ON CONFLICT (course_id) DO UPDATE SET course_id = EXCLUDED.course_id
                        RETURNING share_id
                        """,
                        String.class,
                        shareId, courseId, courseDataJson
                );
            } catch (DuplicateKeyException e) {
                // share_id 충돌이다. 루프를 돌아 새 ID로 다시 넣는다.
            }
        }
        throw new IllegalStateException("공유 ID를 %d회 시도 안에 만들지 못했습니다.".formatted(MAX_ID_ATTEMPTS));
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
