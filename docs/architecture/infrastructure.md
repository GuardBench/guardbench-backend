# 인프라 구성

> Status: DRAFT
> Owner: Infra
> Last reviewed: 2026-08-24
> Canonical source: GitHub
> Origin: [Notion 인프라 구성 설계](https://app.notion.com/p/3c0eeed6b62d81269f60e1c69fbf9fcc)
> AI assistance: 이 문서의 초안은 LLM의 도움으로 작성되었으며 사람의 검토가 필요합니다.

> ECS·SQS·Transactional Outbox를 사용하는 큰 방향은 합의했지만, 실제 AWS 리소스와 네트워크, 배포 topology, 작업 분할 단위 및 운영 수치는 아직 확정 전이다.

## MVP 방향

- Java·Spring Boot 단일 백엔드
- PostgreSQL/Amazon RDS에 테스트 자산과 감사 가능한 실행 결과 저장
- Amazon ECS의 API Service와 Worker Service 분리
- Amazon SQS Standard Queue를 통한 비동기 TestRun 처리와 DLQ 운용
- PostgreSQL/Amazon RDS 기반 Transactional Outbox로 TestRun 저장과 메시지 발행의 원자성 확보
- AWS SDK for Java를 이용한 Amazon Bedrock Guardrails 연동
- Amazon CloudWatch 로그와 지표
- Docker 기반 패키징과 GitHub Actions 기반 서비스 CI/CD

서비스 자체의 CI/CD는 범위에 포함하지만 고객 애플리케이션의 자동 배포 차단 기능은 MVP가 아니다. FastAPI/Python 보조 서버는 두지 않는다.

## TestRun 비동기 실행 구조

```text
Client
  │ POST /api/v1/test-runs
  ▼
API Service (ECS)
  │ 하나의 RDS 트랜잭션
  ├─ TestRun 저장
  ├─ TestCaseSnapshot 저장
  └─ OutboxEvent 저장
  │
  ├─ commit 성공 → 202 Accepted
  └─ commit 실패 → TestRun과 OutboxEvent 모두 생성하지 않음

Outbox Publisher
  │ PENDING 이벤트 조회 및 발행
  ▼
Amazon SQS Standard Queue
  │
  ▼
Worker Service (ECS)
  ├─ DB에서 TestRun과 Snapshot 조회
  ├─ 대상 준비 및 테스트 실행
  ├─ 실행 결과와 상태 저장
  └─ DB commit 이후 SQS 메시지 삭제
```

API Service가 TestRun을 저장한 뒤 SQS에 직접 메시지를 보내는 이중 쓰기는 사용하지 않는다. TestRun, 실행 대상 Snapshot, OutboxEvent를 하나의 RDS 트랜잭션으로 저장한다. 트랜잭션이 성공하면 TestRun과 발행할 이벤트가 함께 존재하고, 실패하면 둘 다 존재하지 않는다.

Outbox Publisher는 커밋된 `PENDING` 이벤트를 SQS에 전달하고 발행 상태를 기록한다. SQS 전송 성공 후 발행 상태를 기록하기 전에 장애가 발생하면 같은 이벤트가 다시 전달될 수 있으므로, Outbox와 SQS를 정확히 한 번 전달 수단으로 간주하지 않는다.

## 메시지 계약 원칙

SQS 메시지는 TestCase 입력이나 전체 Snapshot 같은 대용량 Payload를 포함하지 않는다. 최초 실행 요청 메시지는 작업을 식별하는 최소 정보만 전달한다.

```json
{
  "eventId": "01J...",
  "eventType": "TestRunRequested",
  "eventVersion": 1,
  "testRunId": 123
}
```

- RDS를 TestRun과 Snapshot 데이터의 최종 기준으로 사용한다.
- `eventId`는 이벤트 중복 처리 방지를 위한 안정적인 식별자다.
- `eventType`과 `eventVersion`으로 메시지 형식의 변경을 명시한다.
- 메시지에는 비밀 정보, Guardrail 접속 자격 증명, 전체 테스트 입력을 넣지 않는다.
- API의 `Idempotency-Key`는 중복 TestRun 생성을 방지하고, `eventId` 기반 멱등 처리는 이미 생성된 이벤트의 중복 소비를 방지한다. 두 규칙은 서로 대체하지 않는다.

## Outbox 발행 규칙

OutboxEvent는 개념적으로 다음 운영 정보를 가진다. 정확한 테이블과 타입은 DB 설계 Issue에서 확정한다.

- 이벤트 ID와 이벤트 유형·버전
- Aggregate 유형과 TestRun ID
- 발행할 Payload
- `PENDING` 또는 `PUBLISHED` 발행 상태
- 생성·발행 시각
- 시도 횟수, 다음 시도 가능 시각, 마지막 오류

Publisher는 여러 인스턴스가 같은 이벤트를 동시에 점유하지 않도록 행 잠금 또는 그에 준하는 경쟁 제어를 사용한다. 일시적인 SQS 장애에는 제한된 재시도와 backoff를 적용한다. `PUBLISHED` 이벤트는 즉시 물리 삭제하지 않고, 운영 추적에 필요한 보존 기간을 정한 뒤 별도 정리한다.

Outbox Publisher는 논리적으로 API 요청 처리와 분리한다. 별도 ECS Service로 배포할지 API 컨테이너의 독립 실행 프로세스로 둘지는 부하 시험과 운영 복잡도를 바탕으로 별도 결정한다.

## Worker 멱등성과 실패 처리

Amazon SQS Standard Queue는 동일 메시지를 두 번 이상 전달할 수 있다. Worker는 중복과 재시작을 정상 상황으로 취급한다.

- `eventId` 처리 기록이나 원자적인 TestRun 상태 전이로 동일 이벤트의 중복 실행을 막는다.
- 실행 결과에는 중복 저장을 막는 유일성 제약을 둔다.
- Worker는 결과와 상태 변경을 DB에 커밋한 후에만 SQS 메시지를 삭제한다.
- 재시도 가능한 일시 오류와 재시도해도 해결되지 않는 오류를 구분한다.
- 최대 수신 횟수를 초과한 메시지는 DLQ로 이동한다.
- DLQ 메시지를 재처리할 때도 동일한 멱등 규칙을 적용한다.
- Visibility Timeout보다 오래 걸리는 작업은 timeout을 연장하거나 더 작은 작업으로 분할한다.

## 대량 테스트 작업 분할

외부 API 계약은 TestRun 하나를 접수하지만 내부 실행은 확장 가능한 단위로 나눌 수 있다.

```text
TestRunRequested
  → Coordinator가 실행 대상 준비
  → Snapshot 또는 일정 개수의 Snapshot을 실행 작업으로 분할
  → Worker들이 병렬 실행
  → 완료 결과 집계
  → Assertion·Change·Quality Gate 최종 평가
```

최초 Outbox 이벤트는 TestRun ID만 전달하며, 세부 병렬화는 실행 계층 내부 책임으로 둔다. 테스트 하나당 메시지 하나로 고정하거나 TestRun 전체를 항상 메시지 하나로 실행한다는 규칙을 공개 API 계약에 노출하지 않는다. 작업 청크 크기, 동시 실행 한도, 대상별 rate limit은 부하 시험 후 운영 설정으로 확정한다.

## 확장과 관측성

Worker Service의 확장 기준은 단순 큐 길이가 아니라 실행 중인 ECS Task 수 대비 처리 대기량을 기본으로 한다. 최소한 다음 지표를 관찰한다.

- SQS 처리 가능 메시지 수와 가장 오래된 메시지의 대기 시간
- 실행 중인 Worker Task 수와 작업 처리 시간
- DLQ 메시지 수
- `PENDING` OutboxEvent 수와 가장 오래된 이벤트의 대기 시간
- TestRun 상태별 체류 시간과 전체 완료 시간
- 재시도, 중복 감지, 영구 실패 횟수

경보 임계값과 ECS Auto Scaling 정책은 실제 부하 시험 결과를 근거로 정한다.

## 미결정 사항

- VPC/subnet/security group topology
- RDS engine version과 백업 정책
- SQS visibility timeout, 최대 수신 횟수, DLQ 보존 기간과 재처리 절차
- Standard Queue 내부의 실행 작업 분할 단위와 메시지 청크 크기
- Outbox Publisher의 배포 단위, polling 주기, batch 크기, 재시도와 보존 정책
- ECS Task 크기, 최소·최대 개수, Auto Scaling 목표값
- 환경 분리와 secret 관리
- CloudWatch alarm과 보존 기간

이 항목에 의존하는 구현은 ADR 또는 승인된 인프라 Issue가 생길 때까지 확정하지 않는다.

## 참고 자료

- [AWS Prescriptive Guidance: Transactional outbox pattern](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html)
- [Amazon SQS: At-least-once delivery](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues-at-least-once-delivery.html)
- [Amazon SQS: Visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html)
