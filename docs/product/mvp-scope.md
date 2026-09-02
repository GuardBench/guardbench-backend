# GuardBench MVP 범위

> Status: APPROVED
> Owner: KOSA AWS 3팀
> Last reviewed: 2026-09-01
> Canonical source: GitHub
> Origin: [Notion 최신 PRD](https://app.notion.com/p/3c0eeed6b62d80759d77f0ab0d5bcbd3)
> Related: [ADR 0011](../decisions/0011-ai-application-target-and-guardrail-evaluator.md)

## 제품 정의

GuardBench는 사람이 정의한 기대 동작으로 AI Application의 자연어 응답을 검증하는 정책 테스트 플랫폼이다. System Under Test와 Target은 AI Application이며, Guardrail은 Application 응답을 GuardBench 공통 판정으로 변환하는 Evaluator다. AWS Bedrock Guardrail은 첫 번째 Guardrail Evaluator 구현이다.

## 목표

- TestSuite와 TestCase를 재사용 가능한 정책 테스트 자산으로 축적한다.
- TestRun 시점의 TestCase를 TestCaseSnapshot으로 고정해 재현성을 확보한다.
- 하나의 TestRun에서 하나의 `HTTP_ENDPOINT` Application Target을 실행한다.
- 사용자의 평가 목적을 inline Evaluation Profile로 받아 GuardBench가 실제 Evaluator 설정으로 해석한다.
- 실행에 사용한 Evaluator 설정과 버전을 Run에 불변하게 식별한다.
- Evaluator가 만든 EvaluationResult가 ExpectedResult를 만족하는지 Assertion한다.
- 현재 Run의 Assertion 결과로 Quality Gate를 판정한다.
- 완료된 두 Run의 저장 결과를 재호출 없이 비교해 Regression을 판정한다.
- 외부 provider 타입을 Core 판정 계약에서 분리한다.

MVP Evaluation Profile은 `PII_LEAKAGE`와 `HARMFUL_CONTENT`만 지원한다. Bedrock Guardrail Evaluator는 Application의 자연어 output을 `ApplyGuardrail(source=OUTPUT)`으로 평가하며, Bedrock `PROMPT_ATTACK`을 output evaluator로 사용하지 않는다. Prompt Injection 성공 여부 평가는 향후 별도 evaluator 후보다.

## 핵심 사용자 흐름

1. 사용자가 TestSuite와 현재 TestCase 정의를 관리한다.
2. 하나의 HTTP AI Application Target과 inline Evaluation Profile로 TestRun을 요청한다.
3. 서버는 profile을 실제 Evaluator 설정으로 해석하고 TestCaseSnapshot과 사용한 설정 식별자를 고정한다.
4. 각 Snapshot input을 Application Target에 전달하고 자연어 응답을 받는다.
5. Evaluator가 자연어 응답을 `EvaluationResult(ALLOW | BLOCK)`로 정규화한다.
6. ExpectedResult와 EvaluationResult를 비교해 Assertion을 만든다.
7. 현재 TestRun의 Assertion 결과를 집계해 Quality Gate를 판정한다.
8. 필요하면 완료된 현재 Run과 비교 가능한 과거 Run의 저장 결과를 비교해 Regression을 조회한다.

Application의 자연어 응답은 내부 Evaluator 입력으로만 사용하고 사용자-facing 결과에는 노출하지 않는다. 사용자는 TestCase input, Evaluator verdict, ExpectedResult, Assertion과 실행 오류를 확인한다.

```text
TestCaseSnapshot
      ↓
AI Application Target
      ↓
Natural Language Response
      ↓
Evaluator
      ↓
EvaluationResult(ALLOW | BLOCK)
      ↓
ExpectedResult
      ↓
Assertion
      ↓
Quality Gate
```

## Regression 흐름

Regression은 Quality Gate와 별도다. 완료된 TestRun A와 B의 저장 결과를 비교하며 Application과 Evaluator를 다시 호출하지 않는다. 최소 비교 가능 조건은 동일한 테스트 정의와 동일한 Evaluator 설정이다.

```text
Completed TestRun A + Completed TestRun B
                    ↓
            Comparability Check
                    ↓
          Stored Result Comparison
                    ↓
             Regression Result
```

## 현재 구현

현재 `dev`는 다음 MVP 흐름을 구현한다.

- #114: EvaluatorReference 고정과 Guardrail Target 의존 제거
- #115: HTTP Endpoint AI Application 실행과 자연어 응답 수집
- #125·#128: OpenAI-compatible 전용 계약과 필수 `model`
- #116: AWS Bedrock Guardrail Evaluator Adapter
- #117: Application 실행 → Evaluator → Assertion Worker
- #118: 현재 TestRun Assertion 기반 Quality Gate
- #119: 저장된 완료 Run 결과 기반 Regression API

OpenAI-compatible `HTTP_ENDPOINT` Application Target의 응답은 Bedrock Guardrail Evaluator의 입력으로 사용되며, 그 결과인 `EvaluationResult`와 `ExpectedResult`로 Assertion을 만든다. Quality Gate는 현재 Run의 평가 가능한 Assertion 통과율과 전체 Snapshot 실행 성공률을 집계해 두 비율이 각각 95% 이상이면 `PASS`, 평가 가능한 Assertion이 없으면 `NOT_EVALUATED`로 판정한다.

Regression API는 완료된 TestRun의 저장된 Snapshot/Evaluator 설정과 EvaluationResult만 사용해 comparable historical Run을 찾고 결과 변화를 계산한다. Regression 과정에서는 Application Target이나 Evaluator를 다시 호출하지 않는다.

## Non-Goals

- 정책 또는 Guardrail 설정 자동 생성
- 고객 애플리케이션 CI/CD 또는 PR Gate 제품 통합
- `TestCaseRevision` 또는 `TestSuiteRevision` 도입
- Evaluation Profile을 저장·재사용하는 독립 CRUD 리소스
- provider ensemble 또는 provider별 고급 설정의 공개 계약 확정
- Prompt Injection 성공 여부 평가와 Bedrock `PROMPT_ATTACK` input 평가
