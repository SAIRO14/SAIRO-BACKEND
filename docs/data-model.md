# 데이터 모델

스키마 정본은 `src/main/resources/db/migration/` 아래 마이그레이션 파일이다.
이 문서는 마이그레이션만 봐서는 알기 어려운 **각 컬럼의 의미와 배경**을 설명한다.

컬럼을 추가하거나 바꿀 때는 새 `V<번호>__<설명>.sql`을 만든다.
규칙은 [AGENTS.md](../AGENTS.md) 8절에 있다.

## 전체 구조

```text
photos          사진 풀과 임베딩. 추천의 입력.
spots           관광 장소 마스터. 코스의 재료.
shared_courses  공유 시점의 코스 스냅샷.
```

테이블 사이에 외래키가 없다. 스냅샷은 참조가 아니라 **복사본**으로 보관하기 때문이다.
공유된 코스는 원본 장소 정보가 나중에 바뀌어도 공유 당시 모습을 그대로 재현해야 한다.

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
| `region_name` | TEXT | 지역명. 추천된 지역으로 장소를 찾을 때 `ILIKE` 부분 일치로 조회한다. |
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

## shared_courses

공유 버튼을 누른 시점의 코스를 담는 읽기 전용 스냅샷이다.

| 컬럼 | 타입 | 의미 |
|---|---|---|
| `share_id` | TEXT PK | 공유 링크에 들어가는 ID. UUID에서 하이픈을 빼고 앞 10자를 쓴다. |
| `course_data` | JSONB | Day 1과 Day 2 장소 목록의 스냅샷 |
| `created_at` | TIMESTAMP | 생성 시각 |

### 왜 JSONB인가

공유 코스는 **조회만 하고 수정하지 않는다.** 그리고 공유 당시 모습이 그대로 남아야 한다.
정규화해서 `spots`를 참조하면 원본 장소 정보가 바뀔 때 과거 공유 링크의 내용도 함께 바뀐다.

대신 스냅샷이 깨졌을 때 역직렬화가 실패할 수 있다. 목록 조회에서는
[부분 실패 처리](./api-contract.md#6-부분-실패-처리) 규칙에 따라 해당 항목만 제외한다.

### 현재의 한계

`share_id`가 짧아(10자) 규모가 커지면 충돌 확률이 올라간다. 충돌 시 재생성 처리가 필요하다.
만료 정책도 아직 없다. → [Q-02](./open-questions.md)

## 저장되지 않는 데이터

| 데이터 | 현재 위치 | 문제 |
|---|---|---|
| 분석 결과 (임베딩·태그) | `AnalysisStore`의 프로세스 메모리 | 재시작 시 소실, TTL 없음, 다중 인스턴스 불가 → [Q-06](./open-questions.md) |
| 생성된 코스 | 어디에도 저장 안 됨 | `courseId`를 발급하지만 버린다 → [Q-03](./open-questions.md) |
| 저장 여행지 | 미구현 | → [Q-01](./open-questions.md) |
