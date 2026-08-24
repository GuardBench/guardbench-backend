# GuardBench Backend

Amazon Bedrock Guardrails의 Baseline과 Candidate를 같은 테스트 자산으로 검증하고, 기대 동작 위반과 정책 회귀를 구분하는 AI Security Regression Test Platform입니다.

## MVP 범위

- TestSuite와 TestCase로 정책 테스트 자산 관리
- 동일 Snapshot을 이용한 Baseline/Candidate 실행
- Candidate assertion, comparability, change classification
- 실행 신뢰도와 Quality Gate 분리
- Amazon Bedrock Guardrails를 MVP SUT로 사용

백엔드 애플리케이션은 아직 생성되지 않았습니다. Java·Spring Boot 프로젝트 생성과 실행 방법은 별도 Issue에서 추가합니다.

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
