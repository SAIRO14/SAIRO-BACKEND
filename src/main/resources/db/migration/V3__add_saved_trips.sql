-- 저장 여행지. 사용자가 저장한 추천 지역과 그 시점의 코스를 가리킨다.
--
-- 코스 내용은 courses.course_data에 이미 있으므로 여기서 다시 복사하지 않는다.
-- 스냅샷을 복사하면 같은 JSON이 두 벌 존재하고 한쪽만 고쳐질 여지가 생긴다. (ADR 0011)

CREATE TABLE IF NOT EXISTS saved_trips (
    saved_trip_id      TEXT PRIMARY KEY,
    -- 익명 사용자 식별자(X-Device-Id). 소유자 조건을 쿼리에 넣기 위해 저장 행이 직접 들고 있다.
    -- courses에는 소유자가 없다.
    device_id          TEXT NOT NULL,
    course_id          TEXT NOT NULL REFERENCES courses (course_id),
    -- 저장 당시의 지역명. CourseSnapshot.regionName을 그대로 넣는다.
    -- 응답의 regionName과 저장 목록의 지역 필터에 쓴다.
    -- **중복 판정에는 쓰지 않는다.** 이유는 아래 유니크 인덱스 주석에 있다.
    region_key         TEXT NOT NULL,
    -- 코스 지문. 장소 ID를 정렬해 이어 붙인 값의 SHA-256이다.
    -- 순서를 넣지 않는 이유는 CourseFingerprint의 주석에 있다.
    course_fingerprint TEXT NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 같은 사용자가 같은 장소 구성을 두 번 저장할 수 없다. 구성이 다르면 별도 저장이다.
--
-- region_key는 넣지 않는다. CourseService.resolveRegionName이 한 코스의 모든 장소가
-- 같은 region_name을 갖도록 강제하므로, 장소 집합이 정해지면 지역명도 함께 정해진다.
-- 즉 지문이 같으면 지역명도 같아서 이 축은 코스를 갈라주지 못한다.
-- 넣으면 spots.region_name을 수정했을 때 같은 코스가 중복으로 쌓이는 일만 한다. (ADR 0011)
CREATE UNIQUE INDEX IF NOT EXISTS saved_trips_identity_idx
    ON saved_trips (device_id, course_fingerprint);

-- 저장 목록은 최신순 커서 페이지로 읽는다. created_at만으로는 같은 시각 항목의
-- 순서가 흔들리므로 saved_trip_id를 tie-breaker로 함께 넣는다.
CREATE INDEX IF NOT EXISTS saved_trips_device_created_idx
    ON saved_trips (device_id, created_at DESC, saved_trip_id DESC);

-- PostgreSQL은 외래키를 거는 쪽에 인덱스를 자동으로 만들지 않는다.
-- 이게 없으면 courses에서 한 행을 지울 때마다 saved_trips 전체를 순차 스캔해 참조를 확인한다.
-- 지금은 코스 삭제 경로가 없지만 정리 정책(Q-02)이 들어오면 배치 삭제가 여기서 느려진다.
CREATE INDEX IF NOT EXISTS saved_trips_course_id_idx
    ON saved_trips (course_id);
