# CLAUDE.md

이 저장소의 코딩 규약 정본은 **[AGENTS.md](./AGENTS.md)** 다. 작업을 시작하기 전에 먼저 읽는다.

규약을 여기 옮겨 적지 않는다. 두 문서가 어긋나면 AGENTS.md가 우선한다.
규약이 바뀌면 AGENTS.md만 고친다.

요구사항·데이터 모델·결정 기록은 [docs/](./docs/)에 있다. 문서 지도는 [docs/README.md](./docs/README.md)다.

**작업을 시작하기 전에 [docs/open-questions.md](./docs/open-questions.md)를 확인한다.**
아직 정해지지 않은 항목이 여럿이고 일부는 다른 작업을 막고 있다. 걸리면 추측하지 말고 사용자에게 묻는다.

## 자주 쓰는 명령

```bash
# 테스트 (Testcontainers가 Docker를 사용한다)
./gradlew test

# 빌드
./gradlew build

# 로컬 실행 — application-local.yaml 필요
./gradlew bootRun --args='--spring.profiles.active=local'
```

실행 후 Swagger UI는 `http://localhost:8080/swagger-ui.html` 에서 확인한다.

## 특히 자주 놓치는 것

- Jackson 3을 쓴다. `com.fasterxml.jackson`이 아니라 `tools.jackson`이다.
- 오류는 `ResponseStatusException`이 아니라 `BusinessException` + `ErrorCode`로 던진다.
- 스키마는 Flyway로 관리한다. `db/migration/V<번호>__<설명>.sql`을 추가하고, **이미 적용된 파일은 고치지 않는다.**
- `ddl-auto: validate`라 엔티티와 마이그레이션이 어긋나면 애플리케이션이 뜨지 않는다.
- 미결정(TBD) 항목은 추측해서 구현하지 말고 사용자에게 먼저 확인한다.

자세한 배경은 모두 AGENTS.md에 있다.
