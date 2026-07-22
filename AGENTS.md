# AGENTS.md

사이로(SAIRO) 백엔드에서 코드를 작성할 때 따르는 규약이다. 사람과 AI 에이전트가 같은 문서를 본다.

이 문서는 **현재 코드베이스에 실제로 존재하는 패턴**만 기록한다. 앞으로 하고 싶은 일은 여기 쓰지 않는다.

---

## 1. 프로젝트

사진의 분위기로 국내 여행지와 1박 2일 코스를 추천하는 서비스의 백엔드다.

핵심 흐름: `사진 풀 조회 → 취향 분석 → 지역 추천 → 코스 생성 → 저장/공유`

사용자는 로그인하지 않는다. 기기가 만든 익명 식별자로만 구분한다.

### 제품 규칙 중 코드에 직접 영향을 주는 것

- **사진 선택 단계에서 장소 정보를 노출하지 않는다.** 사진 풀 응답에 장소명·지역명·분위기 태그·임베딩을 넣지 않는다.
- **추천 단위는 개별 장소가 아니라 지역이다.**
- **다른 익명 사용자의 데이터에 접근할 수 없어야 한다.** 소유자 조건 없는 조회 쿼리를 만들지 않는다.
- **로그에 API 키, 익명 사용자 ID 원문, 전체 임베딩을 남기지 않는다.**

---

## 2. 기술 스택

| 항목 | 내용 |
|---|---|
| 언어 | Java 17 (toolchain 고정) |
| 프레임워크 | Spring Boot 4.1.0 |
| 빌드 | Gradle (Groovy DSL) |
| DB | PostgreSQL + pgvector (512차원 CLIP 임베딩) |
| 데이터 접근 | Spring Data JPA + JdbcTemplate 혼용 (§4 참고) |
| API 문서 | springdoc-openapi 3.0.3 |
| 테스트 | JUnit 5 + Testcontainers |
| 외부 API | 한국관광공사 TourAPI KorService2 |

### Jackson 3 주의

Spring Boot 4는 **Jackson 3**을 쓴다. 패키지가 `tools.jackson.*`이다.

```java
import tools.jackson.databind.ObjectMapper;   // 올바름
import com.fasterxml.jackson.databind.ObjectMapper;  // 쓰지 않는다
```

`ObjectMapper`는 직접 생성하지 말고 주입받는다. 사용 예: `course/CourseService.java`

---

## 3. 패키지 구조

기술 계층이 아니라 **도메인**으로 먼저 나눈다.

```
com.sairo.sairo_backend
├── common/     오류 계약, 추적 ID 등 전역 공통
├── config/     Spring 설정 클래스
├── photo/      사진 풀
├── analysis/   취향 분석과 추천
├── spot/       장소 엔티티와 조회
├── place/      장소 상세 (TourAPI 보완 포함)
└── course/     코스 생성과 공유
```

새 도메인은 `com.sairo.sairo_backend.<도메인>` 아래에 컨트롤러·서비스·리포지토리·DTO를 함께 둔다.
`controller`, `service`, `dto` 같은 계층 패키지를 따로 만들지 않는다.

---

## 4. 계층 규칙

### 컨트롤러

- 요청 검증과 위임만 한다. 비즈니스 로직을 두지 않는다.
- 클래스 레벨 `@RequestMapping`으로 공통 경로를 잡는다.
  - 예외: `analysis/TasteAnalysisController`는 두 엔드포인트의 최상위 경로가 서로 달라 클래스 레벨 매핑이 없다. 같은 상황이 아니면 따라 하지 않는다.
- 생성자 주입은 Lombok `@RequiredArgsConstructor` + `private final` 필드로 한다. `@Autowired` 필드 주입은 쓰지 않는다.

### 서비스

- 비즈니스 로직과 오류 판단을 담당한다.
- HTTP 상태 코드를 알지 못한다. 오류는 `BusinessException`으로만 던진다 (§5).

### 리포지토리

두 방식을 함께 쓴다. 새 코드에서는 아래 기준으로 고른다.

| 방식 | 쓰는 경우 | 예 |
|---|---|---|
| Spring Data JPA | 엔티티 단순 CRUD | `spot/SpotRepository`, `photo/PhotoRepository` |
| `JdbcTemplate` | 벡터 검색, JSONB, 커서 페이지 등 JPA로 어색한 쿼리 | `photo/PhotoEmbeddingRepository`, `course/SharedCourseRepository` |

`ddl-auto: validate`이므로 **엔티티와 `db/schema.sql`이 어긋나면 애플리케이션이 뜨지 않는다.** 엔티티를 바꾸면 스키마도 함께 바꾼다.

### DTO

