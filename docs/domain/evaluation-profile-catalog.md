# Evaluation Profile Catalog 계약

> Status: DEPRECATED
> Owner: Backend
> Last reviewed: 2026-09-04
> Canonical source: GitHub
> Origin: GitHub Issue #114
> Superseded by: [ADR 0013](../decisions/0013-response-behavior-classifier.md) — inline Evaluation Profile과 Evaluator catalog 개념 전체가 제거되었다.

이 문서가 정의하던 inline `EvaluationProfile(checks + strictness)` → Bedrock Guardrail catalog 해석 계약은 [Issue #173](https://github.com/GuardBench/guardbench-backend/issues/173)에서 완전히 제거되었다.

## 현재 계약

- TestRun 생성 요청에는 `evaluationProfile`이 없다. 사용자는 evaluator/classifier 설정을 제출하지 않는다.
- Evaluator는 Guardrail이 아니라 Response Behavior Classifier다. `(prompt, actualResponse) -> COMPLY | REFUSE`만 판단하며 `PII_LEAKAGE`/`HARMFUL_CONTENT` 같은 checks나 strictness 개념이 없다.
- classifier 설정(provider, model)은 catalog lookup이 아니라 서비스 전역 고정 설정이다.
- `EVALUATION_PROFILE_NOT_SUPPORTED` 오류 코드는 제거되었다.

현재 계약은 [평가 계약](evaluation-contract.md), [핵심 도메인 모델](core-model.md)과 [ADR 0013](../decisions/0013-response-behavior-classifier.md)을 따른다. 이 문서는 과거 결정의 history 보존을 위해서만 남긴다.
