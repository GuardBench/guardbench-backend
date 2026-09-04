# 핵심 도메인 모델

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-04
> Canonical source: GitHub
> Related: [ADR 0013](../decisions/0013-response-behavior-classifier.md)

## 목표 도메인 계약

| 객체 | 책임 |
| --- | --- |
| `TestSuite` | 관련 TestCase를 묶는 정책 테스트 자산 |
| `TestCase` | 현재 편집 가능한 input, ExpectedResult, severity, category 정의 |
| `TestCaseSnapshot` | TestRun 접수 시 TestCase 이름과 실행 정의를 불변 복제한 실행 기준 |
| `TestRun` | 하나의 AI Application Target, classifier contract version과 Snapshot 집합의 수명주기 관리 |
| `TargetReference` | TestRun이 실행할 AI Application을 재식별하는 local reference |
| `ClassifierReference` | Run이 사용한 classifier contract version을 불변하게 재식별하는 local reference |
| `ApplicationResponse` | Application Target이 반환한 자연어 응답 |
| `EvaluationResult` | Evaluator가 ApplicationResponse를 `ALLOW | BLOCK`으로 정규화한 결과 |
| `AssertionResult` | ExpectedResult와 EvaluationResult의 일치 여부 |
| `QualityGateResult` | 한 TestRun의 Assertion 결과 집계 판정 |
| `RegressionResult` | 비교 가능한 완료 TestRun 두 개의 저장 결과 비교 |

classifier 설정은 사용자가 제출하는 profile이나 provider 선택이 아니라 서버 배포 configuration이다. 저장된 완료 Run의 Regression 비교 모델과 API는 #119에서 구현되었다.

## 핵심 불변식

- TestCase는 현재 정의만 보유하고 과거 실행 기준은 TestCaseSnapshot이 보존한다.
- TestCase 삭제는 물리 삭제이며 기존 Snapshot과 실행·판정 결과에 전파하지 않는다.
- 하나의 TestRun은 하나의 Application Target만 실행한다.
- MVP Application Target type은 `HTTP_ENDPOINT`다.
- MVP의 `HTTP_ENDPOINT`는 OpenAI-compatible chat completions 계약만 지원한다.
- Target의 `identifier`는 full HTTP/HTTPS endpoint URL이고 `model`은 필수 실행 정보다.
- TestRun 요청에는 별도 평가 profile이 없으며 사용자는 classifier provider 설정을 직접 제출하지 않는다.
- 하나의 TestRun은 classifier contract version을 사후에 불변하게 식별할 수 있어야 한다.
- Application Target은 자연어 응답을 반환하며 `ALLOW`와 `BLOCK`을 직접 반환하는 판정 주체가 아니다.
- ApplicationResponse는 내부 Evaluator 입력이며 public 결과 DTO에 노출하지 않는다.
- Response Behavior Classifier만 ApplicationResponse를 GuardBench 공통 EvaluationResult로 정규화한다.
- EvaluationResult가 있으면 ExpectedResult와 비교해 AssertionResult를 생성한다. Application 실행 또는 평가 실패로 EvaluationResult가 없으면 AssertionResult를 생성하지 않는다.
- Quality Gate는 같은 TestRun의 Assertion 결과만 집계한다.
- Regression은 완료된 두 TestRun의 저장 결과만 비교하고 Application Target이나 Evaluator를 다시 호출하지 않는다.
- Regression은 최소한 동일한 테스트 정의와 동일한 Evaluator 설정을 전제로 한다.
- 실행 오류, 평가 오류, Assertion FAIL, Quality Gate `NOT_EVALUATED`와 Regression 비교 불가는 서로 다른 상태다.
- 외부 Context의 Domain 타입·ID VO·Enum·Repository를 직접 재사용하지 않고 소비 Context가 local reference와 outbound Port를 소유한다.

## 실행 흐름

```text
TestSuite + TestCase
        ↓ TestRun 요청(OpenAI-compatible HTTP Target)
QUEUED: classifier contract + Target reference + TestCaseSnapshot + OutboxEvent
        ↓
RUNNING: Snapshot당 Application 실행
        ↓
Natural Language ApplicationResponse
        ↓
SageMaker Response Behavior Classifier
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

## 현재 구현

#173을 통해 기존 profile/catalog 경로를 제거하고 SageMaker Response Behavior Classifier contract로 전환했다. #115와 #125를 통해 HTTP Application Target 실행과 OpenAI-compatible 응답 정규화가 구현되었고, #128에서 generic HTTP 경로를 제거해 OpenAI-compatible 전용 계약으로 단순화했다.

#117을 통해 Application 실행 → Evaluator → Assertion Worker orchestration이 구현되었다. #118을 통해 Quality Gate가 현재 TestRun의 Assertion 통과율과 실행 성공률을 기준으로 판정하도록 전환되었고, #119를 통해 저장된 완료 TestRun 결과만 사용하는 Regression 비교 API가 구현되었다.

따라서 현재 MVP backend는 Application Target 실행, Evaluator 판정, Assertion, 현재 Run Quality Gate, comparable historical Run의 stored-result Regression까지 목표 도메인 흐름을 구현한다.
