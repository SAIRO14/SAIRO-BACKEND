# PR #43 상세 리뷰 보고서

- PR: `feat: 코스 영속화와 공유 연결 (#27)`
- 대상 브랜치: `main`
- 헤드 브랜치: `feat/27-course-persistence`
- 리뷰 일자: 2026-07-26
- 리뷰 결론: **Request changes 권고**

> 이 파일은 리뷰 전달을 위한 임시 보고서다.

## 1. 변경 요약

PR #43은 다음 문제를 해결한다.

- `POST /courses`가 생성 결과를 `courses` 테이블에 저장한다.
- `POST /courses/{courseId}/share`가 요청 본문 대신 서버에 저장된 코스를 사용한다.
- 공유 스냅샷에 `regionName`을 포함한다.
- 같은 코스의 공유 요청이 중복돼도 같은 `shareId`를 반환한다.
- `share_id` 충돌 시 새 ID로 최대 5회 재시도한다.
- 코스 생성 시 요청 지역과 장소 지역의 일치 여부를 검증한다.
- Flyway V2 마이그레이션과 관련 문서 및 통합 테스트를 추가한다.

변경 규모는 18개 파일, `+394/-121`이다.

## 2. 종합 평가

코스를 서버에 영속화하고 공유 요청 본문을 신뢰하지 않도록 바꾼 방향은 요구사항과 잘 맞는다.
특히 다음 부분은 적절하게 구현됐다.

- 서버가 생성한 코스만 공유할 수 있도록 `courseId`를 실제 조회에 사용한다.
- `courses.course_data`와 `shared_courses.course_data`를 별도 스냅샷으로 보관한다.
- `shared_courses.course_id` 유니크 인덱스와 `ON CONFLICT`를 사용해 공유 생성을 멱등하게 처리한다.
- 코스 삭제가 공유 스냅샷을 제거하지 않도록 외래키에 `ON DELETE SET NULL`을 적용한다.
- V2 추가 이후에도 기존 DB의 V1 baseline 동작을 검증하도록 테스트 의도를 개선했다.

다만 기존 공유 데이터와의 호환성 문제 1건이 사용자에게 직접 영향을 줄 수 있다.
ID 오류 계약과 문서 정합성 문제도 병합 전에 정리하는 것이 좋다.

## 3. 발견 사항

### 3.1 [P1] 기존 공유 링크에서 `regionName`이 `null`로 반환된다

관련 위치:

- `src/main/resources/db/migration/V2__add_courses.sql:18`
- `src/main/java/com/sairo/sairo_backend/course/CourseService.java:84`
- `src/main/java/com/sairo/sairo_backend/course/CourseSnapshot.java:10`

V2는 기존 `shared_courses` 행을 유지하면서 nullable `course_id`만 추가한다.
기존 `course_data`는 삭제된 `ShareCourseRequest` 구조인 다음 형태다.

```json
{
  "day1": [],
  "day2": []
}
```

새 조회 로직은 이 데이터를 `CourseSnapshot`으로 역직렬화한다.

```java
CourseSnapshot snapshot = objectMapper.readValue(json, CourseSnapshot.class);
return new SharedCourseViewResponse(
        shareId,
        snapshot.regionName(),
        snapshot.day1(),
        snapshot.day2()
);
```

기존 JSON에는 `regionName`이 없으므로 기본 Jackson 설정에서는 해당 값이 `null`이 된다.
따라서 V2 적용 전에 만든 공유 링크는 다음과 같이 응답한다.

```json
{
  "shareId": "...",
  "regionName": null,
  "day1": [],
  "day2": []
}
```

이는 이번 변경의 주요 완료 조건인 “공유 상세에서 지역명을 표시할 수 있어야 한다”를
기존 공유 링크에 대해서는 만족하지 못한다.

권고:

1. 기존 공유 스냅샷의 지역을 신뢰할 수 있게 추론할 수 있다면 V2 또는 후속 마이그레이션에서 보완한다.
2. 보완이 불가능하다면 레거시 스냅샷을 명시적으로 처리하고 `regionName`이 nullable일 수 있음을 OpenAPI에 표시한다.
3. 기존 형식의 `shared_courses` 행을 준비한 뒤 V2 적용과 공유 조회까지 확인하는 통합 테스트를 추가한다.

### 3.2 [P2] 잘못된 `courseId` 형식이 400이 아니라 404로 처리된다

관련 위치:

- `src/main/java/com/sairo/sairo_backend/course/CourseController.java:59`
- `src/main/java/com/sairo/sairo_backend/course/CourseService.java:70`
- `src/test/java/com/sairo/sairo_backend/course/CourseApiTest.java:154`
- `docs/api-contract.md:37`

`courseId`는 UUID로 발급되지만 컨트롤러가 일반 `String`으로 받은 뒤 형식 검증 없이 DB를 조회한다.
현재 테스트도 `not-a-real-course`를 없는 코스로 간주해 404 `COURSE_NOT_FOUND`를 기대한다.

그러나 전역 API 계약은 다음과 같이 규정한다.

- 형식이 맞는 ID지만 대상이 없음: 404
- ID 형식 자체가 잘못됨: 400

따라서 현재 동작은 전역 오류 계약과 다르다.

권고:

- `@PathVariable UUID courseId`로 받거나 서비스 진입 전에 UUID 형식을 명시적으로 검증한다.
- 테스트를 다음 두 경우로 분리한다.

```text
not-a-real-course
  → 400 INVALID_REQUEST

존재하지 않는 정상 UUID
  → 404 COURSE_NOT_FOUND
```

DB 컬럼을 `TEXT`로 유지하더라도 API 경계에서는 UUID 형식을 검증할 수 있다.

