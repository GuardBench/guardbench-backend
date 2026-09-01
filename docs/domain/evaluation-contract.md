# MVP 평가 계약

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-01
> Canonical source: GitHub
> Related: [ADR 0011](../decisions/0011-ai-application-target-and-guardrail-evaluator.md)

## 최소 계약

- `ExpectedResult.action`: `ALLOW | BLOCK`
- `EvaluationResult.action`: `ALLOW | BLOCK`
- `AssertionStatus`: `PASS | FAIL`
- `QualityGateStatus`: `PASS | FAIL | NOT_EVALUATED`

AI Application은 자연어 ApplicationResponse를 반환한다. `ALLOW`와 `BLOCK`은 Evaluator가 자연어 응답을 평가해 만드는 GuardBench 공통 verdict다. AWS Bedrock Guardrail은 첫 번째 Evaluator 구현이며 Application Target이 아니다.

ApplicationResponse는 내부 Evaluator 입력이며 frontend-facing API에 노출하지 않는다. 공개 결과는 TestCase input, 실행 상태·안전한 오류, Evaluator verdict, ExpectedResult와 Assertion을 제공한다.

TestRun 생성 요청의 inline `evaluationProfile`은 사용자가 선택한 평가 목적을 `checks`와 `strictness`로 표현한다. 사용자는 Evaluator type, provider 또는 Bedrock Guardrail identifier/version을 제출하지 않으며 GuardBench가 profile을 실제 Evaluator 설정으로 해석한다. 요청한 profile은 TestRun 조회에서 다시 확인할 수 있어야 한다.

EvaluationResult가 있으면 ExpectedResult와 비교해 AssertionResult를 생성한다.

| ExpectedResult | EvaluationResult | Assertion |
| --- | --- | --- |
| ALLOW | ALLOW | PASS |
| ALLOW | BLOCK | FAIL |
| BLOCK | BLOCK | PASS |
| BLOCK | ALLOW | FAIL |

Application 실행 실패, timeout 또는 Evaluator 실패로 EvaluationResult가 없으면 AssertionResult를 생성하지 않는다. 실행과 평가 실패의 구체적인 저장·공개 오류 계약은 #117에서 확정·구현한다.

## Quality Gate

Quality Gate는 하나의 현재 TestRun의 Assertion 결과를 집계한다. 다른 TestRun, 복수 Target 실행 결과와 Regression 결과를 입력으로 사용하지 않는다.

- 집계 가능한 현재 Run 결과가 있으면 정책에 따라 `PASS` 또는 `FAIL`이다.
- 집계 가능한 결과가 없으면 `NOT_EVALUATED`다.
- 구체적인 metric, 임계값, nullable 규칙과 API 전환은 #118이 소유한다.
- Quality Gate와 TestRun `FINISHED`의 원자 저장 및 이미 완료된 결과를 덮어쓰지 않는 원칙은 ADR 0004의 대체되지 않은 부분을 유지한다.

## Regression

Regression은 Quality Gate와 별도 유스케이스다.

- 입력은 이미 완료된 TestRun A와 TestRun B다.
- Application Target과 Evaluator를 다시 호출하지 않고 각 Run에 저장된 결과만 비교한다.
- 비교 가능성은 최소한 동일한 테스트 정의와 동일한 Evaluator 설정을 요구한다.
- 구체적인 comparability key, 추가 조건, 변화 분류와 API는 #119에서 결정·구현한다.

```text
Completed TestRun A + Completed TestRun B
                    ↓
            Comparability Check
                    ↓
          Stored Result Comparison
                    ↓
             Regression Result
```

## 판정 제외 항목

- `severity`와 `category`는 조회·필터 또는 후속 Quality Gate 정책 입력 후보이며, #118의 승인 없이 판정 공식을 임의로 정하지 않는다.
- Provider 원문 오류, 내부 예외 메시지와 stack trace는 공개 결과에 노출하지 않는다.
- Evaluation Profile CRUD, provider별 고급 설정과 provider ensemble은 현재 구현 계약이 아니다.

```text
HTTP 오류 ≠ Application 실행 오류 ≠ Evaluator 오류 ≠ Assertion FAIL ≠ Quality Gate 판정 ≠ Regression 결과
```

## 현재 구현

현재 코드는 Application response를 내부 execution에 저장하고 Bedrock Guardrail Evaluator가 만든 `EvaluationResult`를 ExpectedResult와 Assertion에 사용한다. 단일 TestRun Quality Gate와 Regression은 각각 #118과 #119 범위다.