- 전부 `record`로 만든다.
- **파일 하나에 하나씩**, 도메인 패키지 바로 아래 둔다. 컨트롤러 안에 중첩 record를 두지 않는다.
- 응답 DTO에 엔티티를 그대로 담지 않는다. `static from(Entity)` 팩터리로 변환한다. 예: `photo/PhotoResponse`
- 요청 DTO 검증은 Bean Validation 어노테이션으로 선언한다. 예: `analysis/TasteAnalysisRequest`
- JSON 필드명은 lower camel case를 쓴다.

---

## 5. 오류 처리

### 계약

모든 오류 응답은 아래 한 가지 형태다. `common/ErrorResponse`

```json
{
  "code": "ANALYSIS_NOT_FOUND",
  "message": "분석 결과를 찾을 수 없습니다.",
  "retryable": false,
  "traceId": "3f8c1a20b4d1"
}
```

- `code` — 클라이언트 분기 기준. `common/ErrorCode` enum 이름과 1:1이다.
- `retryable` — **같은 요청을 그대로 재시도해서 성공할 가능성이 있는가.** 입력 오류와 리소스 부재는 false다.
- `traceId` — 응답 헤더 `X-Trace-Id`와 같은 값이며 서버 로그와 대조할 수 있다.

### 상태 코드

| 상황 | 코드 |
|---|---|
| 잘못된 입력 | 400 |
| 리소스 없음 / 만료 | 404 |
| 충돌 (중복 저장 등) | 409 |
| 서버 오류 | 500 |

### 던지는 방법

```java
throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
throw new BusinessException(ErrorCode.INVALID_PHOTO_SELECTION, "유효한 사진 ID가 없습니다.");
```

- **`ResponseStatusException`을 새로 쓰지 않는다.** 핸들러에 방어선이 남아 있지만 임시 대응용이다.
- 맞는 코드가 없으면 `ErrorCode`에 추가한다. 화면 단위가 아니라 **원인 단위**로 만든다.
- 예외를 감쌀 때는 원인을 버리지 않는다. `new BusinessException(code, "메시지", e)`

### 로그

- 4xx는 `warn`, 5xx는 `error`로 남긴다. `common/GlobalExceptionHandler`가 처리하므로 서비스에서 다시 로깅하지 않는다.
- **요청 본문과 헤더 값을 로그에 넣지 않는다.** 추적은 `traceId`로 한다.

---

## 6. API 명세

**명세의 정본은 코드에서 생성되는 OpenAPI 문서다.** 별도 명세 문서를 두지 않는다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### 작성 규칙

컨트롤러마다 아래를 채운다. 빠지면 명세가 곧바로 낡는다.

| 어노테이션 | 위치 | 내용 |
|---|---|---|
| `@Tag` | 클래스 | 도메인 이름과 한 줄 설명 |
| `@Operation` | 메서드 | `summary`는 한 줄, `description`은 동작 규칙과 제약 |
| `@ApiResponses` | 메서드 | 성공 + **발생 가능한 오류를 `ErrorCode` 이름과 함께** |
| `@Parameter` | 파라미터 | 설명과 `example` |

오류 응답은 `"404", description = "PLACE_NOT_FOUND — 장소 없음"` 형식으로 코드 이름을 앞에 적는다.
클라이언트가 명세만 보고 분기할 수 있어야 한다.

**구현과 설명이 다른 어노테이션을 쓰지 않는다.** 틀린 명세는 없는 명세보다 나쁘다.

---

## 7. 테스트

- 통합 테스트는 `IntegrationTestBase`를 상속한다. Testcontainers가 pgvector 컨테이너를 띄우고 `db/schema.sql`을 적용한다.
- 컨테이너는 `PostgresTestContainer`에서 JVM당 하나만 뜬다. 테스트 클래스마다 새로 만들지 않는다.
- `MockMvc`는 `IntegrationTestBase`가 필터까지 등록해 만든다. 직접 `webAppContextSetup`을 호출하지 않는다.
- 테스트 이름은 `대상_조건_결과` 형식이다. 예: `recommendations_withInvalidAnalysisId_returns404`
- 오류 응답의 **본문 형태**는 `common/ErrorContractTest`에서 한 곳으로 검증한다. 개별 API 테스트는 상태 코드와 도메인 필드에 집중한다.
- 새 API에는 성공 케이스와 주요 오류 케이스를 함께 추가한다.

---

## 8. 데이터베이스

스키마는 **Flyway**로 관리한다. 정본은 `src/main/resources/db/migration/` 아래 마이그레이션 파일이다.

### 규칙

- 파일 이름은 `V<번호>__<설명>.sql` 형식이다. 예: `V2__add_saved_trips.sql`
- **이미 적용된 마이그레이션 파일은 절대 수정하지 않는다.** 체크섬이 달라지면 검증 단계에서 실패한다.
  잘못된 내용은 새 버전 파일로 바로잡는다.
