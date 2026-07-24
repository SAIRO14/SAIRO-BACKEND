# 코드 리뷰 가이드

PR을 열기 전에는 **[CONTRIBUTING.md](./CONTRIBUTING.md)** 체크리스트를 완료한다.  
이 문서는 **리뷰어**가 PR을 볼 때 사용한다.

---

## 리뷰 원칙

- 작동하는가보다 **코드베이스 규약을 따르는가**를 먼저 본다.
- `**[필수]**` 표기가 있는 항목은 머지 전에 반드시 해결한다. 나머지는 제안이다.
- 확신 없으면 추측하지 않고 댓글로 묻는다.
- 올바른 선택에는 확인 댓글을 남긴다. 침묵은 승인이 아니다.

---

## 즉시 차단해야 하는 항목

아래 중 하나라도 있으면 머지하지 않는다.

- `ResponseStatusException`을 새로 던진다
- `com.fasterxml.jackson.*`을 임포트한다 (Spring Boot 4는 `tools.jackson.*`)
- `new ObjectMapper()`로 직접 생성한다
- `application-local.yaml`, API 키, 비밀번호가 커밋에 포함된다
- 기존 Flyway 마이그레이션 파일을 수정했다
- 소유자 조건 없는 조회 쿼리가 있다

---

## 1. 오류 처리

### 던지는 방법
```java
// 올바름
throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
throw new BusinessException(ErrorCode.INVALID_PHOTO_SELECTION, "유효한 사진 ID가 없습니다.");
throw new BusinessException(ErrorCode.EXTERNAL_API_FAILED, "TourAPI 응답 실패", e); // 원인 버리지 않기

// 금지
throw new ResponseStatusException(HttpStatus.NOT_FOUND, "...");
```

- [ ] 서비스 계층에서 `ResponseStatusException`을 사용하지 않는다.
- [ ] 예외를 감쌀 때 원인(cause)을 버리지 않는다. 세 번째 인자에 `e`를 전달한다.
- [ ] 맞는 `ErrorCode`가 없어 새로 추가했다면, 화면 단위가 아닌 **원인 단위**로 만들었는가.
- [ ] `retryable` 값이 의미에 맞는가. 입력 오류·리소스 없음은 `false`, 일시적 서버 오류는 `true`.

### 현재 정의된 ErrorCode (추가 여부 확인용)
| 코드 | HTTP | 상황 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 요청 값 오류, Bean Validation 위반 |
| `DEVICE_ID_REQUIRED` | 400 | X-Device-Id 헤더 누락 |
| `DEVICE_ID_INVALID` | 400 | X-Device-Id 형식 오류 |
| `INVALID_PHOTO_SELECTION` | 400 | 사진 선택 오류 |
| `INSUFFICIENT_SPOTS` | 400 | 코스 생성 장소 부족 |
| `COURSE_REGION_MISMATCH` | 400 | 지역 불일치 |
| `SAVED_TRIP_CONFLICT` | 409 | 중복 저장 |
| `ANALYSIS_NOT_FOUND` | 404 | 분석 ID 없음 또는 만료 |
| `PLACE_NOT_FOUND` | 404 | 장소 없음 |
| `COURSE_NOT_FOUND` | 404 | 코스 없음 |
| `SHARED_COURSE_NOT_FOUND` | 404 | 공유 코스 없음 또는 만료 |
| `SAVED_TRIP_NOT_FOUND` | 404 | 저장 여행지 없음 |
| `SAVED_TRIP_FORBIDDEN` | **404** | 다른 사용자의 저장 여행지 (정보 노출 방지용 404) |
| `PHOTO_POOL_UNAVAILABLE` | 500 | 사진 풀 로드 실패 |
| `ANALYSIS_FAILED` | 500 | 취향 분석 실패 |
| `RECOMMENDATION_FAILED` | 500 | 추천 생성 실패 |
| `SHARE_CREATION_FAILED` | 500 | 공유 링크 저장 실패 |
| `EXTERNAL_API_FAILED` | 500 | 외부 API 실패 (TourAPI 등) |
| `INTERNAL_ERROR` | 500 | 그 외 내부 오류 |

> `SAVED_TRIP_FORBIDDEN`이 404인 것은 의도적이다. 403을 내면 리소스 존재 여부가 노출된다.

