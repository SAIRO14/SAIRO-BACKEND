# 데이터 모델

스키마 정본은 `src/main/resources/db/migration/` 아래 마이그레이션 파일이다.
이 문서는 마이그레이션만 봐서는 알기 어려운 **각 컬럼의 의미와 배경**을 설명한다.

컬럼을 추가하거나 바꿀 때는 새 `V<번호>__<설명>.sql`을 만든다.
규칙은 [AGENTS.md](../AGENTS.md) 8절에 있다.

## 전체 구조

```text
photos          사진 풀과 임베딩. 추천의 입력.
spots           관광 장소 마스터. 코스의 재료.
courses         생성된 코스 스냅샷. 공유와 저장이 참조한다.
shared_courses  공유 시점의 코스 스냅샷.
saved_trips     사용자가 저장한 지역과 코스. 내용은 courses를 참조한다.
```

**스냅샷은 참조가 아니라 복사본으로 보관한다.** `courses`와 `shared_courses`는 장소 목록을
JSONB로 복사해 담고 `spots`를 참조하지 않는다. 공유된 코스는 원본 장소 정보가 나중에 바뀌어도
공유 당시 모습을 그대로 재현해야 하기 때문이다.

**저장 여행지는 예외로 복사하지 않는다.** `saved_trips`는 `course_id`로 내용을 가져온다.
"저장 당시 모습 그대로"가 요구사항인 공유와 달리, 저장은 사용자가 다시 열어보는 목록이라
같은 JSON을 두 벌 두는 비용이 이득보다 크다. ([ADR 0011](./decisions/0011-saved-trip-identity.md))

외래키는 둘이다. `shared_courses.course_id`는 내용을 가져오기 위한 참조가 아니라
**어느 코스에서 나온 공유인지 남기는 용도**이고 ([ADR 0010](./decisions/0010-course-persistence.md)),
`saved_trips.course_id`는 **내용을 가져오는 참조**다.

## photos

사진 풀이자 추천의 기준 데이터다. `scripts/import_data.py`로 적재한다.

| 컬럼 | 타입 | 의미 |
|---|---|---|
| `id` | TEXT PK | 사진 식별자 |
| `title` | TEXT NOT NULL | 원본 데이터의 제목. **API로 노출하지 않는다.** |
| `image_url` | TEXT NOT NULL | 사진 URL. 선택 화면에 표시된다. |
| `location` | TEXT | `"경상북도 안동"` 형태의 지역 문자열. 지역 추천의 집계 기준. |
| `keywords` | TEXT | 쉼표로 구분된 키워드. 분위기 태그의 원천. |
| `embedding` | vector(512) NOT NULL | 512차원 CLIP 임베딩. |

> [!IMPORTANT]
> **`title`, `location`, `keywords`, `embedding`은 사진 풀 API 응답에 넣지 않는다.**
> 사용자가 장소를 보고 고르면 제품의 전제가 무너진다.
> 사진 풀 응답은 `id`와 `imageUrl`만 담는다.

### embedding 다루기

`embedding`은 **JPA 엔티티에 매핑하지 않는다.** `Photo` 엔티티에는 이 필드가 없고,
벡터 조회와 유사도 검색은 `PhotoEmbeddingRepository`가 `JdbcTemplate`과 native SQL로 처리한다.

읽고 쓸 때 `vector`와 문자열 사이의 변환이 필요하다.

```sql
SELECT id, embedding::text FROM photos WHERE id = ANY(?)   -- 읽기
ORDER BY embedding <=> ?::vector                            -- 코사인 거리 정렬
```

`<=>`는 pgvector의 코사인 거리 연산자다. **거리**이므로 값이 작을수록 유사하다.

### photos_embedding_idx

```sql
CREATE INDEX photos_embedding_idx
    ON photos USING ivfflat (embedding vector_cosine_ops) WITH (lists = 50);
```

