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
- 현재 Issue/PR와 무관한 이슈, 다른 worktree, 다른 커밋을 대화 및 리뷰에서 끌어오지 않는다. 관련성이 필요할 때는 현재 Issue와의 관계를 먼저 명시한다.

## DDD Aggregate와 Context 경계

- 같은 Bounded Context 안에서 Aggregate 사이에는 객체 참조나 가변 컬렉션 대신 그 Context가 소유한 전용 ID VO를 사용한다.
- 같은 Context에서 다른 Aggregate의 실행 시점 값이 필요하면 불변 값으로 복제한다. Aggregate 간 생성·저장·상태 전이 조율은 해당 Domain의 Application Service가 담당한다.
- Context 경계를 넘을 때는 다른 Context의 Domain 타입·ID VO·Enum·Repository를 직접 재사용하거나 import하지 않는다. 소비 Context가 outbound Port와 scalar/code 값 계약, 로컬 reference/value 타입을 소유한다.
- Integration Adapter만 양쪽 경계를 연결해 값을 명시적으로 변환하며, `common` 또는 공유 Domain 타입으로 이 규칙을 우회하지 않는다.

## Git과 검증

- Issue 하나당 별도 worktree와 `agent/{issue-number}-{slug}` 브랜치를 사용한다.
- Issue에 명시된 에이전트 권한을 확인한다. 커밋이 허용된 경우에만 관련 테스트를 실행한 뒤 논리적 단위로 로컬 커밋한다.
- Domain/Port, Persistence Adapter, 테스트, 문서 등 독립적으로 검토 가능한 변경은 기본적으로 별도 커밋으로 분리한다. 다수 파일 단일 커밋이 필요한 경우 커밋 전에 이유와 리뷰 단위를 사용자에게 제시한다.
- 커밋 전에 현재 Issue 범위와 변경 파일이 일치하는지 `git status` 및 staged diff로 점검한다.
- push 전에 commit log, 파일 통계(`git diff --stat`), staged/committed diff 범위를 반드시 확인하고 보고한다.
- 이미 push된 커밋을 분리/재구성해야 할 때는 branch/worktree 격리 여부와 `force-with-lease` 필요성 및 위험성을 명확히 안내한다.
- push, PR 생성, 병합, force push는 사람의 명시적 승인 없이 수행하지 않는다.
- 검증하지 않은 결과를 완료했다고 표현하지 않는다.

## 코드 리뷰

- 리뷰 요청은 [에이전트 코드 리뷰](docs/ai-development/review.md)가 `APPROVED` 상태일 때 해당 지침을 따른다.
- 리뷰만 요청받았다면 파일 수정, commit, push, 승인 또는 병합을 수행하지 않는다.
- 발견 사항은 심각도와 차단 여부를 구분하고 결론부터 간결하게 보고한다.

상세 규칙은 [AI 개발 워크플로](docs/ai-development/workflow.md), [개발 컨벤션](docs/README.md#개발-컨벤션), [API 계약](docs/api/README.md)을 따른다.
