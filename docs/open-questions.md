# 미결 항목

아직 정해지지 않아 **추측으로 구현하면 안 되는** 항목이다.

다른 문서는 확정된 내용만 담고, 미결 항목은 `[Q-01]` 형태로 여기를 참조한다.

## 사용법

**작업을 시작하기 전에** 관련 항목이 있는지 확인한다.
걸리는 항목이 있으면 임의로 정하지 말고 팀에 확인한다.

**확정되면** 이 문서에서 지우고 내용을 해당 문서로 옮긴다.
되돌리기 어려운 선택이었다면 [decisions/](./decisions/)에 ADR도 함께 남긴다.

상태는 셋 중 하나다.

| 상태 | 의미 |
|---|---|
| 열림 | 논의 전 |
| 논의 중 | 후보는 있으나 확정 전 |
| 차단 | 이것 때문에 다른 작업이 멈춰 있음 |

---

## Q-05. 지역별 최종 코스 장소 수

**상태:** 열림
**영향:** 코스 생성, 추천 응답 크기, 화면 밀도

현재는 지역당 장소를 최대 5개 조회해 Day 1·Day 2로 나눈다.
이 값이 제품 기준으로 확정된 것은 아니다.

관련 상수는 [recommendation.md §4](./recommendation.md#4-현재-상수)에 정리돼 있다.

**논의 위치: #51.** 데이터 기준 상한선(장소가 있는 지역 7개)까지 정리돼 있다.
상태는 담당자가 판단해 갱신한다.

---

## Q-08. 사진 풀 크기와 추천 지역 개수

**상태:** 열림
**영향:** 사진 풀 API, 추천 응답 크기, 화면 밀도

현재 값은 코드에 상수로 박혀 있고 제품 기준으로 확정된 것이 아니다.

| 값 | 현재 |
|---|---|
| 사진 풀 기본 크기 | 40장 |
| 한 번에 내려줄 로딩 단위 | 전체를 한 번에 (분할 없음) |
| 추천 지역 개수 | 최대 3개 |

정해야 하는 것:

- 사진 풀을 한 번에 40장 내려줄지, 나눠서 받을지
- 추천 지역 3개가 적절한지

관련 상수는 [recommendation.md §4](./recommendation.md#4-현재-상수)에 모여 있다.

**논의 위치: #51.** 다만 #51은 `TOP_REGION_COUNT`·`SPOTS_PER_REGION`·`PREVIEW_SPOT_COUNT`·
`SIMILAR_PHOTO_LIMIT`를 다루면서 Q-05만 참조하고 이 항목은 참조하지 않는다.
`TOP_REGION_COUNT`는 여기 "추천 지역 개수"와 같은 값이다. 확정할 때 두 항목을 함께 닫는다.

---

## Q-09. 요청 타임아웃 기준

**상태:** 차단
**막고 있는 것:** 장소 상세의 결측 필드별 TourAPI 보완 (#29, P1), **주변 장소 추천 ([ADR 0016](./decisions/0016-self-built-course-with-nearby-places.md))**
**영향:** 취향 분석·추천 응답 시간, 클라이언트 로딩 화면 처리

> **2026-08-05. 우선순위가 올라갔다.** 주변 장소 추천(B안)이 확정되면서 코스 확정 시
> TourAPI를 호출하고, 이미지가 없으면 Google Places를 한 번 더 거치는 구조가 됐다.
> 외부 호출이 2단이 된 만큼 이 항목을 닫기 전에 구현에 들어가면 무한 대기 경로가 생긴다.
> **외부 결정을 기다리지 않는 유일한 미결이라 지금 바로 진행할 수 있다.**
> Q-05·Q-08은 #51에, Q-10·Q-11은 기획 확정에 걸려 있다.

정해야 하는 것에 **타임아웃 값만 있는 게 아니다.** 코스 조회 1회가 관광지 수만큼
TourAPI를 부르므로(최대 5회), 순차로 부르면 상한이 5배가 된다.
**병렬 호출 여부와 코스당 조회 상한**을 함께 정해야 값이 의미를 갖는다.

요구사항은 "DB와 외부 API 타임아웃을 설정하고 무한 대기를 허용하지 않는다"를 요구하지만
구체적인 값이 정해지지 않았고 현재 설정도 없다.

정해야 하는 것:

- DB 쿼리 타임아웃
- TourAPI 호출 타임아웃과 재시도 여부
- 취향 분석·추천 생성의 전체 응답 시간 상한
- 상한을 넘겼을 때의 응답 (현재는 정의되지 않음)

---

## Q-10. 이미지 없는 주변 장소의 처리

**상태:** 열림
**영향:** 주변 장소 노출량, Google Places 호출 비용, 디테일뷰 빈 상태

주변 장소 추천(B안)은 **이미지가 있어야 의미가 있다**는 전제로 채택됐다.
([ADR 0016](./decisions/0016-self-built-course-with-nearby-places.md))
그런데 TourAPI가 주는 장소 중 이미지가 없는 것이 섞여 있다.

정해야 하는 것:

- 이미지 없는 장소를 **Google Places로 보완할지, 그냥 제외할지.** 둘을 섞는다면 어디까지 보완할지
- 보완한다면 호출 상한 — Google Places 무료 한도는 월 $200 크레딧(약 1,000건) 수준이라 비용 판단이 필요하다
- 보완도 제외도 한 뒤 **주변 장소가 0건이 된 코스**의 응답 형태

회의에서는 "구글 place api에 한 번 거쳐서 나오거나 필터링해서 없애는 것도 좋다"까지만 나왔고
어느 쪽인지 정하지 않았다.

---

## Q-11. 주변 장소의 유형 매핑과 추가 유형

**상태:** 열림
**영향:** 주변 장소 조회 쿼리, 디테일뷰 카테고리 구분 UI

필수 유형은 **식당 · 숙박 · 카페**로 확정됐다. 그런데 **셋 중 하나가 아직 매핑되지 않는다.**

TourAPI KorService2에서 식당은 `contentTypeId=39`, 숙박은 `32`인데
**카페는 별도 `contentTypeId`가 없다.** 음식점(39) 안에서 분류 코드(`cat3`)로만 갈린다.
즉 `contentTypeId`만으로는 필수 유형 셋을 채울 수 없다.

> `spots` 테이블에는 `cat1`·`cat2`·`cat3` 컬럼이 이미 있고 `import_data.py`가 적재하고 있다.
> 다만 [data-model.md](./data-model.md)가 적어둔 대로 **현재 조회에 쓰지 않는다.**
> 분류 코드를 조회 축으로 쓰기 시작하는 첫 사례가 된다.

정해야 하는 것:

- 카페를 무엇으로 판정할지 — `contentTypeId=39` + `cat3` 조합인지, 다른 방법인지
- 식당·숙박 상세를 부를 때의 처리. 지금 `TourApiClient.fetchDetail`은
  `detailIntro2`에 `contentTypeId=12`(관광지)를 하드코딩한다. **유형별로 응답 필드가 달라 갈라야 한다.**
- 식당·숙박·카페 외에 무엇을 더 넣을지

**TourAPI 스펙은 구현 전에 직접 확인한다.** 위 코드 값은 문서 조사 기준이라 검증이 필요하다.

---

## 확정되어 옮겨진 항목

| 항목 | 결정 | 기록 |
|---|---|---|
| 오류 응답 형식 | `{code, message, retryable, traceId}` | [ADR 0001](./decisions/0001-error-response-contract.md) |
| API 명세 관리 | OpenAPI를 정본으로 | [ADR 0002](./decisions/0002-openapi-as-api-spec-source.md) |
| 스키마 마이그레이션 | Flyway, baseline-on-migrate | [ADR 0003](./decisions/0003-flyway-for-schema-migration.md) |
| 코딩 규약 문서 | AGENTS.md 정본, CLAUDE.md 참조 | [ADR 0004](./decisions/0004-agents-md-as-convention-source.md) |
| 익명 사용자 ID 전달 방식 | `X-Device-Id` 헤더, UUID v4 | [ADR 0007](./decisions/0007-anonymous-device-id.md), [api-contract.md §4](./api-contract.md#4-소유권과-멱등성) |
| 코스를 서버에 저장할 것인가 | `courses` 테이블에 저장하고 공유·저장이 참조 | [ADR 0010](./decisions/0010-course-persistence.md), [data-model.md](./data-model.md) |
| 중복 저장 판정 기준 | `UNIQUE(익명 사용자 ID, 코스 지문)`. 지문은 장소 ID를 정렬해 해시하며 순서를 넣지 않는다 | [ADR 0011](./decisions/0011-saved-trip-identity.md), [data-model.md](./data-model.md) |
| 일부 사진 ID가 유효하지 않을 때 | 중복 제거 후 고유 장수 5 미만 → 400 / 유효 장수 5 미만 → 400, 그 외는 유효한 것으로 진행 | `TasteAnalysisService.prepareInput` |
| 공유 링크 만료와 코스 정리 | MVP는 만료 없음. 정리 배치도 두지 않고 `saved_trips`가 코스 삭제를 막는 동작을 의도로 유지한다 | [ADR 0014](./decisions/0014-share-link-lifetime.md), [PRD §12 #3](./PRD.md) |
| 분석 결과 저장소와 TTL | `POST /taste-analysis`가 코스를 직접 반환하면서 `analysisId`가 주 흐름에서 사라졌다. `GET /recommendations`가 제거되면 `AnalysisStore`도 함께 제거한다. #26(P0) 작업은 사실상 해소됨. | #61 |
| 추천 결과에 주변 장소를 넣을 것인가 | **B안 확정.** 코스에 주변 식당·숙박·카페를 함께 제공한다. 무료로 이미지를 주는 곳이 TourAPI뿐이라 데이터 양 부족을 감수하고 채택했다 | [ADR 0016](./decisions/0016-self-built-course-with-nearby-places.md), 2026-08-03 2차 회의 |
| 코스를 무엇으로 만드는가 | **자체 `spots` + greedy NN 유지.** TourAPI 큐레이션 코스(ADR 0009)는 구현되지 않은 채 대체됐다 | [ADR 0016](./decisions/0016-self-built-course-with-nearby-places.md), #51 A안 |
