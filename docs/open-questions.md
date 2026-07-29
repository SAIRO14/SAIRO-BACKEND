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

## Q-02. 공유 링크 만료 기간

**상태:** 열림
**영향:** `shared_courses` 스키마, 공유 조회 오류 처리, 정리 배치 필요 여부

공유 코스를 얼마나 보관할지 정해지지 않았다.

정해야 하는 것:

- 만료 기간 (무기한 / N일)
- 만료분 정리 방식. `courses`도 함께 늘어나므로 같이 정한다.
- **저장된 코스를 어떻게 다룰지.** `saved_trips.course_id`가 `courses`를 참조하며
  `ON DELETE` 절이 없어 삭제가 막힌다.
- **저장 생성의 FK 레이스.** `SavedTripService.save`는 코스 조회와 삽입이 별개 auto-commit이라,
  그 사이에 코스가 지워지면 FK 위반이 500 `INTERNAL_ERROR`로 나간다. 원인은 "없는 코스"이므로
  404 `COURSE_NOT_FOUND`가 맞다. 삭제 경로가 생기는 시점에 매핑을 추가해야 한다.

`share_id` 충돌 시 재생성은 구현했다. (`SharedCourseRepository.save`)

만료와 부재를 응답에서 구분하지 않는 것은 이미 정해졌다.
([api-contract.md §2](./api-contract.md#2-상태-코드))

---

## Q-05. 지역별 최종 코스 장소 수

**상태:** 열림
**영향:** 코스 생성, 추천 응답 크기, 화면 밀도

현재는 지역당 장소를 최대 5개 조회해 Day 1·Day 2로 나눈다.
이 값이 제품 기준으로 확정된 것은 아니다.

관련 상수는 [recommendation.md §4](./recommendation.md#4-현재-상수)에 정리돼 있다.

---

## Q-06. 분석 결과 저장소와 TTL

**상태:** 열림
**영향:** 추천 재조회 성공률, 서버 자원, 다중 인스턴스 배포

`AnalysisStore`가 프로세스 메모리 `ConcurrentHashMap`이다.
재시작하면 분석 ID가 사라지고, TTL이 없어 계속 쌓이며, 인스턴스가 여러 개면 동작하지 않는다.

정해야 하는 것:

- 저장소 (외부 캐시 / DB / 그 외)
- TTL 길이
- 만료된 분석 ID의 응답 (현재는 `ANALYSIS_NOT_FOUND` 404)

---

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

---

## Q-09. 요청 타임아웃 기준

**상태:** 열림
**영향:** 취향 분석·추천 응답 시간, 클라이언트 로딩 화면 처리

요구사항은 "DB와 외부 API 타임아웃을 설정하고 무한 대기를 허용하지 않는다"를 요구하지만
구체적인 값이 정해지지 않았고 현재 설정도 없다.

정해야 하는 것:

- DB 쿼리 타임아웃
- TourAPI 호출 타임아웃과 재시도 여부
- 취향 분석·추천 생성의 전체 응답 시간 상한
- 상한을 넘겼을 때의 응답 (현재는 정의되지 않음)

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
| 일부 사진 ID가 유효하지 않을 때 | 중복 제거 후 고유 장수 5 미만 → 400 / 유효 장수 5 미만 → 400, 그 외는 유효한 것으로 진행 | `TasteAnalysisService.analyze` |
