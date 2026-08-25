# Bedrock Guardrails Adapter 설계 근거

> Status: DRAFT
> Scope: Issue #17
> Canonical implementation contract: Issue #49에서 분리 예정
> Last verified: 2026-08-25

이 문서는 #17의 Port와 Normalizer가 실제 Amazon Bedrock API/Java SDK 계약을 어떻게 반영하는지 기록한다. 이 문서 자체는 운영 인프라나 AWS 자격 증명을 승인하지 않으며, 실제 SDK dependency와 concrete Adapter 구현 전의 설계 근거다.

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
| guardrailIdentifier | TestRun의 고정 target identifier |
| clientRequestToken | `guardbench-test-run-{testRunId}` |
| description | MVP에서는 선택 사항이며 별도 의미를 부여하지 않음 |

AWS 문서상 `clientRequestToken`은 1~256자의 영숫자/하이픈 값이며 같은 token의 재요청은 idempotent하게 처리된다. 따라서 TestRun별 결정적 token을 `GuardrailMaterializationRequest.java`가 생성한다.

### 응답

HTTP 202 응답의 `guardrailId`와 숫자형 `version`을 `GuardrailMaterializedVersion.java`로 변환한다. DRAFT version이나 빈 version은 실행 대상으로 허용하지 않는다.

## Guardrail 실행

### 요청

ApplyGuardrail에는 다음을 전달한다.

| AWS 필드 | 설계 값 |
| --- | --- |
| guardrailIdentifier | materialized target identifier |
| guardrailVersion | 1~8자리 숫자형 resolved version |
| source | MVP input 실행이므로 `INPUT` |
| content | Snapshot input을 하나의 text content block으로 변환 |
| outputScope | MVP에서는 action만 필요하므로 기본값 사용 |

ApplyGuardrailRequest의 `content`는 필수이며 `source`는 `INPUT` 또는 `OUTPUT`이다. MVP는 TestCaseSnapshot input 평가이므로 `INPUT`만 사용한다.

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

이 문서의 DRAFT 상태를 APPROVED 구현 계약으로 승격하는 작업과 canonical 문서 라우팅은 #49에서 처리한다.
