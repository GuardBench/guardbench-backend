# GuardBench MVP 범위

> Status: APPROVED
> Owner: KOSA AWS 3팀
> Last reviewed: 2026-08-30
> Canonical source: GitHub
> Origin: [Notion 최신 PRD](https://app.notion.com/p/3c0eeed6b62d80759d77f0ab0d5bcbd3)

## 제품 정의

GuardBench는 사람이 정의한 기대 동작과 실제 Target 실행 결과를 비교하는 정책 테스트 플랫폼이다. MVP의 System Under Test는 Amazon Bedrock Guardrails다.

## 목표

- TestSuite와 TestCase를 재사용 가능한 정책 테스트 자산으로 축적한다.
- TestRun 시점의 TestCase를 Snapshot으로 고정해 재현성을 확보한다.
- Snapshot마다 하나의 Target을 실행한다.
- ActualResult가 ExpectedResult를 만족하는지 assertion한다.
- 실행 오류와 정책 판정을 분리하고 Quality Gate의 신뢰도를 보장한다.
- AWS SDK 타입을 Core 판정 계약에서 분리한다.

## 핵심 사용자 흐름

1. 사용자가 TestSuite와 현재 TestCase 정의를 관리한다.
2. Bedrock Guardrails Target과 `DRAFT` 또는 numbered revision을 지정해 TestRun을 요청한다.
3. 서버는 DRAFT를 numbered revision으로 materialize하고 실행 대상을 고정한다.
4. TestCaseSnapshot을 생성하고 각 Snapshot을 하나의 Target에 실행한다.
5. 결과를 Core의 ActualResult로 정규화한다.
6. Expected/Actual assertion을 수행한다.
7. 실행 결과를 제공한다. 단일 Target 실행에는 비교 결과가 없으므로 Quality Gate는 `NOT_EVALUATED`다.

## Non-Goals

- 정책 또는 Guardrail 설정 자동 생성
- Bedrock Guardrails 이외 SUT의 MVP 지원
- 일반적인 LLM 답변 품질 평가
- 고객 애플리케이션 CI/CD 또는 PR Gate 제품 통합
- 서로 다른 TestRun 사이의 이력 비교
- `TestCaseRevision` 또는 `TestSuiteRevision` 도입
- 미래 확장을 위한 범용 multi-provider 추상화 선제 구현
