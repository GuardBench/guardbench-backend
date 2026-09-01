# Evaluator Provider 설정 추상화 조사

> Status: DRAFT
> Owner: Backend
> Last reviewed: 2026-09-01
> Canonical source: GitHub
> Related Issue: #120

이 문서는 후속 구현 여부를 판단하기 위한 조사 결과다. 기존 MVP의
[`EvaluationProfile Catalog`](../domain/evaluation-profile-catalog.md)와 공개 API를 변경하거나
`EvaluationProfile`을 독립 도메인 모델로 확정하지 않는다.

## 결론

- 정책 목적, 검사 대상(`INPUT`/`OUTPUT`), 차단 기준, 실제 설정 identity는 공통으로 다룰 수 있다.
- `RELAXED`/`STANDARD`/`STRICT`를 모든 provider에서 같은 검출률이나 위험 수준으로 해석할 수는 없다.
  provider별 범주, 점수 체계, 검사 위치와 필터 lifecycle이 다르다.
- 따라서 사용자에게는 **정책 목적과 제품이 정의한 민감도**만 보이고, provider별 mapping은 운영
  catalog가 소유해야 한다. 민감도는 provider 간 동등성 보장이 아니라 해당 정책 안의 상대적 의도다.
- MVP Bedrock catalog와 `EvaluatorReference` snapshot은 이 결론에 부합한다. 지금 DB/API를
  일반화하거나 두 번째 adapter를 구현할 필요는 없다.

## Provider 비교

| Provider | 현재 안전성 설정 모델 | 현재 범주와의 연결 | immutable identity / 재현성 주의점 |
| --- | --- | --- | --- |
| Amazon Bedrock Guardrails | 하나의 Guardrail에 content-filter 강도, prompt-attack 강도, PII/regex, denied topic, word filter 등을 설정한다. `ApplyGuardrail`은 identifier와 numbered version을 받는다. | prompt attack과 유해 콘텐츠는 범주별 `NONE`/`LOW`/`MEDIUM`/`HIGH` 강도로 설정할 수 있다. PII는 선택한 entity/regex와 block·mask 동작으로 설정한다. | Guardrail identifier + numbered version을 사용한다. `DRAFT`는 재현 가능한 실행 identity가 아니므로 Run에는 numbered version만 고정한다. |
| Azure AI Content Safety | Text Analyze는 harm category별 severity와 blocklist match를 반환하며, API 호출 자체는 차단 threshold를 받지 않는다. Prompt Shields는 prompt attack을 별도로 판정한다. API Management policy는 category threshold, blocklist, Prompt Shield 적용 여부를 조합해 enforcement할 수 있다. | harm은 Hate/SelfHarm/Sexual/Violence와 4·8단계 severity다. prompt attack은 Prompt Shields의 별도 capability다. 일반 Text Analyze는 PII evaluator가 아니다. | resource endpoint, API version, 선택 category·output granularity, blocklist revision 및 GuardBench가 적용한 threshold rule을 함께 고정해야 한다. Azure가 immutable policy revision을 제공한다고 가정하면 안 된다. |
| Google Gemini / Vertex AI | 모델 호출의 `SafetySetting`에서 harm category별 block method와 threshold를 지정한다. PII는 non-configurable filter이고, 별도 Sensitive Data Protection은 inspection template 또는 request config를 사용한다. | harm은 Hate Speech/Harassment/Sexually Explicit/Dangerous Content이며 probability와 severity를 사용한다. 이 safety filter는 prompt injection 전용 evaluator가 아니다. | configurable content filter는 model version과 별도의 version이 없다. model identifier/version, safety setting, API version을 고정해야 하며, text 응답만 독립 평가하는 provider adapter와 같은 형태로 취급할 수 없다. |

