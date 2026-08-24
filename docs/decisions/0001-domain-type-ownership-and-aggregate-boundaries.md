# 0001. 도메인 타입 소유권과 Aggregate 경계

> Status: DRAFT
> Owner: Backend
> Last reviewed: 2026-08-24
> Canonical source: GitHub
> Origin: [GitHub Issue #3](https://github.com/GuardBench/guardbench-backend/issues/3)
> AI assistance: 이 문서의 초안은 LLM의 도움으로 작성되었으며 사람의 검토가 필요합니다.

- ADR Status: PROPOSED
- Decision date: 미정 (팀 승인 시 기록)
- Related Issue: #3

## Context

GuardBench를 도메인별로 병렬 구현할 때 타입 소유자와 Aggregate 경계가 없으면 `ExpectedResult`, `ActualResult`, Repository Port가 여러 패키지에 중복되고 `testdefinition`, `testrun`, `evaluation` 사이에 순환 의존이 생길 수 있다.

다음 승인 계약을 함께 만족해야 한다.

- Domain은 Spring MVC, JPA, AWS SDK, HTTP DTO에 의존하지 않는다.
- 최상위는 package-by-domain이며 `common`에는 실제 횡단 관심사만 둔다.
- `TestCase`는 현재 정의를, `TestCaseSnapshot`은 TestRun 접수 시점의 불변 실행 정의를 보유한다.
- Baseline과 Candidate 실행은 같은 Snapshot을 사용하며 `ActualResult`는 성공한 `TestExecution`에만 존재한다.
- Assertion, Change, Quality Gate는 실행 결과와 분리된 평가 결과다.
- TestCase는 독립 식별자로 조회·수정·논리 삭제되고 목록은 페이지 단위로 조회된다.
- TestRun 접수 시 TestRun, 활성 TestCase의 Snapshot, OutboxEvent는 하나의 트랜잭션으로 저장되어야 한다.
- MVP를 위해 불필요한 범용 provider 추상화나 중복 Entity를 선제 도입하지 않는다.

이 ADR은 타입과 패키지의 논리적 소유권, Aggregate의 일관성 경계, 컴파일 타임 의존 방향만 다룬다. JPA 매핑, DB 스키마·FK·인덱스, 트랜잭션 구현 방식, Controller·DTO, AWS SDK 구현은 다루지 않는다.

## Decision

아래 내용은 **팀 승인을 요청하는 권고안**이다. 현재 ADR Status가 `PROPOSED`이므로 아직 승인된 구현 계약이 아니다.

### 타입 소유권과 Aggregate 경계

| 항목 | 권고안 | 경계와 이유 |
| --- | --- | --- |
| `ExpectedResult` | `testdefinition/domain`이 소유하는 불변 Value Object | 사용자가 작성하는 현재 TestCase 정의의 일부다. Snapshot은 그 값을 복제해 보존하되 별도 `ExpectedResult` 타입을 만들지 않는다. |
| `ActualResult` | `testrun/domain`이 소유하는 불변 Value Object | 정규화된 실행 산출물이며 `SUCCEEDED`인 `TestExecution`에만 존재한다. Guardrail Adapter는 AWS 응답을 이 타입으로 변환하고 Evaluation은 읽기 전용 입력으로 사용한다. |
| `TestSuite`와 `TestCase` | `testdefinition` 안의 별도 Aggregate Root | `TestCase`는 불변 `testSuiteId`로 소속을 가리키며 `TestSuite`는 가변 TestCase 컬렉션을 Aggregate 내부에 보유하지 않는다. 독립 조회·수정·논리 삭제와 페이지 조회를 지원하고 큰 Aggregate의 동시 수정 충돌을 피한다. |
| `TestRun`과 `TestCaseSnapshot` | `testrun` 안의 별도 Aggregate Root | Snapshot은 `testRunId`와 원본 `testCaseId`를 식별 정보로 보유하고 생성 후 실행 정의를 바꾸지 않는다. TestRun은 Snapshot 객체 컬렉션을 Aggregate 내부에 보유하지 않고 접수 시 고정한 `testCaseCount`를 관리한다. |
| Evaluation 결과 | `evaluation/domain`이 `AssertionResult`, `ChangeResult`, `QualityGateResult`를 소유 | 실행 상태와 정책 판정을 분리한다. Evaluation은 Snapshot의 ExpectedResult와 TestExecution의 ActualResult를 입력으로 사용하지만 두 입력 타입을 다시 정의하지 않는다. |

별도 Aggregate라는 선택은 원자적 저장을 금지하지 않는다. 승인된 API 계약에 따라 TestSuite와 초기 TestCase, TestRun과 Snapshot·OutboxEvent의 생성은 Application Service가 하나의 트랜잭션으로 조정한다. Aggregate 사이에는 객체 참조 대신 식별자를 사용하고, 생성 이후 한 Aggregate의 수정이 다른 Aggregate를 암묵적으로 변경하지 않게 한다.

TestCaseSnapshot은 `name`, `input`, `ExpectedResult`, `severity`, `category`를 값으로 복제한다. 원본 TestCase를 객체로 참조하거나 원본의 수정·삭제를 Snapshot에 전파하지 않는다. 한 TestRun에서 원본 TestCase당 Snapshot이 하나라는 불변식은 접수 유스케이스가 중복 없는 활성 TestCase 집합으로 Snapshot을 만들도록 보장해야 한다. 구체적인 DB 강제 방식은 후속 Persistence 결정의 범위다.

### 패키지 의존 방향

아래에서 `A -> B`는 A가 B의 공개 Domain 타입에 의존할 수 있음을 뜻한다.

```text
testrun    -> testdefinition   (Snapshot이 ExpectedResult를 사용)
evaluation -> testdefinition   (ExpectedResult를 평가 입력으로 사용)
evaluation -> testrun          (Snapshot, TestExecution, ActualResult를 평가 입력으로 사용)
guardrail  -> testrun          (실행 Port 구현과 ActualResult 정규화)
```

역방향 의존은 허용하지 않는다. 특히 `testdefinition`은 `testrun`이나 `evaluation`을 모르고, `testrun`은 `evaluation`이나 구체 Guardrail Adapter를 모른다. TestRun 실행에 필요한 외부 호출 계약은 소비자인 `testrun` 쪽에 두고 `guardrail/infrastructure`가 구현한다. 평가를 시작하고 TestRun 완료 상태를 반영하는 오케스트레이션은 `evaluation/application`이 `testrun`의 공개 Domain/Application 계약을 사용한다.

`common`은 위 도메인 의존을 우회하는 허브로 사용하지 않는다. `ExpectedResult`, `ActualResult`, 식별자, Repository Port 또는 평가 Enum을 `common/domain`에 두지 않는다. 실제로 여러 도메인에 동일한 의미와 변경 이유를 가진 횡단 관심사가 확인될 때만 별도 승인 후 `common`을 사용한다.

### Repository Port 위치

- Aggregate 저장 계약은 소유 도메인의 `domain/repository`에 둔다.
- 권고안에 따른 저장 계약은 `testdefinition/domain/repository`의 `TestSuiteRepository`, `TestCaseRepository`와 `testrun/domain/repository`의 `TestRunRepository`, `TestCaseSnapshotRepository`다.
- Evaluation 결과에 독립 저장 수명주기가 필요하면 해당 Aggregate 경계를 후속 구현 Issue에서 확인한 뒤 `evaluation/domain/repository`에 둔다. 이 ADR만으로 결과별 Repository를 선제 생성하지 않는다.
- Repository 구현은 각 소유 도메인의 `infrastructure/persistence`에 둔다.
- 페이지 조회나 화면용 Projection처럼 Aggregate 저장 계약이 아닌 읽기 Port는 소유 도메인의 Application 경계에 둘 수 있으며 Repository로 위장하지 않는다.
- Value Object와 Aggregate 내부 Entity에는 Repository를 만들지 않는다.

### 최상위 패키지 책임

| 패키지 | 책임 | 소유하지 않는 것 |
| --- | --- | --- |
| `testdefinition` | 현재 TestSuite·TestCase 자산, ExpectedResult와 편집 불변식 | Snapshot, 실행 결과, 평가 결과 |
| `testrun` | TestRun 수명주기, 고정 Target, Snapshot, TestExecution, ActualResult, 실행에 필요한 Port | 현재 TestCase 편집, Assertion·Change·Quality Gate 규칙, AWS SDK 타입 |
| `evaluation` | Assertion, 비교 가능성, 변화 분류, Metric과 Quality Gate | 실행 호출, 현재 TestCase 편집, ExpectedResult·ActualResult의 중복 타입 |
| `guardrail` | Bedrock Guardrails 대상 준비·실행 Adapter와 AWS 응답 정규화 | Core 평가 규칙, TestRun 수명주기, provider 공통 계층 |
| `common` | 검증된 횡단 관심사만 수용 | 도메인 모델, Repository, 범용 util/helper/service 모음 |

## Alternatives

### 전체 구조 선택지 비교

| 선택지 | 타입 단일 소유 | Aggregate 명확성 | 순환 의존 위험 | 병렬 구현 적합성 | MVP 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1. 도메인별 소유와 단방향 의존 확정 | 높음 | 높음 | 낮음 | 높음 | 높음 |
| 2. 공유 타입을 `domain/common`에 집중 | 겉보기에는 높음 | 낮음 | common을 통한 간접 결합이 커짐 | 초기에는 높으나 변경 충돌이 집중됨 | 낮음 |
| 3. 구현 시점까지 경계 미확정 | 낮음 | 낮음 | 높음 | 낮음 | 낮음 |

권고안은 선택지 1이다. 타입의 변경 이유가 있는 도메인에 소유권을 주고 필요한 방향으로만 의존하면 중복 없이 응집도를 유지할 수 있다. 선택지 2는 `common/domain`을 금지하는 승인된 패키지 계약과 충돌하고, 선택지 3은 Issue가 해결하려는 병렬 구현 충돌을 그대로 남긴다.

### Aggregate 세부 대안

`TestSuite`가 모든 TestCase를 자식 Entity로 소유하는 대안은 한 객체에서 소속을 표현하기 쉽다. 그러나 TestCase가 독립 URL과 식별자로 수정·삭제되고 목록 전체를 Aggregate와 함께 읽지 않는 승인된 API 계약에 맞지 않으며, TestCase가 늘어날수록 불필요한 로딩과 동시 수정 충돌이 커진다.

`TestRun`이 모든 Snapshot을 자식 Entity 컬렉션으로 소유하는 대안은 접수 시점의 일관성을 한 Aggregate로 표현하기 쉽다. 그러나 Snapshot별 비동기 실행과 페이지 단위 결과 조회에서 TestRun 전체가 경합 지점이 된다. Snapshot은 생성 후 불변이고 원본 TestCase와 수명주기가 분리되므로 `testrun` 안의 별도 Aggregate가 더 작은 일관성 경계를 제공한다.

Evaluation이 `ActualResult`를 소유하는 대안은 evaluator 입력을 한 패키지에 모을 수 있다. 하지만 ActualResult가 없는 실행 실패와 평가 실패를 혼동시키고, 실행 결과를 평가 구현에 종속시킨다. `ActualResult`는 TestExecution과 함께 `testrun`이 소유하고 Evaluation은 소비하는 편이 실행과 판정의 승인된 분리를 유지한다.

## Consequences

장점은 다음과 같다.

- 핵심 타입의 정의 위치가 하나여서 후속 작업자가 같은 개념을 중복 생성하지 않는다.
- `testdefinition -> testrun` 또는 `testrun -> evaluation` 같은 역방향 의존을 금지해 순환 경로를 정적으로 차단할 수 있다.
- TestCase와 Snapshot을 작은 Aggregate로 유지해 페이지 조회와 병렬 실행에서 큰 객체 그래프나 단일 경합 지점을 피한다.
- AWS SDK 변경이 Guardrail Adapter 밖으로 전파되지 않고 평가 규칙도 실행 Adapter와 분리된다.

비용과 위험은 다음과 같다.

- 여러 Aggregate를 함께 생성해야 하는 유스케이스는 Application 트랜잭션 조정이 필요하다.
- `testrun`은 Snapshot의 ExpectedResult를 재사용하기 위해 `testdefinition`의 안정적인 Domain 타입에 컴파일 타임으로 의존한다.
- TestCase 소속과 Snapshot 유일성처럼 Aggregate 사이의 불변식은 단일 객체만으로 보장할 수 없으므로 Application 검증과 후속 Persistence 제약을 함께 설계해야 한다.
- Evaluation 결과의 세부 Aggregate와 저장 단위는 이 ADR에서 확정하지 않으므로 실제 저장 구현 전에 별도 확인이 필요하다.

승인 전에는 이 제안을 자유롭게 수정하거나 폐기할 수 있다. 승인 후 경계를 바꾸려면 기존 ADR을 조용히 수정하지 않고 새 ADR로 supersede한다.

## Validation

문서 승인 전에는 다음을 확인한다.

1. Issue #3의 결정 범위 6개 항목과 다섯 최상위 패키지 책임이 모두 명시되어 있는지 검토한다.
2. `docs/domain/core-model.md`, `docs/domain/evaluation-contract.md`, `docs/architecture/system-overview.md`, `docs/conventions/package-structure.md`, `docs/api/README.md`와 충돌하지 않는지 검토한다.
3. 의존 허용 목록을 그래프로 따라갔을 때 순환 경로가 없는지 검토한다.
4. `ExpectedResult`, `ActualResult`, 각 Repository Port의 소유 위치가 하나인지 검토한다.
5. 문서 diff 외에 코드, API, DB, 의존성 변경이 없는지 확인한다.

팀이 권고안을 승인할 때만 다음 변경을 수행한다.

- 문서 `Status`를 `APPROVED`, ADR Status를 `ACCEPTED`로 변경한다.
- 실제 승인 날짜를 `Decision date`와 `Last reviewed`에 기록한다.
- 후속 Feature Issue가 이 ADR을 승인 근거로 참조하게 한다.
- 구현 단계에서 패키지 의존 규칙을 정적 검사나 아키텍처 테스트로 검증한다.