### 3.3 [P2] 현재 동작 문서가 새 구현과 반대로 남아 있다

관련 위치:

- `docs/recommendation.md:117`
- `docs/glossary.md:41`
- `docs/api-contract.md:129`
- `docs/data-model.md:123`

`docs/recommendation.md`는 여전히 다음과 같이 설명한다.

> 요청의 `regionName`을 검증하지 않는다.

하지만 이번 PR은 `CourseService.verifyRegion`을 추가해 모든 장소의 지역을 검증한다.

`docs/glossary.md`도 공유 중복 정책을 다음과 같이 설명한다.

> 누를 때마다 새 스냅샷

새 구현과 `api-contract.md`는 코스당 공유 링크를 하나만 만들고 기존 `shareId`를 반환한다.
두 문서가 서로 반대되는 상태다.

AGENTS.md는 코스 계산을 변경하면 `recommendation.md`를 갱신하도록 요구한다.
문서가 현재 구현을 설명하는 자료이므로 이번 PR에서 함께 수정해야 한다.

권고:

- `recommendation.md`의 지역 검증 한계 문단을 현재 구현으로 갱신한다.
- 정렬이 입력 순서에 따라 달라진다는 기존 설명도 현재 결정적 정렬 구현과 함께 재확인한다.
- `glossary.md`의 공유 중복 설명을 “같은 코스는 기존 공유 링크 반환”으로 수정한다.

### 3.4 [P2] Q-01의 일부 항목이 미확정인데 미결 목록과 이슈를 닫는다

관련 위치:

- `docs/requirements.md:104`
- `docs/requirements.md:109`
- `docs/open-questions.md`
- GitHub Issue #20

기존 Q-01과 이슈 #20은 다음 항목을 결정하도록 요구했다.

- 익명 사용자 ID 전달 위치
- 헤더 이름과 형식
- 누락 및 형식 오류
- 저장 API 4종의 URI

이번 PR은 `X-Device-Id` 헤더와 검증 규칙은 확정했지만,
`requirements.md`에는 저장 API URI를 구현할 때 결정한다고 남겼다.
동시에 Q-01을 `open-questions.md`에서 제거하고 PR 본문은 이슈 #20을 닫는다.

이 상태에서는 미결 사항의 추적이 끊긴다.

권고:

- 저장 API URI를 이번 결정에 포함해 확정하거나,
- URI 결정을 별도 미결 항목 또는 이슈로 명시적으로 이관한 뒤 #20을 닫는다.

## 4. 테스트 검토

추가된 테스트는 다음 핵심 경로를 검증한다.

- 정상 코스 생성
- 지역 불일치 시 400 `COURSE_REGION_MISMATCH`
- 정상 공유 생성과 공유 조회
- 없는 코스 공유 시 404 `COURSE_NOT_FOUND`
- 같은 코스를 두 번 공유할 때 같은 `shareId` 반환
- 공유 행이 하나만 생성되는지 확인
- 공유 조회에 `regionName` 포함
- 기존 DB의 V1 baseline과 V2 이후 마이그레이션 적용

추가를 권장하는 테스트:

1. V1 형식의 기존 공유 행을 V2 적용 후 조회하는 호환성 테스트
2. 같은 `courseId`로 병렬 공유 요청을 보내는 동시성 테스트
3. `share_id` 충돌 후 재생성되는 경로를 결정적으로 검증하는 테스트
4. 잘못된 UUID와 존재하지 않는 정상 UUID를 구분하는 테스트
5. 코스 생성 후 `courses.course_data`에 `regionName`, `day1`, `day2`가 저장됐는지 직접 확인하는 테스트

현재 중복 공유 테스트는 두 요청을 순차 실행한다.
구현 주석이 동시 요청 안전성까지 보장한다고 설명하므로 병렬 요청 테스트가 있으면 회귀 방지에 도움이 된다.

## 5. 검증 결과

로컬 검증:

```text
./gradlew build --rerun-tasks
BUILD SUCCESSFUL
```

- 테스트 38개 발견
- 37개 실행 성공
- 기존 비활성 테스트 1개
- 실패 0
- `git diff --check origin/main...HEAD` 성공
- 리뷰 과정에서 소스 코드 변경 없음

GitHub 상태:

- CI `test` 성공
- 기존 리뷰 및 코멘트 없음
- PR 상태는 Open

## 6. 병합 전 권장 체크리스트

- [ ] 기존 공유 스냅샷의 `regionName` 호환 정책 결정
- [ ] 레거시 공유 데이터 테스트 추가
- [ ] 잘못된 `courseId` 형식을 400으로 처리
- [ ] 정상 형식의 미존재 UUID에 대한 404 테스트 추가
- [ ] `recommendation.md`의 지역 검증 설명 갱신
- [ ] `glossary.md`의 공유 중복 정책 갱신
- [ ] Q-01의 미확정 저장 API URI를 별도 항목으로 이관하거나 확정
- [ ] 병렬 공유 멱등성 테스트 검토
- [ ] 다른 작업과 Flyway 버전 번호가 겹치지 않는지 병합 직전 재확인

## 7. 최종 의견

코스 영속화와 공유 연결의 구조적 방향은 타당하다.
서버가 생성한 코스만 공유하게 만든 점과 데이터베이스 제약으로 멱등성을 보장한 점도 좋다.

다만 기존 공유 링크에서 지역 정보가 사라지는 호환성 문제는 사용자에게 직접 영향을 줄 수 있으므로
병합 전에 정책과 동작을 확정하는 것이 안전하다. 나머지 ID 계약과 문서 문제도 수정 범위가 크지 않으므로
같은 PR에서 정리한 뒤 병합하는 것을 권장한다.
