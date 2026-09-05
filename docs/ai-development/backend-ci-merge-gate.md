# Backend CI와 dev Merge Gate

> Status: DRAFT
> Owner: Backend
> Scope: GitHub Issue #82

이 문서는 Backend CI workflow의 실행 범위와 `dev` 병합 차단을 위한 GitHub 관리자 설정 절차를 기록한다. 새 공개 API, DB 계약 또는 도메인 동작을 결정하지 않는다.

## Backend CI workflow

[Backend CI workflow](../../.github/workflows/backend-ci.yml)는 다음 event에서 실행된다.

| Event | 대상 | 실행 명령 |
| --- | --- | --- |
| `pull_request` | base branch가 `dev`인 PR | Java source/build 입력 변경 시 `testFast`, `integrationTest`, `bootJar`를 독립 job으로 병렬 실행하고 `verify` aggregate가 모두 요구; 그 외에는 `verify` skip |
| `push` | `dev` | Java source/build 입력 변경 시 `testFast`, `integrationTest`, `bootJar`를 독립 job으로 병렬 실행하고 `verify` aggregate가 모두 요구; 그 외에는 `verify` skip |
| `workflow_dispatch` | `source` | `testFast`, `integrationTest`, `bootJar`를 독립 job으로 실행하고 `verify` aggregate가 모두 요구 |
| `workflow_dispatch` | `infrastructure` | source 검증 없이 infrastructure deploy만 수행 |

`workflow_dispatch`의 `deployment_target`은 `dev` 또는 `performance`다. `push`는 항상 `dev`
환경에만 자동 배포하고, Performance Backend는 수동 실행에서 `deployment_target: performance`를
선택한다. deploy job은 선택된 GitHub Environment의 다음 변수를 사용한다. 값은 repository/org
공통 변수가 아니라 각 Environment에 설정한다.

Performance application revision을 배포할 때는 Actions에서 `Backend CI`를 `dev` branch로
실행하고 `deployment_target: performance`, `deployment_mode: source`를 선택한다. Terraform
인프라 변경을 반영할 때는 같은 target에 `deployment_mode: infrastructure`를 선택한다.

```text
AWS_DEPLOY_ROLE_ARN
AWS_REGION
ECR_REPOSITORY
ECS_CLUSTER
ECS_SERVICE
ECS_CONTAINER_NAME
ECS_TASK_DEFINITION_FAMILY
```

GitHub Environment와 IaC가 맞춰야 하는 Performance 배포 계약은 다음과 같다.

| 항목 | 값 또는 규칙 |
| --- | --- |
| Environment name | `performance` |
| Allowed deployment branch | `dev` |
| `AWS_DEPLOY_ROLE_ARN` | Performance ECS 배포 role ARN |
| `AWS_REGION` | `ap-northeast-2` |
| `ECR_REPOSITORY` | Performance Backend image repository name |
| `ECS_CLUSTER` | Performance ECS cluster name |
| `ECS_SERVICE` | Performance Backend ECS service name |
| `ECS_CONTAINER_NAME` | Performance app container name |
| `ECS_TASK_DEFINITION_FAMILY` | Performance app task-definition family name; ARN이나 revision을 사용하지 않음 |

Performance role의 web identity trust policy는 다음 조건을 모두 허용한다.

```json
{
  "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
  "token.actions.githubusercontent.com:sub": "repo:GuardBench/guardbench-backend:environment:performance"
}
```

위 조건은 `sts:AssumeRoleWithWebIdentity` 허용 statement에 `StringEquals`로 설정한다. `dev`는
별도 Environment와 별도 role을 사용하고 subject는
`repo:GuardBench/guardbench-backend:environment:dev`다. role ARN만 추가하고 trust subject를
갱신하지 않으면 OIDC AssumeRole 단계에서 배포가 실패한다.

Performance 배포는 최신 ACTIVE infrastructure task definition을 base로 사용하며, configured
service가 같은 task-definition family를 사용하지 않으면 등록 전에 실패한다. source 배포에서는
ECR repository가 `IMMUTABLE`인지와 image tag가 전체 Git SHA인지 확인한다.

workflow는 `ubuntu-latest`, Temurin JDK 21, Gradle dependency cache를 사용한다. 변경 범위 감지 대상은 `src/`와 Gradle build/configuration 입력이다. `testFast`는 외부 컨테이너 없는 단위·컨트롤러·계약 테스트를, `integrationTest`는 PostgreSQL·SQS·E2E 테스트를 실행한다. `bootJar`는 실행 가능한 Spring Boot JAR 패키징을 검증한다. Testcontainers 테스트는 GitHub-hosted Ubuntu runner의 Docker 환경을 사용한다. 각 job은 Gradle cache hit와 실행 시간을 Summary에 기록하고, integration job은 Testcontainers image 준비를 포함한 실행 시간을 기록한다. Java source/build 입력이 없는 변경에서는 Gradle 검증 job을 실행하지 않는다.

Gradle의 기존 `test`와 `check` task는 전체 테스트 suite를 실행하는 로컬·호환 경로로 유지한다. CI의 `verify`는 `testFast`, `integrationTest`, `bootJar` 결과를 aggregate하며 세 결과가 모두 성공해야 통과한다. OpenAPI 명세 변경은 별도 `OpenAPI contract` workflow에서 독립적으로 검증된다.

이 workflow가 PR에 표시하는 required check 후보는 다음이다.

```text
Backend CI / verify
```

GitHub UI에 표시되는 이름은 workflow를 처음 실행한 뒤 확인한다. Ruleset에는 UI에 실제로 표시된 이름을 선택한다.

## dev ruleset 관리자 적용

repository administrator는 GitHub repository에서 다음을 적용한다.

1. **Settings → Rules → Rulesets → New branch ruleset**을 연다.
2. target branch pattern을 `dev`로 설정한다.
3. **Require status checks to pass**를 활성화한다.
4. 첫 Backend CI 실행 후 표시된 `Backend CI / verify` check를 required status check로 선택한다.
5. 팀 정책에 따라 pull request 요구, bypass actor, 최신 base branch 요구 여부를 별도로 설정한다. 이 문서는 해당 정책을 결정하지 않는다.
6. ruleset을 저장한 뒤 새 PR에서 Backend CI가 성공하지 않으면 merge control이 차단되는지 확인한다.

기존 branch protection을 사용하는 repository는 동등하게 **Settings → Branches → Branch protection rules**에서 `dev` rule에 같은 required status check를 추가할 수 있다. Ruleset과 legacy branch protection을 중복 적용할 때의 우선순위·bypass 정책은 repository administrator가 확인한다.

## 검증 절차

1. Backend Java 변경이 있는 disposable PR을 열어 `Backend CI / verify`가 자동 실행되고 성공하는지 확인한다.
2. 별도 disposable branch에서 컴파일 오류 또는 고의 실패 테스트를 추가한 PR을 열어 check가 실패하고 merge가 차단되는지 확인한다.
3. 실패 검증 PR은 merge하지 않고 닫은 뒤 disposable branch를 삭제한다.
4. `dev` push에서도 같은 workflow가 실행되는지 Actions history에서 확인한다.

## 권한 제한

현재 `gh-agent` 인증 토큰은 branch protection/ruleset 조회 API에 `403 Resource not accessible by personal access token`을 반환한다. 따라서 workflow 파일의 구현·PR check 관찰은 에이전트가 할 수 있지만, required status check를 실제 `dev` merge gate로 적용하는 관리자 설정은 repository admin 권한이 있는 담당자가 수행해야 한다.
