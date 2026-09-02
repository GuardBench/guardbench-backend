# Evaluation Profile Catalog 계약

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-31
> Canonical source: GitHub
> Origin: GitHub Issue #114

이 문서는 MVP의 inline `EvaluationProfile`을 실제 AWS Bedrock Guardrail Evaluator 설정으로 해석하는 정규화와 catalog 계약을 정의한다.

공개 API의 필드·Enum·Validation shape은 [OpenAPI](../api/openapi.yaml)를 따른다. 이 문서는 API shape을 바꾸지 않고 `checks + strictness`를 provider-specific immutable EvaluatorReference로 해석하는 규칙을 정의한다.

## 목적

GuardBench는 TestSuite 내용을 분석하여 Guardrail을 동적으로 생성하지 않는다. MVP에서는 사용자가 평가 의도를 `EvaluationProfile`로 직접 선언하고, Backend가 제한된 운영 catalog에서 이미 provisioning된 immutable Evaluator 설정을 선택한다.

```text
TestSuite                    // 무엇을 시험할 것인가
EvaluationProfile            // 어떤 보안 관점과 민감도로 평가할 것인가
        ↓ normalization
Evaluator Catalog
        ↓ lookup
EvaluatorReference           // 실제 사용할 immutable provider configuration
```

TestRun 생성 중에는 Guardrail DRAFT를 수정하거나 numbered version을 생성하지 않는다.

## 공개 Evaluation Profile

MVP Evaluation Check는 두 종류다.

```text
PII_LEAKAGE
HARMFUL_CONTENT
```

`strictness`는 다음 세 값을 사용한다.

```text
RELAXED
STANDARD
STRICT
```

공개 API에서는 MVP 일정과 기존 frontend 계약을 유지하기 위해 `strictness`를 계속 필수로 받는다.

## strictness 의미

`strictness`는 GuardBench가 정의한 추상적인 평가 민감도다. AWS Bedrock의 특정 필드명을 그대로 공개하는 값이 아니다.

### HARMFUL_CONTENT

`strictness`를 harmful content filtering strength로 정규화한다.

```text
RELAXED  → LOW
STANDARD → MEDIUM
STRICT   → HIGH
```

MVP의 `HARMFUL_CONTENT`는 provider의 harmful content 범주를 하나의 GuardBench check로 묶어 취급한다. 세부 provider 범주를 public EvaluationProfile로 노출하지 않는다.

### PII_LEAKAGE

`PII_LEAKAGE`에는 `strictness`를 적용하지 않는다. MVP에서는 GuardBench가 정의한 하나의 고정 PII policy를 사용한다.

고정 PII policy의 구체적인 entity 목록과 provider 설정은 provisioning 설정에서 관리하며 public TestRun request에는 노출하지 않는다.

```text
PII_LEAKAGE + RELAXED  ─┐
PII_LEAKAGE + STANDARD ─┼→ 동일한 고정 PII Evaluator 설정
PII_LEAKAGE + STRICT   ─┘
```

PII-only 요청에서 전달된 `strictness`는 API 호환성을 위해 수용하고 TestRun의 요청 snapshot에는 보존할 수 있지만 실제 PII Evaluator 선택에는 영향을 주지 않는다.

PII-only에서 의미 없는 `strictness`를 API와 UI에서 조건부 제거하는 개선은 MVP 이후 별도 작업으로 다룬다.

## 지원 Profile과 canonical catalog entry

두 check의 non-empty 조합은 3개이고 API가 표현할 수 있는 `(checks, strictness)` 입력은 9개다.

그러나 PII-only의 세 strictness 입력이 동일한 Evaluator 설정으로 정규화되므로 실질 catalog entry는 7개다.

| checks | canonical entry 수 | strictness 적용 |
| --- | ---: | --- |
| `PII_LEAKAGE` | 1 | 미적용 |
| `HARMFUL_CONTENT` | 3 | 적용 |
| `HARMFUL_CONTENT + PII_LEAKAGE` | 3 | HC에 적용 |
| **합계** | **7** | |

`checks`의 요청 배열 순서는 Profile identity에 영향을 주지 않는다. catalog lookup 전 canonical order로 정렬하여 동일한 check set은 하나의 key로 취급한다.

## Bedrock Guardrail 물리 배치

하나의 Bedrock Guardrail에 Profile을 numbered version으로 몰아넣지 않는다. MVP에서는 다음 3개의 non-empty check 조합을 Guardrail identity로 분리한다.

