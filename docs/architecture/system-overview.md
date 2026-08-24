# 시스템 아키텍처 개요

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-23
> Canonical source: GitHub
> Origin: [Notion 도메인 모델 정의](https://app.notion.com/p/3c0eeed6b62d81b48c03ed6034440936)
> AI assistance: 이 문서의 초안은 LLM의 도움으로 작성되었으며 사람의 검토가 필요합니다.

GuardBench MVP는 Java·Spring Boot 단일 백엔드다. Core 정책 판정과 구체 기술 Adapter를 분리한다.

```text
HTTP/SQS Adapter → Application Use Case → Domain/Core
                                      ↑
DB Adapter · Bedrock Adapter · Result Normalizer
```

- Domain은 Spring MVC, JPA, AWS SDK, HTTP DTO에 의존하지 않는다.
- Controller는 Application Service만 호출한다.
- Repository 계약은 Domain에, 구현은 Infrastructure에 둔다.
- AWS 원본 응답은 Adapter/Normalizer가 `ActualResult`로 변환한다.
- TestSuite, TestCase, ExpectedResult, Assertion, Change, Quality Gate의 의미는 Guardrail SDK와 독립적이다.
- 미래 확장을 이유로 범용 provider 계층이나 불필요한 엔티티를 MVP에 선제 도입하지 않는다.
