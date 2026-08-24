# GuardBench 구현 문서 지도

> Status: APPROVED
> Owner: KOSA AWS 3팀
> Last reviewed: 2026-08-24
> Canonical source: GitHub
> Origin: [Notion Dashboard](https://app.notion.com/p/3c0eeed6b62d80fbb64eec69796cc56d)
> AI assistance: 이 문서의 초안은 LLM의 도움으로 작성되었으며 사람의 검토가 필요합니다.

이 문서는 구현 계약의 진입점이다. 문서의 `Status`가 개별 판단의 효력을 결정한다.

## 상태 정의

- `DRAFT`: 검토 중인 초안. 구현을 확정하는 근거로 단독 사용하지 않는다.
- `APPROVED`: 팀이 승인한 구현 계약.
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
| 결정 | [ADR 0002: PostgreSQL 영속성 계약과 물리 ERD](decisions/0002-postgresql-persistence-contract.md) | DRAFT | Backend | 2026-08-24 | 없음 |
| 결정 | [ADR 0003: 실행·평가 결과 Aggregate와 write-side Port 경계](decisions/0003-result-aggregate-and-write-port-boundaries.md) | DRAFT | Backend | 2026-08-24 | 없음 |
| AI 개발 | [작업 워크플로](ai-development/workflow.md) | APPROVED | Team | 2026-08-24 | 현재 대화에서 승격 |

## 개발 컨벤션

| 문서 | 상태 | Notion 원본 |
| --- | --- | --- |
| [커밋](conventions/commits.md) | APPROVED | [커밋 컨벤션](https://app.notion.com/p/3c0eeed6b62d813382bdd56fd910c66e) |
| [코드 스타일](conventions/code-style.md) | APPROVED | [코드 컨벤션](https://app.notion.com/p/3c0eeed6b62d816a8028cc3261c3edf3) |
| [DTO 네이밍](conventions/dto-naming.md) | APPROVED | [DTO 네이밍](https://app.notion.com/p/3c0eeed6b62d8153b1f4fdffc39e328e) |
| [패키지 구조](conventions/package-structure.md) | APPROVED | [패키지 네이밍](https://app.notion.com/p/3c0eeed6b62d81d59ec3cb3beb995c68) |
| [API 공통 응답](conventions/api-response.md) | APPROVED | [API 공통 응답 DTO](https://app.notion.com/p/3c1eeed6b62d81e7abe2eea3d730c611) |
| [애플리케이션 오류](conventions/application-errors.md) | APPROVED | [애플리케이션 오류 코드](https://app.notion.com/p/3c1eeed6b62d81d3a7c9f014bb788aa8) |

## 충돌 처리

현재 Issue의 승인된 요구사항, APPROVED GitHub 계약, 테스트/공개 코드 계약, DRAFT GitHub 문서, Notion 순으로 판단한다. 충돌은 숨기지 말고 Issue 또는 PR에 기록한다.