### 로깅
- [ ] 서비스 계층에서 직접 로깅하지 않는다. `GlobalExceptionHandler`가 처리한다.
- [ ] 로그에 요청 본문, 헤더 값, 사진 ID 목록, 디바이스 ID, 임베딩을 남기지 않는다.
- [ ] 원인 추적은 `traceId`로 충분하다.

---

## 2. Jackson / 직렬화

- [ ] `tools.jackson.*`을 쓴다. `com.fasterxml.jackson.*`는 임포트 자체를 금지한다.
- [ ] `ObjectMapper`를 `new ObjectMapper()`로 생성하지 않는다. 생성자 주입으로 받는다.
- [ ] JSON 직렬화·역직렬화에서 예외가 발생하면 적절한 `ErrorCode`로 감싸는가.

---

## 3. DTO

- [ ] DTO는 전부 `record`다.
- [ ] **파일 하나에 record 하나**, 도메인 패키지 바로 아래에 위치한다. 컨트롤러 내 중첩 record 금지.
- [ ] 응답 DTO는 엔티티를 직접 담지 않는다. `static from(Entity)` 팩터리로 변환한다.
- [ ] 요청 DTO에 Bean Validation 어노테이션이 적절히 선언됐는가.
- [ ] 컨트롤러 파라미터에 `@Valid`가 빠지지 않았는가. (`@RequestBody @Valid DtoType dto`)
- [ ] JSON 필드명은 lower camel case다.

---

## 4. 컨트롤러

- [ ] 요청 검증과 서비스 위임만 한다. 비즈니스 로직이 컨트롤러에 없는가.
- [ ] 클래스 레벨 `@RequestMapping`으로 공통 경로를 잡는다.
- [ ] 생성자 주입: `@RequiredArgsConstructor` + `private final`. `@Autowired` 필드 주입 없음.
- [ ] HTTP 상태 코드를 서비스 계층이 알고 있지 않은가. 서비스는 `BusinessException`만 던진다.

---

## 5. Swagger / API 명세

컨트롤러마다 아래 네 가지를 채운다. 하나라도 빠지면 요청한다.

- [ ] `@Tag(name, description)` — 클래스 레벨, 도메인 이름과 한 줄 설명
- [ ] `@Operation(summary, description)` — 메서드 레벨, 동작 규칙과 제약 포함
- [ ] `@ApiResponses` — 성공 + **발생 가능한 오류를 `ErrorCode` 이름과 함께**
  ```java
  @ApiResponse(responseCode = "404", description = "PLACE_NOT_FOUND — 장소 없음")
  ```
- [ ] `@Parameter(description)` — 경로 변수, 쿼리 파라미터마다

> **구현과 설명이 다른 어노테이션은 없는 것보다 나쁘다.** 틀린 명세가 있으면 반드시 수정 요청한다.

---

## 6. 데이터베이스 / Flyway

- [ ] 스키마 변경은 새 마이그레이션 파일로만 한다. **기존 파일 수정은 체크섬 불일치로 앱 기동 실패.**
- [ ] 파일 이름 형식: `V<번호>__<설명>.sql` — 번호가 순서대로 이어지는가.
- [ ] 다른 열린 PR의 마이그레이션 번호와 겹치지 않는가. (겹치면 CI에서 먼저 머지된 쪽을 따른다)
- [ ] 엔티티 필드를 바꿨다면 마이그레이션도 함께 추가됐는가. (`ddl-auto: validate`)
- [ ] 새 컬럼에 `NOT NULL` 제약을 걸었다면 기존 데이터 처리(기본값, 백필)를 고려했는가.

---

## 7. 보안 / 프라이버시

- [ ] 사진 풀 응답에 장소명, 지역명, 분위기 태그, 임베딩이 없다. (사진 선택 단계에서 장소를 노출하지 않는다)
- [ ] 소유자 조건 없는 조회 쿼리가 없다. 익명 사용자가 남의 데이터를 볼 수 없어야 한다.
- [ ] 다른 사용자 소유 리소스 접근 시 403이 아닌 404를 반환하는가. (`SAVED_TRIP_FORBIDDEN` 패턴)
- [ ] `application-local.yaml`, `.env`, API 키, 절대 경로가 코드나 커밋에 없는가.
- [ ] 로그에 API 키, 디바이스 ID 원문, 전체 임베딩 벡터가 없는가.

