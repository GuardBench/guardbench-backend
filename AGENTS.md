# GuardBench Codex 운영 규칙

구현 전에 [문서 지도](docs/README.md)와 작업에 관련된 계약 문서를 읽는다.

## 판단 기준

1. 현재 Issue의 승인된 요구사항과 사용자의 명시적 지시
2. `APPROVED` 상태의 GitHub 구현 계약
3. 테스트와 현재 공개 코드 계약
4. `DRAFT` 상태의 GitHub 문서
5. Notion의 회의·초안·참고자료

- 요청을 충족하는 최소 변경만 수행한다.
- `APPROVED` 계약을 임의로 변경하지 않는다.
- `DRAFT`를 확정 요구사항으로 간주하지 않는다. DRAFT에 의존해야 구현할 수 있으면 미결정을 Issue에 기록하고 중단한다.
- GitHub와 Notion이 충돌하면 GitHub를 우선하고 차이를 보고한다.
- 공개 API, DB, 의존성 또는 아키텍처 변경은 사전 확인한다.
- 기존 미커밋 변경을 보존한다. 출처가 불명확한 변경을 되돌리지 않는다.

## Git과 검증

- Issue 하나당 별도 worktree와 `agent/{issue-number}-{slug}` 브랜치를 사용한다.
- Issue에 명시된 에이전트 권한을 확인한다. 커밋이 허용된 경우에만 관련 테스트를 실행한 뒤 논리적 단위로 로컬 커밋한다.
- push, PR 생성, 병합, force push는 사람의 명시적 승인 없이 수행하지 않는다.
- 검증하지 않은 결과를 완료했다고 표현하지 않는다.

## 코드 리뷰

- 리뷰 요청은 [에이전트 코드 리뷰](docs/ai-development/review.md)가 `APPROVED` 상태일 때 해당 지침을 따른다.
- 리뷰만 요청받았다면 파일 수정, commit, push, 승인 또는 병합을 수행하지 않는다.
- 발견 사항은 심각도와 차단 여부를 구분하고 결론부터 간결하게 보고한다.

상세 규칙은 [AI 개발 워크플로](docs/ai-development/workflow.md), [개발 컨벤션](docs/README.md#개발-컨벤션), [API 계약](docs/api/README.md)을 따른다.
