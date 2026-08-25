# 0007. TestRun Candidate 입력은 DRAFT만 허용

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-25
> Canonical source: GitHub
> Origin: [GitHub Issue #32](https://github.com/GuardBench/guardbench-backend/issues/32)

- ADR Status: ACCEPTED
- Decision date: 2026-08-25
- Related Issue: #32

## Context

`POST /api/v1/test-runs`의 Candidate 입력에 대해 OpenAPI는 `source: DRAFT`만 허용하지만 API README에는 numbered version도 허용한다고 적혀 있었다. Candidate materialization은 비동기 Worker의 `PREPARING` 단계에서 실행하도록 이미 승인됐으므로, numbered version을 HTTP 입력으로 허용하면 입력·materialization·Idempotency fingerprint 의미가 이중화된다.

## Decision

- `CandidateTargetReq.source`는 `DRAFT`만 허용한다.
- numbered Candidate version은 HTTP 요청으로 받지 않는다.
- Worker는 `PREPARING`에서 DRAFT를 numbered version으로 materialize하고, 성공한 version을 `candidateResolvedVersion`에 고정한다.
- TestRun create Idempotency fingerprint는 `testSuiteId`, Baseline `guardrailId`·`version`, Candidate `guardrailId`·`source`의 정규화된 생성 의도만 포함한다.
- 이미 생성된 TestRun의 재요청은 현재 TestCase나 materialized version을 다시 읽지 않고 기존 Run을 반환한다.

## Alternatives

### numbered version도 Candidate 입력으로 허용

재현 실행에는 편리하지만 DRAFT materialization 경로와 별도 입력 경로를 만들고, API 입력과 Idempotency fingerprint·lifecycle 의미를 복잡하게 만든다. MVP에서는 선택하지 않는다.

## Consequences

OpenAPI의 `CandidateTargetReq`는 유지되고 API README의 numbered version 설명은 제거한다. Candidate materialization 실패와 retry는 HTTP 접수가 아니라 비동기 resolution 계약의 책임이다.

## Validation

1. Candidate source가 `DRAFT`가 아니면 API validation이 거부한다.
2. 동일 Idempotency-Key와 동일 DRAFT 생성 의도는 기존 TestRun을 반환한다.
3. Candidate materialization은 `PREPARING` 이후에만 발생하고 resolved version은 HTTP 입력에서 받지 않는다.
