# GuardBench Backend

AI Application의 자연어 응답을 Evaluator로 판정하고, 기대 동작 위반과 저장된 실행 결과의 Regression을 구분하는 AI Application Test Platform입니다. AWS Bedrock Guardrail은 첫 번째 Guardrail Evaluator 구현입니다.

## MVP 범위

- TestSuite와 TestCase로 정책 테스트 자산 관리
- TestRun 시점의 TestCaseSnapshot 고정
- 하나의 TestRun에서 하나의 `HTTP_ENDPOINT` AI Application Target 실행
- inline Evaluation Profile을 실제 Evaluator 설정으로 서버가 해석
- Application 자연어 응답을 Evaluator가 `ALLOW | BLOCK`으로 판정
- ExpectedResult와 EvaluationResult의 Assertion
- 현재 TestRun의 Assertion 기반 Quality Gate
- 완료된 두 TestRun의 저장 결과 기반 Regression

## 구현 상태

목표 계약은 [ADR 0011](docs/decisions/0011-ai-application-target-and-guardrail-evaluator.md)과 [OpenAPI](docs/api/openapi.yaml)다. 현재 코드는 OpenAI-compatible `HTTP_ENDPOINT` Application Target만 접수하고, inline Evaluation Profile을 운영자 catalog로 해석해 immutable `EvaluatorReference`를 고정하며, Bedrock Guardrail을 Evaluator Adapter로 구현했다. Worker는 아직 Evaluator를 호출하지 않지만, Quality Gate는 현재 Run의 평가 가능한 Assertion 통과율과 전체 실행 성공률을 집계한다. #117의 Worker orchestration과 #119의 Regression이 남아 있다.

## 로컬 개발

JDK 21이 필요합니다. Gradle은 별도로 설치하지 않고 저장소의 Gradle Wrapper를 사용합니다.

로컬 PostgreSQL 준비:

```bash
cp .env.example .env
```

`./gradlew bootRun` 실행 시 Spring Boot Docker Compose 지원이 `compose.yaml`의 PostgreSQL을 시작하고, Flyway가 승인된 스키마를 적용합니다. `.env`는 로컬 자격 증명 파일이므로 커밋하지 않습니다.

통합 테스트는 로컬 Compose DB를 사용하지 않고 Testcontainers PostgreSQL을 별도로 시작합니다. 테스트 실행 전 Docker daemon이 실행 중이어야 합니다.

애플리케이션 실행:

```bash
./gradlew bootRun
```

테스트:

```bash
./gradlew clean test
```

실행 가능한 JAR 빌드:

```bash
./gradlew bootJar
```

## 기술 방향

- Java, Spring Boot, Gradle
- PostgreSQL, Amazon SQS, Amazon Bedrock Guardrails
- Docker, GitHub Actions, Amazon CloudWatch

## 문서

- [문서 지도](docs/README.md)
- [MVP 범위](docs/product/mvp-scope.md)
- [핵심 도메인 모델](docs/domain/core-model.md)
- [API 계약](docs/api/README.md)
- [AI 개발 워크플로](docs/ai-development/workflow.md)
- [Codex 운영 규칙](AGENTS.md)

GitHub의 구현 계약이 최종 기준입니다. Notion은 회의, 아이디어, 참고자료와 논의 과정을 보관합니다.
