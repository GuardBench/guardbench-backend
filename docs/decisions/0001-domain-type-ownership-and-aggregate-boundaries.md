# 0001. 도메인 타입 소유권과 Aggregate 경계

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-24
> Canonical source: GitHub
> Origin: [GitHub Issue #3](https://github.com/GuardBench/guardbench-backend/issues/3)
> Approval: [PR #20 정식 승인 리뷰](https://github.com/GuardBench/guardbench-backend/pull/20#pullrequestreview-5005535163)

- ADR Status: ACCEPTED
- Decision date: 2026-08-24
- Related Issue: #3

## Context

도메인별 병렬 구현에서 같은 타입과 Repository Port가 여러 패키지에 생기거나 순환 의존이 생기지 않도록 소유권과 일관성 경계를 정한다.

이 문서에서 Aggregate Root(AR)는 함께 일관성을 지키는 객체 묶음의 진입점, Value Object(VO)는 식별자 없이 값으로 비교하는 불변 타입, Repository Port는 Domain이 정의하는 저장 인터페이스를 뜻한다. 별도 AR은 별도 최상위 도메인 객체라는 뜻이지 별도 DB 트랜잭션을 강제한다는 뜻이 아니다.

결정 범위는 타입·패키지 소유권, AR 경계, 의존 방향, Repository Port, 최상위 패키지와 TestRun 조회 소유권이다. Controller·DTO 상세, JPA·DB·AWS SDK 구현은 다루지 않는다. 전용 ID VO, `Action`, 조회 소유권은 Issue #3 검토 중 필요성이 확인되어 PR #20 승인 범위에 포함됐다.

## Decision

선택지 1인 도메인별 단일 소유와 단방향 의존 구조를 적용한다.

### 타입 소유권과 Aggregate 경계

| 항목 | 선택지 1의 결정 내용 | 경계와 이유 |
| --- | --- | --- |
| `ExpectedResult` | `testdefinition/domain`이 소유하는 불변 Value Object | 사용자가 작성하는 현재 TestCase 정의의 일부다. Snapshot은 그 값을 복제해 보존하되 별도 `ExpectedResult` 타입을 만들지 않는다. |
| `ActualResult` | `testrun/domain`이 소유하는 불변 Value Object | 정규화된 실행 산출물이며 `SUCCEEDED`인 `TestExecution`에만 존재한다. Guardrail Adapter는 AWS 응답을 이 타입으로 변환하고 Evaluation은 읽기 전용 입력으로 사용한다. |
| `Action` | `testdefinition/domain`이 소유하는 `Action` Enum | 값은 `ALLOW`와 `BLOCK`이다. `ExpectedResult`와 `ActualResult`가 같은 타입을 사용한다. |
| `TestSuite`와 `TestCase` | `testdefinition` 안의 별도 Aggregate Root | `TestCase`는 불변 `TestSuiteId`로 소속을 가리키며 `TestSuite`는 가변 TestCase 컬렉션을 Aggregate 내부에 보유하지 않는다. 독립 조회·수정·논리 삭제와 페이지 조회를 지원하고 큰 Aggregate의 동시 수정 충돌을 피한다. |
| `TestRun`과 `TestCaseSnapshot` | `testrun` 안의 별도 Aggregate Root | TestRun은 대상 `TestSuiteId`를 보유한다. Snapshot은 `TestRunId`와 원본 `TestCaseId`를 식별 정보로 보유하고 생성 후 실행 정의를 바꾸지 않는다. TestRun은 Snapshot 객체 컬렉션을 Aggregate 내부에 보유하지 않고 접수 시 고정한 `testCaseCount`를 관리한다. |
| Evaluation 결과 | `evaluation/domain`이 `AssertionResult`, `ChangeResult`, `QualityGateResult`를 소유 | 실행 상태와 정책 판정을 분리한다. Evaluation은 Snapshot의 ExpectedResult와 TestExecution의 ActualResult를 입력으로 사용하지만 두 입력 타입을 다시 정의하지 않는다. |

별도 Aggregate라는 선택은 원자적 저장을 금지하지 않는다. 승인된 API 계약에 따라 TestSuite와 초기 TestCase, TestRun과 Snapshot·OutboxEvent의 생성은 Application Service가 하나의 트랜잭션으로 조정한다. Aggregate 사이에는 객체 참조 대신 식별자를 사용하고, 생성 이후 한 Aggregate의 수정이 다른 Aggregate를 암묵적으로 변경하지 않게 한다.

TestCaseSnapshot은 `name`, `input`, `ExpectedResult`, `severity`, `category`를 값으로 복제한다. 원본 TestCase를 객체로 참조하거나 원본의 수정·삭제를 Snapshot에 전파하지 않는다. 한 TestRun에서 원본 TestCase당 Snapshot이 하나라는 불변식은 접수 유스케이스가 중복 없는 활성 TestCase 집합으로 Snapshot을 만들도록 보장해야 한다. 구체적인 DB 강제 방식은 후속 Persistence 결정의 범위다.

### 전용 식별자 Value Object

각 AR에는 원시 숫자 대신 다음 전용 식별자 VO를 둔다.

- `testdefinition/domain`: `TestSuiteId`, `TestCaseId`
- `testrun/domain`: `TestRunId`, `TestCaseSnapshotId`

이 네 타입은 별도 AR 사이의 객체 참조를 끊고 서로 다른 식별자의 오사용을 컴파일 시점에 방지하기 위해 이 ADR에서 명시적으로 도입한다. 다른 도메인이 식별자를 참조할 때 원시 값이나 같은 의미의 별도 래퍼를 만들지 않고 소유 도메인의 식별자 타입을 사용한다. 따라서 `TestRun`은 `TestSuiteId`를, `TestCaseSnapshot`은 `TestRunId`와 `TestCaseId`를 보유한다.

식별자 VO의 구현 형태를 `class`와 `record` 중 무엇으로 할지는 개발 컨벤션에 따르며, 공통 ID 기반 클래스나 인터페이스는 만들지 않는다. `TestExecution`과 Evaluation 결과는 세부 Aggregate 경계가 미결정이므로 이 ADR만으로 전용 ID를 추가하지 않는다.

### 도메인 객체 소유권 지도

아래 표는 APPROVED 핵심 모델·평가·API 계약의 타입과 이 ADR이 명시적으로 도입한 전용 식별자 VO를 소유 패키지별로 모은 것이다. `명시적으로 결정한 Aggregate Root` 열에 없는 객체의 세부 Aggregate 경계는 확정하지 않는다.

| 소유 패키지 | 명시적으로 결정한 Aggregate Root | 그 밖의 소유 Domain 타입 | 경계와 미결정 사항 |
| --- | --- | --- | --- |
| `testdefinition` | `TestSuite`, `TestCase` | `TestSuiteId`, `TestCaseId`, `ExpectedResult`, `Action`, `Severity` | TestCase의 현재 `name`, `input`, `severity`, `category` 정의를 함께 소유한다. TestSuite와 TestCase는 별도 Aggregate이며 각 식별자와 `Action`도 `testdefinition`이 소유한다. `category`는 승인된 API 계약과 같이 문자열 값으로 둔다. |
| `testrun` | `TestRun`, `TestCaseSnapshot` | `TestRunId`, `TestCaseSnapshotId`, `TestExecution`, `ActualResult`, `TestRunExecutionOutcome` | 실행 수명주기와 실행 상태를 소유한다. `TestExecution`의 세부 Aggregate·저장 경계는 이 ADR에서 확정하지 않는다. |
| `evaluation` | 이 ADR에서 확정하지 않음 | `AssertionResult`, `ChangeResult`, `QualityGateResult`, `AssertionStatus`, `ComparabilityStatus`, `ChangeType`, `QualityGateStatus` | 평가 결과의 세부 Aggregate·저장 경계는 실제 저장 구현 전에 별도 확인한다. |
| `guardrail` | 없음 | MVP에서 소유하는 Core Domain 타입 없음 | Bedrock Guardrails 대상 준비·실행 Adapter와 AWS 응답 Normalizer를 제공한다. |
| `common` | 없음 | MVP에서 소유하는 Domain 타입 없음 | 패키지는 비어 있어도 된다. 검증된 횡단 관심사가 생기기 전에는 타입을 선제 도입하지 않는다. |

객체의 소유 패키지가 정해졌다는 사실만으로 그 객체를 Aggregate Root나 독립 저장 대상으로 간주하지 않는다. 각 Aggregate 식별자는 해당 Aggregate의 소유 패키지에 두며 공통 식별자 계층을 만들지 않는다.

### 규범적 Java 패키지와 파일 위치

아래 경로를 구현 기준으로 사용한다. 기준 Java 패키지는 `com.guardbench`이며, `[AR]`은 Aggregate Root, `[VO]`는 Value Object, `[Port]`는 Domain Repository Port를 뜻한다.

```text
src/main/java/com/guardbench/
├── testdefinition/
│   ├── domain/
│   │   ├── TestSuite.java                         [AR]
│   │   ├── TestSuiteId.java                       [VO: TestSuite 식별자]
│   │   ├── TestCase.java                          [AR]
│   │   ├── TestCaseId.java                        [VO: TestCase 식별자]
│   │   ├── ExpectedResult.java                    [VO]
│   │   ├── Action.java                            [enum: ALLOW, BLOCK]
│   │   ├── Severity.java                          [enum]
│   │   └── repository/
│   │       ├── TestSuiteRepository.java           [Port]
│   │       └── TestCaseRepository.java            [Port]
│   ├── application/                               [두 AR을 조정하는 유스케이스]
│   ├── presentation/controller/
│   ├── presentation/dto/
│   └── infrastructure/persistence/                [Repository Port 구현]
├── testrun/
│   ├── domain/
│   │   ├── TestRun.java                           [AR]
│   │   ├── TestRunId.java                         [VO: TestRun 식별자]
│   │   ├── TestCaseSnapshot.java                  [AR]
│   │   ├── TestCaseSnapshotId.java                [VO: Snapshot 식별자]
│   │   ├── TestExecution.java                     [Domain 타입: AR 여부 미결정]
│   │   ├── ActualResult.java                      [VO]
│   │   ├── TestRunExecutionOutcome.java           [enum]
│   │   └── repository/
│   │       ├── TestRunRepository.java             [Port]
│   │       └── TestCaseSnapshotRepository.java    [Port]
│   ├── application/                               [두 AR과 실행 흐름 조정]
│   │   └── query/                                 [testrun 소유 실행 조회 계약]
│   ├── presentation/controller/                   [모든 TestRun API]
│   ├── presentation/dto/
│   └── infrastructure/persistence/                [Repository Port 구현]
├── evaluation/
│   ├── domain/
│   │   ├── AssertionResult.java                   [Domain 타입: AR 여부 미결정]
│   │   ├── ChangeResult.java                      [Domain 타입: AR 여부 미결정]
│   │   ├── QualityGateResult.java                 [Domain 타입: AR 여부 미결정]
│   │   ├── AssertionStatus.java
│   │   ├── ComparabilityStatus.java
│   │   ├── ChangeType.java
│   │   └── QualityGateStatus.java
│   ├── application/                               [평가 유스케이스]
│   └── infrastructure/
│       ├── query/                                 [testrun 조회 Port 구현과 실행·평가 조합]
│       └── persistence/                           [평가 저장 경계 확정 후 Repository 구현]
├── guardrail/
│   └── infrastructure/bedrock/                    [testrun Port의 Bedrock 구현]
└── common/                                        [MVP Domain 타입 없음]
```

구현할 때 다음 경계를 지킨다.

- AR 사이에는 객체 대신 위 표의 전용 ID를 둔다. 여러 AR을 함께 저장하면 소유 도메인의 Application Service가 Repository Port를 조정한다.
- `Action`은 `testdefinition.domain.Action` 한 곳에만 둔다. ID, Domain Enum과 Repository도 `common`이나 다른 도메인에 복제하지 않는다.
- `TestExecution`과 Evaluation 결과에는 이 ADR만으로 ID나 Repository를 추가하지 않는다. 세부 AR·저장 경계는 후속 결정 사항이다.
- 모든 TestRun API는 `testrun/presentation`이 소유한다. `testrun/application/query`의 조회 전용 Projection Port를 `evaluation/infrastructure/query`가 구현하고, 평가 값은 nullable scalar code로 변환한다. 이 Port를 쓰기 Repository로 사용하지 않는다.
- 빈 디렉터리를 미리 만들지 않고 구현에 필요한 파일만 만든다. `presentation`은 `controller`와 `dto`로 나눈다.

### Aggregate별 구현 규칙

"별도 AR"은 별도 최상위 도메인 클래스와 Repository Port를 둔다는 뜻이며, 반드시 별도 DB 트랜잭션을 사용하라는 뜻은 아니다.

| 관계 | 결정한 구조 | 구현 규칙 |
| --- | --- | --- |
| `TestSuite` - `TestCase` | `testdefinition` 안의 별도 AR | `TestCase`에는 `TestSuiteId`를 저장한다. `TestSuiteRepository`와 `TestCaseRepository`를 분리하고, 함께 생성할 때 `testdefinition/application`이 두 Port를 한 트랜잭션에서 호출한다. |
| `TestRun` - `TestCaseSnapshot` | `testrun` 안의 별도 AR | TestRun에는 대상 `TestSuiteId`, Snapshot에는 `TestRunId`와 원본 `TestCaseId`를 저장한다. 두 Repository Port를 분리하고, 접수 시 `testrun/application`이 TestRun·Snapshot·Outbox 저장을 한 트랜잭션으로 조정한다. |
| `ActualResult` - Evaluation | `ActualResult`는 `testrun`의 VO이고 Evaluation은 읽기 입력으로만 사용 | Guardrail Adapter가 `ActualResult`를 만들고 `evaluation/application`이 `ExpectedResult`와 함께 평가 Domain 타입에 전달한다. |

### 패키지 의존 방향

아래에서 `A -> B`는 A가 B의 공개 Domain 타입에 의존할 수 있음을 뜻한다.

```text
testrun    -> testdefinition   (TestSuiteId, TestCaseId, ExpectedResult, Action, Severity를 사용)
evaluation -> testdefinition   (ExpectedResult와 Action을 평가 입력으로 사용)
evaluation -> testrun          (Snapshot, TestExecution, ActualResult를 사용하고 조회 Port를 구현)
guardrail  -> testdefinition   (ActualResult 정규화 시 Action을 사용)
guardrail  -> testrun          (실행 Port 구현과 ActualResult 정규화)
```

역방향 의존은 허용하지 않는다. 특히 `testdefinition`은 `testrun`, `evaluation`, `guardrail`을 모르고 `testrun`은 `evaluation`이나 구체 Guardrail Adapter를 모른다. TestRun 실행에 필요한 외부 호출 계약은 소비자인 `testrun` 쪽에 두고 `guardrail/infrastructure`가 구현한다. Guardrail Adapter는 `ActualResult`를 만들 때 `testdefinition`의 `Action`을 사용한다. 평가를 시작하고 TestRun 완료 상태를 반영하는 오케스트레이션은 `evaluation/application`이 `testrun`의 공개 Domain/Application 계약을 사용한다.

MVP에서 `common`이 소유하는 Domain 타입은 없다. `common`은 비어 있어도 되며 도메인 의존을 우회하는 허브로 사용하지 않는다. `Action`, `ExpectedResult`, `ActualResult`, 식별자, Repository Port 또는 평가 Enum을 `common/domain`에 두지 않는다. `ApiResponse`, 오류 Envelope, Pagination처럼 여러 경계에서 사용할 수 있는 기술 계약도 Domain 타입이 아니며 이 ADR의 소유권 결정 대상이 아니다. 실제 횡단 관심사가 확인되면 변경 이유와 사용 경계를 검토하고 별도 승인 후 `common` 사용 여부를 결정한다.

### Repository Port 위치

- Aggregate 저장 계약은 소유 도메인의 `domain/repository`에 둔다.
- 선택지 1에 따른 저장 계약은 `testdefinition/domain/repository`의 `TestSuiteRepository`, `TestCaseRepository`와 `testrun/domain/repository`의 `TestRunRepository`, `TestCaseSnapshotRepository`다.
- Evaluation 결과에 독립 저장 수명주기가 필요하면 해당 Aggregate 경계를 후속 구현 Issue에서 확인한 뒤 `evaluation/domain/repository`에 둔다. 이 ADR만으로 결과별 Repository를 선제 생성하지 않는다.
- Repository 구현은 각 소유 도메인의 `infrastructure/persistence`에 둔다.
- 페이지 조회나 화면용 Projection처럼 Aggregate 저장 계약이 아닌 읽기 Port는 소유 도메인의 Application 경계에 둘 수 있으며 Repository로 위장하지 않는다.
- Value Object와 Aggregate 내부 Entity에는 Repository를 만들지 않는다.
- 실행·평가 조합 조회 Port 구현은 `evaluation/infrastructure/query`에 두며 Aggregate Repository로 취급하지 않는다.

### 최상위 패키지 책임

| 패키지 | 책임 | 소유하지 않는 것 |
| --- | --- | --- |
| `testdefinition` | 현재 TestSuite·TestCase 자산, ExpectedResult, Action과 편집 불변식 | Snapshot, 실행 결과, 평가 결과 |
| `testrun` | TestRun 수명주기, 고정 Target, Snapshot, TestExecution, ActualResult, 실행에 필요한 Port와 모든 TestRun API의 조회 Projection | 현재 TestCase 편집, Assertion·Change·Quality Gate 규칙, AWS SDK 타입 |
| `evaluation` | Assertion, 비교 가능성, 변화 분류, Metric, Quality Gate와 TestRun 조회 Port 구현 | 실행 호출, TestRun 수명주기·Presentation, ExpectedResult·ActualResult·Action의 중복 타입 |
| `guardrail` | Bedrock Guardrails 대상 준비·실행 Adapter와 AWS 응답 정규화. MVP에서 소유 Core Domain 타입은 없음 | Core 평가 규칙, TestRun 수명주기, provider 공통 계층 |
| `common` | MVP에서 소유 Domain 타입 없음. 검증·승인된 횡단 관심사가 생길 때만 사용 | 도메인 모델, Repository, 범용 util/helper/service 모음 |

## Alternatives

| 기각한 대안 | 선택하지 않은 이유 |
| --- | --- | --- |
| 결정을 미루거나 공유 타입을 `common/domain`에 집중 | 중복과 순환 의존을 남기거나 변경이 `common`에 집중된다. |
| TestSuite가 TestCase 컬렉션을 소유 | 독립 수정·논리 삭제·페이지 조회 시 큰 AR과 경합을 만든다. |
| TestRun이 Snapshot 컬렉션을 소유 | 불변 Snapshot의 병렬 실행·페이지 조회가 TestRun 전체에 결합된다. |
| Evaluation이 `ActualResult`를 소유 | 실행 실패와 평가 실패가 섞이고 같은 타입이 중복된다. |
| Evaluation이 TestRun 조회 API를 소유 | TestRun 수명주기와 Presentation 책임이 Evaluation으로 번진다. |
| `Action`을 도메인별로 복제 | 값 집합 불일치와 승인되지 않은 `common/domain` 의존을 만든다. |

## Consequences

- 타입의 정의 위치가 하나이고 의존 방향이 단방향이라 중복과 순환을 줄인다. 작은 AR은 독립 수정, 페이지 조회와 병렬 실행의 경합을 줄인다.
- 대신 여러 AR의 원자적 변경은 Application 트랜잭션 조정과 Persistence 제약이 필요하다. 하위 도메인은 상위 도메인의 ID·VO 변경에 컴파일 타임으로 영향을 받는다.
- Projection의 scalar code 매핑은 API 계약 테스트로 검증해야 한다. Evaluation 결과의 AR·저장 단위는 아직 미결정이다.

승인 후 경계를 바꾸려면 기존 ADR을 조용히 수정하지 않고 새 ADR로 supersede한다.

## Validation

다음을 검토했으며 구현 단계에서도 유지한다.

1. Issue #3의 결정 범위 6개 항목, 전용 식별자 VO와 `Action`, APPROVED 핵심 객체의 소유 패키지와 다섯 최상위 패키지 책임이 모두 명시되어 있는지 검토한다.
2. `docs/domain/core-model.md`, `docs/domain/evaluation-contract.md`, `docs/architecture/system-overview.md`, `docs/conventions/package-structure.md`, `docs/api/README.md`와 충돌하지 않는지 검토한다.
3. 의존 허용 목록을 그래프로 따라갔을 때 순환 경로가 없는지 검토한다.
4. `ExpectedResult`, `ActualResult`, `Action`, 각 Repository Port의 소유 위치가 하나인지 검토한다.
5. 평가 값을 포함하는 TestRun 조회 API가 소비자 소유 Projection Port를 통해 `testrun -> evaluation` 의존 없이 구현 가능한지 검토한다.
6. 문서 diff 외에 코드, API, DB, 의존성 변경이 없는지 확인한다.
7. 구현 단계에서 패키지 의존 규칙을 정적 검사나 아키텍처 테스트로 검증한다.