ivfflat은 근사 최근접 인덱스라 **정확도를 일부 포기하고 속도를 얻는다.**
`lists` 값은 데이터 규모에 맞춰 조정하는 값이며, 현재 50은 초기 데이터 기준으로 정한 것이다.
데이터가 크게 늘면 재검토가 필요하다.

## spots

관광 장소 마스터다. 코스에 들어가는 장소가 여기서 나온다.

| 컬럼 | 타입 | 의미 |
|---|---|---|
| `spot_id` | TEXT PK | TourAPI의 콘텐츠 ID와 같은 값. 장소 상세 보완에 그대로 쓴다. |
| `name` | TEXT | 장소명 |
| `region_name` | TEXT | 광역시도 단위 지역명. 추천된 지역으로 장소를 찾을 때 `ILIKE` 부분 일치로 조회한다. |
| `area_name` | TEXT | 시군구 단위 지역권. `import_data.py`가 addr1에서 파생한다. 예: `"충북 보은"`, `"충북 일대"`. 추천 지역 카드의 `regionArea`로 노출된다. null이면 클라이언트에서 생략 처리한다. |
| `lat` / `lng` | FLOAT | 좌표. **결측일 수 있다.** |
| `image_url` | TEXT | 대표 이미지 |
| `operating_hours` | TEXT | 운영시간 |
| `closed_days` | TEXT | 휴무일 |
| `parking` | TEXT | 주차 정보 |
| `contact` | TEXT | 연락처 |
| `cat1` `cat2` `cat3` | TEXT | TourAPI 분류 코드. 현재 조회에 쓰지 않는다. |

`spot_id`를 제외한 모든 컬럼이 nullable이다. 원본 데이터의 완성도가 고르지 않기 때문에
**결측을 정상 상태로 다뤄야 한다.**

- 운영시간·휴무일·주차·연락처가 비면 TourAPI로 보완을 시도한다.
- 보완 후에도 비면 응답에 결측임을 표시한다.
- 좌표가 없는 장소는 코스에서 뒤로 배치한다.

### spots_location_idx

`(lat, lng)` 복합 인덱스다. 현재 코드에는 좌표 범위로 조회하는 경로가 없어
**아직 사용되지 않는다.** 근처 장소 검색이 생기면 그때 쓰인다.

## courses

`POST /courses`가 만든 코스다. 공유와 저장이 코스를 ID로 참조할 수 있게 하려고 저장한다.

| 컬럼 | 타입 | 의미 |
|---|---|---|
| `course_id` | TEXT PK | 발급한 코스 ID (UUID) |
| `device_id` | TEXT | 코스를 만든 기기. 공유와 저장은 소유자만 할 수 있다. |
| `course_data` | JSONB NOT NULL | 지역명과 Day 1·Day 2 장소 목록의 스냅샷 |
| `created_at` | TIMESTAMP NOT NULL | 생성 시각 |

`course_data`의 형태는 `course/CourseSnapshot` 레코드다. **지역명을 함께 담는다.**
공유 코스는 "지역과 코스의 스냅샷"이므로 지역이 빠지면 공유 상세에서 지역명을 표시할 수 없다.

Day 1·Day 2의 각 장소는 `course/SpotSummary` 형태로 장소 ID·이름·이미지·좌표와
운영시간·휴무일·주차·연락처를 담는다. 값은 코스를 만든 시점의 `spots` 데이터다.
이후 장소 마스터가 바뀌어도 저장된 코스와 공유 스냅샷은 바뀌지 않는다.
생성 시점에 없던 값과 상세 필드가 추가되기 전에 만들어진 옛 스냅샷의 값은 `null`이다.

코스는 만든 뒤 수정하지 않는다. **MVP에서는 만료시키지도 정리하지도 않는다.**
→ [ADR 0014](./decisions/0014-share-link-lifetime.md)

### device_id가 nullable인 이유

