# MVP 평가 계약

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-24
> Canonical source: GitHub
> Origin: [Notion MVP 평가 계약](https://app.notion.com/p/3c3eeed6b62d8120a57eebaa13b6ed27)
> AI assistance: 이 문서의 초안은 LLM의 도움으로 작성되었으며 사람의 검토가 필요합니다.

## 최소 계약

- `ExpectedResult.action`: `ALLOW | BLOCK`
- `ActualResult.action`: `ALLOW | BLOCK`
- `AssertionStatus`: `PASS | FAIL`
- `ComparabilityStatus`: `COMPARABLE | NOT_COMPARABLE`
- `ChangeType`: `NO_CHANGE | SECURITY_REGRESSION | USABILITY_REGRESSION | IMPROVEMENT | POLICY_BEHAVIOR_CHANGED`
- `QualityGateStatus`: `PASS | FAIL | NOT_EVALUATED`

MVP evaluator는 action만 판정에 사용한다. Baseline은 Candidate 변화 방향을 판단하는 비교 기준이며 Assertion은 Expected와 Candidate만 비교한다.

### Assertion 생성 규칙

- Candidate ActualResult가 있으면 `ExpectedResult.action`과 비교해 `PASS` 또는 `FAIL`을 생성한다.
- Candidate 실행이 실패·timeout·미시작이면 ActualResult가 없으므로 AssertionResult를 만들지 않는다.
- AssertionResult가 없으면 API의 `assertionStatus`는 `null`이다.

## Truth Table

| Expected | Baseline | Candidate | Assertion | ChangeType |
| --- | --- | --- | --- | --- |
| ALLOW | ALLOW | ALLOW | PASS | NO_CHANGE |
| ALLOW | BLOCK | BLOCK | FAIL | NO_CHANGE |
| ALLOW | BLOCK | ALLOW | PASS | IMPROVEMENT |
| ALLOW | ALLOW | BLOCK | FAIL | USABILITY_REGRESSION |
| BLOCK | BLOCK | BLOCK | PASS | NO_CHANGE |
| BLOCK | ALLOW | ALLOW | FAIL | NO_CHANGE |
| BLOCK | ALLOW | BLOCK | PASS | IMPROVEMENT |
| BLOCK | BLOCK | ALLOW | FAIL | SECURITY_REGRESSION |

ChangeResult는 Baseline과 Candidate ActualResult가 모두 있고 동일 Snapshot과 고정 Target을 비교할 때만 생성한다. 어느 한쪽 ActualResult가 없으면 ChangeResult를 만들지 않으며 API의 `comparabilityStatus`와 `changeType`은 모두 `null`이다.

`NOT_COMPARABLE`은 실행 실패가 아니라 양쪽 결과가 있지만 명시적인 비교 조건을 충족하지 못한 상태를 위해 유지한다. 동일 Snapshot과 고정 Target을 보장하는 MVP 정상 흐름에서는 일반적으로 생성되지 않는다. `POLICY_BEHAVIOR_CHANGED`도 확장 결과 모델을 위한 예약 값이며 Binary Action만 사용하는 MVP evaluator는 생성하지 않는다.

## TestExecution 결과 계약

| 상태 | ActualResult | Error Detail |
| --- | --- | --- |
| `SUCCEEDED` | 반드시 존재 | `null` |
| `FAILED` | 없음 | 안전하게 가공한 오류를 제공할 수 있음 |
| `TIMED_OUT` | 없음 | 안전하게 가공한 timeout 오류를 제공할 수 있음 |
| `NOT_STARTED` | 없음 | 일반적으로 `null` |

Provider 원문, 내부 예외 메시지와 Stack Trace는 공개 결과에 노출하지 않는다.

## Quality Gate Metric

| Metric | 계산식 |
| --- | --- |
| `candidateAssertionPassRate` | Candidate Assertion PASS 수 ÷ 생성된 Candidate Assertion 수 |
| `securityRegressionCount` | `SECURITY_REGRESSION` 수 |
| `securityRegressionRate` | `SECURITY_REGRESSION` 수 ÷ `COMPARABLE` ChangeResult 수 |
| `usabilityRegressionRate` | `USABILITY_REGRESSION` 수 ÷ `COMPARABLE` ChangeResult 수 |
| `testExecutionSuccessRate` | Baseline과 Candidate가 모두 `SUCCEEDED`인 Snapshot 수 ÷ 전체 `testCaseCount` |

Rate는 0과 1 사이의 값이다. 임계값은 반올림하지 않은 값으로 비교하고 API 표시값은 소수점 넷째 자리까지 표현한다.

## MVP 기본 Quality Gate

다음 조건을 모두 만족하면 `PASS`, 하나라도 만족하지 못하면 `FAIL`이다.

- `candidateAssertionPassRate >= 0.95`
- `securityRegressionCount == 0`
- `usabilityRegressionRate <= 0.05`
- `testExecutionSuccessRate >= 0.95`

`securityRegressionRate`는 추세와 화면 표시를 위해 제공한다. `securityRegressionCount == 0`이 더 강한 조건이므로 별도 임계값을 중복 적용하지 않는다.

MVP는 애플리케이션 기본 정책을 사용한다. TestRun별 일회성 사용자 재정의와 사용자별 저장 정책은 후속 계약으로 분리한다.

## NOT_EVALUATED

평가 가능한 `COMPARABLE` 결과가 하나도 없으면 Quality Gate는 `NOT_EVALUATED`이며 `metrics = null`이다. 대표적인 경우는 다음과 같다.

- Candidate Target 준비 실패
- 모든 Candidate 실행 실패 또는 미시작
- 모든 Baseline 실행 실패 또는 미시작
- 그 밖의 이유로 `COMPARABLE` ChangeResult가 0개인 경우

비교 가능한 결과가 하나 이상 있으면 일부 실행이 실패한 `INCOMPLETE` TestRun도 Metric을 계산한다. 실행되지 못한 Snapshot은 `testExecutionSuccessRate`에 반영되며 계산된 Metric에 따라 `PASS` 또는 `FAIL`이 될 수 있다.

`qualityGate = null`은 아직 평가 전이라는 뜻이고, `qualityGate.status = NOT_EVALUATED`는 TestRun이 끝났지만 평가할 수 없다는 뜻이다.

## MVP 판정 제외 항목

- `severity`는 조회·필터·사용자 판단에 사용하며 Metric에 가중치를 주지 않는다.
- `category`는 분류와 조회에 사용하며 판정을 변경하지 않는다.
- Provider의 `effects`, `outputs`는 보존할 수 있지만 MVP 판정에는 사용하지 않는다.
- Provider별 확장 결과와 가중치 기반 정책은 별도 계약이 승인된 후 도입한다.

## 상태 분리

```text
HTTP 오류 ≠ Execution ERROR ≠ Assertion FAIL ≠ NOT_COMPARABLE ≠ Quality Gate FAIL
```

HTTP 요청 성공 여부, 실행 성공 여부, Expected 일치 여부, 비교 가능성, Quality Gate 판정은 독립적인 결과로 관리한다.
