# 핵심 도메인 모델

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-01
> Canonical source: GitHub
> Related: [ADR 0011](../decisions/0011-ai-application-target-and-guardrail-evaluator.md)

## 목표 도메인 계약

| 객체 | 책임 |
| --- | --- |
| `TestSuite` | 관련 TestCase를 묶는 정책 테스트 자산 |
| `TestCase` | 현재 편집 가능한 input, ExpectedResult, severity, category 정의 |
| `TestCaseSnapshot` | TestRun 접수 시 TestCase 이름과 실행 정의를 불변 복제한 실행 기준 |
| `TestRun` | 하나의 AI Application Target, 실제 Evaluator 식별자와 Snapshot 집합의 수명주기 관리 |
| `TargetReference` | TestRun이 실행할 AI Application을 재식별하는 local reference |
| `EvaluationProfile` | 사용자가 요청한 평가 목적을 checks와 strictness로 표현하는 inline 입력 값 |
| `EvaluatorReference` | Run이 실제 사용한 Evaluator 설정과 버전을 불변하게 재식별하는 local reference |
| `ApplicationResponse` | Application Target이 반환한 자연어 응답 |
| `EvaluationResult` | Evaluator가 ApplicationResponse를 `ALLOW | BLOCK`으로 정규화한 결과 |
| `AssertionResult` | ExpectedResult와 EvaluationResult의 일치 여부 |
| `QualityGateResult` | 한 TestRun의 Assertion 결과 집계 판정 |
| `RegressionResult` | 비교 가능한 완료 TestRun 두 개의 저장 결과 비교 |

`EvaluationProfile`은 MVP에서 독립 Aggregate나 CRUD 리소스가 아니다. 요청 profile과 실제 `EvaluatorReference`를 고정하는 구조와 catalog resolution은 #114에서 구현되었다. Regression 결과 모델은 #119에서 구체화한다.

## 핵심 불변식

- TestCase는 현재 정의만 보유하고 과거 실행 기준은 TestCaseSnapshot이 보존한다.
- TestCase 삭제는 논리 삭제이며 기존 Snapshot과 실행·판정 결과에 전파하지 않는다.
- 하나의 TestRun은 하나의 Application Target만 실행한다.
- MVP Application Target type은 `HTTP_ENDPOINT`다.
- MVP의 `HTTP_ENDPOINT`는 OpenAI-compatible chat completions 계약만 지원한다.
- Target의 `identifier`는 full HTTP/HTTPS endpoint URL이고 `model`은 필수 실행 정보다.
- TestRun 요청은 inline EvaluationProfile을 포함하고 사용자는 Evaluator/provider 설정을 직접 제출하지 않는다.
- 하나의 TestRun은 실제 사용한 Evaluator 설정과 버전을 사후에 불변하게 식별할 수 있어야 한다.
- Application Target은 자연어 응답을 반환하며 `ALLOW`와 `BLOCK`을 직접 반환하는 판정 주체가 아니다.
- ApplicationResponse는 내부 Evaluator 입력이며 public 결과 DTO에 노출하지 않는다.
- Evaluator만 ApplicationResponse를 GuardBench 공통 EvaluationResult로 정규화한다.
- EvaluationResult가 있으면 ExpectedResult와 비교해 AssertionResult를 생성한다. Application 실행 또는 평가 실패로 EvaluationResult가 없으면 AssertionResult를 생성하지 않는다.
- Quality Gate는 같은 TestRun의 Assertion 결과만 집계한다.
- Regression은 완료된 두 TestRun의 저장 결과만 비교하고 Application Target이나 Evaluator를 다시 호출하지 않는다.
- Regression은 최소한 동일한 테스트 정의와 동일한 Evaluator 설정을 전제로 한다.
- 실행 오류, 평가 오류, Assertion FAIL, Quality Gate `NOT_EVALUATED`와 Regression 비교 불가는 서로 다른 상태다.
- 외부 Context의 Domain 타입·ID VO·Enum·Repository를 직접 재사용하지 않고 소비 Context가 local reference와 outbound Port를 소유한다.

## 실행 흐름

```text
TestSuite + TestCase
        ↓ TestRun 요청(OpenAI-compatible HTTP Target + inline EvaluationProfile)
QUEUED: requested policy + references + TestCaseSnapshot + OutboxEvent
        ↓
RUNNING: Snapshot당 Application 실행
        ↓
Natural Language ApplicationResponse
        ↓
Evaluator
        ↓
EvaluationResult(ALLOW | BLOCK)
        ↓
ExpectedResult → AssertionResult
        ↓
현재 TestRun의 QualityGateResult
        ↓
FINISHED
```

## Regression 흐름

```text
Completed TestRun A + Completed TestRun B
                    ↓
            Comparability Check
                    ↓
          Stored Result Comparison
                    ↓
             RegressionResult
```

## 현재 구현과 목표 계약의 차이

#114를 통해 EvaluationProfile catalog resolution과 immutable EvaluatorReference 고정 구조가 구현되었다. #115와 #125를 통해 HTTP Application Target 실행과 OpenAI-compatible 응답 정규화가 구현되었고, #128에서 generic HTTP 경로를 제거해 OpenAI-compatible 전용 계약으로 단순화했다. #116을 통해 Bedrock Guardrail이 Evaluator Adapter로 전환되었다.

현재 legacy worker는 아직 Evaluator Port를 호출하지 않고 결과를 `ActualResult`로 저장한다. #117이 Worker orchestration을 Application 실행 → Evaluator → Assertion 흐름으로 변경하고, #118과 #119가 각각 Quality Gate와 Regression을 완성한다.

목표 OpenAPI를 배포 완료의 증거로 해석하지 않고, 아직 남은 #117~#119 구현 상태를 코드와 함께 확인한다.
