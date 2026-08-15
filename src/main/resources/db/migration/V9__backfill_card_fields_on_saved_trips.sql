-- V6이 추가한 카드 필드(region_area, image_url, reason)를 이미 저장된 행에 채운다.
--
-- V6은 컬럼만 만들고 옛 행을 NULL로 두었다. 그 행들은 저장 목록에서 지역명만 있는 카드로
-- 나와, 같은 화면에서 옛 항목만 눈에 띄게 다른 모양이 된다.
-- V7·V8이 spot_names·spot_image_urls를 백필한 것과 같은 이유다.
--
-- 값은 코스 스냅샷에서 가져온다. 저장 시점의 값이 아니라 코스의 값이지만, 이 세 필드는
-- 코스를 만들 때 정해진 뒤 바뀌지 않으므로 같은 값이다.
--
-- 이미 값이 있는 행은 건드리지 않는다. COALESCE로 기존 값을 우선한다.
-- 저장 항목이 보관하는 것은 최초 저장 시점의 표시값이고, 코스 쪽이 나중에 달라졌더라도
-- 그것으로 덮지 않는다. (ADR 0011)
--
-- POST /courses 경유 코스는 스냅샷에도 이 세 값이 없어 NULL로 남는다. 채울 것이 없는 것이지
-- 빠뜨린 것이 아니다. 이 경로의 코스는 지역 카드를 거치지 않아 소재지도 추천 이유도 만들어지지 않는다.
--
-- course_data가 객체가 아닌 행에서 ->> 는 오류 대신 NULL을 준다. 그 행은 그대로 남는다.
UPDATE saved_trips st
SET region_area = COALESCE(st.region_area, c.course_data ->> 'regionArea'),
    image_url   = COALESCE(st.image_url, c.course_data ->> 'imageUrl'),
    reason      = COALESCE(st.reason, c.course_data ->> 'reason')
FROM courses c
WHERE c.course_id = st.course_id
  AND (st.region_area IS NULL OR st.image_url IS NULL OR st.reason IS NULL);
