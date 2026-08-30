# MVP 평가 계약

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-30
> Canonical source: GitHub
> Related: [ADR 0010](../decisions/0010-single-target-test-run-model.md)

## 최소 계약

- `ExpectedResult.action`: `ALLOW | BLOCK`
- `ActualResult.action`: `ALLOW | BLOCK`
- `AssertionStatus`: `PASS | FAIL`
- `QualityGateStatus`: `NOT_EVALUATED` (단일 Target TestRun)

MVP evaluator는 action만 판정에 사용한다. Target ActualResult가 있으면 ExpectedResult와 비교해 AssertionResult를 생성한다.

| Expected | Actual | Assertion |
| --- | --- | --- |
| ALLOW | ALLOW | PASS |
| ALLOW | BLOCK | FAIL |
| BLOCK | BLOCK | PASS |
| BLOCK | ALLOW | FAIL |

Target 실행이 `FAILED`, `TIMED_OUT`, `NOT_STARTED`이면 ActualResult가 없으므로 AssertionResult를 생성하지 않고 API의 `assertionStatus`는 `null`이다.

## TestExecution 결과

| 상태 | ActualResult | Error Detail |
| --- | --- | --- |
| `SUCCEEDED` | 반드시 존재 | `null` |
| `FAILED` | 없음 | 안전하게 가공한 오류를 제공할 수 있음 |
| `TIMED_OUT` | 없음 | 안전하게 가공한 timeout 오류를 제공할 수 있음 |
| `NOT_STARTED` | 없음 | 일반적으로 `null` |

Provider 원문, 내부 예외 메시지와 stack trace는 공개 결과에 노출하지 않는다.

## Comparison과 Quality Gate

단일 Target TestRun은 Baseline/Candidate 쌍을 만들지 않고 `ChangeResult`나 regression을 새로 생성하지 않는다. 따라서 현재 흐름에서 비교 가능한 ChangeResult가 0개이며, TestRun 종료 시 Quality Gate는 항상 다음 형태다.

```json
{
  "status": "NOT_EVALUATED",
  "metrics": null
}
```

Assertion PASS/FAIL은 계속 저장·조회하지만 Quality Gate PASS/FAIL로 변환하지 않는다. 새 Quality Gate 정책, 비교 run, regression 집계는 별도 승인 계약의 범위다.

## 판정 제외 항목

- `severity`와 `category`는 조회·필터에 사용하며 판정을 변경하지 않는다.
- Provider의 assessments, outputs, 원문 content는 판정·DB·API·일반 로그에 전달하지 않는다.
- Provider별 확장 결과, 자연어 출력, 가중치 정책은 범위 외다.

```text
HTTP 오류 ≠ Execution ERROR ≠ Assertion FAIL ≠ Quality Gate NOT_EVALUATED
```
