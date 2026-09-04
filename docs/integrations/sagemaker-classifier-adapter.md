# SageMaker Response Behavior Classifier Adapter 설계 근거

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-04
> Canonical source: GitHub
> Related: [ADR 0013](../decisions/0013-response-behavior-classifier.md)
> Related Issue: #173

## 목표 역할

Amazon SageMaker endpoint에 직접 서빙한 텍스트 모델은 Guardrail 정책 판단이 아니라 Response Behavior
Classifier다. AI Application이 반환한 자연어 ApplicationResponse가 원 요청(prompt)을 실제로
수행했는지(COMPLY) 또는 거부했는지(REFUSE)만 관측하고 GuardBench 공통 `EvaluationResult(ALLOW | BLOCK)`로
정규화한다.

```text
TestCaseSnapshot.input (prompt)
        ↓
AI Application Target
        ↓ Natural Language ApplicationResponse
SageMaker Classifier Evaluator Adapter
        ↓ InvokeEndpoint (OpenAI-compatible chat completions)
COMPLY | REFUSE
        ↓ normalize
EvaluationResult(ALLOW | BLOCK)
```

classifier는 SAFE/UNSAFE, 도메인 정책, 응답 품질, PASS/FAIL 또는 REGRESSION을 판단하지 않는다. 이 판단은
계속 GuardBench 코어(ExpectedResult, Assertion, Regression, Quality Gate)가 소유한다.

사용자는 classifier endpoint, provider 또는 prompt를 제출하지 않는다. classifier system prompt는
서비스 전역 고정 정책이며 TestSuite/TestCase에 evaluation system prompt 필드를 추가하지 않는다.

## 왜 Bedrock이 아닌 SageMaker인가

[ADR 0013](../decisions/0013-response-behavior-classifier.md)에서 최초 채택한 AWS Bedrock
`Converse` API 기반 구현은 Bedrock API 제약으로 SageMaker endpoint에 모델을 직접 서빙하는 방식으로
교체되었다. 실험으로 검증된 DJL LMI(vLLM 기반) 컨테이너 기준 latency는 스크리닝 80건 평균 105.6ms,
Stage 2 전체(2169건) 평균 124.4ms, P95 193.7ms로 Bedrock 대비 크게 낮다.

## 보안 경계

Application response와 prompt는 Evaluator 입력으로만 사용하며 public API에 노출하지 않는다. Provider
원문 오류, classifier 원문 출력, 사용자 input, Application response, 자격 증명과 stack trace는 승인된
저장·관측 계약 없이 DB·일반 로그에 노출하지 않는다. 외부 호출은 DB 트랜잭션 밖에서 수행하고, retry·timeout·
stale 결과 차단은 Worker 계약과 함께 구현한다.

## 구현

`com.guardbench.evaluator.infrastructure.sagemaker.SageMakerClassifierEvaluatorAdapter`는
`EvaluatorExecutionPort`를 구현한다. Adapter는 TestRun이 고정한 `EvaluatorReference`로
`evaluator_reference`의 `provider_code`/`model_id`(SageMaker에서는 endpoint name 값)를 조회하고,
prompt와 Application의 자연어 응답을 하나의 classifier 입력으로 조합해 AWS SDK `InvokeEndpoint` 요청으로
변환한다.

| 단계 | 소비자 소유 Port/저장 | SageMaker Runtime API |
| --- | --- | --- |
| Evaluator reference 등록 | `RegisterEvaluatorReferencePort` / `evaluator_reference` | 외부 호출 없음 |
| Application response 평가 | `EvaluatorExecutionPort` | `InvokeEndpoint` |

### 요청/응답 스키마 (검증됨)

endpoint는 DJL LMI(vLLM) 컨테이너의 OpenAI-compatible chat completions 스키마를 사용한다.
Content-Type과 Accept는 모두 `application/json`이다.

요청 body:

```json
{
  "messages": [
    { "role": "system", "content": "<classifier system prompt>" },
    { "role": "user", "content": "USER REQUEST:\n<prompt>\n\nASSISTANT RESPONSE:\n<response>" }
  ],
  "temperature": 0,
  "max_tokens": 8,
  "chat_template_kwargs": { "enable_thinking": false }
}
```

`chat_template_kwargs.enable_thinking: false`는 필수다. Qwen3 계열 모델은 기본적으로 thinking mode가
켜져 있어 `<think>...</think>` 블록을 응답에 섞어 넣으며, 이를 끄지 않으면 순수 라벨 텍스트를 뽑기
어렵다.

응답 body:

```json
{
  "choices": [
    { "message": { "role": "assistant", "content": "REFUSE" }, "finish_reason": "stop" }
  ],
  "usage": { "prompt_tokens": 183, "completion_tokens": 3, "total_tokens": 186 }
}
```

라벨은 `choices[0].message.content`에 raw text로 들어있다(구조화 필드가 아니다). `COMPLY`는 `ALLOW`,
`REFUSE`는 `BLOCK`으로 정규화한다. 이 두 레이블이 아닌 출력, choices/message/content가 없는 응답,
body가 없는 응답은 `PROVIDER_RESPONSE_INVALID`로 실패한다. SageMaker 호출 실패, timeout, throttling과
classifier 출력 파싱 실패는 `ALLOW`/`BLOCK`으로 임의 fallback하지 않는다.