```text
G1: PII_LEAKAGE
G2: HARMFUL_CONTENT
G3: PII_LEAKAGE + HARMFUL_CONTENT
```

각 Guardrail에는 해당 check set에서 필요한 profile 설정을 numbered version으로 미리 provisioning한다.

- PII-only Guardrail은 strictness와 무관한 하나의 canonical policy version만 필요하다.
- HARMFUL_CONTENT를 포함한 Guardrail은 `RELAXED`, `STANDARD`, `STRICT`에 대응하는 세 canonical configuration을 가진다.
- version 번호 자체에는 strictness 의미를 부여하지 않는다.
- `RELAXED → version 1` 같은 규칙을 도메인 계약으로 사용하지 않는다.
- 실제 `strictness → numberedVersion` 연결은 운영 catalog가 소유한다.

## Catalog key와 value

개념적인 lookup 계약은 다음과 같다.

```text
EvaluationProfile
    checks
    strictness
        ↓ canonicalize
CatalogKey
        ↓ exact lookup
CatalogEntry
    guardrailIdentifier
    numberedVersion
        ↓ snapshot
EvaluatorReference
```

PII-only는 key 생성 시 strictness 차이를 collapse한다.

```text
canonicalize([PII_LEAKAGE], RELAXED)
canonicalize([PII_LEAKAGE], STANDARD)
canonicalize([PII_LEAKAGE], STRICT)

→ 같은 catalog key
```

그 외 Profile은 canonicalized check set과 strictness가 함께 key를 구성한다.

## Runtime 계약

TestRun 접수 시 Backend는 다음 순서로 동작한다.

1. 공개 `EvaluationProfile`을 검증한다.
2. `checks`를 canonical order로 정규화한다.
3. PII-only이면 strictness 차이를 collapse한다.
4. canonical key로 운영 catalog를 exact lookup한다.
5. 이미 provisioning된 immutable Bedrock Guardrail identifier와 numbered version을 얻는다.
6. 사용자의 원래 EvaluationProfile snapshot과 실제 `EvaluatorReference`를 TestRun 실행 조건으로 각각 고정한다.

catalog에 canonical key가 없으면 계약된 `409 EVALUATION_PROFILE_NOT_SUPPORTED`로 실패한다. MVP 운영 catalog는 위 7개 canonical entry 전체를 provisioning한다.

운영 catalog의 현재 Bedrock 연결은 다음과 같다. version 번호 자체에는 strictness 의미를 부여하지 않지만, 현재 provisioning 결과에서는 각 Guardrail의 version 1/2/3이 각각 RELAXED/STANDARD/STRICT에 대응한다.

| Guardrail | Identifier | Version mapping |
| --- | --- | --- |
| G1 PII_LEAKAGE | `ddsaa7puiszg` | PII-only fixed policy: `1` |
| G2 HARMFUL_CONTENT | `0vkh7a4wkphg` | RELAXED `1`, STANDARD `2`, STRICT `3` |
| G3 PII_LEAKAGE + HARMFUL_CONTENT | `571rwjlw2ozk` | RELAXED `1`, STANDARD `2`, STRICT `3` |

## 책임 경계

### EvaluationProfile

사용자가 요청한 평가 의도를 보존한다.

### Catalog normalization

사용자-facing 추상 표현을 provider-specific configuration identity로 변환한다.

### EvaluatorReference

실제로 실행에 사용한 immutable provider 설정을 사후 재식별한다.

이 세 책임을 같은 객체로 합치지 않는다.

## Non-Goals

- TestSuite 내용을 분석해 checks 또는 strictness를 자동 추론
- TestRun 생성 요청 중 Guardrail 생성, DRAFT 수정 또는 numbered version 생성
- 사용자에게 Guardrail identifier/version 또는 AWS provider 설정을 직접 입력받기
- Evaluation Profile 저장/재사용 CRUD
- PII entity set을 사용자-facing 세부 옵션으로 노출
- PII-only strictness를 API/UI에서 조건부 제거하는 개선
- multi-provider/ensemble 일반화
- Prompt Injection 평가는 MVP 범위에 포함하지 않는다. Bedrock `PROMPT_ATTACK`을 output 평가에 매핑하지 않으며, 별도 evaluator 후보로 남긴다.

## 관련 계약

- [ADR 0011 — AI Application Target과 Guardrail Evaluator 역할 분리](../decisions/0011-ai-application-target-and-guardrail-evaluator.md)
- [API 안내](../api/README.md)
- [OpenAPI](../api/openapi.yaml)
- [Issue #114](https://github.com/GuardBench/guardbench-backend/issues/114)
