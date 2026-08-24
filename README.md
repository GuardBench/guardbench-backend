# GuardBench Backend

Amazon Bedrock Guardrails의 Baseline과 Candidate를 같은 테스트 자산으로 검증하고, 기대 동작 위반과 정책 회귀를 구분하는 AI Security Regression Test Platform입니다.

## MVP 범위

- TestSuite와 TestCase로 정책 테스트 자산 관리
- 동일 Snapshot을 이용한 Baseline/Candidate 실행
- Candidate assertion, comparability, change classification
- 실행 신뢰도와 Quality Gate 분리
- Amazon Bedrock Guardrails를 MVP SUT로 사용

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
