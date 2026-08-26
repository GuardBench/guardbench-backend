# Backend CI와 dev Merge Gate

> Status: DRAFT
> Owner: Backend
> Scope: GitHub Issue #82

이 문서는 Backend CI workflow의 실행 범위와 `dev` 병합 차단을 위한 GitHub 관리자 설정 절차를 기록한다. 새 공개 API, DB 계약 또는 도메인 동작을 결정하지 않는다.

## Backend CI workflow

[Backend CI workflow](../../.github/workflows/backend-ci.yml)는 다음 event에서 실행된다.

| Event | 대상 | 실행 명령 |
| --- | --- | --- |
| `pull_request` | base branch가 `dev`인 모든 PR | `./gradlew clean check bootJar --no-daemon` |
| `push` | `dev` | `./gradlew clean check bootJar --no-daemon` |

workflow는 `ubuntu-latest`, Temurin JDK 21, Gradle dependency cache를 사용한다. `check`는 테스트를 포함하며, `bootJar`는 실행 가능한 Spring Boot JAR 패키징을 검증한다. Testcontainers 테스트는 GitHub-hosted Ubuntu runner의 Docker 환경을 사용한다.

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
