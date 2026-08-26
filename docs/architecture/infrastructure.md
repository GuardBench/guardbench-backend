# 인프라 구성

> Status: APPROVED
> Owner: Infra
> Last reviewed: 2026-08-26
> Canonical source: GitHub
> Origin: [Notion 인프라 구성 설계](https://app.notion.com/p/3c0eeed6b62d81269f60e1c69fbf9fcc)
> Approved by: [GitHub Issue #87](https://github.com/GuardBench/guardbench-backend/issues/87)

이 문서는 GuardBench MVP를 처음 배포할 때 적용할 AWS 물리 인프라 기준이다. 실제 리소스를 만드는 IaC와 배포 실행은 후속 구현 Issue에서 다룬다. 공개 API, Domain 경계, DB 스키마와 [ADR 0005](../decisions/0005-async-test-run-execution-contract.md)·[ADR 0008](../decisions/0008-async-testrun-persistence-contract.md)의 메시지·claim·Outbox 계약은 변경하지 않는다.

## 결정 요약

| 항목 | MVP 결정 |
| --- | --- |
| AWS Region | 서울 `ap-northeast-2` 단일 Region |
| 환경 | `dev`, `prod`를 별도 AWS 계정과 별도 VPC로 분리 |
| 네트워크 | 2개 AZ의 public/app-private/db-isolated subnet, 환경당 NAT Gateway 1개 |
| RDS | PostgreSQL 16, Single-AZ, `db.t4g.micro`(dev) / `db.t4g.small`(prod) |
| ECS | Fargate의 API, Orchestrator, Executor Service; Publisher는 API 프로세스의 background component |
| SQS | Standard Queue 3개와 전용 DLQ 3개, visibility 30초, `maxReceiveCount=5` |
| Secret | 자격 증명은 AWS Secrets Manager, AWS 접근은 ECS Task Role |
| 관측성 | CloudWatch Logs·Metrics·Alarms와 환경별 SNS 알림 Topic |

### 승인 계약 정합성

- ADR 0005의 세 Standard Queue, `(snapshotId, targetType)` 작업 단위, API 내 Publisher, 15초 Provider timeout, 30초 visibility, 45초 claim lease와 `maxReceiveCount=5`를 유지한다.
- ADR 0008의 `PENDING/PUBLISHED` Outbox와 `SKIP LOCKED` 점유를 유지하고 운영 편의를 위한 새 상태나 컬럼을 추가하지 않는다.
- 물리 배포 위치와 초기 운영값만 확정하므로 ADR 0005·0008을 supersede할 새 ADR은 필요하지 않다.

## 리전과 환경 분리

- Bedrock Runtime과 Guardrails `ApplyGuardrail`을 지원하는 `ap-northeast-2`를 주 Region으로 사용한다.
- `dev`와 `prod`는 같은 AWS Organization 아래 별도 AWS 계정으로 분리한다. VPC, RDS, ECS Cluster, Queue, Secret과 로그를 공유하지 않는다.
- 리소스 이름은 `guardbench-{env}-{resource}` 형식을 사용하고 모든 리소스에 `Project=GuardBench`, `Environment={env}`, `ManagedBy=IaC` 태그를 붙인다.
- MVP에는 상시 `staging`을 두지 않는다. 부하 시험이나 배포 리허설은 `dev`에 prod와 같은 Task Definition을 일시 적용하고, 독립적인 상시 검증 환경이 필요해질 때 별도 Issue로 `staging` 계정을 추가한다.
- Region 장애를 위한 multi-region, cross-region DB 복제와 DR 자동화는 MVP 범위가 아니다. RDS snapshot을 이용한 수동 복구를 기본으로 한다.

계정 분리는 prod 오조작과 dev 자격 증명 유출의 영향 범위를 줄이는 대신, 배포 Role과 비용 추적 구성이 두 벌 필요하다. MVP에서도 prod 데이터와 권한의 격리를 비용 절감보다 우선한다.

## VPC와 네트워크 경계

각 환경은 `10.0.0.0/16`처럼 서로 겹치지 않는 CIDR의 VPC 하나를 소유한다. 정확한 CIDR은 IaC Issue에서 조직의 기존 대역과 충돌하지 않도록 선택한다.

```text
Internet
  │ HTTPS 443
  ▼
Internet Gateway
  │
  ▼
Application Load Balancer
  ├─ public subnet AZ-a
  └─ public subnet AZ-c
          │ HTTP 8080, ALB SG만 허용
          ▼
API ECS Service ───────────────┐
app-private subnet AZ-a/AZ-c   │ PostgreSQL 5432
                               ▼
Orchestrator ECS Service ───▶ Amazon RDS PostgreSQL
app-private subnet AZ-a/AZ-c   db-isolated subnet AZ-a/AZ-c
                               ▲
Executor ECS Service ──────────┘
app-private subnet AZ-a/AZ-c
          │ HTTPS 443
          ▼
NAT Gateway 1개 → SQS·Bedrock·Secrets Manager·ECR·CloudWatch
```

### Subnet과 route

- AZ 두 곳에 public, app-private, db-isolated subnet을 각각 하나씩 둔다.
- public subnet은 Internet Gateway route를 가지며 ALB와 NAT Gateway만 배치한다.
- ECS Task는 app-private subnet에서 public IP 없이 실행한다. 두 app-private subnet의 기본 outbound route는 환경당 NAT Gateway 한 개를 향한다.
- db-isolated subnet은 인터넷 기본 route를 갖지 않는다. 두 AZ의 subnet을 RDS DB subnet group으로 묶고 RDS의 `PubliclyAccessible`은 `false`다.
- MVP는 비용을 줄이기 위해 NAT Gateway를 한 개만 둔다. 해당 AZ 장애 시 외부 AWS API 호출이 멈출 수 있다는 위험을 수용하고, prod 가용성 요구가 생기면 AZ별 NAT 또는 필요한 VPC endpoint로 전환한다.
- ALB listener는 80 요청을 443으로 redirect하고, ACM 인증서를 사용하는 443 listener만 API Target Group으로 전달한다.

### Security Group

| Security Group | Inbound | Outbound |
| --- | --- | --- |
| `gb-{env}-alb-sg` | 인터넷의 TCP 443, redirect용 TCP 80 | `api-sg`의 TCP 8080 |
| `gb-{env}-api-sg` | `alb-sg`의 TCP 8080만 | `db-sg`의 TCP 5432, NAT를 통한 TCP 443 |
| `gb-{env}-worker-sg` | 없음 | `db-sg`의 TCP 5432, NAT를 통한 TCP 443 |
| `gb-{env}-db-sg` | `api-sg`, `worker-sg`의 TCP 5432만 | 응답 traffic만 |

- Orchestrator와 Executor는 `worker-sg`를 공유하되 IAM Task Role은 분리한다.
- SSH port와 광범위한 내부 CIDR inbound 규칙을 열지 않는다.
- AWS API 접근은 장기 access key가 아니라 Service별 최소 권한 ECS Task Role을 사용한다.

## Amazon RDS for PostgreSQL

애플리케이션의 로컬·통합 테스트가 `postgres:16-alpine`을 사용하므로 RDS도 PostgreSQL major 16으로 맞춘다. 최초 IaC는 결정 시점의 서울 Region 지원 minor인 `16.14`를 사용한다. `AutoMinorVersionUpgrade=true`로 RDS가 지정한 안정 minor를 maintenance window에 적용하되 major upgrade는 별도 호환성 검증과 승인 없이 수행하지 않는다.

| 설정 | `dev` | `prod` |
| --- | ---: | ---: |
| Instance class | `db.t4g.micro` (2 vCPU, 1 GiB) | `db.t4g.small` (2 vCPU, 2 GiB) |
| Availability | Single-AZ | Single-AZ |
| Storage | gp3 20 GiB, autoscaling 최대 100 GiB | gp3 20 GiB, autoscaling 최대 100 GiB |
| Automated backup retention | 1일 | 7일 |
| Deletion protection | 끔 | 켬 |
| 삭제 시 final snapshot | 선택 | 필수 |

- storage encryption은 RDS용 AWS managed KMS key를 사용하고 PostgreSQL 연결은 TLS를 강제한다.
- backup window는 `18:00-19:00 UTC`, maintenance window는 일요일 `19:00-20:00 UTC`로 분리한다.
- prod 삭제 시 automated backup을 보존 기간 동안 유지하고, 별도의 final snapshot을 만든다. 분기마다 prod 계정 안의 외부 연결이 차단된 임시 DB로 snapshot 복구 절차를 검증하고 검증 직후 삭제한다.
- Single-AZ와 burstable instance는 초기 비용을 낮추지만 장애 복구와 지속 부하에 약하다. CPU credit, connection, memory 또는 복구 시간 목표가 임계값을 반복해서 넘으면 먼저 `db.t4g.medium` 또는 Multi-AZ 전환을 검토한다.

## ECS Cluster와 Service

환경마다 Fargate 전용 ECS Cluster 하나를 두고 동일한 애플리케이션 image를 역할별 Task Definition으로 실행한다. 역할은 Spring profile 또는 명시적 실행 mode로 분리하며 별도 코드베이스를 만들지 않는다.

| 환경 | ECS Service | 역할 | Task CPU / Memory | 최소 / 최대 Task |
| --- | --- | --- | ---: | ---: |
| dev | API | HTTP API와 Outbox Publisher | 0.5 vCPU / 1 GiB | 1 / 1 |
| dev | Orchestrator | `gb-run-resolve`, `gb-run-finalize` 소비 | 0.5 vCPU / 1 GiB | 0 / 1 |
| dev | Executor | `gb-workitems` 소비와 Bedrock 호출 | 0.5 vCPU / 1 GiB | 0 / 2 |
| prod | API | HTTP API와 Outbox Publisher | 0.5 vCPU / 1 GiB | 1 / 2 |
| prod | Orchestrator | `gb-run-resolve`, `gb-run-finalize` 소비 | 0.5 vCPU / 1 GiB | 1 / 2 |
| prod | Executor | `gb-workitems` 소비와 Bedrock 호출 | 0.5 vCPU / 1 GiB | 1 / 4 |

- Fargate Linux platform version은 `LATEST`, CPU architecture는 애플리케이션 image가 검증된 `X86_64`로 시작한다. Graviton 전환은 image와 부하 시험 뒤 별도 변경으로 다룬다.
- rolling deployment는 `minimumHealthyPercent=100`, `maximumPercent=200`을 사용한다. prod API 최소 1개라는 결정은 완전한 무중단이나 AZ 장애 허용을 보장하지 않는다.
- API만 ALB Target Group에 등록한다. Worker Service에는 inbound listener나 public IP를 두지 않는다.
- 각 역할의 JDBC connection pool 상한은 Task당 10으로 시작한다. 최대 Task 수를 늘리기 전에 RDS connection 여유를 다시 계산한다.
- API Task 최소 1개를 유지하므로 같은 프로세스의 Publisher도 계속 진행된다.

### Service Auto Scaling

모든 지표는 1분 주기로 평가한다. scale-out cooldown은 60초, scale-in cooldown은 300초다.

| Service | Target tracking 기준 | 초기 목표값 |
| --- | --- | ---: |
| API | `ECSServiceAverageCPUUtilization` | 60% |
| API | `ECSServiceAverageMemoryUtilization` | 70% |
| Orchestrator | `(resolve visible + finalize visible) / max(running task, 1)` | Task당 5개 |
| Executor | `workitems visible / max(running task, 1)` | Task당 2개 |

- Queue 소비자는 단순 queue length가 아니라 backlog per task metric math를 사용한다.
- dev Worker는 최소 0이므로 첫 workload metric 한 건 뒤 1개부터 기동하는 cold start를 허용한다.
- 여러 target tracking policy가 적용된 API는 어느 하나가 scale-out을 요구하면 확장하고 모든 policy가 scale-in을 허용할 때만 축소한다.
- 목표값과 최대 Task 수는 초기 운영값이다. 부하 시험과 실제 처리 시간으로 조정하되 ADR 0005의 작업 단위, timeout과 claim 의미는 바꾸지 않는다.

## SQS Queue와 DLQ

환경마다 다음 Standard Queue와 각각의 전용 Standard DLQ를 만든다.

| Source Queue | Event | DLQ |
| --- | --- | --- |
| `guardbench-{env}-gb-run-resolve` | `TestRunRequested` | 같은 이름에 `-dlq` suffix |
| `guardbench-{env}-gb-workitems` | `TestExecutionRequested` | 같은 이름에 `-dlq` suffix |
| `guardbench-{env}-gb-run-finalize` | `TestExecutionCompleted` | 같은 이름에 `-dlq` suffix |

세 Source Queue에 동일한 초기 resource parameter를 적용한다.

`TestExecutionRequested` 한 건은 ADR 0005대로 `(snapshotId, targetType)` 하나만 처리한다. 별도 메시지 chunk를 만들거나 TestRun 전체를 한 메시지로 묶지 않는다.

| 설정 | 값 | 근거 |
| --- | ---: | --- |
| Queue type | Standard | 중복·역순을 Application 멱등성으로 흡수 |
| Visibility timeout | 30초 | ADR 0005 초기값 |
| Message retention | 4일 | 단기 장애 복구 여유 |
| Receive wait time | 20초 | empty polling 감소 |
| Delivery delay | 0초 | Outbox 발행 직후 처리 |
| DLQ `maxReceiveCount` | 5회 | ADR 0005 초기값 |
| DLQ retention | 14일 | Source보다 길게 보존해 수동 분석·redrive |
| Encryption | SSE-SQS | 별도 KMS key 운영 없이 at-rest 암호화 |

- redrive policy는 Source마다 자기 DLQ 하나를 지정하고, DLQ의 redrive allow policy는 해당 Source ARN만 허용한다.
- 실행 재시도 시 Application은 약 5초 뒤 보이도록 visibility를 조정할 수 있다. heartbeat는 사용하지 않으며 execution claim lease 45초와 Provider 전체 timeout 15초는 ADR 0005 값을 유지한다.
- Queue payload에 입력, 결과, credential, Provider 원문 오류를 넣지 않는다.
- DLQ alarm이 발생하면 consumer를 무조건 재시작하거나 메시지를 수정하지 않는다. 원인을 고치고 메시지 계약을 확인한 다음 AWS DLQ redrive로 원 Source에 저속 재전달하며, 처리 완료와 DLQ 감소를 함께 확인한다. 기존 Domain ID·claim·Outbox 멱등 규칙을 그대로 적용한다.

## Outbox Publisher 배포와 운영값

Publisher는 별도 ECS Service나 sidecar가 아니라 API Spring Boot 프로세스의 HTTP 요청 경로와 분리된 background component로 실행한다. API가 2개로 확장되어 Publisher도 여러 개가 되더라도 `SELECT ... FOR UPDATE SKIP LOCKED`로 batch 점유를 분리한다.

| 항목 | 초기값 |
| --- | ---: |
| 정상 polling 간격 | 1초 |
| DB 조회 batch | 10개 |
| SQS 발행 | Queue별 `SendMessageBatch`, 요청당 최대 10개 |
| SDK retry | AWS SDK standard retry mode, 최대 3회 |
| batch 전체 실패 backoff | 5초부터 지수 증가, 최대 60초, jitter 적용 |
| `PENDING` 보존 | 발행될 때까지 삭제하지 않음 |
| `PUBLISHED` 보존 | MVP 동안 삭제하지 않음 |

- 성공 항목만 `PUBLISHED`로 변경하고 실패 항목은 `PENDING`으로 남긴다. `PROCESSING`, `DEAD`, attempt와 next-attempt 컬럼은 추가하지 않는다.
- `PUBLISHED` 자동 cleanup은 ADR 0005의 MVP Non-Goal을 유지한다. table 크기를 월 1회 확인하고, 저장 비용이나 query 성능이 문제로 관측되면 보존 기간과 cleanup을 새 운영 Issue에서 결정한다.
- 별도 Publisher Service는 API와 독립적으로 확장·배포할 수 있지만 최소 Task와 배포 단위가 늘어난다. MVP에서는 이미 항상 실행되는 API Task를 활용하는 편이 단순하며 ADR 0005의 결정을 유지한다.

## Secret과 IAM

- Secret 저장소는 AWS Secrets Manager를 사용한다. Parameter Store `SecureString`을 secret 저장소로 병행하지 않는다.
- `guardbench/{env}/database/application`에는 애플리케이션 DB 사용자 이름과 password만 저장한다. RDS master credential은 별도 Secret으로 두고 ECS Task Role이 읽지 못하게 한다.
- TestRun이 참조하는 Guardrail ID와 version은 credential이 아니므로 기존 입력·DB 계약에 따라 저장한다. 배포 Region 같은 non-secret 값만 Task Definition 환경 설정으로 관리한다. AWS access key는 저장하지 않고 Orchestrator·Executor Task Role의 임시 자격 증명을 사용한다.
- Secret ARN만 Task Definition에 기록하고 값은 source, image, 로그와 CI output에 남기지 않는다. ECS execution role은 필요한 Secret ARN의 `GetSecretValue`만 허용한다.
- MVP의 application DB password는 90일마다 수동 회전한다. 새 값 적용, ECS 강제 재배포와 health 확인, 이전 값 폐기의 순서와 담당자를 운영 runbook에 기록한다. Lambda 기반 자동 rotation은 IaC와 운영 절차가 추가되므로 후속 범위다.
- API Task Role은 Publisher가 사용하는 세 Queue의 `SendMessage`만, Orchestrator와 Executor Task Role은 자신이 소비하는 Queue의 receive/delete/visibility 권한만 갖는다. Bedrock version 생성 권한은 Orchestrator, `ApplyGuardrail` 권한은 Executor에만 부여한다.

## CloudWatch 로그, 지표와 경보

### Log Group

| Log Group | 내용 | dev / prod 보존 |
| --- | --- | ---: |
| `/guardbench/{env}/api` | HTTP API와 `component=outbox-publisher` 로그 | 14일 / 30일 |
| `/guardbench/{env}/orchestrator` | resolution·finalization 소비 로그 | 14일 / 30일 |
| `/guardbench/{env}/executor` | 실행 claim·Bedrock 결과 요약 로그 | 14일 / 30일 |

- Log Group은 Task 시작 전에 IaC로 만들고 retention과 encryption을 명시한다. 무기한 기본 보존을 사용하지 않는다.
- JSON 구조화 로그에 `environment`, `service`, `testRunId`, `eventId`, `eventType`, `snapshotId`, `targetType`, `executionStatus`, `durationMs`를 필요한 범위에서 기록한다.
- 테스트 입력, ActualResult 전문, Guardrail 설정 전문, Secret, ARN, SDK 예외 원문은 기록하지 않는다.

### 최소 지표와 alarm

Application custom metric namespace는 `GuardBench/{env}`다. 모든 prod alarm과 핵심 dev alarm은 `guardbench-{env}-ops` SNS Topic으로 전달한다.

| 대상 | Alarm 조건 | 평가 |
| --- | --- | --- |
| 각 DLQ | `ApproximateNumberOfMessagesVisible >= 1` | 1분 1회 |
| `gb-run-resolve` | oldest message age `>= 60초` | 1분 3회 연속 |
| `gb-workitems` | oldest message age `>= 120초` | 1분 3회 연속 |
| `gb-run-finalize` | oldest message age `>= 60초` | 1분 3회 연속 |
| Orchestrator backlog | visible 합계가 최대 처리 목표 `10개` 초과 | 1분 5회 연속 |
| Executor backlog | visible이 최대 처리 목표 `8개` 초과 | 1분 5회 연속 |
| Outbox | `OutboxOldestPendingAgeSeconds >= 60` | 1분 3회 연속 |
| Outbox | `OutboxPendingCount >= 100` | 1분 5회 연속 |
| TestRun | `PREPARING >= 120초` 또는 `RUNNING >= 10분`인 Run 1개 이상 | 1분 3회 연속 |
| Worker 오류 | consumer 기술 실패 또는 unsupported message 1건 이상 | 5분 1회 |
| Worker retry | retry 10건 초과 | 5분 2회 연속 |
| ECS Service | CPU `>= 85%` 또는 memory `>= 85%` | 1분 10회 연속 |
| prod ECS Service | Running Task가 설정된 최소값 미만 | 1분 2회 연속 |
| RDS | CPU `>= 80%`, connection `>= 80%`, free memory `< 256 MiB` 중 하나 | 5분 3회 연속 |
| RDS storage | free storage `< 10 GiB` | 5분 1회 |
| ALB | healthy host 0 또는 5xx 비율 `>= 1%`이면서 요청 20건 이상 | 1분 5회 연속 |

- Dashboard에는 Source/DLQ queue length와 age, ECS running task와 처리 시간, Outbox count/age, TestRun 상태별 체류 시간, retry·duplicate·영구 실패 수, RDS와 ALB 상태를 함께 표시한다.
- alarm 임계값은 최초 배포 기준값이다. 낮은 트래픽에서 생기는 오탐과 실제 p95 처리 시간을 한 달간 관찰한 뒤 변경 근거와 전후 값을 운영 Issue에 남긴다.
- DLQ 유입이나 PENDING Outbox를 TestExecution 실패로 변환하지 않는다. alarm 후 원인을 복구하고 승인된 재발행·redrive 절차를 따른다.

## 선택하지 않은 대안과 트레이드오프

### PostgreSQL 18 또는 Aurora PostgreSQL

최신 major와 Aurora의 확장성보다 현재 개발·Testcontainers 기준인 PostgreSQL 16과의 일치, 작은 운영 표면을 우선한다. major upgrade와 Aurora 전환은 호환성·부하·복구 요구가 생길 때 별도로 결정한다.

### Multi-AZ RDS와 AZ별 NAT

가용성은 높아지지만 MVP 초기 고정 비용이 커진다. 현재는 Single-AZ RDS와 NAT 한 개의 장애 위험을 명시적으로 수용하고 backup·alarm·수동 복구를 갖춘다.

### dev·staging·prod 상시 운영

release rehearsal 격리는 좋아지지만 사용량이 적은 단계에서 RDS·NAT·ECS 운영 세트가 하나 더 생긴다. dev/prod만 상시 운영하고 필요할 때 동일 IaC로 임시 검증 환경을 만든다.

### 별도 Outbox Publisher Service

독립 배포와 scale-to-zero가 가능하지만 최소 Task와 장애 지점이 늘어난다. Publisher 경쟁 제어가 이미 DB에 있으므로 API background component를 선택한다.

### Parameter Store만 사용

비용은 낮지만 DB credential lifecycle과 audit 목적에 특화된 Secrets Manager를 선택한다. non-secret 설정에만 일반 환경 설정을 사용한다.

## 후속 구현 경계

이 문서로 기존 미결정 사항은 모두 초기 운영값 또는 명시적인 MVP Non-Goal로 정리됐다. 후속 IaC Issue는 이 계약을 그대로 프로비저닝하고 다음을 검증해야 한다.

1. dev/prod 계정·VPC 격리와 Security Group reachability
2. RDS backup·restore, deletion protection와 TLS 연결
3. Queue별 redrive policy, DLQ alarm과 수동 redrive
4. Fargate Task 크기·최소/최대 수와 scale-from-zero
5. API 다중 Publisher의 `SKIP LOCKED` 경쟁과 PENDING alarm
6. Secret least privilege와 로그 redaction
7. CloudWatch alarm의 SNS 전달과 runbook 연결

IaC 도구 선택, 실제 AWS 리소스 생성, CI/CD 변경, multi-region/DR, 자동 Outbox cleanup, stale TestRun Reconciler와 DLQ 자동 redrive는 이 문서 PR에 포함하지 않는다.

## 참고 자료

- [AWS Well-Architected: Separate workloads using accounts](https://docs.aws.amazon.com/wellarchitected/latest/framework/sec_securely_operate_multi_accounts.html)
- [Amazon Bedrock endpoints and quotas](https://docs.aws.amazon.com/general/latest/gr/bedrock.html)
- [Amazon RDS for PostgreSQL release calendar](https://docs.aws.amazon.com/AmazonRDS/latest/PostgreSQLReleaseNotes/postgresql-release-calendar.html)
- [Amazon RDS for PostgreSQL automatic minor upgrades](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_UpgradeDBInstance.PostgreSQL.Minor.html)
- [Amazon RDS in a VPC](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_VPC.WorkingWithRDSInstanceinaVPC.html)
- [Amazon RDS backup retention](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.BackupRetention.html)
- [Amazon ECS Fargate task CPU and memory](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-tasks-services.html)
- [Amazon ECS service auto scaling](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html)
- [Amazon SQS visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html)
- [Amazon SQS dead-letter queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html)
- [Amazon SQS long polling](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-short-and-long-polling.html)
- [Amazon SQS SSE-SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-sqs-sse-queue.html)
- [AWS Systems Manager guidance for credential storage](https://docs.aws.amazon.com/systems-manager/latest/userguide/parameter-store-policies.html)
- [CloudWatch Logs retention](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/Working-with-log-groups-and-streams.html)
