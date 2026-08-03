-- CourseCard 카드 필드(region_area, image_url, reason)를 saved_trips에 추가한다.
-- POST /taste-analysis 저장 시점에 함께 기록하며, 목록 조회 시 snapshot 역직렬화 없이 카드를 구성한다.
-- 이전에 저장된 행과 POST /courses 경유 코스는 NULL이다.
ALTER TABLE saved_trips
    ADD COLUMN region_area TEXT,
    ADD COLUMN image_url   TEXT,
    ADD COLUMN reason      TEXT;