---

## 8. 테스트

- [ ] 새 API에 성공 케이스와 주요 오류 케이스가 추가됐는가.
- [ ] 통합 테스트는 `IntegrationTestBase`를 상속한다. 직접 `webAppContextSetup`을 호출하지 않는다.
- [ ] PostgreSQL 컨테이너는 `PostgresTestContainer`에서 JVM당 하나만 뜬다. 테스트마다 새로 만들지 않는다.
- [ ] 테스트 이름은 `대상_조건_결과` 형식이다.
  ```
  shareCourse_withInvalidRequest_returns400
  recommendations_withExpiredAnalysisId_returns404
  ```
- [ ] 오류 응답 형태(`code, message, retryable, traceId`)는 `ErrorContractTest`에서 검증한다. 개별 테스트에서 중복 검증하지 않는다.

---

## 9. 미결 항목 (open-questions.md)

PR이 아래 항목을 추측으로 구현했다면 **필수** 코멘트를 남긴다. 팀 합의 없이 닫힌 항목이 아니다.

| 항목 | 상태 | 확인 포인트 |
|---|---|---|
| **Q-01** 익명 사용자 ID 전달 방식 | **차단** | `X-Device-Id` 헤더 방식 외의 구현이 있는가. 소유권 검증 없는 조회가 있는가 |
| **Q-03** 코스 서버 저장 여부 | **차단** | `courseId`를 실제 저장 없이 참조하거나, 공유 API가 요청 본문을 검증 없이 신뢰하는가 |
| Q-02 공유 링크 만료 기간 | 열림 | 만료 로직을 임의로 구현했는가 |
| Q-06 분석 결과 TTL | 열림 | `AnalysisStore` 구현을 외부 캐시로 바꿨다면 이 항목을 먼저 확정해야 한다 |
| Q-07 일부 사진 ID 유효하지 않을 때 | 열림 | "하나라도 유효하면 진행" 정책과 다른 구현이 있는가 |
| Q-09 요청 타임아웃 | 열림 | TourAPI나 DB 호출에 타임아웃 값을 임의로 설정했는가 |

---

## 10. 문서

- [ ] 되돌리기 어려운 기술 선택을 했다면 `docs/decisions/`에 ADR이 추가됐는가.
- [ ] 미결 항목을 확정했다면 `docs/open-questions.md`에서 해당 문서로 옮겼는가.
- [ ] 스키마를 바꿨다면 `docs/data-model.md`를 갱신했는가.
- [ ] 추천·코스 로직을 바꿨다면 `docs/recommendation.md`를 갱신했는가.
- [ ] 전역 API 규칙이 생겼다면 `docs/api-contract.md`를 갱신했는가.
- [ ] 새 개념에 이름을 붙였다면 `docs/glossary.md`를 갱신했는가.

---

## 자주 나오는 실수 (과거 리뷰 기록)

| 실수 | 올바른 방법 | 레퍼런스 |
|---|---|---|
| `import com.fasterxml.jackson.*` | `import tools.jackson.*` | AGENTS.md §2 |
| `throw new ResponseStatusException(...)` | `throw new BusinessException(ErrorCode.X, ...)` | AGENTS.md §5 |
| `new ObjectMapper()` | 생성자 주입 | `CourseService.java` 참고 |
| 컨트롤러에 비즈니스 로직 | 서비스로 이동 | AGENTS.md §4 |
| `@RequestBody DtoType dto` | `@RequestBody @Valid DtoType dto` | PR #19 리뷰 |
| 기존 마이그레이션 파일 수정 | 새 `V<n+1>__<설명>.sql` 추가 | AGENTS.md §8 |
| `@ApiResponses`에 오류 코드 누락 | `ErrorCode` 이름을 `description`에 명시 | AGENTS.md §6 |
| JPA `findAllById()` 반환 순서 의존 | 명시적 정렬 기준 추가 | `CourseService.sortByNearestNeighbor()` |
| 소유권 검증 없는 조회 | `WHERE owner_id = ?` 조건 필수 | Q-01, AGENTS.md §1 |
