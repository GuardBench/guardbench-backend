# 시스템 아키텍처 개요

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-30
> Canonical source: GitHub
> Origin: [Notion 도메인 모델 정의](https://app.notion.com/p/3c0eeed6b62d81b48c03ed6034440936)

GuardBench MVP는 Java·Spring Boot 단일 백엔드다. Core 정책 판정과 구체 기술 Adapter를 분리한다.

```text
HTTP/SQS Adapter → Application Use Case → Domain/Core
                                      ↑
DB Adapter · Bedrock Adapter · Result Normalizer
```

- Domain은 Spring MVC, JPA, AWS SDK, HTTP DTO에 의존하지 않는다.
- `testdefinition`, `testrun`, `evaluation`, `target`은 같은 프로세스에 배포하더라도 독립 경계로 개발한다.
- 다른 Context의 Domain Java 타입을 직접 사용하지 않고 소비자가 소유한 Port와 값 계약을 Integration Adapter가 연결한다.
- Controller는 Application Service만 호출한다.
- Repository 계약은 Domain에, 구현은 Infrastructure에 둔다.
- AWS 원본 응답은 Adapter/Normalizer가 소비자 소유 값 계약으로 변환하고 TestRun Application이 `ActualResult`를 생성한다.
- TestRun은 단일 TargetReference만 소유하고 provider type·identifier·revision은 Target 경계가 소유한다.
- TestSuite, TestCase, ExpectedResult, Assertion, Quality Gate의 의미는 Guardrail SDK와 독립적이다.
- 미래 확장을 이유로 범용 provider 계층이나 불필요한 엔티티를 MVP에 선제 도입하지 않는다.

```text
Consumer Application/Domain
        -> consumer-owned Port
        <- Integration Adapter
        -> Provider Application API 또는 외부 프로토콜
```

이 구조는 물리적 MSA 전환을 요구하지 않는다. 목적은 각 Context가 상대 구현 없이 Core를 병렬 구현·테스트하고, 실제 통합 결합을 Adapter에 제한하는 것이다. 세부 규칙은 [ADR 0006](../decisions/0006-independent-domain-contract-boundaries.md)을 따른다.
