# 시스템 아키텍처 개요

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-01
> Canonical source: GitHub
> Origin: [Notion 도메인 모델 정의](https://app.notion.com/p/3c0eeed6b62d81b48c03ed6034440936)
> Related: [ADR 0011](../decisions/0011-ai-application-target-and-guardrail-evaluator.md), [ADR 0013](../decisions/0013-response-behavior-classifier.md)

GuardBench MVP는 Java·Spring Boot 단일 백엔드다. AI Application 실행, 평가 계약과 구체 기술 Adapter를 분리한다.

```text
HTTP/SQS Adapter → Application Use Case → Domain/Core
                                      ↑
                  DB Adapter · Application Target Adapter
                             · Evaluator Adapter
```

- Domain은 Spring MVC, JPA, AWS SDK, HTTP DTO에 의존하지 않는다.
- `testdefinition`, `testrun`, `evaluation`과 외부 통합 경계는 같은 프로세스에 배포하더라도 독립적으로 개발한다.
- 다른 Context의 Domain Java 타입을 직접 사용하지 않고 소비자가 소유한 Port와 scalar/value 계약을 Integration Adapter가 연결한다.
- Controller는 Application Service만 호출한다.
- Repository 계약은 Domain에, 구현은 Infrastructure에 둔다.
- TestRun은 하나의 AI Application Target을 실행하고 실제 사용한 Evaluator 설정과 버전을 불변하게 식별한다.
- MVP 공개 Application Target type은 `HTTP_ENDPOINT`다.
- `HTTP_ENDPOINT`는 OpenAI-compatible chat completions 계약만 지원하며 `identifier`와 `model`을 필수 실행 정보로 사용한다.
- TestRun 생성 요청은 evaluator/classifier 설정을 포함하지 않는다.
- GuardBench는 서비스 전역 고정 Response Behavior Classifier로 실제 응답 행동(`COMPLY | REFUSE`)을 관측하며, 사용자는 provider나 model 식별자를 직접 지정하지 않는다.
- Application Target Adapter는 Snapshot input을 OpenAI-compatible request로 외부 Application에 전달하고 `choices[0].message.content`를 자연어 ApplicationResponse로 정규화한다.
- Evaluator Adapter는 prompt와 ApplicationResponse를 `COMPLY | REFUSE`로 분류하고 `EvaluationResult(ALLOW | BLOCK)`로 정규화한다. `COMPLY -> ALLOW`, `REFUSE -> BLOCK`이다.
- Amazon SageMaker endpoint에 직접 서빙한 텍스트 모델(`InvokeEndpoint` API)은 첫 번째 Response Behavior Classifier Adapter 구현이다.
- TestSuite, TestCase, ExpectedResult, Assertion, Quality Gate와 Regression의 의미는 AWS SDK와 독립적이다.
- Evaluator/classifier catalog, provider ensemble이나 provider별 범용 고급 설정 계층을 선제 도입하지 않는다.

```text
TestCaseSnapshot
      ↓
OpenAI-compatible HTTP Application Target Adapter
      ↓
Natural Language ApplicationResponse
      ↓
Evaluator Adapter
      ↓
EvaluationResult
      ↓
Assertion → 현재 TestRun Quality Gate
```

Regression은 위 실행 흐름에 포함되지 않는다. 완료된 두 TestRun의 저장 결과를 comparability check 뒤 비교하며 외부 Application이나 Evaluator를 재호출하지 않는다.

## 현재 구현

#114를 통해 실제 immutable `EvaluatorReference`를 TestRun에 고정하는 경계가 구현되었다.

#115와 #125를 통해 `HTTP_ENDPOINT` Application Target이 자연어 응답을 수집하고 OpenAI-compatible chat completions 요청·응답을 처리하는 Adapter가 구현되었다. #128은 MVP 계약을 단순화해 generic HTTP 경로를 제거하고 모든 HTTP Target에 `model`을 필수화했다.

#116을 통해 AWS Bedrock Guardrail이 Evaluator Adapter로 전환되어 `EvaluatorExecutionPort` 구현으로 존재했으나, #173을 통해 Amazon SageMaker endpoint 기반 Response Behavior Classifier Adapter로 대체되었다([ADR 0013](../decisions/0013-response-behavior-classifier.md)). #117을 통해 Worker가 prompt와 Application response를 Evaluator에 전달하고 정규화된 `EvaluationResult`와 Assertion을 저장한다. Application response는 내부 실행 결과로만 보존하며 public 결과에는 노출하지 않는다.

#118을 통해 Quality Gate가 현재 TestRun의 평가 가능한 Assertion 통과율과 전체 Snapshot 실행 성공률을 집계하도록 전환되었다. 두 비율이 각각 95% 이상이면 `PASS`, 평가 가능한 Assertion이 없으면 `NOT_EVALUATED`다.

#119를 통해 완료된 TestRun 사이의 stored-result Regression API가 구현되었다. backend가 Snapshot 정의와 Evaluator 설정을 기준으로 comparable historical Run을 판단하고 저장된 EvaluationResult를 비교하며, 이 과정에서 Application Target이나 Evaluator를 다시 호출하지 않는다.

현재 MVP backend의 Application → Evaluator → Assertion → Quality Gate 실행 흐름과 저장 결과 기반 Regression 흐름은 구현되어 있다.

물리 구조는 [TestRun Persistence 구현 인덱스](testrun-persistence.md)에서 확인한다.

이 구조는 물리적 MSA 전환을 요구하지 않는다. 목적은 각 Context가 상대 구현 없이 Core를 개발·테스트하고 실제 통합 결합을 Adapter에 제한하는 것이다. 세부 타입 격리는 [ADR 0006](../decisions/0006-independent-domain-contract-boundaries.md)의 대체되지 않은 원칙을 따른다.
