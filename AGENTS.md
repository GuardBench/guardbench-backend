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

### 계약 대체와 호환성

- 현재 Issue가 기존 계약, 공개 API, 내부 API 또는 모델을 **명시적으로 대체하거나 제거**하면 별도 요구가 없는 한 이전 내부 API, overload, deprecated shim, legacy 호환 경로를 임의로 보존하지 않는다.
- `최소 변경`은 변경 범위를 최소화하라는 뜻이며 대체된 구 계약을 남겨두라는 뜻이 아니다. 대체가 승인된 범위에서는 기존 호출부와 테스트를 새 계약에 맞게 수정하고 구 API를 제거한다. 호환 shim을 추가해 기존 테스트를 통과시키는 우회는 하지 않는다.
- backward compatibility, source compatibility 또는 data compatibility가 필요하다고 판단되면 임의로 구현하지 않고 필요성과 영향 범위를 먼저 사용자에게 보고한다.
- 이 규칙은 판단 기준 1(현재 Issue와 사용자의 명시적 지시)이 3(테스트와 현재 공개 코드 계약)보다 우선한다는 점을 구체화한다. 새로운 breaking change 권한을 부여하지 않으므로 현재 Issue가 대체를 승인하지 않은 공개 API, DB, 의존성 또는 아키텍처 변경은 그대로 사전 확인한다.
- Issue 또는 사용자가 호환성 보존을 명시적으로 요구하면 그 요구가 이 규칙보다 우선한다.

호환성 보존 또는 추가 여부는 다음 절차로 판단한다.

- 유효한 근거는 현재 공개 API 소비처, 실제 내부 호출부, 명시된 migration/data compatibility 요구, 사용자 또는 Issue의 명시적 backward compatibility 요구, 운영 중인 실제 배포 버전 간 호환 필요성으로 제한한다. 기존 테스트의 존재만으로는 근거가 되지 않는다.
- 구현 전에 대상 경로의 consumer/reference를 검색한다. 공개 API 호출처, 내부 호출부, 설정·스크립트·문서의 참조, migration/data 호환 요구, 운영 배포 버전 정보를 확인하고 검색 범위와 결과(없음 포함)를 Issue 또는 PR에 보고한다.
- 위 근거가 없고 새 계약이 기존 경로를 명시적으로 대체하면 legacy alias, deprecated shim, 호환 경로를 추가하거나 남기지 않는 것을 기본값으로 한다. `최소 변경`은 구 경로를 유지하는 근거가 아니다.
- 다음은 호환성 보존의 금지된 판단 근거다: “기존 테스트가 깨질 수 있으니 일단 alias를 유지”, “혹시 쓰는 곳이 있을 수 있으니 deprecated shim을 보존”, “최소 변경이므로 구 경로도 남김”, 소비처 조사 없이 backward-compatible alias를 추가하는 것.
- 기존 경로가 명시적으로 대체되지 않았거나 호환성 필요성이 의심되면, 공개 계약을 임의로 깨거나 호환 경로를 추측해 추가하지 말고 필요성·영향 범위를 먼저 사용자에게 보고한다.
- 호환성을 유지하기로 결정하면 Issue 또는 PR에 근거, 호환 대상과 영향 범위, 제거 조건 또는 유지 기간을 기록하고 그 계약을 검증하는 테스트를 함께 갱신한다.

## Flyway Migration

DB schema 또는 Flyway migration을 변경할 때 다음 규칙을 따른다.

- dev, performance 등 공유 또는 배포 DB에 한 번이라도 적용된 versioned migration 파일은 immutable로 취급하고 수정·삭제·이름 변경하지 않는다.
- table, column, index, constraint 등 schema 변경은 기존 migration을 고치는 대신 다음 순번의 새로운 versioned migration으로 추가한다.
- 기존 migration 파일을 수정해야 한다고 판단되면 먼저 해당 migration의 공유/배포 DB 적용 여부와 `flyway_schema_history` 영향 범위를 확인하고 사용자에게 보고한다. 적용 여부를 확인하지 않은 채 기존 파일을 수정하지 않는다.
- migration 구현 후 fresh DB에서 전체 migration chain이 처음부터 적용되는 경로와, 직전 schema가 적용된 DB에서 신규 migration만 순차 적용되는 upgrade 경로를 모두 검증한다.
- migration flatten, version history 재작성, checksum을 바꾸는 기존 migration 수정, schema reset, `flyway_schema_history` 초기화는 일반 기능 구현의 일부로 수행하지 않는다. 필요하면 데이터 폐기와 배포 영향까지 포함해 사용자 또는 Issue의 명시적 승인을 먼저 받는다.
- 공유/배포 DB가 존재하는 환경에서는 in-place upgrade compatibility를 기본 전제로 검토한다. 데이터나 migration history를 폐기할 수 있다고 임의로 가정하지 않는다.
- migration PR에는 신규 version, 이전 schema에서의 upgrade 검증 결과, fresh DB 검증 결과, 기존 데이터/배포 DB 영향 여부를 기록한다.

## DDD Aggregate와 Context 경계

- 같은 Bounded Context 안에서 Aggregate 사이에는 객체 참조나 가변 컬렉션 대신 그 Context가 소유한 전용 ID VO를 사용한다.
- 같은 Context에서 다른 Aggregate의 실행 시점 값이 필요하면 불변 값으로 복제한다. Aggregate 간 생성·저장·상태 전이 조율은 해당 Domain의 Application Service가 담당한다.
- Context 경계를 넘을 때는 다른 Context의 Domain 타입·ID VO·Enum·Repository를 직접 재사용하거나 import하지 않는다. 소비 Context가 outbound Port와 scalar/code 값 계약, 로컬 reference/value 타입을 소유한다.
- Integration Adapter만 양쪽 경계를 연결해 값을 명시적으로 변환하며, `common` 또는 공유 Domain 타입으로 이 규칙을 우회하지 않는다.

## 비동기 처리와 Retry

SQS, 외부 Provider, Outbox, Worker 등 retry가 존재하는 경로를 설계·수정할 때 다음 원칙을 따른다. 상세 근거와 테스트 기준은 [비동기 신뢰성 및 테스트 원칙](docs/architecture/async-reliability-and-testing.md)을 참고한다.

- delivery retry와 business retry를 같은 횟수나 상태로 취급하지 않는다.
- retry 전에 해당 작업이 idempotent한지 확인한다.
- transient failure와 permanent failure를 구분한다.
- retry는 bounded해야 하며 exhaustion 이후의 terminal 상태를 정의한다.
- 여러 계층의 retry가 곱셈식으로 증폭되지 않는지 확인한다.
- timeout, visibility timeout, claim lease 등 시간 기반 설정은 독립 값이 아니라 하나의 cross-component contract로 검토한다.
- retry 횟수나 DLQ `maxReceiveCount`를 단순히 늘리는 것을 근본 해결책으로 사용하지 않는다.
- `AlreadyHeld`, partial finalization 같은 정상 중간 상태를 실패/retry 신호로 변환하지 않는다.
- 비동기 상태 머신은 개별 상태 전이의 정확성뿐 아니라 safety와 eventual convergence를 함께 검증한다.

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
