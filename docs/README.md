# GuardBench 구현 문서 지도

> Status: APPROVED
> Owner: KOSA AWS 3팀
> Last reviewed: 2026-08-24
> Canonical source: GitHub
> Origin: [Notion Dashboard](https://app.notion.com/p/3c0eeed6b62d80fbb64eec69796cc56d)

이 문서는 구현 계약의 진입점이다. 필요한 문서만 아래 순서로 읽는다. 문서의 `Status`가 판단 효력을 결정한다.

## 작업별 읽기 순서

| 작업 | 먼저 읽을 문서 | 필요할 때 추가로 읽을 문서 |
| --- | --- | --- |
| 모든 작업 | `AGENTS.md`, 현재 Issue | [작업 워크플로](ai-development/workflow.md) |
| 코드 리뷰 | 현재 Issue, PR diff, 관련 APPROVED 계약 | [에이전트 코드 리뷰](ai-development/review.md) |
| API 구현 | [API 안내](api/README.md), [OpenAPI](api/openapi.yaml) | 오류·응답·DTO 컨벤션 |
| 도메인 구현 | [핵심 모델](domain/core-model.md), [평가 계약](domain/evaluation-contract.md) | ADR 0001, 패키지 구조 |
| 영속성·인프라 | 관련 승인 Issue와 ADR | 시스템 개요, DRAFT 인프라는 참고만 사용 |
| 문서·결정 | 관련 계약과 [ADR 안내](decisions/README.md) | [ADR 템플릿](decisions/template.md) |

## 상태 정의

- `DRAFT`: 검토 중인 초안. 구현을 확정하는 근거로 단독 사용하지 않는다.
- `APPROVED`: 팀이 승인한 구현 계약. 승격할 때 초안 검토 상태를 나타내는 `AI assistance` 메타데이터를 제거한다.
- `DEPRECATED`: 더 이상 사용하지 않는 계약. 대체 문서를 확인한다.

## 문서 지도

| 영역 | 문서 | 상태 | 담당 | 최종 검토일 | Notion 원본 |
| --- | --- | --- | --- | --- | --- |
| 제품 | [MVP 범위](product/mvp-scope.md) | APPROVED | KOSA AWS 3팀 | 2026-08-23 | [최신 PRD](https://app.notion.com/p/3c0eeed6b62d80759d77f0ab0d5bcbd3) |
| 도메인 | [핵심 모델](domain/core-model.md) | APPROVED | Backend | 2026-08-24 | [도메인 모델 정의](https://app.notion.com/p/3c0eeed6b62d81b48c03ed6034440936) |
| 도메인 | [평가 계약](domain/evaluation-contract.md) | APPROVED | Backend | 2026-08-24 | [MVP 평가 계약](https://app.notion.com/p/3c3eeed6b62d8120a57eebaa13b6ed27) |
| API | [API 안내](api/README.md) · [OpenAPI](api/openapi.yaml) | APPROVED | Backend | 2026-08-24 | [API 명세서](https://app.notion.com/p/3c0eeed6b62d805dac0be8db487b1359) |
| 아키텍처 | [시스템 개요](architecture/system-overview.md) | APPROVED | Backend | 2026-08-23 | [도메인 모델 정의](https://app.notion.com/p/3c0eeed6b62d81b48c03ed6034440936) |
| 아키텍처 | [인프라](architecture/infrastructure.md) | DRAFT | Infra | 2026-08-24 | [인프라 구성 설계](https://app.notion.com/p/3c0eeed6b62d81269f60e1c69fbf9fcc) |
| 결정 | [ADR 안내](decisions/README.md) | APPROVED | Team | 2026-08-23 | 없음 |
| 결정 | [ADR 0001: 도메인 타입 소유권과 Aggregate 경계](decisions/0001-domain-type-ownership-and-aggregate-boundaries.md) | APPROVED | Backend | 2026-08-24 | 없음 |
| 결정 | [ADR 0002: PostgreSQL 영속성 계약과 물리 ERD](decisions/0002-postgresql-persistence-contract.md) | APPROVED | Backend | 2026-08-24 | 없음 |
| 결정 | [ADR 0003: 실행·평가 결과 Aggregate와 write-side Port 경계](decisions/0003-result-aggregate-and-write-port-boundaries.md) | APPROVED | Backend | 2026-08-24 | 없음 |
| 결정 | [ADR 0004: TestRun 최종 평가와 종료의 원자성](decisions/0004-testrun-finalization-atomicity.md) | DRAFT | Backend | 2026-08-24 | 없음 |
| AI 개발 | [작업 워크플로](ai-development/workflow.md) | APPROVED | Team | 2026-08-24 | 현재 대화에서 승격 |
| AI 개발 | [코드 리뷰](ai-development/review.md) | APPROVED | Team | 2026-08-24 | PR #20·#23 사례와 Issue #33 |

## 개발 컨벤션

| 문서 | 상태 | Notion 원본 |
| --- | --- | --- |
| [커밋](conventions/commits.md) | APPROVED | [커밋 컨벤션](https://app.notion.com/p/3c0eeed6b62d813382bdd56fd910c66e) |
| [코드 스타일](conventions/code-style.md) | APPROVED | [코드 컨벤션](https://app.notion.com/p/3c0eeed6b62d816a8028cc3261c3edf3) |
| [DTO 네이밍](conventions/dto-naming.md) | APPROVED | [DTO 네이밍](https://app.notion.com/p/3c0eeed6b62d8153b1f4fdffc39e328e) |
| [패키지 구조](conventions/package-structure.md) | APPROVED | [패키지 네이밍](https://app.notion.com/p/3c0eeed6b62d81d59ec3cb3beb995c68) |
| [API 공통 응답](conventions/api-response.md) | APPROVED | [API 공통 응답 DTO](https://app.notion.com/p/3c1eeed6b62d81e7abe2eea3d730c611) |
| [애플리케이션 오류](conventions/application-errors.md) | APPROVED | [애플리케이션 오류 코드](https://app.notion.com/p/3c1eeed6b62d81d3a7c9f014bb788aa8) |

## 판단 우선순위

현재 Issue와 사용자 지시 → APPROVED GitHub 계약 → 테스트와 공개 코드 → DRAFT GitHub 문서 → Notion 순이다. DRAFT에 따라 공개 동작이 달라지면 구현하지 않는다. 충돌과 미결정은 Issue 또는 PR에 기록한다.
