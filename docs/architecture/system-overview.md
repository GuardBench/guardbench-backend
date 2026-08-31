# 시스템 아키텍처 개요

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-31
> Canonical source: GitHub
> Origin: [Notion 도메인 모델 정의](https://app.notion.com/p/3c0eeed6b62d81b48c03ed6034440936)
> Related: [ADR 0011](../decisions/0011-ai-application-target-and-guardrail-evaluator.md)

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
- MVP 공개 Application Target type은 `HTTP_ENDPOINT`이며 TestRun 생성 요청은 inline Evaluation Profile을 포함한다.
- GuardBench가 Evaluation Profile을 실제 Evaluator/provider 설정으로 해석하며 사용자는 provider를 직접 지정하지 않는다.
- Application Target Adapter는 Snapshot input을 외부 Application에 전달하고 자연어 ApplicationResponse를 반환한다.
- Evaluator Adapter는 ApplicationResponse를 `EvaluationResult(ALLOW | BLOCK)`로 변환한다.
- AWS Bedrock Guardrail은 첫 번째 Evaluator Adapter 구현이다.
- TestSuite, TestCase, ExpectedResult, Assertion, Quality Gate와 Regression의 의미는 AWS SDK와 독립적이다.
- Evaluation Profile CRUD, provider ensemble이나 provider별 범용 고급 설정 계층을 선제 도입하지 않는다.

```text
TestCaseSnapshot
      ↓
AI Application Target Adapter
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

현재 `target` 경계는 `BEDROCK_GUARDRAIL`과 `HTTP_ENDPOINT`를 모두 Target provider로 저장하고, Bedrock Adapter가 Snapshot input을 `ApplyGuardrail`에 직접 전달해 `ActualResult`를 만든다. inline Evaluation Profile 해석도 없다. 이는 목표 경계가 구현된 상태가 아니다.

#114~#117에서 Application Target과 Evaluator 실행 경계를 전환하고, #118~#119에서 Quality Gate와 Regression을 구현한다. 전환 전 물리 구조는 [TestRun Persistence 구현 인덱스](testrun-persistence.md)에서 current implementation으로만 확인한다.

이 구조는 물리적 MSA 전환을 요구하지 않는다. 목적은 각 Context가 상대 구현 없이 Core를 개발·테스트하고 실제 통합 결합을 Adapter에 제한하는 것이다. 세부 타입 격리는 [ADR 0006](../decisions/0006-independent-domain-contract-boundaries.md)의 대체되지 않은 원칙을 따른다.
