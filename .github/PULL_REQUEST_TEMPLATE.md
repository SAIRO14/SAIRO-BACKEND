## 관련 이슈
closes #

## 변경 내용
<!-- 무엇을 왜 바꿨는지 -->

## 체크리스트
- [ ] 로컬 빌드 통과 (`./gradlew build`)
- [ ] API 응답 직접 확인
- [ ] `application-local.yaml` 등 민감정보 미포함

### API를 추가하거나 바꿨다면
- [ ] Swagger 어노테이션 작성 — `@Operation`, 발생 가능한 오류를 `ErrorCode` 이름과 함께
- [ ] 오류 응답이 공통 계약(`{code, message, retryable, traceId}`)을 따름

### 문서
<!-- 해당 없으면 지워도 됩니다 -->
- [ ] 미결 항목을 확정했다면 `docs/open-questions.md`에서 옮김
- [ ] 되돌리기 어려운 선택을 했다면 `docs/decisions/`에 ADR 추가
- [ ] 스키마·추천 로직·전역 규칙·용어를 바꿨다면 해당 문서 갱신

### DB를 바꿨다면
- [ ] 새 `V<번호>__<설명>.sql` 파일로 추가 (기존 마이그레이션은 수정하지 않음)
- [ ] 마이그레이션 번호가 다른 PR과 겹치지 않는지 확인
