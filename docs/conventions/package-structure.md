# 패키지 구조와 네이밍

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-25
> Canonical source: GitHub
> Origin: [Notion 패키지 네이밍](https://app.notion.com/p/3c0eeed6b62d81d59ec3cb3beb995c68)

최상위는 기술 계층이 아니라 도메인으로 나누는 package-by-domain을 사용한다.

```text
<base-package>/
├─ testdefinition/
├─ testrun/
├─ evaluation/
├─ target/
└─ common/
```

각 도메인 내부는 필요할 때 `domain`, `application`, `presentation`, `infrastructure`로 나눈다.

```text
testrun/
├─ domain/
├─ application/port/out/
├─ presentation/controller/
├─ presentation/dto/
└─ infrastructure/
   ├─ persistence/
   └─ integration/testdefinition/
```

- Repository 계약 예: `testrun/domain/repository/TestRunRepository.java`
- 기술 구현 예: `testrun/infrastructure/persistence/TestRunRepositoryAdapter.java`
- API DTO 예: `testrun/presentation/dto/TestRunCreateReq.java`
- AWS Adapter 예: `evaluator/infrastructure/bedrock/BedrockGuardrailEvaluatorAdapter.java`
- Context 연동 Port 예: `testrun/application/port/out/LoadTestCaseSnapshotSourcesPort.java`
- Context 연동 Adapter 예: `testrun/infrastructure/integration/testdefinition/TestDefinitionSnapshotSourceAdapter.java`

패키지는 소문자이며 `_`, `-`를 사용하지 않는다. `common`에는 실제 횡단 관심사만 둔다. `common/util`, `common/helper`, `common/domain`, `common/service` 같은 잡동사니 패키지는 만들지 않는다.

전역 `controller/service/repository/entity/dto` 구조는 사용하지 않는다. Domain에 JPA·Spring·AWS·HTTP 타입을 노출하지 않는다.

다른 Context의 Domain Java 타입을 직접 import하지 않는다. 소비 Context가 `application/port` 아래에 필요한 Port와 값 계약을 정의하고 `infrastructure/integration` Adapter가 공급 Context의 Application API 또는 외부 프로토콜을 호출해 명시적으로 mapping한다. 상세 경계와 예외는 [ADR 0006](../decisions/0006-independent-domain-contract-boundaries.md)을 따른다.
