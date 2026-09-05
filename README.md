# GuardBench Backend

AI Application의 자연어 응답을 Response Behavior Classifier로 판정하고, 기대 동작 위반과 저장된 실행 결과의 Regression을 구분하는 AI Application Test Platform입니다. Classifier는 SageMaker Runtime endpoint에서 실행됩니다.

## MVP 범위

- TestSuite와 TestCase로 정책 테스트 자산 관리
- TestRun 시점의 TestCaseSnapshot 고정
- 하나의 TestRun에서 하나의 `HTTP_ENDPOINT` AI Application Target 실행
- 고정된 Response Behavior Classifier로 Application 응답의 행동을 분류
- Application 자연어 응답을 Evaluator가 `ALLOW | BLOCK`으로 판정
- ExpectedResult와 EvaluationResult의 Assertion
- 현재 TestRun의 Assertion 기반 Quality Gate
- 완료된 두 TestRun의 저장 결과 기반 Regression

## 구현 상태

목표 계약은 [Response Behavior Classifier Adapter](docs/integrations/sagemaker-classifier-adapter.md)와 [OpenAPI](docs/api/openapi.yaml)다. 현재 실행은 OpenAI-compatible `HTTP_ENDPOINT` Application Target의 응답과 원래 prompt를 SageMaker Runtime classifier에 전달하고, `COMPLY | REFUSE`를 `ALLOW | BLOCK`으로 정규화한다. Worker는 classifier 실패를 임의 action으로 대체하지 않으며, Quality Gate는 현재 Run의 평가 가능한 Assertion 통과율과 전체 실행 성공률을 집계한다.

## 로컬 개발

JDK 21이 필요합니다. Gradle은 별도로 설치하지 않고 저장소의 Gradle Wrapper를 사용합니다.

로컬 PostgreSQL 준비:

```bash
cp .env.example .env
```

`./gradlew bootRun` 실행 시 Spring Boot Docker Compose 지원이 `compose.yaml`의 PostgreSQL을 시작하고, Flyway가 승인된 스키마를 적용합니다. `.env`는 로컬 자격 증명 파일이므로 커밋하지 않습니다.

`testFast`는 외부 컨테이너 없이 단위·컨트롤러·계약 테스트만 실행합니다. `integrationTest`는
PostgreSQL, SQS, E2E 통합 테스트를 실행하므로 Docker daemon이 필요합니다. 기존 `test`와
`check`는 전체 테스트 suite를 실행하는 호환 경로로 유지합니다.

애플리케이션 실행:

```bash
./gradlew bootRun
```

테스트:

```bash
./gradlew testFast
./gradlew integrationTest
./gradlew clean test
```

`testFast`와 `integrationTest`를 함께 실행할 때는 다음처럼 사용할 수 있습니다.

```bash
./gradlew testFast integrationTest
```

PR CI에서는 두 테스트 task와 `bootJar`를 독립 job으로 실행하고 `verify` aggregate check에서
세 결과를 모두 요구합니다. Gradle dependency cache, task별 실행 시간과 integration job의
Testcontainers 이미지 준비 시간을 GitHub Actions Summary에 기록합니다.

실행 가능한 JAR 빌드:

```bash
./gradlew bootJar
```

## 기술 방향

- Java, Spring Boot, Gradle
- PostgreSQL, Amazon SQS, Amazon SageMaker Runtime
- Docker, GitHub Actions, Amazon CloudWatch

## 문서

- [문서 지도](docs/README.md)
- [MVP 범위](docs/product/mvp-scope.md)
- [핵심 도메인 모델](docs/domain/core-model.md)
- [API 계약](docs/api/README.md)
- [AI 개발 워크플로](docs/ai-development/workflow.md)
- [Codex 운영 규칙](AGENTS.md)

GitHub의 구현 계약이 최종 기준입니다. Notion은 회의, 아이디어, 참고자료와 논의 과정을 보관합니다.
