-- 생성된 코스를 서버에 영속화한다.
-- 이전에는 POST /courses가 courseId만 발급하고 결과를 버려서, 공유와 저장이
-- 코스를 ID로 참조할 방법이 없었다. 그래서 공유 API가 요청 본문을 그대로 믿었다.
--
-- course_data에는 지역과 Day 1·Day 2를 함께 담은 스냅샷을 넣는다.
-- 공유 스냅샷이 지역명을 표시할 수 있어야 하기 때문이다.

CREATE TABLE IF NOT EXISTS courses (
    course_id   TEXT PRIMARY KEY,
    course_data JSONB NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 공유 스냅샷이 어느 코스에서 나왔는지 기록한다.
-- 같은 코스를 두 번 공유해도 새 링크를 만들지 않고 기존 링크를 돌려주는 데도 쓴다.
-- 이 컬럼이 생기기 전에 만들어진 행에는 값이 없으므로 NULL을 허용한다.
-- 코스가 지워져도 공유 스냅샷 자체는 남아야 하므로 ON DELETE SET NULL이다.
ALTER TABLE shared_courses
    ADD COLUMN IF NOT EXISTS course_id TEXT REFERENCES courses (course_id) ON DELETE SET NULL;

-- NULL은 여러 개 허용되므로 기존 행과 충돌하지 않는다.
CREATE UNIQUE INDEX IF NOT EXISTS shared_courses_course_id_idx
    ON shared_courses (course_id);
