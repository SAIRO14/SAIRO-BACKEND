-- 저장 목록 카드가 썸네일을 두 장 겹쳐 표시한다.
--
-- V7의 spot_names와 같은 이유이자 같은 방식이다. 장소 사진이 courses.course_data 안에만
-- 있으면 목록 화면이 카드마다 GET /courses/{courseId}를 불러야 한다.
-- (docs/api-contract.md §6, ADR 0011)
--
-- image_url 컬럼과 별개다. image_url은 코스 대표 이미지 한 장이고
-- POST /taste-analysis 경유 코스에만 있다. 이 컬럼은 코스에 속한 장소들의 사진이라
-- POST /courses 경유 코스에도 값이 있다.
--
-- spot_names와 인덱스가 맞는다. i번째 원소는 둘 다 i번째 장소의 것이고, 사진이 없는 장소는
-- 빼는 대신 NULL로 남긴다. 빼버리면 몇 번째 장소의 사진인지가 사라져 이름과 짝지어 표시하는
-- 선택지가 없어진다. 무엇을 보여줄지 고르는 일은 응답 레이어(SavedTripResponse)가 한다.
ALTER TABLE saved_trips
    ADD COLUMN spot_image_urls TEXT[] NOT NULL DEFAULT '{}';

-- backfill:start
--
-- 이미 저장된 행은 코스 스냅샷에서 채운다. 근거와 방식은 V7의 백필과 같다.
-- 원소마다 ->> 'imageUrl'을 뽑아 장소 하나가 원소 하나가 되게 하고,
-- 배열이 아닌 day는 jsonb_typeof로 걸러 마이그레이션이 멈추지 않게 한다.
UPDATE saved_trips st
SET spot_image_urls = sub.urls
FROM (
    SELECT s.saved_trip_id,
           ARRAY(
               SELECT spot ->> 'imageUrl'
               FROM jsonb_array_elements(
                        CASE WHEN jsonb_typeof(c.course_data -> 'day1') = 'array'
                             THEN c.course_data -> 'day1' ELSE '[]'::jsonb END
                            || CASE WHEN jsonb_typeof(c.course_data -> 'day2') = 'array'
                                    THEN c.course_data -> 'day2' ELSE '[]'::jsonb END
                    ) AS spot
           ) AS urls
    FROM saved_trips s
             JOIN courses c ON c.course_id = s.course_id
) sub
WHERE st.saved_trip_id = sub.saved_trip_id;