코스 소유자([ADR 0012](./decisions/0012-course-ownership.md))가 도입되기 전에 만들어진 행에는
소유자가 없다. 소유자 조건이 `device_id = ?`이므로 그런 행은 어떤 기기로도 걸리지 않는다.
공유도 저장도 할 수 없게 되지만 출시 전이라 대상 행이 사실상 없어 백필하지 않는다.
`shared_courses.course_id`와 같은 판단이다.

**소유자를 보지 않는 것은 공유 링크 조회뿐이다.** 공유는 남에게 보내라고 만든 것이다.

## shared_courses

공유 버튼을 누른 시점의 코스를 담는 읽기 전용 스냅샷이다.

| 컬럼 | 타입 | 의미 |
|---|---|---|
| `share_id` | TEXT PK | 공유 링크에 들어가는 ID. UUID에서 하이픈을 빼고 앞 10자를 쓴다. |
| `course_id` | TEXT UNIQUE | 이 공유가 어느 코스에서 나왔는지. 코스가 지워지면 NULL이 된다. |
| `course_data` | JSONB | `courses.course_data`를 복사한 스냅샷 |
| `created_at` | TIMESTAMP | 생성 시각 |

`course_id`가 유니크라 **한 코스의 공유 링크는 하나뿐이다.** 같은 코스를 다시 공유하면
새 링크를 만들지 않고 기존 링크를 돌려준다. ([api-contract.md §4 멱등성](./api-contract.md#4-소유권과-멱등성))

이 컬럼이 생기기 전에 만들어진 행에는 값이 없으므로 NULL을 허용한다.
NULL은 유니크 인덱스에서 여러 개가 허용된다.

**옛 행의 `course_data`에는 지역이 없다.** 코스 영속화([ADR 0010](./decisions/0010-course-persistence.md))
이전 스냅샷은 `day1`·`day2`만 담고 있어 조회 시 `regionName`이 null로 나간다.
`spots`를 조인해 채우는 백필은 하지 않기로 했다. 출시 전이라 대상 행이 사실상 없다.

### 왜 JSONB인가

공유 코스는 **조회만 하고 수정하지 않는다.** 그리고 공유 당시 모습이 그대로 남아야 한다.
정규화해서 `spots`를 참조하면 원본 장소 정보가 바뀔 때 과거 공유 링크의 내용도 함께 바뀐다.

대신 스냅샷이 깨졌을 때 역직렬화가 실패할 수 있다. 목록 조회에서는
[부분 실패 처리](./api-contract.md#6-부분-실패-처리) 규칙에 따라 해당 항목만 제외한다.

### 현재의 한계

`share_id`가 짧아(10자) 규모가 커지면 충돌 확률이 올라간다.
충돌하면 `SharedCourseRepository`가 새 ID로 다시 시도한다.
**만료 정책은 두지 않는다.** MVP에서 공유 링크는 만료하지 않고 정리 배치도 없다.
따라서 이 압력은 시간이 지나도 줄지 않는다. → [ADR 0014](./decisions/0014-share-link-lifetime.md)

## saved_trips

사용자가 저장한 **추천 지역과 그 시점의 코스**다. 내용은 복사하지 않고 `courses`에서 가져온다.

| 컬럼 | 타입 | 의미 |
|---|---|---|
| `saved_trip_id` | TEXT PK | 저장 항목 ID (UUID) |
| `device_id` | TEXT NOT NULL | 소유자. `X-Device-Id`로 받은 익명 사용자 식별자다. |
| `course_id` | TEXT NOT NULL FK | 저장된 코스. 내용을 가져오는 참조다. |
| `region_key` | TEXT NOT NULL | 저장 당시의 지역명. `CourseSnapshot.regionName`을 그대로 넣는다. 표시와 필터에 쓰고 **중복 판정에는 쓰지 않는다.** 아래 참고. |
| `course_fingerprint` | TEXT NOT NULL | 코스 지문. 장소 ID를 정렬해 이어 붙인 값의 SHA-256. |
| `created_at` | TIMESTAMP NOT NULL | 저장 시각 |

### 소유자를 저장 행이 직접 들고 있는 이유

`courses.device_id`는 **코스를 만든 기기**이고 `saved_trips.device_id`는 **저장한 기기**다.
지금은 자기 코스만 저장할 수 있어 둘이 항상 같지만, 같은 값이라고 해서 조인으로 대신할 수는 없다.
저장 목록 조회가 매번 `courses`를 거쳐야 하고, 저장의 소유권이 코스의 소유권에 얹히게 된다.
[소유자 조건은 각 테이블의 쿼리에 직접 넣는다](./api-contract.md#4-소유권과-멱등성).

### 중복 판정

`UNIQUE(device_id, course_fingerprint)`가 정체성이다.
([ADR 0011](./decisions/0011-saved-trip-identity.md))

- **`region_key`는 판정에 들어가지 않는다.** 한 코스의 장소는 모두 같은 `region_name`이어야 하므로
  장소 집합이 정해지면 지역명도 함께 정해진다. 지문이 이미 지역명을 결정하므로 이 축은 코스를
  갈라주지 못하고, `spots.region_name`을 수정하면 같은 코스를 중복으로 쌓는 일만 한다.
- 지문에 **장소 순서를 넣지 않는다.** 순서는 사용자가 고른 것이 아니라 좌표 정렬이 정하는 값이라
  정렬 방식을 바꾸면 과거 저장분과 어긋난다.
- 같은 장소로 코스를 다시 만들어 새 `course_id`로 저장해도 같은 항목이다.
  이때 남는 `course_id`와 `region_key`는 **처음 저장할 때의 값**이다.

`course_fingerprint`는 **스냅샷 전체의 해시가 아니다.** 장소 ID만 넣으므로 Day 배치나
장소의 이름·좌표·이미지가 달라도 같은 지문이다. 그 차이를 중복 판정에서 무시하고
최초 저장 스냅샷을 유지하겠다는 것이 결정의 내용이다.
따라서 충돌로 볼 상황이 정의되지 않아 **`SAVED_TRIP_CONFLICT` 409는 이 설계에서 발생하지 않는다.**

### 외래키 정책이 shared_courses와 다른 이유

| | `shared_courses.course_id` | `saved_trips.course_id` |
|---|---|---|
| 정책 | `ON DELETE SET NULL` | 절 없음 (= `NO ACTION`) |
| 이유 | 스냅샷을 복사해 두므로 코스가 사라져도 공유 링크가 계속 열려야 한다 | 스냅샷을 복사하지 않으므로 코스가 사라지면 저장 항목이 내용을 잃는다 |

`courses`를 지우면 외래키가 막는다. **이것이 원하는 동작이다.**
왜 그렇게 정했는지는 [ADR 0014](./decisions/0014-share-link-lifetime.md)에 있다.

### 현재의 한계

지금은 코스를 지우는 경로 자체가 없어 이 제약이 실제로 걸리지 않는다.
**삭제 경로가 생길 때 함께 처리해야 하는 것**이 하나 있다.

`SavedTripService.save`는 코스 조회와 삽입이 별개 auto-commit이라, 그 사이에 코스가 지워지면
FK 위반이 500 `INTERNAL_ERROR`로 나간다. 원인은 "없는 코스"이므로 404 `COURSE_NOT_FOUND`가 맞다.
지금은 재현되지 않으므로 삭제 경로를 만드는 변경에서 이 매핑을 함께 넣는다.

## 저장되지 않는 데이터

| 데이터 | 현재 위치 | 문제 |
|---|---|---|
| 분석 결과 (임베딩·태그) | `AnalysisStore`의 프로세스 메모리 | 재시작 시 소실, TTL 없음, 다중 인스턴스 불가 → [Q-06](./open-questions.md) |
