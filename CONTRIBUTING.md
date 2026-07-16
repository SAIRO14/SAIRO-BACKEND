# 개발 워크플로우

## 브랜치 전략

```
main          — 배포 가능한 상태만 유지
feat/이슈번호-기능명  — 기능 개발
fix/이슈번호-버그명   — 버그 수정
chore/작업명         — 설정, 문서, 리팩터링 등
```

예시:
- `feat/3-photo-api`
- `fix/7-recommendation-null`
- `chore/gitignore-update`

## 작업 흐름

1. **이슈 생성** — GitHub Issues에서 feature 또는 bug 템플릿으로 등록
2. **브랜치 생성** — `main`에서 분기
   ```bash
   git switch main && git pull
   git switch -c feat/이슈번호-기능명
   ```
3. **개발 → 커밋**
   ```bash
   git commit -m "feat: 사진 풀 조회 API 구현 (#3)"
   ```
4. **PR 생성** — `main` 대상, PR 템플릿 작성 후 `closes #이슈번호` 명시
5. **리뷰 → 머지** — Squash merge 사용

## 커밋 메시지 규칙

| 타입 | 용도 |
|------|------|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `chore` | 설정, 의존성, 문서 |
| `refactor` | 동작 변경 없는 코드 개선 |
| `test` | 테스트 추가/수정 |

형식: `타입: 내용 (#이슈번호)`

## 보안 원칙

- `application-local.yaml`, `.env`, `*.pem`, `*.json` 데이터 파일은 절대 커밋 금지
- API 키는 `application-local.yaml`에만 작성 (`.gitignore` 적용됨)
- PR 체크리스트의 민감정보 미포함 항목 반드시 확인 후 머지

## 로컬 실행

```bash
# PostgreSQL 컨테이너 시작
docker start sairo-postgres

# 빌드 및 실행 (application-local.yaml 필요)
./gradlew bootRun --args='--spring.profiles.active=local'
```
