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
    -- 중복 판정용 지역 키. CourseSnapshot.regionName, 즉 서버가 spots.region_name에서
    -- 유도한 값이다. 요청 문자열("주" 같은 부분 일치값)이 직접 들어오지 않는다.
    -- 표기가 통일된 값은 아니다 — "경북"과 "경상북도"는 여전히 다른 키다. (ADR 0011)
    region_key         TEXT NOT NULL,
    -- 코스 지문. 장소 ID를 정렬해 이어 붙인 값의 SHA-256이다.
    -- 순서를 넣지 않는 이유는 CourseFingerprint의 주석에 있다.
    course_fingerprint TEXT NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 같은 사용자가 같은 지역의 같은 코스를 두 번 저장할 수 없다.
-- 지역이 같아도 장소 구성이 다르면 별도 저장이다.
CREATE UNIQUE INDEX IF NOT EXISTS saved_trips_identity_idx
    ON saved_trips (device_id, region_key, course_fingerprint);

-- 저장 목록은 최신순 커서 페이지로 읽는다. created_at만으로는 같은 시각 항목의
-- 순서가 흔들리므로 saved_trip_id를 tie-breaker로 함께 넣는다.
CREATE INDEX IF NOT EXISTS saved_trips_device_created_idx
    ON saved_trips (device_id, created_at DESC, saved_trip_id DESC);
