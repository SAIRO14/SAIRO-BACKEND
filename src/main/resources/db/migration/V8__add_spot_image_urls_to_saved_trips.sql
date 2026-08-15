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
-- 이미지가 없는 장소는 담지 않는다. 그래서 spot_names와 길이도 순서도 일치하지 않는다.
-- 짝을 맞추면 앞선 장소에 사진이 없을 때 썸네일 자리가 비는데, 카드는 두 장을 요구한다.
ALTER TABLE saved_trips
    ADD COLUMN spot_image_urls TEXT[] NOT NULL DEFAULT '{}';

-- 이미 저장된 행은 코스 스냅샷에서 채운다. 근거는 V7의 백필과 같다.
--
-- 순서는 day1 다음 day2이며 코스의 동선 순서다.
-- jsonb_path_query_array(lax 모드)를 쓰는 이유도 V7과 같다. day1이 배열이 아닌 행이 섞여 있어도
-- 그 행만 빈 배열이 되고 마이그레이션은 계속 진행된다.
--
-- '$.day1[*].imageUrl'은 JSON에 imageUrl 키가 없거나 값이 null인 장소를 건너뛴다.
-- 전자는 경로가 매치되지 않아서, 후자는 아래 jsonb_array_elements_text가 SQL NULL로 주고
-- ARRAY(...)가 그것을 걸러내지 않으므로 WHERE로 명시해 거른다.
UPDATE saved_trips st
SET spot_image_urls = sub.urls
FROM (
    SELECT s.saved_trip_id,
           -- 별칭을 spot_image_url로 둔다. image_url로 두면 saved_trips.image_url과 이름이 겹쳐
           -- WHERE 절이 그쪽으로 해석돼 모든 행이 걸러진다.
           ARRAY(
               SELECT spot_image_url
               FROM jsonb_array_elements_text(
                        jsonb_path_query_array(c.course_data, '$.day1[*].imageUrl')
                            || jsonb_path_query_array(c.course_data, '$.day2[*].imageUrl')
                    ) AS spot_image_url
               WHERE spot_image_url IS NOT NULL
           ) AS urls
    FROM saved_trips s
             JOIN courses c ON c.course_id = s.course_id
) sub
WHERE st.saved_trip_id = sub.saved_trip_id;
