# SAIRO-BACKEND

사진의 분위기로 국내 여행지와 1박 2일 코스를 추천하는 서비스 **사이로(SAIRO)** 의 백엔드다.

사용자는 검색어를 입력하지 않는다. 장소 정보를 가린 채 관광 사진을 고르면, 선택한 사진의
분위기와 어울리는 지역과 코스를 추천한다. 로그인 없이 기기 단위 익명 식별자로 동작한다.

## 문서

| 문서 | 내용 |
|---|---|
| [docs/](./docs/) | **무엇을 왜 만드는가** — 요구사항, API 계약, 데이터 모델, 결정 기록, 미결 항목 |
| [AGENTS.md](./AGENTS.md) | **코드를 어떻게 쓰는가** — 패키지 구조, 계층 규칙, 오류 처리, 테스트 |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | 브랜치 전략, 커밋 규칙, PR 흐름 |
| [CLAUDE.md](./CLAUDE.md) | AI 에이전트용 진입점 (AGENTS.md를 참조한다) |

처음이라면 [docs/README.md](./docs/README.md)에서 시작하면 된다. 어떤 질문에 어떤 문서를 볼지 정리돼 있다.

## 기술 스택

Java 17 · Spring Boot 4.1.0 · Gradle · PostgreSQL + pgvector · Flyway · springdoc-openapi · Testcontainers

## 데이터베이스 스키마

스키마 정본은 `src/main/resources/db/migration/` 아래 Flyway 마이그레이션이다.
변경할 때는 새 `V<번호>__<설명>.sql` 파일을 추가하고, **이미 적용된 파일은 수정하지 않는다.**
자세한 규칙은 [AGENTS.md](./AGENTS.md) 8절에 있다.

## 로컬 실행

### 1. PostgreSQL 준비

테스트 컨테이너와 같은 이미지를 쓴다.

```bash
docker run -d --name sairo-postgres \
  -e POSTGRES_DB=sairo \
  -e POSTGRES_PASSWORD=sairo1234 \
  -p 5433:5432 \
  pgvector/pgvector:pg16
```

스키마는 **Flyway가 애플리케이션 기동 시 자동 적용**한다. 수동으로 DDL을 실행할 필요가 없다.

이미 테이블이 있는 기존 DB라면 Flyway가 기준선만 기록하고 넘어가므로 그대로 두면 된다.

### 2. `application-local.yaml` 작성

`src/main/resources/application-local.yaml` 을 만든다. 이 파일은 `.gitignore` 대상이며 커밋하지 않는다.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/sairo
    username: postgres
    password: sairo1234
    driver-class-name: org.postgresql.Driver

tour-api:
  base-url: https://apis.data.go.kr/B551011/KorService2
  service-key: ${TOUR_API_SERVICE_KEY}
```

TourAPI 키는 팀 채널에서 공유받아 환경 변수로 설정한다.

### 3. 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

## API 문서

서버 실행 후 확인한다. 명세의 정본은 코드에서 생성되는 OpenAPI 문서다.

- Swagger UI — <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON — <http://localhost:8080/v3/api-docs>

## 테스트

Testcontainers가 pgvector 컨테이너를 띄우므로 **Docker가 실행 중이어야 한다.**

```bash
./gradlew test
```

## 오류 응답

모든 API는 오류를 아래 한 가지 형태로 반환한다. 자세한 규칙은 [AGENTS.md](./AGENTS.md) 5절에 있다.

```json
{
  "code": "ANALYSIS_NOT_FOUND",
  "message": "분석 결과를 찾을 수 없습니다.",
  "retryable": false,
  "traceId": "3f8c1a20b4d1"
}
```

`traceId`는 응답 헤더 `X-Trace-Id`와 같은 값이며 서버 로그와 대조할 수 있다.
