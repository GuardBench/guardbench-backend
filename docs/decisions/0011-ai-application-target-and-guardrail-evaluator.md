# 0011. AI Application Target과 Guardrail Evaluator 역할 분리

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-31
> Canonical source: GitHub
> Origin: [GitHub Issue #113](https://github.com/GuardBench/guardbench-backend/issues/113)

- ADR Status: ACCEPTED
- Decision date: 2026-08-31
- Related Issue: #113
- Supersedes in part: ADR 0001·0002·0003·0004·0005·0006·0008·0010의 Guardrail Target/SUT, Target `ActualResult` 정규화, 실행 중 comparison과 Quality Gate 의미

## Context

ADR 0010은 하나의 TestRun이 하나의 Target을 실행하도록 Baseline/Candidate 동시 실행을 제거했다. 그러나 현재 문서와 구현은 여전히 Amazon Bedrock Guardrail을 Target으로 호출하고 그 action을 `ActualResult`로 저장한다. 이 모델에서는 실제 사용자가 배포한 AI Application의 자연어 응답을 시험할 수 없고, Guardrail 버전 비교가 제품의 중심이라는 오래된 의미가 남는다.

GuardBench가 검증하려는 System Under Test는 AI Application이다. Guardrail은 Application 자체가 아니라 Application의 자연어 응답을 GuardBench 공통 판정으로 해석하는 Evaluator다. 현재 실행의 품질 판정과 이미 완료된 실행 사이의 Regression도 서로 다른 책임으로 분리해야 한다.

## Decision

### 실행과 평가

1. GuardBench의 SUT와 Target은 **AI Application**이다. 하나의 TestRun은 하나의 Application Target만 실행하며 MVP 공개 Target type은 `HTTP_ENDPOINT`다.
2. TestRun 접수 시 활성 TestCase 정의를 `TestCaseSnapshot`으로 불변 복제한다. 이후 TestCase 수정·삭제는 해당 Run의 실행과 판정 의미를 바꾸지 않는다.
3. Application Target은 Snapshot input을 받아 자연어 `ApplicationResponse`를 반환한다. `ALLOW`와 `BLOCK`은 Application 응답 값이 아니다.
4. Evaluator는 ApplicationResponse를 평가해 GuardBench 공통 `EvaluationResult(ALLOW | BLOCK)`를 만든다. AWS Bedrock Guardrail은 첫 번째 Guardrail Evaluator 구현이다.
5. TestRun 생성 요청은 사용자의 평가 목적을 구조화한 inline `evaluationProfile`을 받는다. MVP profile은 `checks`와 `strictness`로 구성하며 독립 CRUD 리소스나 `evaluationProfileId`가 아니다.
6. 사용자는 Evaluator type, provider, Bedrock Guardrail identifier/version을 직접 제출하지 않는다. GuardBench가 `evaluationProfile`을 실제 Evaluator 설정으로 해석하고, TestRun은 사용한 설정과 버전을 사후에 불변하게 식별할 수 있어야 한다. `EvaluatorReference`의 물리 표현과 해석 결과 저장은 [#114](https://github.com/GuardBench/guardbench-backend/issues/114)에서 구현한다.
7. 기존 `ExpectedResult(ALLOW | BLOCK)`과 Assertion 의미를 재사용한다. Assertion은 ExpectedResult와 EvaluationResult가 같으면 `PASS`, 다르면 `FAIL`이다. Application 실행 또는 평가가 완료되지 않아 EvaluationResult가 없으면 Assertion을 만들지 않는다.

```text
TestCaseSnapshot
        ↓
AI Application Target
        ↓
Natural Language ApplicationResponse
        ↓
Evaluator
        ↓
EvaluationResult(ALLOW | BLOCK)
        ↓
ExpectedResult와 비교
        ↓
Assertion
        ↓
현재 TestRun의 Quality Gate
```

### Quality Gate와 Regression

Quality Gate는 하나의 현재 TestRun에 저장된 Assertion 결과를 집계해 `PASS`, `FAIL` 또는 `NOT_EVALUATED`를 판정한다. 다른 Run이나 복수 Target 실행 결과는 Quality Gate 입력이 아니다. 구체적인 집계 정책과 저장/API 전환은 [#118](https://github.com/GuardBench/guardbench-backend/issues/118)에서 구현한다.

Regression은 Quality Gate와 별도 유스케이스다. 이미 완료된 두 TestRun의 저장 결과만 비교하며, 비교 시 과거 Application Target이나 Evaluator를 다시 호출하지 않는다.

```text
Completed TestRun A + Completed TestRun B
                    ↓
            Comparability Check
                    ↓
          Stored Result Comparison
                    ↓
             Regression Result
```

최소 비교 가능 조건은 동일한 테스트 정의와 동일한 Evaluator 설정이다. 구체적인 동일성 식별자, 추가 조건, 결과 모델과 API는 [#119](https://github.com/GuardBench/guardbench-backend/issues/119)에서 결정·구현한다. 한 TestRun 안에서 Baseline/Candidate를 동시에 실행하거나 Guardrail Version A/B를 제품의 비교 단위로 삼지 않는다.

### 현재 구현과 전환

이 ADR과 [OpenAPI](../api/openapi.yaml)는 합의된 목표 계약이다. #113에서는 Java 코드, Migration과 물리 ERD를 변경하지 않는다. 현재 구현은 `BEDROCK_GUARDRAIL`과 `HTTP_ENDPOINT`를 같은 Target abstraction으로 저장하고, Bedrock Guardrail을 실행해 `ActualResult`를 만들며, 단일 Target Quality Gate를 `NOT_EVALUATED`로 저장한다.

목표 계약과 구현의 차이는 다음 후속 Issue에서 해소한다.

- [#114](https://github.com/GuardBench/guardbench-backend/issues/114): TestRun의 EvaluatorReference 고정과 Guardrail Target 의존 제거
- [#115](https://github.com/GuardBench/guardbench-backend/issues/115): HTTP Endpoint AI Application 실행과 자연어 응답 수집
- [#116](https://github.com/GuardBench/guardbench-backend/issues/116): AWS Bedrock Guardrail Evaluator Adapter 전환
- [#117](https://github.com/GuardBench/guardbench-backend/issues/117): Worker를 Application 실행 → Evaluator → Assertion 흐름으로 전환
- [#118](https://github.com/GuardBench/guardbench-backend/issues/118): 현재 TestRun Assertion 기반 Quality Gate
- [#119](https://github.com/GuardBench/guardbench-backend/issues/119): 저장된 완료 Run 결과 기반 Regression API

inline Evaluation Profile의 공개 입력 계약은 #113에서 확정한다. Profile을 독립 영속·재사용 리소스로 만드는 설계, provider별 고급 설정과 provider ensemble은 Research 또는 후속 구현 범위이며 이 ADR에서 확정하지 않는다.

## Alternatives

- Bedrock Guardrail을 SUT로 유지하고 Guardrail 버전만 비교하는 모델은 실제 AI Application의 최종 응답을 검증하지 못하고 제품을 특정 provider lifecycle에 결합하므로 선택하지 않았다.
- 한 TestRun이 Baseline/Candidate Application을 동시에 실행하는 모델은 실행과 이력 비교의 수명주기를 다시 결합하므로 선택하지 않았다.
- Regression 시 과거 Target과 Evaluator를 재실행하는 모델은 저장된 역사 결과가 아니라 현재 외부 상태를 비교하게 되어 재현성을 해치므로 선택하지 않았다.
- 사용자에게 provider와 Guardrail 식별자를 직접 선택하게 하는 모델은 제품 의도를 인프라 설정에 결합하므로 선택하지 않았다.
- Evaluation Profile CRUD와 provider 공통 고급 설정 모델을 이번 결정에서 확정하는 방안은 Research가 끝나지 않았으므로 선택하지 않았다.

## Consequences

- Application 실행 실패, Evaluator 실패, Assertion FAIL과 Quality Gate 판정을 서로 구분할 수 있다.
- 기존 ExpectedResult와 `ALLOW/BLOCK` Assertion 자산을 유지하면서 SUT를 AI Application으로 일반화한다.
- Quality Gate는 현재 Run의 상태를, Regression은 완료된 두 Run의 저장 결과 비교를 각각 설명한다.
- Java·DB의 기존 Target/Execution/Quality Gate 모델은 후속 구현이 끝날 때까지 목표 OpenAPI와 어긋난다. current implementation 문서는 이 차이를 명시해야 하며, 미래 물리 구조를 추측해 설명해서는 안 된다.
- 변경을 되돌리려면 새 ADR로 SUT, Evaluator와 비교 책임을 대체한다. 이전 Guardrail Target 또는 Baseline/Candidate 계약을 active 문서에 다시 도입하지 않는다.

## Validation

1. active 제품·도메인·아키텍처 문서가 Application Target → 자연어 응답 → Evaluator → EvaluationResult → Assertion → Quality Gate 흐름을 사용한다.
2. Quality Gate가 현재 TestRun만 집계하고 Regression은 완료된 두 Run의 저장 결과만 비교하는지 확인한다.
3. Regression 설명에 Application 또는 Evaluator 재호출이 없는지 확인한다.
4. 이전 ADR의 충돌 부분이 이 ADR로 부분 supersede되었음이 문서 지도와 각 ADR 경고에서 추적되는지 확인한다.
5. current implementation과 target architecture가 구분되고 #114~#119가 각 구현 차이를 소유하는지 확인한다.
6. OpenAPI가 inline Evaluation Profile만 요청으로 받고 Evaluator/provider 설정을 요청 필드로 노출하지 않는지 확인한다.
7. Java, Migration과 PlantUML/PNG ERD가 변경되지 않았는지 확인하고 OpenAPI syntax와 `$ref` 무결성을 검증한다.
