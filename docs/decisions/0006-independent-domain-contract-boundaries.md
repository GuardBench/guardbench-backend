# 0006. 독립 도메인 개발을 위한 경계 계약과 Java 타입 격리

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-25
> Canonical source: GitHub
> Origin: [GitHub Issue #54](https://github.com/GuardBench/guardbench-backend/issues/54)

- ADR Status: ACCEPTED
- Decision date: 2026-08-25
- Related Issue: #54
- Supersedes in part: ADR 0001, ADR 0002, ADR 0003, ADR 0004, ADR 0005의 경계 간 Java 타입 재사용과 직접 패키지 의존 결정
- Superseded in part by: [ADR 0013](0013-response-behavior-classifier.md) — Target Adapter를 classifier Adapter 역할로 전환. 소비자 소유 Port와 타입 격리는 유지

## Context

GuardBench MVP는 하나의 Spring Boot 애플리케이션으로 배포하지만 `testdefinition`, `testrun`, `evaluation`을 서로의 구현 완료를 기다리지 않고 병렬 개발해야 한다. 목표는 즉시 물리적인 MSA로 분리하는 것이 아니라, 각 도메인이 다른 도메인의 Java 구현 타입 없이 승인된 경계 명세만으로 자신의 Core를 구현하고 테스트할 수 있게 하는 것이다.

ADR 0001은 순환 의존을 막기 위해 단방향 의존을 선택했지만 다음 Java Domain 타입을 경계 밖에서도 그대로 재사용하도록 결정했다.

```text
testrun    -> testdefinition   (ID, ExpectedResult, Action, Severity)
evaluation -> testdefinition   (ExpectedResult, Action)
evaluation -> testrun          (Snapshot, TestExecution, ActualResult, ID)
classifier -> testdefinition/testrun
```

이 구조는 의존 방향이 단방향이어도 공급 도메인의 소스가 없으면 소비 도메인을 컴파일할 수 없게 한다. 실제로 Issue #9의 TestRun Core를 구현하려면 Issue #7이 소유한 기반 Java 타입이 먼저 필요하다는 작업 순서 결합이 생겼다. 또한 같은 값이라도 현재 편집 모델, 실행 시점 Snapshot, 평가 입력은 수명주기와 변경 이유가 다른데 하나의 Java 타입을 공유하면 한 모델의 변경이 다른 모델로 전파된다.

Aggregate 사이에서 객체 참조 대신 ID를 사용한다는 규칙과 Bounded Context 사이에서 같은 ID VO 클래스를 공유한다는 규칙은 서로 다르다. 전자는 같은 모델 경계 안의 Aggregate 결합을 제한하지만, 후자는 독립 모델 사이에 컴파일 타임 결합을 만든다.

## Decision

### 독립 모델 경계와 목표

- `testdefinition`, `testrun`, `evaluation`을 독립적인 Domain 모델과 변경 이유를 갖는 Bounded Context로 취급한다.
- 세 Context는 같은 프로세스와 데이터베이스에 배포할 수 있지만 서로의 Domain Java 타입 없이 Core를 구현하고 단위 테스트할 수 있어야 한다.
- `classifier`는 별도 Core Domain이나 Bounded Context가 아니라 `testrun`이 소유한 외부 실행 Port를 구현하는 Infrastructure Adapter다.
- 독립 개발의 기준은 다른 Context 구현의 선행 완료가 아니라 승인된 경계 명세의 존재다. 실제 연결은 별도 Integration Adapter 작업에서 수행한다.

### Java 의존 규칙

다른 Bounded Context의 다음 Java 타입을 직접 import하지 않는다.

- Aggregate, Entity, Value Object와 Domain Service
- Domain ID VO와 Enum
- Repository Port와 Persistence Model
- 다른 Context의 Application Service 구현

한 Context의 Domain과 Application Core는 자기 Context의 타입과 자신이 소유한 Port 계약에만 의존한다. `common/domain`이나 공유 Domain artifact로 타입을 옮겨 이 규칙을 우회하지 않는다.

경계 연결을 담당하는 Integration Adapter만 다음 양쪽을 알 수 있다.

- 소비 Context가 소유한 outbound Port와 값 기반 계약
- 공급 Context가 공개한 Application API 또는 외부 프로토콜 계약

Integration Adapter는 양쪽 값을 명시적으로 변환하고 Domain 객체를 그대로 통과시키지 않는다. 공급 Context의 Repository를 직접 호출하지 않으며 공급 Context의 Application 경계를 사용한다.

### 소비자 소유 Port와 값 기반 계약

필요한 기능과 입력 형태는 소비 Context가 outbound Port로 정의한다. 공급 Context가 자신의 Domain 타입을 소비자에게 제공하는 방식으로 계약을 만들지 않는다.

```text
Consumer Domain/Application Core
        |
        v
Consumer-owned outbound Port + value contract
        ^
        |
Integration Adapter
        |
        v
Provider Application API or external protocol
```

Port 계약은 불변 값으로 만들고 경계를 넘는 식별자와 Enum은 scalar 또는 안정적인 code로 표현한다. 공급자의 ID VO나 Enum 클래스를 필드 타입으로 사용하지 않는다. 잘못된 식별 값, 알 수 없는 code와 필수 값 누락은 Integration Adapter 또는 소비 Application의 경계 validation에서 거부한다.

공급 Context가 공개 Application API를 가질 수는 있지만 소비 Context의 Core가 그 Java 타입을 직접 import하지 않는다. 공급 API 호출과 소비 Port 구현을 결합하는 코드는 Integration Adapter에만 둔다.

### TestDefinition에서 TestRun Snapshot 생성

TestRun 접수에 필요한 현재 TestCase 조회는 소비자인 `testrun`이 소유한 outbound Port로 표현한다. 구현 시 구체 이름은 다음 형태를 기준으로 한다.

```text
testrun/application/port/out/
├── LoadTestCaseSnapshotSourcesPort.java
└── TestCaseSnapshotSource.java
```

`TestCaseSnapshotSource`는 최소한 다음 값을 포함하는 불변 Application 계약이다.

- 원본 TestSuite와 TestCase의 scalar 식별 값
- `name`, `input`, `category`
- expected action code와 severity code

`testrun/infrastructure/integration/testdefinition`의 Adapter가 TestDefinition Application API를 호출해 이 계약으로 변환한다. TestDefinition 구현이 준비되지 않은 동안 #9는 fake Port로 TestRun Core와 Application 흐름을 독립 검증할 수 있다.

TestRun은 전달받은 값을 자신의 불변 Snapshot 모델로 변환한다.

- `TestCaseSnapshot`은 TestRun이 소유한 expected result, action과 severity 타입을 사용한다.
- 원본 식별 값은 `SourceTestSuiteId`, `SourceTestCaseId`처럼 TestRun이 소유한 reference VO로 표현한다. 구체 이름은 구현 시 문맥을 유지하는 범위에서 조정할 수 있다.
- 원본 TestCase 객체나 `testdefinition.domain`의 ID, `ExpectedResult`, `Action`, `Severity`를 보유하지 않는다.
- 원본의 수정과 삭제는 이미 생성된 Snapshot에 전파되지 않는다.

같은 값 집합이라는 사실만으로 하나의 Java 타입을 공유하지 않는다. 로컬 타입 복제는 기계적인 중복이 아니라 각 Context의 의미, validation과 변경을 격리하기 위한 모델링이다.

### Evaluation 경계

Evaluation은 TestRun의 Aggregate, 실행 VO와 ID VO를 직접 import하지 않는다.

- `evaluation/application`이 평가에 필요한 실행 사실 조회 Port와 값 기반 입력 계약을 소유한다.
- Integration Adapter가 TestRun Application API 또는 승인된 메시지/조회 계약에서 실행 사실을 읽고 Evaluation 입력으로 변환한다.
- Evaluation Domain은 expected action, actual action과 평가 대상 reference를 로컬 타입으로 변환해 판정한다.
- `SnapshotEvaluation`과 `QualityGateResult`의 식별자는 기존 `snapshot_id`, `test_run_id` 값과 대응하지만 Evaluation이 소유한 reference VO로 표현한다.
- Evaluation 정책, 입력 모델과 결과 구조가 바뀌어도 TestRun Core는 변경하지 않는다.

TestRun 최종화도 Evaluation이 소유한 outbound Port를 통해 요청하고 Integration Adapter가 TestRun Application 경계에 연결한다. ADR 0004의 QualityGateResult 저장과 TestRun `FINISHED` 전환을 하나의 PostgreSQL 트랜잭션으로 처리하는 불변식은 유지한다. Port 도입은 트랜잭션 경계를 분리한다는 뜻이 아니다.

공개 TestRun 조회에서 평가 값을 조합할 때도 조회 소비자가 필요한 Projection Port를 소유하고 Integration Adapter가 scalar projection으로 변환한다. 어느 쪽도 상대 Context의 Domain 객체를 반환하지 않는다.

### Classifier Adapter 경계

외부 classifier 호출 계약은 소비자인 `testrun/application`이 소유한다. `classifier/infrastructure` Adapter는 해당 Port를 구현하고 AWS SDK 요청·응답을 Port의 값 계약으로 변환한다.

- TestRun Core는 classifier Adapter나 AWS SDK를 알지 못한다.
- Classifier Adapter는 TestDefinition Domain 타입을 사용하지 않는다.
- Adapter가 반환한 action code와 실행 값은 TestRun Application 경계에서 검증한 뒤 TestRun 소유 `ActualResult`로 변환한다.
- Classifier 패키지는 Core Domain 타입, Repository 또는 범용 provider 계층을 새로 소유하지 않는다.

### 같은 Context 안의 Aggregate 참조

같은 Bounded Context 안에서는 기존 Aggregate 규칙을 유지한다.

- Aggregate 사이에는 객체나 가변 컬렉션 대신 그 Context가 소유한 ID VO를 사용한다.
- 다른 Aggregate의 실행 시점 값이 필요하면 소유 Context 안에서 불변 복제하거나 Application Service가 조율한다.
- Repository Port는 저장 대상 Aggregate의 소유 Context에 둔다.

따라서 TestDefinition 안의 `TestCase -> TestSuiteId`, TestRun 안의 `TestExecutionId -> TestCaseSnapshotId`는 허용된다. 반면 TestRun이 TestDefinition의 `TestCaseId`를 직접 보유하거나 Evaluation이 TestRun의 `TestRunId`를 Java 타입으로 사용하는 것은 금지한다.

### 패키지와 검증

MVP에서는 이 결정을 위해 Gradle 멀티모듈 전환을 선행하지 않는다. 현재 package-by-domain 구조를 유지하고 다음 아키텍처 테스트로 경계를 검증한다.

- 한 Context의 `domain`은 다른 Context의 `domain`, `application`, `infrastructure`, `presentation`에 의존하지 않는다.
- 한 Context의 Application Core는 다른 Context의 Domain 또는 Application 구현에 의존하지 않는다.
- 경계 간 의존은 승인된 Integration Adapter 패키지에서만 허용한다.
- Integration Adapter는 소비자 소유 Port를 구현하고 양쪽 Domain 객체를 노출하지 않는다.
- `classifier/infrastructure`는 `testrun/application`의 외부 실행 Port만 구현하며 `testdefinition.domain`을 참조하지 않는다.

실제 Gradle subproject 분리와 별도 artifact/version 관리는 독립 배포 또는 빌드 격리가 필요해질 때 후속 Decision으로 검토한다. 단일 Gradle project라는 이유로 Domain 타입 직접 공유를 허용하지 않는다.

### 기존 ADR에서 유지하는 결정

이 ADR은 다음 결정을 변경하지 않는다.

- ADR 0001의 Aggregate Root, Aggregate 내부 참조와 Repository 소유권
- ADR 0002의 PostgreSQL 테이블, PK/FK, 컬럼과 트랜잭션 물리 계약
- ADR 0003의 `TestExecution`, `SnapshotEvaluation`, `QualityGateResult` Aggregate와 write-side Repository 경계
- ADR 0004의 QualityGateResult와 TestRun `FINISHED` 원자적 저장 및 재호출 의미
- ADR 0005의 Outbox, SQS 메시지, Worker, claim, retry와 최종화 실행 계약

기존 문서에서 `TestCaseSnapshotId`, `TestRunId` 같은 이름을 물리 PK와 업무상 식별 의미로 사용할 수는 있다. 그러나 다른 Context가 해당 소유자의 Java ID VO 클래스를 그대로 import하라는 의미로 해석하지 않는다.

## Superseded Decisions

| 기존 ADR | 대체하는 결정 | 유지하는 결정 |
| --- | --- | --- |
| ADR 0001 | Snapshot이 TestDefinition VO/Enum을 재사용한다는 결정, 경계 밖 ID VO 재사용, Domain Java 타입 기반 패키지 의존 방향 | Aggregate 경계, Context별 Repository 소유권, `common/domain` 금지 |
| ADR 0002 | Evaluation Persistence가 TestRun ID VO를 Java 타입으로 재사용한다는 해석, Evaluation Application이 TestRun Repository를 직접 사용하는 구현 지시 | 물리 PK/FK, 테이블, 트랜잭션과 JPA/Flyway 결정 |
| ADR 0003 | Evaluation Repository가 TestRun ID VO를 직접 사용하는 Java signature, `evaluation -> testrun` Domain/Application 직접 의존 | 세 결과 Aggregate, 내부 결과, Repository 수와 저장 단위 |
| ADR 0004 | Evaluation Application이 TestRun Domain/Repository를 직접 호출하는 구현 구조 | 최종화 책임, 원자적 commit, 상태 불변식과 멱등성 |
| ADR 0005 | `evaluation -> testrun`을 Java Domain 의존으로 유지한다는 문구 | 비동기 런타임, 메시지, 처리·재시도·최종화 순서 |

## Alternatives

### 기존 단방향 Java 타입 공유 유지

중복 타입과 mapping은 줄지만 공급자의 타입 구현과 변경에 소비자가 묶인다. #7과 #9 같은 병렬 Core 구현이 불가능해지므로 선택하지 않는다.

### 공유 Domain 또는 contract artifact 도입

하나의 artifact로 컴파일 순서를 고정할 수 있지만 공통 모델의 변경이 모든 Context에 전파되고 소유권이 흐려진다. 현재는 소비자 소유 Port와 로컬 모델로 충분하므로 도입하지 않는다. 실제 외부 프로토콜의 published language와 버전 관리가 필요하면 별도 Decision으로 검토한다.

### 공급자 소유 Application DTO를 소비자 Core가 직접 사용

Domain 객체 공유보다는 약하지만 소비자 Core가 공급자 구현과 릴리스 순서에 의존한다. 공급 API 타입은 Integration Adapter까지만 허용하고 소비자 Core에는 전달하지 않는다.

### 즉시 Gradle 멀티모듈 또는 MSA로 분리

물리적 컴파일·배포 격리는 강해지지만 현재 문제를 해결하는 데 필요한 범위를 넘고 빌드·배포 복잡도를 늘린다. 우선 코드 경계와 아키텍처 테스트를 적용한다.

## Consequences

장점은 다음과 같다.

- #7, #8, #9가 상대 Domain 구현 없이 승인된 경계 명세와 fake Port로 병렬 개발될 수 있다.
- Snapshot과 Evaluation 모델이 공급 모델의 변경에서 격리된다.
- Domain 간 의존이 Java import가 아니라 명시적인 Port, scalar contract와 mapping으로 드러난다.
- 미래의 프로세스 또는 서비스 분리가 Application/Integration 경계 교체로 제한된다.

비용과 위험은 다음과 같다.

- Context별 로컬 ID reference, Enum과 VO 및 명시적 mapping 코드가 늘어난다.
- 같은 code 집합이 어긋날 수 있으므로 contract validation과 통합 테스트가 필요하다.
- Integration Adapter가 양쪽 Context를 아는 허용된 결합 지점이 된다.
- 단일 Gradle project에서는 아키텍처 테스트가 경계 위반 방지의 핵심 수단이다.

이 결정을 되돌리거나 공유 contract artifact, Gradle 멀티모듈 또는 서비스 경계를 도입하려면 측정된 개발·운영 비용을 근거로 새 ADR에서 supersede한다.

## Validation

1. #9의 TestRun Domain과 Application Core가 `testdefinition.domain` 없이 컴파일되고 fake Snapshot source Port로 테스트되는지 검증한다.
2. #8의 Evaluation Core가 `testdefinition.domain`과 `testrun.domain` 없이 평가 truth table을 테스트하는지 검증한다.
3. TestDefinition 수정·삭제 뒤에도 기존 Snapshot 값이 바뀌지 않는지 통합 테스트한다.
4. 잘못된 source ID, action code, severity code와 필수 값 누락을 경계에서 거부하는지 검증한다.
5. TestRun 실행 결과가 Evaluation 입력으로 mapping되어도 Assertion, Change와 Quality Gate 계약이 유지되는지 검증한다.
6. 최종화 Port를 통한 저장에서도 QualityGateResult와 TestRun `FINISHED`가 함께 commit 또는 rollback되는지 검증한다.
7. Classifier Adapter의 AWS 응답이 값 계약을 거쳐 TestRun의 `ActualResult`로 변환되고 AWS 타입이 Core에 노출되지 않는지 검증한다.
8. ArchUnit 또는 동등한 정적 테스트가 금지된 Context 간 Domain/Application import와 `common/domain` 우회를 차단하는지 검증한다.
9. Integration Adapter 밖의 Core 코드가 다른 Context의 구현 패키지를 참조하지 않는지 검증한다.
10. 공개 HTTP API, SQS v1 메시지와 PostgreSQL 물리 스키마가 이 결정으로 바뀌지 않았는지 확인한다.
