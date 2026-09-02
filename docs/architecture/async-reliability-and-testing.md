# 비동기 신뢰성 및 테스트 원칙

> Status: DRAFT
> Owner: Backend
> Last reviewed: 2026-09-02
> Canonical source: GitHub
> Related: [Issue #149](https://github.com/GuardBench/guardbench-backend/issues/149), [ADR 0005](../decisions/0005-async-test-run-execution-contract.md), [ADR 0008](../decisions/0008-async-testrun-persistence-contract.md), [성능 테스트 운영](../performance-testing.md)

이 문서는 GuardBench의 비동기 TestRun 실행에서 지켜야 할 신뢰성 원칙과 테스트 방향을 정리한다.

2026-09-02 dev 환경에서 발생한 TestRun terminal convergence 장애를 계기로 작성했으며, 특정 버그의 일회성 수정 방법보다 이후 SQS, Worker claim, Provider retry, Finalization, 성능 테스트를 설계하고 검증할 때 반복해서 사용할 판단 기준을 남기는 것을 목적으로 한다.

구체적인 구현 결정은 관련 Issue와 ADR에서 확정한다. 이 문서는 문제를 바라보는 공통 모델과 테스트 원칙을 제공한다.

## 1. 배경: dev에서 관측된 장애

Issue #149 조사에서 다음 현상이 확인되었다.

- 하나의 TestRun에 78개 Snapshot이 존재했다.
- 76개 TestExecution만 terminal 상태에 도달했다.
- 나머지 2개 WorkItem은 Provider 최대 시도를 모두 수행하기 전에 WorkItems DLQ로 이동했다.
- TestRun은 `76 / 78` 진행률에서 `RUNNING` 상태로 남았다.
- `executionOutcome`과 `completedAt`이 확정되지 않았다.
- 완료된 Execution이 발생할 때마다 Finalization이 재평가되었고, 아직 전체가 terminal이 아니라는 정상적인 중간 상태가 SQS retry 대상으로 취급되었다.
- `gb-run-finalize`에서는 retry가 380회 관측되었고 76개 메시지가 DLQ로 이동했다.

관측 당시 주요 설정은 다음과 같았다.

```text
SQS visibility timeout = 30s
execution claim lease  = 45s
DLQ maxReceiveCount    = 5
```

WorkItems DLQ로 이동한 두 메시지는 SQS에서 5회 수신되었지만 실제 Provider 호출은 2회까지만 확인되었다.

```text
SQS receive count = 5
Provider invocation = 2
```

이 관측은 다음 가정이 성립하지 않음을 보여준다.

```text
SQS delivery attempt == Provider business attempt
```

## 2. 세 개의 lifecycle을 분리한다

GuardBench 비동기 실행에서는 다음 세 lifecycle을 서로 다른 개념으로 취급한다.

### 2.1 SQS delivery lifecycle

메시지가 Queue에서 consumer에게 전달되는 lifecycle이다.

대표 상태/정보:

- visibility timeout
- `ApproximateReceiveCount`
- redrive policy
- DLQ
- duplicate delivery

이 lifecycle은 메시지 전달 신뢰성을 위한 것이며 Provider business retry 횟수를 의미하지 않는다.

### 2.2 Worker claim lifecycle

동일 Snapshot을 여러 Worker가 동시에 실행하지 않도록 ownership을 획득하고 유지하는 lifecycle이다.

대표 상태/정보:

- claim token
- claimedAt
- leaseUntil
- AlreadyHeld
- claim loss

claim 획득이나 경합은 Worker ownership 문제이며 Provider 호출 횟수와 동일하지 않다.

### 2.3 Provider business execution lifecycle

실제 외부 Application Target 또는 Evaluator Provider 호출의 lifecycle이다.

대표 상태/정보:

- provider attempt
- timeout
- retryable failure
- permanent failure
- retry exhaustion

Provider attempt는 실제 Provider invocation이 시작될 때만 증가해야 한다.

### 2.4 왜 분리가 필요한가

정상 경로에서는 세 lifecycle이 비슷하게 움직여 하나의 retry처럼 보일 수 있다.

```text
message receive
→ claim acquire
→ Provider invoke
```

하지만 장애 경로에서는 서로 다르게 움직인다.

```text
message receive       +1 delivery
→ AlreadyHeld
→ Provider invoke     +0 business attempt
```

또는 다음도 가능하다.

```text
message receive       +1 delivery
→ claim acquire       +1 ownership transition
→ Worker crash
→ Provider invoke     +0 business attempt
```

따라서 delivery count, claim attempt, Provider attempt를 서로 대신 사용하지 않는다.

## 3. Safety invariants

Safety invariant는 "절대로 발생하면 안 되는 상태"를 정의한다.

### S1. Provider 호출 없이 providerAttempt가 증가하지 않는다

```text
actual Provider invocation 없음
→ providerAttempt 증가 없음
```

SQS duplicate delivery나 AlreadyHeld는 Provider attempt가 아니다.

### S2. AlreadyHeld는 business failure가 아니다

```text
AlreadyHeld
→ Provider 호출 없음
→ providerAttempt 증가 없음
→ FAILED/TIMED_OUT 저장 없음
```

### S3. terminal Execution은 non-terminal로 돌아가지 않는다

`SUCCEEDED`, `FAILED`, `TIMED_OUT`으로 확정된 TestExecution은 중복 메시지나 재처리로 다시 실행 상태로 돌아가지 않는다.

### S4. FINISHED TestRun은 다시 RUNNING으로 돌아가지 않는다

Finalization 메시지가 중복 전달되어도 이미 확정된 TestRun 결과를 다시 계산하거나 상태를 되돌리지 않는다.

### S5. 정상적인 business failure를 DLQ가 대신 확정하지 않는다

Provider timeout 등 계약된 business failure는 애플리케이션 상태 머신이 `FAILED` 또는 `TIMED_OUT`으로 확정한다.

DLQ 이동 자체가 TestExecution의 business terminal 상태를 결정해서는 안 된다.

## 4. Liveness invariants

Liveness invariant는 "결국 반드시 도달해야 하는 상태"를 정의한다.

### L1. 처리 가능한 TestExecution은 결국 terminal 상태로 수렴한다

retry, duplicate delivery, claim contention이 발생해도 정상적으로 처리 가능한 WorkItem은 영구적인 `RUNNING` 또는 미확정 상태에 남지 않는다.

### L2. Provider retry budget이 소진되면 terminal로 수렴한다

예를 들어 Provider timeout의 최대 시도가 3회라면 다음을 보장한다.

```text
attempt 1 timeout
→ retryable

attempt 2 timeout
→ retryable

attempt 3 timeout
→ TIMED_OUT
→ PROVIDER_TIMEOUT
→ completedAt 기록
```

### L3. 모든 Execution이 terminal이면 TestRun은 FINISHED로 수렴한다

```text
terminalExecutions == expectedExecutions
→ TestRun FINISHED
→ executionOutcome 계산
→ completedAt 기록
```

SQS delivery history, duplicate 횟수, DLQ 여부가 정상 종료 자체를 막아서는 안 된다.

## 5. Messaging semantics

### 5.1 SQS retry와 Provider retry를 분리한다

```text
SQS retry
= delivery/infrastructure reliability

Provider retry
= application business execution policy
```

`ApproximateReceiveCount`와 `maxReceiveCount`를 Provider retry budget으로 사용하지 않는다.

### 5.2 DLQ의 역할

DLQ는 정상적인 Provider business failure 저장소가 아니다.

주요 대상은 다음과 같다.

- 역직렬화할 수 없는 payload
- 지속적인 DB 또는 infrastructure failure
- consumer 구현 오류
- 예상하지 못한 예외
- 정상적인 상태 머신으로 수렴할 수 없는 poison message

### 5.3 정상적인 중간 상태를 실패로 취급하지 않는다

다음은 정상적인 중간 상태다.

```text
AlreadyHeld
Finalize NotReady
Provider retryable failure before exhaustion
```

이 상태들은 각자의 의미에 맞게 재처리 여부를 결정해야 하며, 단순히 `false`, exception, NACK 하나로 동일하게 취급하지 않는다.

특히 Finalization에서:

```text
terminal < expected
→ progress 반영
→ ACK

terminal == expected
→ Finalize
→ ACK
```

를 기본 모델로 삼는다.

## 6. 시간 기반 설정은 cross-component contract다

visibility timeout, claim lease, Provider timeout은 서로 독립적인 숫자가 아니다.

Backend와 IaC가 각각 유효한 설정을 갖더라도 조합이 잘못되면 시스템 race가 발생할 수 있다.

예:

```text
visibility timeout = 30s
claim lease = 45s
```

이 경우 메시지가 다시 보이는 시점에도 이전 Worker의 claim이 살아 있을 수 있다.

최소한 다음 관계를 검토한다.

```text
visibility timeout > claim lease
```

실제 운영값을 정할 때는 Provider 처리 시간, DB 저장 시간, 네트워크 지연, buffer도 함께 고려한다.

```text
visibility timeout
> claim lease
+ execution/commit margin
+ buffer
```

숫자 자체보다 이 관계를 계약으로 테스트하는 것이 중요하다.

## 7. 테스트 원칙

기존 테스트는 개별 컴포넌트의 상태 전이를 잘 검증하더라도 실제 비동기 lifecycle이 결합되는 순간의 오류를 놓칠 수 있다.

예를 들어 다음 테스트만으로는 충분하지 않다.

```text
attempt = 3을 Fake로 주입
→ TIMED_OUT인가?
```

추가로 다음 질문을 검증해야 한다.

```text
실제 메시지 재전달과 claim 경합 속에서도
attempt = 3에 정상적으로 도달할 수 있는가?
```

따라서 비동기 테스트는 두 종류의 질문을 함께 다룬다.

### 7.1 State correctness

특정 입력에서 올바른 상태를 저장하는가?

예:

- Provider timeout 최종 시도 → `TIMED_OUT`
- permanent failure → `FAILED`
- success → `SUCCEEDED`

### 7.2 Convergence correctness

중복 전달, timeout, claim contention, 순서 변화가 있어도 전체 상태 머신이 결국 올바른 terminal 상태로 수렴하는가?

GuardBench에서는 이 테스트를 **convergence test**로 부른다.

## 8. Reliability integration test boundary

PR CI에서 실제 AWS 전체 환경을 구성하지 않고도 주요 messaging semantics를 재현할 수 있도록 다음 경계를 사용한다.

```text
JUnit
├─ PostgreSQL Testcontainers
├─ LocalStack SQS
│  ├─ work-items queue
│  ├─ work-items DLQ
│  ├─ finalize queue
│  └─ finalize DLQ
└─ deterministic Provider Stub
```

### Real component

다음은 가능한 한 실제 구현을 사용한다.

- PostgreSQL
- claim persistence adapter
- SQS consumer/poller
- ACK/NACK 처리
- visibility timeout
- redrive policy
- DLQ
- application service
- finalization

### Stub component

외부 Provider는 deterministic stub을 사용한다.

예:

```text
always succeed
always PROVIDER_TIMEOUT
fail twice then succeed
permanent failure
```

이 테스트의 목적은 Bedrock이나 실제 AI Application의 동작을 검증하는 것이 아니라 GuardBench 내부 비동기 orchestration의 신뢰성을 검증하는 것이기 때문이다.

## 9. 필수 convergence scenarios

향후 reliability integration suite는 최소한 다음 시나리오를 포함한다.

### Scenario 1. Delivery count와 Provider attempt 분리

```text
visibility < claim lease
Provider always timeout
```

수정 전 장애를 재현할 때:

- SQS receive count가 Provider invocation보다 빠르게 증가할 수 있다.
- Provider 최대 시도 전에 DLQ로 이동할 수 있다.

수정 후:

- SQS receive count와 무관하게 Provider business retry 계약이 유지된다.
- retry exhaustion 시 TestExecution이 terminal로 저장된다.

### Scenario 2. AlreadyHeld 반복

```text
message redelivery
→ claim AlreadyHeld
```

검증:

- Provider 호출 없음
- providerAttempt 증가 없음
- terminal failure 저장 없음

### Scenario 3. Partial finalization

```text
expected = 78
terminal = 76
```

검증:

- Finalization 결과는 아직 준비되지 않음
- message는 정상 ACK
- finalize DLQ 증가 없음

### Scenario 4. Final terminal convergence

성공, timeout, duplicate delivery, AlreadyHeld가 혼합되어도 최종적으로 다음을 만족한다.

```text
terminalExecutions == expectedExecutions
TestRun == FINISHED
completedAt != null
```

### Scenario 5. Duplicate terminal/finalize delivery

동일 terminal event 또는 finalize message가 중복 전달되어도:

- progress가 중복 증가하지 않는다.
- Provider가 다시 호출되지 않는다.
- 이미 FINISHED인 TestRun 결과를 다시 변경하지 않는다.

## 10. PR CI, dev E2E, performance test의 역할

모든 검증을 하나의 테스트 계층에 맡기지 않는다.

### PR CI — Reliability integration

```text
PostgreSQL Testcontainers
+ LocalStack SQS
+ Provider Stub
```

목적:

- 상태 머신 논리 오류 조기 발견
- duplicate/retry/claim/DLQ semantics 검증
- terminal convergence regression 방지

빠르게 반복 실행할 수 있어야 하며 작은 수의 Execution으로 재현한다.

### Dev E2E — 실제 AWS semantics 검증

실제 AWS 환경에서 다음 경계를 확인한다.

- ECS Worker
- AWS SQS
- RDS
- 실제 네트워크
- 실제 Application Target/Evaluator 연결

LocalStack과 AWS의 구현 차이, IaC와 Backend 사이의 설정 계약을 최종 확인한다.

### Performance test — 부하 상황에서도 invariant 유지 검증

성능 테스트는 단순 TPS 또는 latency 측정만 하지 않는다.

부하가 증가해도 다음 조건이 유지되는지 함께 확인한다.

- 장시간 또는 영구적인 `RUNNING` TestRun이 발생하지 않는다.
- 정상적인 business failure가 DLQ로 누출되지 않는다.
- 모든 TestExecution이 결국 terminal로 수렴한다.
- 모든 terminal Execution을 가진 TestRun이 결국 `FINISHED`로 수렴한다.
- retry가 비정상적으로 증폭되지 않는다.

따라서 reliability integration suite는 성능 테스트의 선행 안전망 역할을 한다.

```text
PR Reliability Integration
        ↓
Dev AWS E2E
        ↓
Performance Test
```

## 11. 문제 해결 시 사용하는 질문

비동기 장애를 분석하거나 설계를 변경할 때 다음 순서를 사용한다.

### 11.1 Fact

실제 로그, DB, Queue에서 무엇을 관측했는가?

### 11.2 Inference

관측 사실로부터 어떤 race 또는 failure path를 추론하는가?

### 11.3 Invariant

절대로 깨지면 안 되는 safety property와 반드시 도달해야 하는 liveness property는 무엇인가?

### 11.4 Alternatives

동일 invariant를 만족하는 최소 2개 이상의 구현 대안은 무엇인가?

### 11.5 Failure scenarios

각 대안에서 다음 상황을 검토한다.

- duplicate delivery
- Worker crash
- Provider timeout
- claim contention
- DB commit failure
- visibility timeout expiry
- terminal/finalize duplicate

### 11.6 Verification

구현이 특정 happy-path 테스트만 통과하는 것이 아니라 invariant와 convergence를 실제로 증명하는가?

## 12. 현재 적용 범위

이 문서는 우선 다음 영역에 적용한다.

- Issue #149의 SQS delivery retry / Provider retry 분리
- TestExecution terminal convergence
- TestRun finalization
- Worker claim/lease
- WorkItems/Finalize DLQ semantics
- LocalStack 기반 reliability integration test
- 성능 테스트의 reliability acceptance criteria

향후 Outbox retry command, reconciliation job, Worker 수평 확장 등 비동기 실행 구조를 변경할 때도 동일한 Safety / Liveness / Convergence 관점을 유지한다.
