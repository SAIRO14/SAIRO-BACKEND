# 용어사전

제품 용어와 코드 이름을 잇는 표다. 같은 개념을 다른 이름으로 부르는 걸 막는 게 목적이다.

**새 개념을 만들면 여기에 먼저 이름을 정하고 그 이름으로 코드를 쓴다.**

## 핵심 개념

| 용어 | 정의 | 코드 이름 |
|---|---|---|
| **사진 풀** | 사용자가 취향 분석을 위해 고를 수 있는 관광 사진 집합 | `photos` 테이블, `GET /photos` |
| **선택 사진** | 사용자가 고른 5~10장 | `photoIds` |
| **임베딩** | 사진의 512차원 CLIP 벡터 | `photos.embedding` |
| **대표 임베딩** | 선택 사진 임베딩의 산술 평균 | `TasteAnalysisService.average` |
| **분석** | 대표 임베딩과 분위기 태그를 계산하는 과정 | `TasteAnalysisService.prepareInput` |
| **분위기 태그** | 선택 사진 키워드 빈도 상위 항목 | `moodTags` |
| **추천 지역** | 취향과 유사한 지역 단위 추천 결과 | `regionName` |
| **추천 이유** | 분위기 태그를 기반으로 생성하는 지역 카드 문구 | `MoodReasonMapper`, `reason` |
| **코스 카드** | 취향 분석 응답에 담기는 지역 단위 코스 요약. courseId·지역명·Day1·Day2를 포함한다 | `TasteAnalysisResponse.CourseCard` |
| **장소** | 코스에 들어가는 개별 관광지 | `spots` 테이블, `Spot`, `spotId` |
| **코스** | 지역 안의 장소를 Day 1·Day 2로 나눈 1박 2일 일정 | `courses` 테이블, `CourseResponse`, `day1`, `day2` |
| **주변 장소** | 코스 관광지 인근의 식당·숙박·카페. TourAPI로 조회하며 코스의 장소와 구분된다 | 미구현 → [ADR 0016](./decisions/0016-self-built-course-with-nearby-places.md) |
| **코스 스냅샷** | 지역과 Day 1·Day 2를 함께 담아 저장하는 형태 | `CourseSnapshot`, `course_data` |
| **저장 여행지** | 사용자가 저장한 추천 지역과 그 시점의 코스 | `saved_trips` 테이블, `SavedTrip`, `savedTripId` |
| **코스 지문** | 코스의 내용을 요약해 중복 저장을 판정하는 값 | `CourseFingerprint`, `course_fingerprint` |
| **공유 코스** | 공유 시점의 지역과 코스를 담은 읽기 전용 스냅샷 | `shared_courses`, `shareId` |
| **익명 사용자 ID** | 로그인 없이 사용자를 구분하는 기기 생성 UUID v4 | `X-Device-Id` 헤더, `@DeviceId` 파라미터, `DEVICE_ID_*` 오류 코드 |

## 헷갈리기 쉬운 구분

### 장소(spot) vs 지역(region)

**지역이 추천 단위이고, 장소는 코스의 재료다.**
추천 응답은 지역 카드를 담고, 장소는 그 안의 대표 항목이나 코스 항목으로만 등장한다.
"장소를 추천한다"는 표현은 제품 정의와 어긋난다.

### 저장(save) vs 공유(share)

| | 저장 여행지 | 공유 코스 |
|---|---|---|
| 목적 | 내가 나중에 다시 보기 | 남에게 보내기 |
| 소유자 | 있음 (익명 사용자별 격리) | 없음 (링크를 아는 누구나) |
| 수정 | 저장 해제 가능 | 읽기 전용 |
| 중복 | 같은 내용 중복 저장 불가 | 코스당 링크 하나. 다시 누르면 기존 링크 |

**둘은 별개 기능이다.** 현재 `shared_courses`를 저장 목록처럼 쓰는 코드가 있다면
그건 임시 대응이지 설계가 아니다.

### 코스 ID vs 공유 ID

| 식별자 | 수명 | 서버 저장 |
|---|---|---|
| `courseId` | 영구 | `courses` (소유자 있음) |
| `shareId` | 영구 | `shared_courses` |
| `savedTripId` | 영구 | `saved_trips` |

`POST /taste-analysis`는 코스를 즉시 생성하고 `courseId`를 응답에 담는다.
`courseId`는 공유와 저장이 코스를 가리키는 참조 키다.
다만 같은 장소로 `POST /courses`를 다시 부르면 새 `courseId`가 나온다.
**"내용이 같은 코스"를 판정하는 키는 아니다.** 그 판정은 코스 지문이 한다.
([ADR 0011](./decisions/0011-saved-trip-identity.md))

### 결측(missing) vs 오류(error)

장소 정보가 비어 있는 것은 **정상 상태**다. 원본 데이터의 완성도가 고르지 않기 때문이다.
결측은 응답에 표시할 대상이지 예외를 던질 대상이 아니다.

## 표기 규칙

| 대상 | 규칙 | 예 |
|---|---|---|
| JSON 필드 | lower camel case | `analysisId`, `moodTags` |
| DB 컬럼 | snake_case | `image_url`, `region_name` |
| 오류 코드 | UPPER_SNAKE_CASE, 원인 단위 | `ANALYSIS_NOT_FOUND` |
| URI | 복수형 명사 | `/photos`, `/courses` |
| 마이그레이션 | `V<번호>__<설명>.sql` | `V3__add_saved_trips.sql` |

## 이 저장소에서 쓰지 않는 말

| 쓰지 않음 | 대신 | 이유 |
|---|---|---|
| 회원, 유저 ID | 익명 사용자 ID | 로그인이 없다. 계정 개념과 혼동된다. |
| 북마크, 찜 | 저장 여행지 | 제품 용어를 하나로 통일한다. |
| 관광지 추천 | 지역 추천 | 추천 단위는 지역이다. |
| 여행 계획 | 코스 | 1박 2일 고정 형태를 가리킨다. |
