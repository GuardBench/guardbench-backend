# GuardBench MVP 범위

> Status: APPROVED
> Owner: KOSA AWS 3팀
> Last reviewed: 2026-09-01
> Canonical source: GitHub
> Origin: [Notion 최신 PRD](https://app.notion.com/p/3c0eeed6b62d80759d77f0ab0d5bcbd3)
> Related: [ADR 0011](../decisions/0011-ai-application-target-and-guardrail-evaluator.md), [ADR 0013](../decisions/0013-response-behavior-classifier.md)

## 제품 정의

GuardBench는 사람이 정의한 기대 동작으로 AI Application의 자연어 응답을 검증하는 정책 테스트 플랫폼이다. System Under Test와 Target은 AI Application이며, Evaluator는 Application 응답을 GuardBench 공통 판정으로 변환하는 Response Behavior Classifier다. Amazon SageMaker endpoint에 직접 서빙한 텍스트 모델은 첫 번째 Response Behavior Classifier 구현이다.

## 목표

- TestSuite와 TestCase를 재사용 가능한 정책 테스트 자산으로 축적한다.
- TestRun 시점의 TestCase를 TestCaseSnapshot으로 고정해 재현성을 확보한다.
- 하나의 TestRun에서 하나의 `HTTP_ENDPOINT` Application Target을 실행한다.
- Response Behavior Classifier가 `(prompt, actualResponse) -> COMPLY | REFUSE`를 관측하고 GuardBench가 `COMPLY -> ALLOW`, `REFUSE -> BLOCK`으로 정규화한다. 도메인별 허용/차단 정책은 데이터셋의 `TestCase.expectedAction`이 소유한다.
- 실행에 사용한 Evaluator(classifier)의 provider/model을 Run에 불변하게 식별한다.
- Evaluator가 만든 EvaluationResult가 ExpectedResult를 만족하는지 Assertion한다.
- 현재 Run의 Assertion 결과로 Quality Gate를 판정한다.
- 완료된 두 Run의 저장 결과를 재호출 없이 비교해 Regression을 판정한다.
- 외부 provider 타입을 Core 판정 계약에서 분리한다.

사용자는 evaluator/classifier 설정을 제출하지 않는다. classifier는 SAFE/UNSAFE, 도메인 정책, 응답 품질, PASS/FAIL 또는 REGRESSION을 판단하지 않는다. 최종 SageMaker endpoint name, classifier system prompt와 모델 선정 threshold는 별도 실험으로 확정 중이며 설정으로 주입한다.

## 핵심 사용자 흐름

1. 사용자가 TestSuite와 현재 TestCase 정의를 관리한다.
2. 하나의 HTTP AI Application Target으로 TestRun을 요청한다.
3. 서버는 서비스 전역 고정 classifier 설정으로 TestCaseSnapshot과 사용한 설정 식별자를 고정한다.
4. 각 Snapshot input을 Application Target에 전달하고 자연어 응답을 받는다.
5. Response Behavior Classifier가 prompt와 자연어 응답을 평가해 `COMPLY | REFUSE`를 관측하고 `EvaluationResult(ALLOW | BLOCK)`로 정규화한다.
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
Evaluator (Response Behavior Classifier)
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

Regression은 Quality Gate와 별도다. 완료된 TestRun A와 B의 저장 결과를 비교하며 Application과 Evaluator를 다시 호출하지 않는다. 최소 비교 가능 조건은 동일한 테스트 정의와 동일한 Evaluator provider/model이다.

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
- #116: AWS Bedrock Guardrail Evaluator Adapter (완료, #173로 대체)
- #117: Application 실행 → Evaluator → Assertion Worker
- #118: 현재 TestRun Assertion 기반 Quality Gate
- #119: 저장된 완료 Run 결과 기반 Regression API
- #173: Guardrail Evaluator/Profile을 SageMaker Response Behavior Classifier로 교체([ADR 0013](../decisions/0013-response-behavior-classifier.md))

OpenAI-compatible `HTTP_ENDPOINT` Application Target의 응답은 prompt와 함께 Response Behavior Classifier의 입력으로 사용되며, 그 결과인 `EvaluationResult`와 `ExpectedResult`로 Assertion을 만든다. Quality Gate는 현재 Run의 평가 가능한 Assertion 통과율과 전체 Snapshot 실행 성공률을 집계해 두 비율이 각각 95% 이상이면 `PASS`, 평가 가능한 Assertion이 없으면 `NOT_EVALUATED`로 판정한다.

Regression API는 완료된 TestRun의 저장된 Snapshot/Evaluator provider·model과 EvaluationResult만 사용해 comparable historical Run을 찾고 결과 변화를 계산한다. Regression 과정에서는 Application Target이나 Evaluator를 다시 호출하지 않는다.

## Non-Goals

- 정책 또는 Guardrail 설정 자동 생성
- 고객 애플리케이션 CI/CD 또는 PR Gate 제품 통합
- `TestCaseRevision` 또는 `TestSuiteRevision` 도입
- classifier 모델 학습/fine-tuning, classifier benchmark 코드 작성, 모델 선정 실험
- 고객별 classifier prompt, TestSuite/TestCase의 evaluation system prompt 필드
- semantic correctness/relevance 평가, embedding evaluator
- classifier가 직접 regression 또는 quality gate를 판정하는 기능
- provider ensemble 또는 provider별 고급 설정의 공개 계약 확정