- 스키마 변경은 애플리케이션 기동 시 자동 적용된다. 수동으로 DDL을 실행하지 않는다.
- 엔티티를 바꾸면 마이그레이션도 함께 추가한다. `ddl-auto: validate`라 어긋나면 애플리케이션이 뜨지 않는다.

### baseline

Flyway 도입 전부터 있던 DB에는 V1의 테이블이 이미 존재한다.
`baseline-on-migrate: true`, `baseline-version: 1` 설정으로 이런 DB는 V1을 다시 실행하지 않고 기준선만 기록한다.
빈 DB에는 V1부터 정상 적용된다.

이 동작은 `FlywayBaselineTest`가 검증한다. **설정을 바꾸려면 이 테스트를 먼저 확인한다.**

### 테스트

Testcontainers는 빈 컨테이너를 띄우고 Flyway가 스키마를 만든다.
따라서 마이그레이션 파일 자체가 CI에서 검증된다. initdb 스크립트를 따로 넣지 않는다.

---

## 9. 설정과 비밀값

- `application.yaml` — 공통 설정. 비밀값을 넣지 않는다.
- `application-local.yaml` — 로컬 전용이며 `.gitignore` 대상이다. **절대 커밋하지 않는다.**
- 외부 키는 환경 변수로 주입한다. 예: `service-key: ${TOUR_API_SERVICE_KEY}`
- DB 비밀번호와 절대 경로를 코드에 하드코딩하지 않는다.

---

## 10. Git

`CONTRIBUTING.md`가 정본이다. 요약하면 다음과 같다.

- 브랜치: `feat/이슈번호-기능명`, `fix/이슈번호-버그명`, `chore/작업명`
- 커밋: `타입: 내용 (#이슈번호)` — 타입은 `feat` `fix` `chore` `refactor` `test`
- `main`에 직접 푸시하지 않는다. PR로 올리고 squash merge 한다.
- 커밋과 푸시는 사람이 지시했을 때만 한다.

---

## 11. 문서와 작업 흐름

이 문서는 **코드를 어떻게 쓰는가**를 다룬다.
**무엇을 왜 만드는가**는 [docs/](./docs/)에 있다. 문서 지도는 [docs/README.md](./docs/README.md)다.

| 필요한 것 | 문서 |
|---|---|
| 이 API가 보장해야 하는 동작 | [docs/requirements.md](./docs/requirements.md) |
| 오류 코드, 멱등성, 소유권, 페이지네이션 | [docs/api-contract.md](./docs/api-contract.md) |
| 엔드포인트별 요청·응답 | Swagger `/swagger-ui.html` |
| 테이블·컬럼의 의미 | [docs/data-model.md](./docs/data-model.md) |
| 추천 계산 방식과 한계 | [docs/recommendation.md](./docs/recommendation.md) |
| 용어와 코드 이름 매핑 | [docs/glossary.md](./docs/glossary.md) |
| 이 구조가 왜 이런지 | [docs/decisions/](./docs/decisions/) |
| 아직 안 정해진 것 | [docs/open-questions.md](./docs/open-questions.md) |

### 작업을 시작하기 전에

**[open-questions.md](./docs/open-questions.md)를 먼저 확인한다.**
걸리는 항목이 있으면 **추측해서 구현하지 말고 사용자에게 확인한다.**
익명 사용자 ID 전달 방식, 코스 영속화 여부, 분석 ID TTL 등이 아직 열려 있고
일부는 다른 작업을 막고 있다.

### 작업하면서

- 새 개념에 이름을 붙이기 전에 [glossary.md](./docs/glossary.md)를 본다.
  이미 있는 개념이면 그 이름을 쓴다.
- 전역 규칙이 필요하면 [api-contract.md](./docs/api-contract.md)를 본다.
  거기 없는 새 규칙을 만들었다면 문서에 추가한다.

### 작업을 마치고

문서를 고쳐야 하는 경우는 다음과 같다.

| 한 일 | 고칠 문서 |
|---|---|
| 미결 항목을 확정했다 | open-questions.md에서 지우고 해당 문서로 옮긴다 |
| 되돌리기 어려운 선택을 했다 | [decisions/](./docs/decisions/)에 ADR을 추가한다 |
| 스키마를 바꿨다 | data-model.md |
| 추천·코스 계산을 바꿨다 | recommendation.md |
| 전역 API 규칙을 추가했다 | api-contract.md |
| 새 개념에 이름을 붙였다 | glossary.md |
| 요구사항 대비 격차를 해소했다 | requirements.md §6 표에서 지운다 |

**엔드포인트별 스펙은 문서에 적지 않는다.** 컨트롤러 어노테이션으로 남긴다.