근거: [Bedrock ApplyGuardrail API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ApplyGuardrail.html),
[Bedrock Guardrail components](https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-components.html),
[Azure Text Analyze API](https://learn.microsoft.com/en-us/rest/api/contentsafety/text-operations/analyze-text?view=rest-contentsafety-2024-09-01),
[Azure Prompt Shields](https://learn.microsoft.com/en-us/azure/ai-services/content-safety/concepts/jailbreak-detection),
[Azure API Management content-safety policy](https://learn.microsoft.com/en-us/azure/api-management/llm-content-safety-policy),
[Gemini safety filters](https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/configure-safety-filters),
[Google Sensitive Data Protection inspection templates](https://cloud.google.com/sensitive-data-protection/docs/creating-templates-inspect).

## 공통화 경계

| 공통으로 표현할 의미 | provider-specific으로 남길 의미 |
| --- | --- |
| 정책 목적: prompt attack, harmful content, sensitive information | provider harm category와 taxonomy, PII entity·regex, denied topic, word/block list |
| 평가 source: user input, application output, 또는 generation 과정 | source별 지원 여부, chunking/tagging 규칙, request size와 언어 지원 |
| 제품이 정한 민감도와 allow/block enforcement rule | score probability·severity 방식, threshold 숫자, filter tier/method, detect-only·mask 동작 |
| 실행에 사용한 설정 identity와 activation 시각 | Guardrail numbered version, Azure resource/blocklist revision, Gemini model version·SafetySetting |
| `ALLOW`/`BLOCK` 및 안전한 오류 code | provider 원문 assessment, 모델/서비스 오류, raw input/output |

`STRICT`를 provider enum으로 직접 번역하는 공통 mapping은 만들지 않는다. 예를 들어 Bedrock은 category별
필터 강도를 제공하지만 Azure Text Analyze는 severity를 반환하고 GuardBench 또는 gateway가 threshold를
결정한다. Gemini은 probability와 severity를 선택적으로 사용하는 model-coupled setting이다. 그러므로
동일한 label이 “같은 수준의 차단”을 뜻한다는 API 약속은 부정확하다.

## 사용자-facing 설정 모델 초안

현 MVP의 inline `EvaluationProfile(checks, strictness)`을 유지한다. 이 값은 provider 설정값이 아니라
사용자가 고르는 **평가 의도**다.

```text
EvaluationProfile
  checks: set of policy intents
    PROMPT_INJECTION | HARMFUL_CONTENT | PII_LEAKAGE
  strictness: RELAXED | STANDARD | STRICT
```

- `checks`는 어떤 위험을 평가할지 나타낸다. provider name, category, identifier, version은 포함하지 않는다.
- `strictness`는 해당 profile의 상대적 민감도이며 provider 간 측정 동등성을 뜻하지 않는다.
- policy별로 민감도에 의미가 없으면 catalog가 collapse할 수 있다. 현재 PII-only profile이 그 사례다.
- 다중 provider가 실제로 도입될 때에도 provider-specific advanced options를 request DTO에 추가하지 않는다.
  운영자가 versioned catalog entry를 활성화하고, profile을 그 entry로 해석한다.

이 모델은 향후 profile CRUD, ensemble, provider 선택을 정당화하지 않는다. 그런 요구가 생기면 사용 시나리오,
정책 소유자와 version lifecycle을 별도 Decision으로 확정해야 한다.

## Adapter mapping 예시

| 정책 의도 | Bedrock MVP mapping | Azure 후보 mapping | Gemini/Vertex 후보 mapping |
| --- | --- | --- | --- |
| `PROMPT_INJECTION` | prompt-attack filter와 catalog가 고른 numbered Guardrail version | Prompt Shields의 prompt-attack result를 `BLOCK`으로 수렴 | 별도 전용 evaluator가 없으므로 Gemini safety setting으로 대체하지 않는다 |
| `HARMFUL_CONTENT` | selected category의 content-filter strength | harm severity 결과에 versioned catalog rule을 적용 | harm category별 `SafetySetting`의 method·threshold와 model version을 고정 |
| `PII_LEAKAGE` | fixed PII entity/regex policy와 Guardrail version | 별도 PII service 또는 명시적 미지원 결정이 필요 | non-configurable Gemini filter와 DLP inspection template는 서로 다른 capability이므로 명시적 mapping이 필요 |

모든 adapter는 `ALLOW`/`BLOCK`로 정규화하기 전에 최소한 다음 불변 값을 execution context에 가져야 한다.

```text
provider code
capability code and evaluated source
configuration locator and immutable revision (when provider supports it)
provider/API/model version used for evaluation
catalog rule revision or canonical configuration digest
normalized decision and safe reason code
```

raw assessment, input/output text, credentials, provider error 원문은 현재 보안 경계에 따라 일반 DB·로그나
public response에 저장하지 않는다.

## MVP와 미래 변경의 구분

| 시점 | 결정 |
| --- | --- |
| 지금 | Bedrock `EvaluatorReference`의 identifier + numbered version 고정과 inline profile snapshot을 유지한다. API/DB/도메인 변경 없음. |
| 두 번째 provider 구현을 승인할 때 | provider가 immutable revision을 제공하지 않으면 activation 당시의 canonical configuration digest와 catalog rule revision을 저장하는 persistence 계약을 먼저 결정한다. provider code, endpoint/resource scope, API/model version도 재현성 영향에 따라 명시한다. |
| provider 선택 또는 ensemble이 필요할 때 | 사용자-facing profile과 운영 catalog의 ownership, 활성화/rollback, 비교 가능성, 결과 provenance를 Decision Issue와 APPROVED 계약으로 확정한 뒤 구현한다. |

## 후속 이슈 제안

**[Decision] 두 번째 Evaluator provider의 catalog identity와 재현성 계약**을 생성한다. 후보 provider 하나를
명시하고 다음을 승인 대상으로 둔다: 지원 capability·source, version 또는 digest strategy, 운영 catalog의
activation/rollback, result provenance 보존 범위, 그리고 기존 `ALLOW`/`BLOCK`로 정규화할 수 없는 결과의
오류 처리. 이 Decision 승인 전에는 일반 provider hierarchy, profile CRUD, ensemble 및 API/DB 확장을 구현하지 않는다.
