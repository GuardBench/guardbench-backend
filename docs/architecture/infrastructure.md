# 인프라 구성

> Status: APPROVED
> Owner: Infra
> Last reviewed: 2026-09-04
> Canonical source: GitHub
> Origin: [Notion 인프라 구성 설계](https://app.notion.com/p/3c0eeed6b62d81269f60e1c69fbf9fcc)
> Approved by: Issue #87 요청자의 2026-08-27 배포 결정
> Updated by: [Issue #182](https://github.com/GuardBench/guardbench-backend/issues/182), [guardbench-iac PR #39](https://github.com/GuardBench/guardbench-iac/pull/39)

이 문서는 GuardBench MVP를 **현재 구현 그대로 최초 배포**하기 위한 AWS 물리 인프라 계약이다. 실제 Terraform 변경과 AWS 적용은 `GuardBench/guardbench-iac`에서 수행하며, 공개 API·Domain·DB schema와 [ADR 0005](../decisions/0005-async-test-run-execution-contract.md)·[ADR 0008](../decisions/0008-async-testrun-persistence-contract.md)의 메시지·claim·Outbox 의미는 변경하지 않는다.

## 확인한 배포 기준

2026-08-27에 다음 revision을 직접 확인했다.

| 저장소 | 확인 revision | 배포와 관련된 사실 |
| --- | --- | --- |
| `GuardBench/guardbench-backend` | `73022a6` (`dev`) | 하나의 Spring Boot 프로세스가 HTTP API, Outbox Publisher와 세 Queue consumer를 모두 실행할 수 있다. Worker 역할 선택기는 없다. |
| `GuardBench/guardbench-frontend` | `adc1ee6` (`main`) | React/Vite 정적 SPA이며 현재 화면 데이터는 mock이다. `main` push 시 `guardbench-dev-frontend` S3와 CloudFront `E1PVL0Z78B1HMR`에 배포하도록 선언돼 있다. |
| `GuardBench/guardbench-iac` | PR #39 | SageMaker classifier Real-Time endpoint를 toggle로 생성·삭제하고 ECS task role에 InvokeEndpoint를 연결한다. |

프론트엔드의 최근 GitHub Actions 실패는 3시간 유효한 임시 AWS credential이 만료된 결과이며 인프라 구성 결함이 아니다. 기존 S3·CloudFront 배포는 정상 운영 대상으로 간주하고 그대로 보존한다. Terraform 적용 전에는 기존 resource와 state를 대조하고 unmanaged resource만 import한다.

### AWS 실제 리소스 현황

같은 날 `ap-northeast-2` 계정을 읽기 전용으로 조회한 결과는 다음과 같다. Resource ID와 계정 번호는 환경별 운영 정보이므로 이 아키텍처 계약에 고정하지 않고 Terraform state와 배포 runbook에서 관리한다.

| 구분 | 실제 상태 | 최초 배포 처리 |
| --- | --- | --- |
| `guardbench-dev-vpc` | `10.1.0.0/16`, public/private subnet 각 2개가 배포됨 | 그대로 재사용, 재생성 금지 |
| VPC Endpoint | S3, ECR API/DKR, Logs, SSM, Monitoring, SQS, SageMaker Runtime 배포됨 | 그대로 재사용하고 Secrets Manager Endpoint만 추가 |
| Security Group | ALB, API, Worker, RDS, VPC Endpoint용 SG가 배포됨 | API SG를 단일 App Service에 재사용, Worker SG는 미사용 보존 |
| `guardbench-dev-alb` | internet-facing, active, HTTP 80 listener가 배포됨 | 그대로 재사용하고 ingress만 축소 |
| `guardbench-dev-api-tg` | HTTP 8080, target type `ip`, health path `/health` | 그대로 재사용하고 health path 수정 |
| 기존 CloudFront | S3 origin과 `/api/*` ALB origin이 배포됨 | 그대로 재사용하고 API origin protocol/policy 수정 |
| ECS·ECR·SQS | 배포된 resource 없음 | 이번 최초 배포에서 신규 생성 |
| GuardBench RDS | 배포된 resource 없음 | 이번 최초 배포에서 신규 생성 |
| SageMaker classifier | model·endpoint configuration·execution role 유지, Real-Time endpoint는 비용 절감을 위해 삭제됨 | `sagemaker_classifier_endpoint_enabled=true`일 때만 endpoint 생성 |

별도 VPC에 있는 `kclee-app` RDS는 PostgreSQL 18.3이며 GuardBench 전용 resource가 아니다. VPC Peering으로 연결하거나 GuardBench DB로 재사용하지 않는다.

## 최초 배포 결정

| 항목 | 결정 |
| --- | --- |
| 최초 배포 환경 | 기존 프론트와 연결되는 `dev` 한 개 |
| Region | 서울 `ap-northeast-2` |
| Frontend | 기존 S3 + CloudFront OAC 유지 |
| Backend 진입점 | 같은 CloudFront의 `/api/*` → public ALB HTTP listener → private ECS |
| Backend runtime | Fargate `guardbench-dev-app` Service 하나, desired/min/max `1/1/1` |
| Database | GuardBench VPC에 RDS PostgreSQL 16.14 신규 생성, Single-AZ `db.t4g.micro`, gp3 20 GiB |
| Messaging | SQS Standard source Queue 3개와 전용 DLQ 3개 |
| Secret | RDS managed master secret를 Secrets Manager에서 ECS 환경변수로 주입 |
| Outbound | NAT Gateway 없이 SQS·SageMaker Runtime·ECR·Logs·Secrets Manager VPC Endpoint 사용 |
| 관측성 | CloudWatch native metric, application text log metric filter와 SNS alarm |

`prod`는 최초 배포 범위가 아니다. 만들 때는 별도 Terraform state key, 별도 VPC, RDS, Queue, Secret, ECS Cluster와 Log Group을 사용하며 dev resource를 공유하지 않는다. 현재 하나뿐인 `infra/terraform.tfstate`를 prod와 함께 사용하지 않는다.

## 실행 가능한 전체 topology

```text
Browser
  │ HTTPS
  ▼
기존 CloudFront Distribution
  ├─ /*       → 기존 private S3 bucket (OAC)
  └─ /api/*   → HTTP :80
                  ▼
             public ALB
                  │ HTTP :8080, ALB SG만 허용
                  ▼
             ECS Fargate Service: guardbench-dev-app (desired 1)
             ├─ Spring MVC HTTP API
             ├─ Outbox Publisher
             ├─ gb-run-resolve consumer
             ├─ gb-workitems consumer
             ├─ gb-run-finalize consumer
             └─ SageMaker Runtime `InvokeEndpoint` → Response Behavior Classifier
                  │
                  ├─ PostgreSQL :5432 → private RDS
                  └─ HTTPS :443 → VPC Endpoints
                       ├─ SQS
                       ├─ SageMaker Runtime
                       ├─ ECR API / ECR DKR / S3 Gateway
                       ├─ CloudWatch Logs
                       └─ Secrets Manager
```

현재 `WORKER_ENABLED=true`는 세 Queue adapter와 세 polling scheduler를 한꺼번에 등록한다. 따라서 Orchestrator와 Executor를 별도 ECS Service로 배포하면 두 Service가 모두 세 Queue를 소비한다. 최초 배포는 물리 Service 하나에 논리 역할을 함께 배치해 이 문제를 피하며, 역할별 Service 분리는 worker 역할 선택기가 구현·검증된 뒤 수행한다.

## 네트워크와 CloudFront

### VPC와 subnet

- 이미 배포된 `guardbench-dev-vpc` (`10.1.0.0/16`)를 수정 없이 재사용한다.
- 이미 배포된 `ap-northeast-2a`, `ap-northeast-2c`의 public subnet `10.1.1.0/24`, `10.1.2.0/24`를 재사용한다.
- 같은 AZ의 기존 private subnet `10.1.10.0/24`, `10.1.20.0/24`를 ECS Task, 신규 RDS DB subnet group과 Interface Endpoint에 재사용한다.
- public subnet은 Internet Gateway default route를 가지며 ALB만 배치한다.
- private subnet은 Internet/NAT default route를 갖지 않는다. AWS API 접근은 VPC Endpoint로만 한다.
- RDS는 `PubliclyAccessible=false`이고 ECS Task에도 public IP를 할당하지 않는다.

기존 IaC의 SQS, SageMaker Runtime, ECR API, ECR DKR, CloudWatch Logs Interface Endpoint와 S3 Gateway Endpoint를 유지한다. SageMaker Runtime Interface Endpoint는 private ECS task가 standard SageMaker Runtime hostname으로 호출할 수 있도록 Private DNS를 활성화한다. RDS managed secret 주입을 위해 Secrets Manager Interface Endpoint를 추가한다. SSM Parameter Store를 사용하지 않으면 기존 SSM·CloudWatch Monitoring Endpoint는 최초 배포 필수 항목이 아니므로 비용을 확인한 뒤 제거할 수 있다.

### CloudFront와 ALB 연결

기존 CloudFront의 정적 파일 behavior는 유지하고 `/api/*` behavior만 다음과 같이 고친다.

- cache policy는 `CachingDisabled`를 사용한다.
- origin request policy는 `AllViewerExceptHostHeader`를 사용해 query string, cookie, `Content-Type`, `Authorization`, `Idempotency-Key`를 백엔드에 전달한다.
- backend origin은 ALB DNS이고 `origin_protocol_policy=http-only`, port `80`이다.
- CloudFront 기본 인증서를 유지하므로 Browser는 계속 HTTPS만 사용한다.
- Frontend는 별도 API hostname 대신 상대 경로 `/api/v1/...`를 호출한다. 같은 origin이므로 최초 배포에 application CORS 설정은 필요하지 않다.

현재 IaC의 `https-only` CloudFront origin과 HTTP-only ALB listener 조합은 동작하지 않는다. ALB DNS에 대응하는 ACM 인증서와 custom domain이 없으므로 최초 배포에서는 CloudFront-to-ALB 구간만 HTTP를 수용한다. ALB port 80 ingress는 인터넷 전체가 아니라 AWS managed prefix list `com.amazonaws.global.cloudfront.origin-facing`으로 제한한다.

ALB Target Group은 port `8080`, protocol `HTTP`, target type `ip`를 사용한다. 현재 애플리케이션에는 `/health` endpoint와 Actuator dependency가 없으므로 health check는 공개된 `GET /api/v1/test-suites`, matcher `200`, interval `30초`, timeout `5초`, healthy/unhealthy threshold `2`로 설정한다. 이 검사는 애플리케이션과 DB 연결을 함께 확인한다.

### Security Group

| 기존 Security Group | 최초 배포 용도 | Inbound | Outbound |
| --- | --- | --- | --- |
| `guardbench-dev-alb-sg` | ALB | CloudFront origin-facing prefix list의 TCP 80 | API SG의 TCP 8080 |
| `guardbench-dev-api-sg` | 단일 App ECS Service | ALB SG의 TCP 8080 | RDS SG의 TCP 5432, Endpoint SG의 TCP 443 |
| `guardbench-dev-rds-sg` | 신규 GuardBench RDS | API SG의 TCP 5432 | 응답 traffic |
| `guardbench-dev-vpce-sg` | 기존·신규 Interface Endpoint | API SG의 TCP 443 | 응답 traffic |

기존 `guardbench-dev-worker-sg`는 최초 배포에서 연결하지 않고 state에 보존한다. RDS·Endpoint SG에서 Worker SG를 허용하는 ingress, ALB SG의 인터넷 전체 80/443 ingress와 API SG의 `0.0.0.0/0:443` egress는 위 표의 rule로 교체한다. 기존 SG 자체는 삭제·교체하지 않는다.

## RDS PostgreSQL

| 설정 | dev 최초값 |
| --- | ---: |
| Engine | PostgreSQL `16.14`, auto minor upgrade 켬 |
| Instance | Single-AZ `db.t4g.micro` |
| Storage | encrypted gp3 20 GiB, autoscaling 최대 100 GiB |
| Database / user | `guardbench` / `guardbench` |
| Backup retention | 1일 |
| Deletion protection | 끔 |
| 삭제 시 final snapshot | 최초 dev는 선택 |

- DB subnet group은 두 private subnet으로 구성하고 TCP 5432는 기존 API SG에서만 허용한다.
- 다른 VPC의 `kclee-app`과 VPC Peering을 만들지 않는다. 기존 `guardbench-dev-rds-sg`를 연결한 GuardBench 전용 RDS를 현재 VPC에 새로 만든다.
- `manage_master_user_password=true`로 RDS가 password를 생성·회전 가능한 Secrets Manager secret으로 관리하게 한다. password를 tfvars, Terraform output, GitHub secret이나 Task Definition 평문에 넣지 않는다.
- 현재 Flyway가 애플리케이션 시작 시 current V1 migration을 실행하므로 최초 Task가 schema를 생성한다. 별도 migration container는 만들지 않는다.
- JDBC URL은 `jdbc:postgresql://{rds-endpoint}:5432/guardbench?sslmode=require`를 사용한다.
- 하나의 Task만 실행하므로 기본 Hikari pool을 그대로 사용한다. Task 수를 늘리기 전 pool 상한과 RDS connection 한도를 함께 결정한다.

## ECS image와 단일 Service

백엔드 저장소에는 Dockerfile이 없다. Spring Boot Gradle plugin의 `bootBuildImage`로 Java 21 OCI image를 만들고 ECR에 Git commit SHA tag로 push한다.

```bash
./gradlew clean check bootBuildImage --imageName={account}.dkr.ecr.ap-northeast-2.amazonaws.com/guardbench-dev:{git-sha}
docker push {account}.dkr.ecr.ap-northeast-2.amazonaws.com/guardbench-dev:{git-sha}
```

- ECR tag는 immutable로 설정하고 Task Definition은 `latest`가 아니라 검증한 commit SHA 또는 image digest를 사용한다.
- Fargate Task는 Linux `X86_64`, CPU `512`, memory `1024 MiB`, container port `8080`이다.
- ECS Service desired/min/max는 모두 1이며 Service Auto Scaling은 사용하지 않는다. 현재 역할이 결합돼 있어 Queue backlog나 CPU로 Task를 늘리면 API·Publisher·모든 consumer가 함께 늘기 때문이다.
- rolling deployment는 `minimumHealthyPercent=100`, `maximumPercent=200`, deployment circuit breaker rollback을 사용한다.
- Log Group `/ecs/guardbench-dev/app`을 Task 전에 만들고 14일 보존한다.

### Performance Backend application revision ownership

Performance Backend도 dev Backend와 같은 application revision 배포 경계를 사용한다. Terraform은
Performance ECS Service의 shape, capacity, environment/secrets, IAM, networking/logging과
bootstrap Task Definition을 소유한다. Backend CI는 `workflow_dispatch`에서 `performance`
Environment를 선택했을 때 immutable Git SHA image를 사용해 최신 ACTIVE infrastructure Task
Definition을 base로 새 application revision을 등록하고 Performance ECS Service만 갱신한다.

`dev`와 `performance` GitHub Environment는 각각 별도의 OIDC role과 해당 Environment subject를
허용하는 trust policy를 제공해야 한다. Performance Environment의 허용 branch는 `dev`이며,
Performance role trust policy는 `sts:AssumeRoleWithWebIdentity`에 다음 두 조건을 `StringEquals`로
설정한다.

```text
token.actions.githubusercontent.com:aud = sts.amazonaws.com
token.actions.githubusercontent.com:sub = repo:GuardBench/guardbench-backend:environment:performance
```

role ARN만 설정하고 trust policy를 갱신하지 않으면 OIDC AssumeRole 단계에서 실패한다. 각
Environment는 다음 ECS/ECR 식별자를 제공해야 한다:
`AWS_REGION`, `ECR_REPOSITORY`, `ECS_CLUSTER`, `ECS_SERVICE`,
`ECS_CONTAINER_NAME`, `ECS_TASK_DEFINITION_FAMILY`. CI는 service가 configured task-definition
family와 일치하는지, source image repository가 immutable tag 정책인지, rollout 후 실제
service task definition과 primary rollout state가 요청한 값인지 검증한다. Infrastructure
configuration 변경은 Terraform apply로 최신 bootstrap revision을 만든 뒤, Backend CI가 그
revision을 다음 application revision의 base로 사용한다.

### Task 환경변수

| 이름 | 값 또는 주입원 | 이유 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | ALB Target Group과 일치 |
| `SPRING_DOCKER_COMPOSE_ENABLED` | `false` | ECS에서 로컬 compose lifecycle 비활성화 |
| `SPRING_DATASOURCE_URL` | RDS JDBC URL | PostgreSQL 연결 |
| `SPRING_DATASOURCE_USERNAME` | RDS managed secret의 `username` | ECS secret JSON key 주입 |
| `SPRING_DATASOURCE_PASSWORD` | RDS managed secret의 `password` | ECS secret JSON key 주입 |
| `AWS_REGION` | `ap-northeast-2` | SQS·SageMaker Runtime client Region |
| `SQS_ENABLED` | `true` | SQS client와 Outbox Publisher 활성화 |
| `WORKER_ENABLED` | `true` | 세 Queue consumer와 Response Behavior Classifier 활성화 |
| `SAGEMAKER_CLASSIFIER_ENDPOINT_NAME` | `guardbench-qwen3-4b-endpoint` | Terraform이 주입하는 Real-Time endpoint 이름 |
| `SAGEMAKER_CLASSIFIER_SYSTEM_PROMPT` | 승인된 classifier system prompt v1 | 비어 있으면 평가 시 configuration error로 실패. 배포 전에 반드시 주입 |
| `SAGEMAKER_CLASSIFIER_USER_PROMPT_TEMPLATE` | 선택값 | 비어 있으면 `USER REQUEST / ASSISTANT RESPONSE` 기본 형식 사용 |
| `SPRING_TASK_SCHEDULING_POOL_SIZE` | `4` | 세 long-poll scheduler와 Outbox Publisher를 서로 막지 않게 실행 |
| `GUARDBENCH_WORKER_WORK_ITEMS_CONCURRENCY` | `1` | task 내부 WorkItems 동시 실행 상한. 양의 정수이며 Resolve/Finalize에는 적용하지 않음 |
| `GUARDBENCH_WORKER_WORK_ITEMS_SHUTDOWN_TIMEOUT_SECONDS` | `30` | 종료 시 신규 WorkItem 수신을 중단하고 in-flight 작업을 기다리는 최대 시간 |
| `GUARDBENCH_SQS_QUEUE_URLS_RESOLVE` | resolve Queue URL | startup의 `GetQueueUrl` 호출 제거 |
| `GUARDBENCH_SQS_QUEUE_URLS_WORK_ITEMS` | work-items Queue URL | startup의 `GetQueueUrl` 호출 제거 |
| `GUARDBENCH_SQS_QUEUE_URLS_RUN_FINALIZE` | finalize Queue URL | startup의 `GetQueueUrl` 호출 제거 |

현재 scheduled method는 Queue별 3개와 Publisher 1개다. Spring 기본 scheduling pool 1개로 배포하면 long polling이 Publisher까지 막으므로 pool을 4로 고정한다. Queue URL 세 개는 secret이 아니며 Task Definition 환경변수로 직접 넣는다. 명시적 URL을 모두 주입하므로 application Task Role에 `sqs:GetQueueUrl`은 필요하지 않다.

## SQS와 현재 runtime 동작

| Source Queue | Payload | DLQ |
| --- | --- | --- |
| `guardbench-dev-gb-run-resolve` | `TestRunRequested` | 같은 이름 + `-dlq` |
| `guardbench-dev-gb-workitems` | `TestExecutionRequested` | 같은 이름 + `-dlq` |
| `guardbench-dev-gb-run-finalize` | `TestExecutionCompleted` | 같은 이름 + `-dlq` |

세 Source Queue는 Standard, SSE-SQS, visibility timeout `30초`, retention `4일`, receive wait time `20초`, delivery delay `0초`다. 각 DLQ는 retention `14일`, `maxReceiveCount=5`이며 자기 Source ARN만 redrive를 허용한다.

현재 Resolve/Finalize consumer는 한 번에 최대 10개를 받고 순차 처리하며, WorkItems consumer는 `GUARDBENCH_WORKER_WORK_ITEMS_CONCURRENCY`로 bounded executor의 최대 in-flight 수를 제한한다. WorkItems는 가용 slot만큼만 `ReceiveMessage`를 요청하고, 실제 수신한 메시지의 처리·ACK가 끝나거나 실패한 뒤 slot을 반환한다. 따라서 설정값이 2/4여도 scheduler pool 확장만으로 동시성을 만들지 않으며, WorkItems 외 queue의 처리 계약은 변하지 않는다. 수신 시점부터 실제 처리 시작까지의 대기는 `workerDispatchWaitMs`로 기록하고, `queueWaitMs`는 기존 SQS 전송 시각부터 배치 수신 시각까지의 의미를 유지한다.

종료 시 WorkItems 신규 수신과 제출을 중단하고 `GUARDBENCH_WORKER_WORK_ITEMS_SHUTDOWN_TIMEOUT_SECONDS`만큼 in-flight 작업을 기다린다. 제한 시간 안에 끝난 작업만 기존 ACK 규칙을 적용하며, 미완료 작업은 조기 ACK하지 않아 SQS visibility 만료 후 재전달될 수 있다.

배포 후에는 startup 로그의 `WorkItems worker 동시성 설정을 적용합니다`에서 `concurrency`와 `shutdownTimeoutSeconds`를 확인한다. 이후 WorkItem 처리 시작 로그의 `configuredConcurrency`, `currentInFlight`, `workerDispatchWaitMs`를 Logs Insights에서 `testRunId`·`snapshotId`와 함께 조회해 설정 상한 초과가 없는지, 수신 후 대기가 별도 기록되는지 확인한다.

현재 consumer는 한 번에 최대 10개를 받고, 결과 DB commit 뒤 `DeleteMessage`한다. nack에는 `ChangeMessageVisibility`를 호출하지 않으므로 ADR 0005의 약 5초 조기 재시도 대신 현재 visibility가 끝나는 약 30초 뒤 재전달된다. 이는 최초 배포의 알려진 복구 지연으로 수용하고, 15초 Provider timeout보다 visibility를 짧게 낮추지는 않는다.

Outbox Publisher는 같은 Task 안에서 1초 fixed delay, batch 10으로 `SendMessageBatch`를 호출한다. 실패 항목은 `PENDING`에 남아 다음 1초 주기에 다시 시도되며 현재 구현에는 지수 backoff가 없다. `PUBLISHED` 자동 정리도 하지 않는다.

## IAM

### Task execution role

- ECR image pull과 CloudWatch Logs 전송에 `AmazonECSTaskExecutionRolePolicy`를 사용한다.
- RDS managed secret 하나에만 `secretsmanager:GetSecretValue`를 허용한다.
- customer-managed KMS key를 선택한 경우에만 해당 key의 `kms:Decrypt`를 추가한다.

### Application task role

단일 Task가 현재 모든 역할을 수행하므로 다음 권한을 하나의 role에 부여한다. Application 호출 주체는 API key가 아니라 ECS task role의 IAM credentials다.

- 세 Source Queue ARN: `sqs:SendMessage`, `sqs:ReceiveMessage`, `sqs:DeleteMessage`
- SageMaker classifier endpoint 하나: `sagemaker:InvokeEndpoint`

  - Role name: `guardbench-dev-app-task-role`
  - Resource: `arn:aws:sagemaker:ap-northeast-2:<account-id>:endpoint/guardbench-qwen3-4b-endpoint`
  - `Resource="*"`와 AdministratorAccess principal을 application 호출 경로에 사용하지 않는다.


Queue URL을 직접 주입하므로 `sqs:GetQueueUrl`과 `Resource="*"`는 사용하지 않는다. DLQ에 애플리케이션이 직접 쓰거나 읽는 권한도 주지 않는다. AWS access key는 image, Task 환경변수와 GitHub 저장소에 두지 않는다.

## 관측성과 alarm

현재 애플리케이션은 JSON logging과 custom CloudWatch metric을 내보내지 않는다. 최초 배포는 awslogs의 기존 text log와 AWS native metric만 사용한다.

| 대상 | Alarm |
| --- | --- |
| 각 DLQ | `ApproximateNumberOfMessagesVisible >= 1`, 1분 1회 |
| resolve/finalize Queue | `ApproximateAgeOfOldestMessage >= 120초`, 1분 3회 |
| work-items Queue | `ApproximateAgeOfOldestMessage >= 300초`, 1분 3회 |
| ECS | `RunningTaskCount < 1`, 1분 2회; CPU 또는 memory 85% 이상, 5분 3회 |
| ALB | `UnHealthyHostCount >= 1`, 1분 2회; healthy host 0, 1분 2회 |
| RDS | free storage 10 GiB 미만, CPU 80% 이상 또는 free memory 256 MiB 미만 |
| Publisher log | `Unexpected error publishing pending outbox events` 1건 이상 |
| Worker log | `Unexpected error polling` 또는 `Malformed message` 1건 이상 |

Alarm은 `guardbench-dev-ops` SNS Topic으로 보내고 실제 수신자가 subscription을 확인해야 한다. ECS Container Insights는 `RunningTaskCount`와 운영 dashboard를 위해 켠다.

`OutboxOldestPendingAgeSeconds`, `OutboxPendingCount`, TestRun 체류 시간은 현재 코드가 metric을 내보내지 않아 CloudWatch alarm으로 만들 수 없다. 최초 배포에서는 아래 SQL을 배포 직후와 장애 대응 시 수동 확인하고, custom metric 또는 별도 probe 구현은 후속 관측성 Issue로 넘긴다.

```sql
select count(*) as pending_count,
       extract(epoch from (current_timestamp - min(created_at))) as oldest_pending_seconds
from outbox_event
where status = 'PENDING';
```

이 query는 V2 migration의 `outbox_event(status, created_at)` 계약과 일치한다. 입력, Application response, EvaluationResult, classifier prompt 전문, classifier provider 응답, secret과 SDK 예외의 credential 값은 로그에 남기지 않는다.

## SageMaker Response Behavior Classifier 운영

classifier는 `(TestCase prompt, ApplicationResponse)`를 SageMaker Real-Time endpoint에 전달하고 `COMPLY` 또는 `REFUSE`만 받는다. 정상 응답의 `choices[0].message.content`가 정확히 `COMPLY`이면 `ALLOW`, 정확히 `REFUSE`이면 `BLOCK`으로 정규화한다. 빈 `choices`·`message`·`content`, 그 외 문자열, SageMaker provider failure는 임의의 action으로 대체하지 않고 configuration/provider error로 처리한다.

DJL LMI/vLLM endpoint 요청에는 `messages`, `temperature: 0`, `max_tokens: 8`, `chat_template_kwargs.enable_thinking: false`를 반드시 포함한다. Qwen3 thinking mode를 끄지 않으면 응답에 think block이 섞여 parser가 오작동할 수 있다.

현재 endpoint는 `InService`여도 instance-hour 과금이 발생하는 Real-Time Inference다. 비용 절감을 위해 기본 toggle은 `false`이며 endpoint만 삭제한 상태다. model, endpoint configuration, execution role, InvokeEndpoint policy와 PrivateLink는 유지한다.

backend 배포·검증 순서는 다음과 같다.

1. 승인된 `SAGEMAKER_CLASSIFIER_SYSTEM_PROMPT`를 deployment configuration에 설정한다.
2. `sagemaker_classifier_endpoint_enabled=true`로 Terraform을 적용한다.
3. endpoint가 `InService`가 될 때까지 기다린다. 생성에는 약 10분이 걸릴 수 있다.
4. task role과 SageMaker 환경변수를 보존한 최신 ECS task definition으로 backend를 배포한다.
5. 실제 TestRun 한 건으로 `InvokeEndpoint`, `COMPLY/REFUSE` parsing, `ALLOW/BLOCK` normalization을 검증한다.
6. 개발·검증이 끝나면 `sagemaker_classifier_endpoint_enabled=false`로 다시 적용해 endpoint를 삭제한다.

## 기존 IaC에서 반드시 고칠 항목

`guardbench-iac`의 현재 `dev`에 다음 변경이 모두 반영돼야 최초 backend 배포가 가능하다.

1. 기존 S3·CloudFront·VPC·subnet·Endpoint·SG·ALB·Target Group을 state와 대조하고 import 또는 보존한다. 신규 resource로 교체하지 않는다.
2. CloudFront API origin을 `http-only`로 고치고 API cache/origin request policy를 위 계약대로 설정한다.
3. ALB health path `/health`를 `/api/v1/test-suites`로 바꾸고 port 80 ingress를 CloudFront origin-facing prefix list로 제한한다.
4. RDS PostgreSQL, Secrets Manager Endpoint, SQS Source/DLQ 6개와 redrive policy를 추가한다.
5. 기존 source의 API/Orchestrator/Executor 세 Task Definition과 Service를 단일 `app` 정의로 바꾸고, 실제로 존재하지 않는 ECS resource를 신규 생성한다.
6. Task Definition에 RDS secret, Queue URL, SageMaker classifier 환경변수와 endpoint name을 넣고 execution/application role을 실제 ARN으로 제한한다.
7. SageMaker model·endpoint configuration·execution role을 유지하고 `sagemaker_classifier_endpoint_enabled`로 billable Real-Time endpoint만 제어한다.
8. `latest` image와 mutable ECR tag를 제거하고 배포할 image SHA로 새 Task Definition revision을 만든다.
9. CloudWatch alarm, log metric filter와 SNS Topic을 추가한다.

기존 Terraform에는 RDS·SQS가 없고 private subnet에 default route도 없으므로, 이 변경 없이 ECS Service부터 만들면 application startup 또는 메시지 처리가 실패한다.

## 최초 배포 순서와 통과 조건

1. Terraform state backup, `state list`, AWS 실제 resource를 대조하고 기존 network·frontend·ALB resource가 state에 없을 때만 import한다.
2. `terraform fmt`, `validate`, `plan` 결과에서 기존 VPC, subnet, Endpoint, SG, S3, CloudFront, ALB와 Target Group의 삭제·교체가 없음을 확인한다.
3. 기존 네트워크 rule과 ALB·CloudFront behavior를 수정하고 RDS, Queue, Secret Endpoint, IAM, ECR을 신규 생성한다.
4. backend `dev`의 `clean check bootBuildImage`가 성공한 commit SHA image를 ECR에 push한다.
5. 해당 image를 가리키는 단일 Task Definition과 ECS Service를 배포한다. Flyway current V1 성공과 ALB healthy target 1개를 확인한다.
6. CloudFront URL에서 `GET /api/v1/test-suites`가 200이고, create/update API의 JSON body와 `Idempotency-Key`가 전달되는지 확인한다.
7. TestRun 한 건을 접수해 `resolve → workitems → finalize`, SageMaker classifier 호출, terminal DB 결과, Source Queue 감소와 DLQ 0을 확인한다.
8. Publisher/Worker log metric filter와 SNS test alarm이 실제 수신되는지 확인한다.

프론트 정적 asset을 다시 배포할 때는 3시간 유효한 임시 AWS credential을 갱신한 뒤 기존 workflow를 실행한다. GitHub OIDC role 전환은 자격 증명 갱신을 자동화하는 후속 개선이며 backend 최초 배포의 차단 조건이 아니다.

## 의도적으로 보류한 항목

- Orchestrator/Executor 별도 ECS Service와 역할별 IAM
- Queue backlog 기반 Service Auto Scaling과 scale-to-zero
- custom domain, ACM을 이용한 CloudFront-to-ALB HTTPS
- prod stack 생성과 multi-account 분리
- JSON structured logging과 application custom metric
- Outbox cleanup, 지수 backoff, 약 5초 조기 재전달과 stale TestRun reconciler
- backend/frontend 완전 자동 배포 workflow
- Multi-AZ RDS, NAT Gateway와 multi-region DR

이 항목은 최초 dev 배포를 막지 않는다. 역할 선택기나 application metric처럼 코드 변경이 필요한 항목을 Terraform만으로 구현한 것으로 간주하지 않는다.

## 참고 자료

- [Amazon ECS task definition parameters](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html)
- [Amazon ECS task execution IAM role](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html)
- [CloudFront managed origin request policies](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-managed-origin-request-policies.html)
- [CloudFront managed prefix list](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/LocationsOfEdgeServers.html#managed-prefix-list)
- [Amazon RDS managed master password](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-secrets-manager.html)
- [Amazon SQS visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html)
- [Amazon SQS dead-letter queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html)
