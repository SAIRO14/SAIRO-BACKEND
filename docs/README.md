# SAIRO 백엔드 문서

이 폴더는 **백엔드가 무엇을 왜 만드는지**를 기록한다.
**코드를 어떻게 쓰는지**는 저장소 루트의 [AGENTS.md](../AGENTS.md)에 있다.

## 무엇을 찾고 있나요

| 질문 | 문서 |
|---|---|
| 이 제품을 왜 만들고 누구를 위한 것인가 | [PRD.md](./PRD.md) |
| 이 API는 무엇을 보장해야 하나 | [requirements.md](./requirements.md) |
| 오류 코드, 멱등성, 페이지네이션 규칙은 | [api-contract.md](./api-contract.md) |
| 엔드포인트별 요청·응답 형태는 | **Swagger UI** — 서버 실행 후 `/swagger-ui.html` |
| 이 테이블·컬럼은 무슨 의미인가 | [data-model.md](./data-model.md) |
| 추천은 어떤 기준으로 계산되나 | [recommendation.md](./recommendation.md) |
| 이 용어가 코드에서 어떤 이름인가 | [glossary.md](./glossary.md) |
| 왜 이렇게 만들었나 | [decisions/](./decisions/) |
| 아직 안 정해진 건 무엇인가 | [open-questions.md](./open-questions.md) |
| 코딩 규약, 패키지 구조, 테스트 | [../AGENTS.md](../AGENTS.md) |
| 브랜치·커밋·PR 규칙 | [../CONTRIBUTING.md](../CONTRIBUTING.md) |
| PR 리뷰어 체크리스트 | [../REVIEW.md](../REVIEW.md) |
| 로컬 실행 방법 | [../README.md](../README.md) |

## 문서의 원칙

**확정된 것만 적는다.** 아직 정해지지 않은 항목은 본문에 추측으로 채우지 않고
[open-questions.md](./open-questions.md)에 모은다. 본문에서 미결 항목을 언급할 때는
`[Q-01]` 처럼 참조만 남긴다.

**결정의 이유는 ADR에 남긴다.** 문서 본문은 "무엇이 정해졌는가"만 서술하고,
"왜 그렇게 정했는가"와 "무엇을 포기했는가"는 [decisions/](./decisions/)에 기록한다.

**한 사실은 한 곳에만 적는다.** 같은 내용을 두 문서에 쓰면 반드시 어긋난다.
다른 문서의 내용이 필요하면 링크한다.

**코드에서 확인할 수 있는 것은 코드를 정본으로 둔다.** 엔드포인트별 요청·응답 스펙은
OpenAPI가 정본이므로 여기에 옮겨 적지 않는다. ([ADR 0002](./decisions/0002-openapi-as-api-spec-source.md))

## 작업 흐름에서 문서를 언제 건드리나

```text
작업 시작
  └─ 관련 요구사항 확인          requirements.md
  └─ 미결 항목에 걸리는지 확인    open-questions.md
       └─ 걸리면: 추측하지 말고 팀에 확인 → 확정되면 문서 이동

구현 중
  └─ 전역 규칙 확인              api-contract.md, AGENTS.md
  └─ 엔드포인트 스펙 작성        컨트롤러 어노테이션 (Swagger)

되돌리기 어려운 선택을 했다면
  └─ ADR 작성                   decisions/

PR 올리기
  └─ 문서 반영 여부 체크          PR 템플릿 체크리스트
```

## 새 문서를 추가할 때

위 표에 한 줄을 추가한다. 표에 없는 문서는 아무도 찾지 못한다.
