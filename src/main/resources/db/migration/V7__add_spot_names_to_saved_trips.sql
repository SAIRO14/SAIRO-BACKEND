-- 저장 목록 카드에 코스의 장소 이름을 표시한다.
--
-- 이전에는 장소 이름이 courses.course_data 안에만 있어서, 저장 목록 화면이 카드마다
-- GET /courses/{courseId}를 따로 불러야 했다. 한 페이지가 20개면 화면 하나에 요청이 21번이다.
--
-- V6의 카드 필드(region_area, image_url, reason)와 같은 방식으로 저장 시점 값을 복사한다.
-- 목록 조회가 courses를 조인해 JSONB를 역직렬화하지 않는다는 성질을 그대로 두기 위해서다.
-- 그 성질이 "항목 하나가 깨져도 목록 전체가 실패하지 않는다"의 근거다.
-- (docs/api-contract.md §6, ADR 0011)
--
-- 카드에 쓰는 것은 앞 2개지만 컬럼에는 코스의 장소를 전부 담는다.
-- 표시 개수는 화면 사정이라 바뀔 수 있고, 그때마다 백필을 다시 돌리지 않기 위해서다.
-- 자르는 일은 응답을 만드는 쪽(SavedTripResponse)이 한다.
--
-- 같은 이유로 이름이 없는 장소도 자리를 비워 담는다(원소 NULL). 이 컬럼은 "장소 i의 이름"이고,
-- 무엇을 보여줄지 고르는 일은 응답 레이어가 한다. spot_image_urls와 인덱스가 맞아야
-- 이름과 사진을 짝지어 표시하는 선택지가 남는다.
ALTER TABLE saved_trips
    ADD COLUMN spot_names TEXT[] NOT NULL DEFAULT '{}';

-- backfill:start
--
-- 이미 저장된 행은 코스 스냅샷에서 채운다.
--
-- V6에서는 옛 행을 NULL로 두었지만 여기서는 백필한다. 이름이 비면 카드에 장소가
-- 한 곳도 표시되지 않아, 옛 저장 항목만 눈에 띄게 다른 모양이 된다.
--
-- 순서는 day1 다음 day2이며 코스의 동선 순서다.
--
-- 원소마다 ->> 'name'을 뽑는다. jsonb_path_query_array('$.day1[*].name')를 쓰면 name 키가
-- 없는 장소에서 결과가 통째로 빠져 인덱스가 밀린다. 여기서는 장소 하나가 원소 하나가 되고,
-- 이름이 없으면 그 자리가 NULL이 된다.
--
-- jsonb_typeof로 배열인지 먼저 본다. day1이 배열이 아닌 행에서 jsonb_array_elements는
-- 예외를 던지고, 그러면 마이그레이션이 멈춰 애플리케이션이 아예 뜨지 않는다.
-- 그런 행은 빈 배열로 두고 넘어간다.
UPDATE saved_trips st
SET spot_names = sub.names
FROM (
    SELECT s.saved_trip_id,
           ARRAY(
               SELECT spot ->> 'name'
               FROM jsonb_array_elements(
                        CASE WHEN jsonb_typeof(c.course_data -> 'day1') = 'array'
                             THEN c.course_data -> 'day1' ELSE '[]'::jsonb END
                            || CASE WHEN jsonb_typeof(c.course_data -> 'day2') = 'array'
                                    THEN c.course_data -> 'day2' ELSE '[]'::jsonb END
                    ) AS spot
           ) AS names
    FROM saved_trips s
             JOIN courses c ON c.course_id = s.course_id
) sub
WHERE st.saved_trip_id = sub.saved_trip_id;
