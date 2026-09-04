# 0013. Guardrail Evaluator를 Response Behavior Classifier로 대체

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-04
> Canonical source: GitHub
> Origin: [GitHub Issue #173](https://github.com/GuardBench/guardbench-backend/issues/173)

- ADR Status: ACCEPTED
- Decision date: 2026-09-04
- Related Issue: #173
- Supersedes in part: ADR 0011의 Guardrail Evaluator 역할, inline Evaluation Profile 계약과 Evaluator catalog/provisioning 경계

## Context

ADR 0011은 AWS Bedrock Guardrail을 첫 번째 Guardrail Evaluator 구현으로 채택했다. 사용자는 `evaluationProfile(checks + strictness)`을 제출하고 GuardBench는 이를 운영자가 사전 provisioning한 Bedrock Guardrail catalog entry로 해석해 `ApplyGuardrail`을 호출했다. Guardrail은 Application 응답의 정책 적합성/안전성(PII, harmful content 등)을 직접 판단하는 주체였다.

새 방향에서는 Guardrail이 응답의 정책 적합성을 판단하지 않는다. 도메인별 허용/차단 정책은 이미 `TestCase.expectedAction(ALLOW | BLOCK)`이 소유하고 있으므로, 평가 계층이 필요로 하는 것은 "실제 응답이 핵심 요청을 수행했는지(comply) 또는 거부했는지(refuse)"를 관측하는 것뿐이다. 이 관측은 `(prompt, actualResponse)`만으로 판단할 수 있으며 Guardrail의 checks/strictness 같은 정책 파라미터를 요구하지 않는다.

Provider는 최초 AWS Bedrock `Converse` API로 검토했으나, Bedrock API 제약으로 Amazon SageMaker endpoint에 텍스트 모델을 직접 서빙하는 방식으로 결정했다. SageMaker Runtime `InvokeEndpoint`와 DJL LMI(vLLM) 컨테이너 조합은 별도 실험으로 검증되었으며(스크리닝 80건 평균 105.6ms, Stage 2 전체 2169건 평균 124.4ms, P95 193.7ms), Bedrock 대비 latency가 크게 낮다.

## Decision

### Response Behavior Classifier

1. GuardBench는 `EvaluatorExecutionPort`를 유지하되 Provider 구현을 Guardrail Evaluator에서 Response Behavior Classifier로 교체한다. Classifier는 `(prompt, actualResponse) -> COMPLY | REFUSE`만 판단하며 SAFE/UNSAFE, 도메인 정책, PASS/FAIL, REGRESSION을 판단하지 않는다.
2. Classifier 입력은 TestCaseSnapshot의 `input`(prompt)과 Application Target이 반환한 자연어 `ApplicationResponse`다. `EvaluatorExecutionRequest`에 `prompt` 필드를 추가해 응답만으로 판단하지 않는다.
3. Classifier 결과는 기존 공통 계약과 호환되도록 `COMPLY -> ALLOW`, `REFUSE -> BLOCK`으로 정규화한다. `EvaluationResult(ALLOW | BLOCK)`, Assertion, Regression, Metrics, Quality Gate, Snapshot/Comparability는 코드 변경 없이 유지한다.
4. 구현은 Amazon SageMaker endpoint에 직접 서빙한 텍스트 모델을 SageMaker Runtime `InvokeEndpoint` API로 호출해 `COMPLY`/`REFUSE` 이진 레이블을 반환받는다. endpoint는 DJL LMI(vLLM) 컨테이너의 OpenAI-compatible chat completions 스키마(`messages` + `chat_template_kwargs.enable_thinking: false`)를 사용하며, 응답은 `choices[0].message.content`의 raw text다. 최종 endpoint name과 classifier system prompt는 이 ADR 확정 시점에도 미결정이며 `guardbench.sagemaker.classifier.endpoint-name`과 `guardbench.sagemaker.classifier.system-prompt` 설정으로 배포 시점에 주입한다.
5. classifier system prompt는 서비스 전역 고정 정책이다. TestSuite/TestCase에 evaluation system prompt 필드를 추가하지 않으며 모델별 도메인 prompt를 만들지 않는다.
6. SageMaker 호출 실패, timeout, throttling과 classifier 출력 파싱 실패는 `ALLOW`/`BLOCK`으로 임의 fallback하지 않는다. 기존 `TestExecutionError`/`EvaluatorFailureCode` 계약을 그대로 사용해 실패로 처리한다.
7. Provider business retry(`PROVIDER_UNAVAILABLE`/`PROVIDER_TIMEOUT`)의 소유권은 Worker claim retry(`ExecuteTestRunService.MAX_EXECUTION_ATTEMPTS`, 최대 3회) 한 계층에만 둔다. SageMaker Runtime SDK 자체 retry(`guardbench.sagemaker.max-attempts`)는 1(재시도 없음)로 고정한다. 두 계층이 동시에 재시도하면 실제 Provider 호출 횟수가 두 값의 곱으로 증폭된다(예: SDK 4회 x claim 3회 = 12회).

### EvaluationProfile과 catalog 제거

8. TestRun 생성 요청의 inline `evaluationProfile(checks + strictness)`을 완전히 제거한다. 사용자는 evaluator/classifier 설정을 제출하지 않는다.
9. profile canonicalization, profile catalog resolution(`ResolveEvaluatorCatalogPort`, `EvaluatorCatalogPersistenceAdapter`, `EvaluatorCatalogProperties`)과 `EVALUATION_PROFILE_NOT_SUPPORTED` 오류를 제거한다. 여러 canonical Guardrail(G1/G2/G3)을 구분해 등록하던 `bedrock_guardrail_evaluator` catalog 테이블도 제거한다.
10. `EvaluatorReference`는 재현성 메타데이터로 유지한다. 다만 catalog entry나 checks/strictness가 아닌 실제 실행에 사용한 classifier의 `providerCode`와 `modelId`만 저장한다. `EvaluatorRegistration(typeCode, identifier, revision)`은 `EvaluatorRegistration(providerCode, modelId)`로 단순화한다. SageMaker 맥락에서 `modelId` 값은 endpoint name을 담는다.
11. classifier 설정은 catalog가 아니라 서비스 전역 고정 설정(`EvaluatorRegistration` bean)이다. `CreateTestRunService`는 TestRun 접수마다 이 고정 registration으로 `EvaluatorReference`를 등록하며, catalog lookup 실패에 의한 409 응답 경로가 없다.

### 실패 시 안전한 기동

11. SageMaker endpoint name 또는 classifier system prompt가 설정되지 않은 상태에서도 애플리케이션은 기동할 수 있어야 한다(`UNCONFIGURED` placeholder). 다만 미설정 상태에서 classifier를 실제로 호출하면 `EVALUATOR_CONFIGURATION_INVALID`로 안전하게 실패하며 `ALLOW`/`BLOCK`으로 fallback하지 않는다.

## Alternatives

- Guardrail catalog 구조를 유지하고 checks/strictness를 classifier 파라미터로 재해석하는 방안은 classifier 책임(comply/refuse 관측)과 무관한 정책 파라미터를 유지하므로 선택하지 않았다. 이슈가 명시한 "Guardrail-specific 개념을 classifier 쪽에 이름만 바꿔 옮기지 않는다"는 원칙에 위배된다.
- AWS Bedrock `Converse` API로 classifier를 호출하는 방안은 Bedrock API 제약이 확인되어 선택하지 않았다. Amazon SageMaker endpoint에 텍스트 모델을 직접 서빙하는 방식으로 대체했다.
- 최종 SageMaker endpoint name, classifier system prompt와 모델 선정 threshold를 이 ADR에서 임의로 확정하는 방안은 별도 실험이 아직 운영 배포로 이어지지 않았으므로 선택하지 않았다. 대신 설정으로 주입 가능한 placeholder만 도입한다.
- `EvaluatorReference`를 완전히 제거하는 방안은 TestRun이 실제 사용한 Evaluator 설정을 사후에 재식별해야 하는 기존 불변식(ADR 0011)을 깨뜨리므로 선택하지 않았다. 대신 저장 내용을 provider/endpoint 식별자로 축소했다.

## Consequences

- 사용자는 TestRun 생성 시 평가 목적을 선택하지 않는다. Frontend는 `evaluationProfile` 없이 `testSuiteId`와 `target`만으로 TestRun을 생성할 수 있다.
- 도메인별 허용/차단 정책은 오직 데이터셋의 `TestCase.expectedAction`에 남고, classifier는 실제 응답 행동만 관측한다.
- Assertion, Regression, Metrics, Quality Gate 코어 로직은 변경되지 않았다. `COMPLY -> ALLOW`, `REFUSE -> BLOCK` 정규화가 기존 `EvaluationResult(ALLOW | BLOCK)` 계약과의 유일한 접점이다.
- 최종 SageMaker endpoint name, classifier system prompt, threshold가 확정되기 전까지 classifier는 설정 미완료 상태로 배포될 수 있으며 이 경우 모든 TestRun 실행이 `EVALUATOR_CONFIGURATION_INVALID`로 실패한다. 값 확정과 배포 반영은 별도 후속 작업이다.
- SageMaker endpoint 호출에 필요한 IAM 최소 권한 정책과 VPC 네트워크 도달성은 이 ADR이 확정하지 않는다. 별도로 설계·검증이 필요하다.
- 변경을 되돌리려면 새 ADR로 Evaluator 책임과 catalog 구조를 다시 정의한다. Guardrail catalog나 checks/strictness 계약을 active 문서에 다시 도입하지 않는다.

## Validation

1. `docs/api/openapi.yaml`과 실제 DTO에 `evaluationProfile`, `EvaluationProfileReq/Res`, `EvaluationCheck`, `EvaluationStrictness`, `EVALUATION_PROFILE_NOT_SUPPORTED`가 없는지 확인한다.
2. `EvaluatorExecutionRequest`가 `prompt`와 `applicationResponse`를 모두 요구하는지, SageMaker adapter가 이 둘을 모두 classifier 입력에 포함하는지 확인한다.
3. classifier 실패(호출 실패, timeout, 파싱 실패)가 `ALLOW`/`BLOCK`으로 귀결되지 않고 `EvaluatorFailureCode`로 반환되는지 단위 테스트로 확인한다.
4. 기존 Assertion/Regression/Quality Gate 테스트가 코드 변경 없이 통과하는지 확인한다.
5. `evaluator_reference` 테이블과 `EvaluatorRegistration`이 catalog 개념 없이 provider/endpoint 식별자만 저장하는지 마이그레이션과 코드를 확인한다.
