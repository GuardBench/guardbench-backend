# Bedrock Guardrails Adapter 설계 근거

> Status: DRAFT
> Scope: Issue #17
> Canonical implementation contract: Issue #49에서 분리 예정
> Last verified: 2026-08-25

이 문서는 #17의 Port와 Normalizer가 실제 Amazon Bedrock API/Java SDK 계약을 어떻게 반영하는지 기록한다. 이 문서 자체는 운영 인프라나 AWS 자격 증명을 승인하지 않으며, 실제 SDK dependency와 concrete Adapter 구현 전의 설계 근거다.

`ApplyGuardrail`은 foundation model 호출과 분리된 독립 평가 API다. 따라서 GuardBench가 평가할 입력 텍스트를 직접 전달하며, 모델 ID나 모델 응답을 요청하지 않는다. #17의 Candidate 경로에서는 먼저 DRAFT를 숫자 버전으로 materialize한 뒤 그 버전을 평가하지만, API 자체는 사전 구성된 Guardrail의 `DRAFT` 버전을 직접 평가하는 것도 허용한다.

## 공식 참고 자료

- [CreateGuardrailVersion API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_CreateGuardrailVersion.html)
- [ApplyGuardrail API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ApplyGuardrail.html)
- [AWS SDK for Java BedrockClient](https://docs.aws.amazon.com/java/api/latest/software/amazon/awssdk/services/bedrock/BedrockClient.html)
- [AWS SDK for Java BedrockRuntimeClient](https://docs.aws.amazon.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/BedrockRuntimeClient.html)
- [AWS SDK for Java ApplyGuardrailResponse](https://docs.aws.amazon.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/model/ApplyGuardrailResponse.html)

## API와 SDK 경계

| GuardBench 작업 | AWS API | Java SDK client | SDK module |
| --- | --- | --- | --- |
| Candidate DRAFT materialization | CreateGuardrailVersion | BedrockClient | bedrock |
| Snapshot input 평가 | ApplyGuardrail | BedrockRuntimeClient | bedrockruntime |

`testrun/application/port/out`은 AWS SDK를 import하지 않는다. 추후 `guardrail/infrastructure/bedrock` Adapter만 SDK client와 SDK model을 알고 Port의 scalar/value contract로 변환한다.

## Candidate materialization

### 요청

CreateGuardrailVersion에는 다음 값을 전달한다.

| AWS 필드 | 설계 값 |
| --- | --- |
| guardrailIdentifier | TestRun의 고정 target identifier. AWS Pattern과 2048자 제약은 실행 요청과 동일하며 현재 Port는 non-blank만 검증한다 |
| clientRequestToken | `guardbench-test-run-{testRunId}` |
| description | MVP에서는 선택 사항이며 별도 의미를 부여하지 않음 |

AWS 문서상 `clientRequestToken`은 1~256자의 영숫자/하이픈 값이며 같은 token의 재요청은 idempotent하게 처리된다. 따라서 TestRun별 결정적 token을 `GuardrailMaterializationRequest.java`가 생성한다.

### 응답

HTTP 202 응답의 `guardrailId`와 숫자형 `version`을 `GuardrailMaterializedVersion.java`로 변환한다. #17의 Candidate materialization 결과는 반드시 숫자형 version이어야 하며, Candidate DRAFT를 그대로 실행 대상으로 전달하지 않는다.

## Guardrail 실행

### 요청

ApplyGuardrail에는 다음을 전달한다.

| AWS 필드 | 설계 값 |
| --- | --- |
| guardrailIdentifier | materialized target identifier. AWS Pattern은 `(\|([a-z0-9]+)\|(arn:aws(-[^:]+)?:bedrock:[a-z0-9-]{1,20}:[0-9]{12}:guardrail/[a-z0-9]+))`이고 최대 2048자다. 현재 Port는 non-blank만 검증한다 |
| guardrailVersion | Candidate 경로에서는 1~8자리 숫자형 resolved version. AWS API 자체는 사전 구성된 Guardrail의 `DRAFT`도 허용 |
| source | MVP input 실행이므로 `INPUT` |
| content | Snapshot input을 하나의 text content block으로 변환 |
| outputScope | MVP에서는 생략하거나 `INTERVENTIONS`. 전체 출력이 필요한 디버깅 시에만 `FULL` |

ApplyGuardrailRequest의 `content`는 필수이며 `source`는 `INPUT` 또는 `OUTPUT`이다. MVP는 TestCaseSnapshot input 평가이므로 `INPUT`만 사용한다. `outputScope`는 Required: No이고 유효한 값은 `INTERVENTIONS`와 `FULL`이다. API Reference는 기본값을 명시하지 않으므로 이 문서도 기본값을 단정하지 않는다. 판정 입력이 action뿐이라 두 값 중 무엇을 보내도 MVP 결과는 달라지지 않는다.

`guardrailVersion` Pattern은 `(|([1-9][0-9]{0,7})|(DRAFT))`이므로 `GuardrailExecutionRequest`와 `GuardrailMaterializedVersion`의 `[1-9][0-9]{0,7}` 검증은 AWS 계약을 따른다. 다만 `docs/api/openapi.yaml`과 `ck_test_run_versions`는 `^[0-9]+$`를 허용해 `"0"`과 9자리 값이 입력·저장될 수 있고, 그 값은 Port 생성 시점에 `IllegalArgumentException`으로 거부된다.

### 응답 정규화

| AWS action | TestRun 결과 |
| --- | --- |
| `NONE` | ActualResult(`ALLOW`) |
| `GUARDRAIL_INTERVENED` | ActualResult(`BLOCK`) |
| null/unknown | `PROVIDER_RESPONSE_INVALID` |

실제 응답에는 `actionReason`, `assessments`, `outputs`, `usage`, `coverage`가 포함될 수 있지만 MVP Core의 판정 입력은 action뿐이다. 이 값들과 ARN, 원문 content는 Port 결과로 노출하지 않는다. `GuardrailResultNormalizer.java`는 SDK 타입이 아닌 action code를 입력으로 받아 이 경계를 검증한다.

## 오류와 timeout 매핑

| AWS/SDK 상황 | GuardBench code | 처리 |
| --- | --- | --- |
| ResourceNotFoundException | TARGET_NOT_FOUND | FAILED |
| AccessDeniedException | TARGET_ACCESS_DENIED | FAILED |
| ValidationException 또는 materialization ConflictException | TARGET_CONFIGURATION_INVALID | FAILED |
| ThrottlingException, InternalServerException, ServiceUnavailableException, ServiceQuotaExceededException | PROVIDER_UNAVAILABLE | retry 후 FAILED |
| SDK 응답 action 누락/미지원 값 | PROVIDER_RESPONSE_INVALID | FAILED |
| SDK/client timeout | PROVIDER_TIMEOUT | timeout 재시도 후 TIMED_OUT |
| SDK 원문 메시지/stack trace | 공개하지 않음 | 고정 안전 메시지 사용 |

DB commit, SQS publish/ack 같은 기술 실패는 이 Adapter에서 TestExecution 실패로 바꾸지 않는다. Provider 호출 결과가 정상적으로 정규화된 뒤의 claim, 저장, retry와 최종화는 #18과 ADR 0005의 책임이다.

## 현재 구현과 후속 범위

현재 PR #58에는 다음만 구현되어 있다.

- 소비자 소유 materialization/execution Port
- provider-independent request/result/failure value contract
- action과 failure code를 Core 결과로 바꾸는 Normalizer
- 실제 AWS 호출 없이 실행하는 단위 테스트

아직 구현하지 않은 항목:

- `software.amazon.awssdk:bedrock` 및 `bedrockruntime` production dependency
- `BedrockClient`/`BedrockRuntimeClient` concrete Adapter
- AWS SDK model/exception mock 테스트
- 실제 AWS credential을 이용한 E2E
- version 제약 세 계층(`openapi.yaml`·`ck_test_run_versions`·Guardrail Port) 정렬과 거부 값을 `TARGET_CONFIGURATION_INVALID`로 정규화하는 변환은 #18의 Worker orchestration에서 처리한다
- `guardrailIdentifier`에 AWS Pattern 검증을 도입할지는 Port 계약을 좁히는 결정이므로 별도 판단이 필요하다

이 문서의 DRAFT 상태를 APPROVED 구현 계약으로 승격하는 작업과 canonical 문서 라우팅은 #49에서 처리한다.
