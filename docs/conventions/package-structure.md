# 패키지 구조와 네이밍

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-23
> Canonical source: GitHub
> Origin: [Notion 패키지 네이밍](https://app.notion.com/p/3c0eeed6b62d81d59ec3cb3beb995c68)

최상위는 기술 계층이 아니라 도메인으로 나누는 package-by-domain을 사용한다.

```text
<base-package>/
├─ testdefinition/
├─ testrun/
├─ evaluation/
├─ guardrail/
└─ common/
```

각 도메인 내부는 필요할 때 `domain`, `application`, `presentation`, `infrastructure`로 나눈다.

```text
testrun/
├─ domain/
├─ application/
├─ presentation/controller/
├─ presentation/dto/
└─ infrastructure/persistence/
```

- Repository 계약 예: `testrun/domain/repository/TestRunRepository.java`
- 기술 구현 예: `testrun/infrastructure/persistence/TestRunRepositoryAdapter.java`
- API DTO 예: `testrun/presentation/dto/TestRunCreateReq.java`
- AWS Adapter 예: `guardrail/infrastructure/bedrock/BedrockGuardrailAdapter.java`

패키지는 소문자이며 `_`, `-`를 사용하지 않는다. `common`에는 실제 횡단 관심사만 둔다. `common/util`, `common/helper`, `common/domain`, `common/service` 같은 잡동사니 패키지는 만들지 않는다.

전역 `controller/service/repository/entity/dto` 구조는 사용하지 않는다. Domain에 JPA·Spring·AWS·HTTP 타입을 노출하지 않는다.