이 스키마는 DJL LMI/vLLM 컨테이너 기준이다. 다른 모델/다른 서빙 컨테이너(HuggingFace TGI 표준, 커스텀
`inference.py` 등)로 교체하면 요청/응답 스키마를 다시 검증해야 한다.

## 설정과 미결정 값

최종 SageMaker endpoint name과 classifier system prompt 최종 버전은 이 문서 작성 시점에 확정되지
않았다. 실험에 사용했던 endpoint(`guardbench-qwen3-4b-endpoint` 등, 고정값 아님)는 검증 후 삭제되었다.
배포 시점에 다음 설정으로 주입한다.

| 설정 키 | 의미 |
| --- | --- |
| `guardbench.sagemaker.classifier.endpoint-name` | 실제 호출할 SageMaker endpoint 이름. 비어 있으면 `UNCONFIGURED` placeholder로 안전하게 처리되며 애플리케이션은 정상 기동하지만 실제 classifier 호출은 `EVALUATOR_CONFIGURATION_INVALID`로 실패한다. |
| `guardbench.sagemaker.classifier.system-prompt` | classifier system prompt(v1) 원문. 비어 있으면 미설정으로 처리한다. |
| `guardbench.sagemaker.classifier.user-prompt-template` | classifier user 메시지 템플릿. 비어 있으면 `USER REQUEST`/`ASSISTANT RESPONSE` 기본 형식을 사용한다. |

값이 확정되면 이 표와 배포 설정을 함께 갱신한다. 값 확정 전까지 운영 환경에서 classifier를 활성화하지
않는다.

`evaluator_reference` 테이블은 catalog나 checks/strictness 없이 실제 실행에 사용한 `provider_code`
(`SAGEMAKER`)와 `model_id`(endpoint name)만 저장한다.

## IAM과 네트워크 (미결정 — 별도 검증 필요)

최소 권한 IAM 정책은 다음과 같다.

```json
{
  "Effect": "Allow",
  "Action": "sagemaker:InvokeEndpoint",
  "Resource": "arn:aws:sagemaker:ap-northeast-2:<account-id>:endpoint/<endpoint-name>"
}
```

이 문서 작성 시점의 실험은 `AmazonSageMakerFullAccess` 관리형 정책으로 검증했으며 운영에는 과도하다.
백엔드가 사용하는 자격 증명(ECS Task Role, EC2 Instance Profile 등)에 위 최소 권한만 추가하는 방식은
아직 별도로 설계·검증되지 않았다.

VPC 설정도 검증되지 않았다. 실험은 VPC 설정이 없는 기본(퍼블릭) endpoint로 진행했다. Worker가 실행되는
네트워크에서 SageMaker Runtime API endpoint(`https://runtime.sagemaker.ap-northeast-2.amazonaws.com`
또는 VPC endpoint)로 도달 가능한지는 배포 환경별로 별도 확인이 필요하다.

## SDK timeout/retry

실측 latency(스크리닝 80건 평균 105.6ms, Stage 2 전체 2169건 평균 124.4ms, P95 193.7ms) 기준으로 기존
`guardbench.sagemaker.*` 15초 한도는 충분한 마진을 갖는다. execution claim lease(45초)보다 짧게
유지한다.

실험(별도 검증 도구)에서는 `ThrottlingException`, `ModelError`, `InternalFailure`,
`ServiceUnavailable`을 지수 백오프(최대 4회, base 1초)로 재시도했다. 그러나 GuardBench Worker는
이미 Provider business retry를 claim 계층(`ExecuteTestRunService.MAX_EXECUTION_ATTEMPTS`, 최대
3회)에서 수행하므로, SDK `max-attempts`를 그대로 4로 설정하면 실제 호출 횟수가 최대 12회(SDK 4회 x
claim 3회)로 증폭된다. 이를 방지하기 위해 `guardbench.sagemaker.max-attempts`는 1(SDK 자체 재시도
없음)로 고정한다. 설정 override도 1 이외의 값을 허용하지 않으며, transient
실패(`ThrottlingException`, `ModelError`, `InternalFailure`,
`ServiceUnavailable` 등)에 대한 재시도는 claim retry 한 계층에만 위임한다. 오류는
`EVALUATOR_NOT_FOUND`, `EVALUATOR_ACCESS_DENIED`, `EVALUATOR_CONFIGURATION_INVALID`,
`PROVIDER_UNAVAILABLE`, `PROVIDER_RESPONSE_INVALID`, `PROVIDER_TIMEOUT`으로 안전하게 수렴한다.

현재 `dev`에서 이 Adapter는 `EvaluatorExecutionPort` 구현으로 존재하며 Worker가 prompt와 Application
response를 함께 전달한다. Worker는 Evaluator의 `EvaluationResult`를 실행 결과로 저장하고, ExpectedResult와
비교한 Assertion을 생성한다. Application response 자체는 내부 실행 결과로만 보존하며 public 결과에는
노출하지 않는다.

AWS 근거는 [InvokeEndpoint API](https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_runtime_InvokeEndpoint.html)다.
