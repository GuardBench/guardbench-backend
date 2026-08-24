# Codex 개발 워크플로

> Status: APPROVED
> Owner: Team
> Last reviewed: 2026-08-24
> Canonical source: GitHub
> Origin: 현재 팀의 Codex 운영 논의
> AI assistance: 이 문서의 초안은 LLM의 도움으로 작성되었으며 팀의 검토와 승인을 거쳤습니다.

이 문서는 **개발자가 GitHub Issue를 Codex의 구현 작업으로 안전하게 전달하는 방법**을 설명한다. 아래 양식은 Codex가 자동으로 읽는 설정 파일이 아니라, 개발자가 새 Codex 작업을 시작할 때 보내는 요청 예시다.

## 1. 문서와 요청의 역할

| 구분 | 역할 | 작성 시점 |
| --- | --- | --- |
| `AGENTS.md` | 모든 작업에 반복 적용할 안전 규칙과 문서 탐색 기준 | 저장소 운영 규칙이 바뀔 때 |
| `docs/` 계약 문서 | API, 도메인, 아키텍처, 컨벤션의 구현 기준 | 팀 결정이 승인되거나 변경될 때 |
| GitHub Issue | 한 작업의 목적, 범위, Non-Goals, 완료 조건, 미결정 사항 | 구현을 시작하기 전 |
| Codex 작업 요청 | 어떤 Issue를 지금 수행할지와 이번 실행의 권한 범위를 전달 | 새 Codex 작업을 시작할 때 |

`AGENTS.md`에 개별 기능 요구사항을 계속 추가하지 않는다. 기능별 요구사항은 Issue에 두고, 작업 요청은 해당 Issue와 계약 문서를 가리킨다. OpenAI 공식 문서가 설명하는 것처럼 `AGENTS.md`는 저장소 범위에 적용되는 지속적인 지침으로 사용한다.

## 2. 기본 원칙

- 한 Issue는 한 Codex 작업, 한 worktree, 한 브랜치에서 처리한다.
- 브랜치 이름은 `codex/{issue-number}-{short-description}`이다.
- Codex는 Issue에 명시된 에이전트 권한 안에서 요청된 범위의 최소 변경만 수행한다.
- 사람은 전체 diff와 판단 사항을 검토한 뒤 push와 PR 생성 여부를 결정한다.
- push, PR 생성, 병합, force push, 운영 데이터 변경, secret 취급은 명시적 승인 없이는 수행하지 않는다.
- Codex가 예상하지 못한 판단을 해야 하는 상황에서는 추측으로 진행하지 않고 중단 조건을 적용한다.

## 3. Issue 준비

개발자는 Codex를 실행하기 전에 Feature, Bug 또는 Engineering Task Issue를 작성한다. Issue에는 최소한 다음 내용이 있어야 한다.

- 목적: 작업이 제공해야 하는 결과
- 관련 GitHub 계약 문서
- 구현 범위
- Non-Goals
- 구체적인 완료 조건
- 공개 API, 도메인, DB, 의존성 변경 여부
- 예상 테스트
- 미결정 사항
- 사람 승인이 필요한 행동
- 에이전트 작업 권한

`완료 조건: 기능이 잘 동작한다`처럼 판단 기준이 모호하면 구현을 시작하지 않는다. 다음처럼 관찰하거나 검증할 수 있게 작성한다.

```text
- [ ] 유효한 요청은 HTTP 201과 TestSuiteCreateRes를 반환한다.
- [ ] 필수 필드가 없으면 VALIDATION_ERROR를 반환한다.
- [ ] 관련 단위 테스트와 통합 테스트가 통과한다.
```

## 4. 문서 상태 확인

1. `docs/README.md`에서 관련 문서와 상태를 확인한다.
2. `APPROVED` 문서는 구현 계약으로 적용한다.
3. `DRAFT`는 참고할 수 있지만 확정 요구사항으로 간주하지 않는다.
4. DRAFT의 해석에 따라 공개 동작이 달라지면 미결정을 Issue에 기록하고 구현을 중단한다.
5. GitHub와 Notion이 충돌하면 GitHub를 우선하고 차이를 보고한다.

## 5. worktree와 브랜치 준비

개발자는 Codex 앱에서 Issue 전용 worktree를 선택하거나, 별도 worktree 생성을 Codex에 명시적으로 요청한다. 기존 공유 작업 폴더에 미커밋 변경이 있다면 그 상태에서 새 구현을 시작하지 않는다.

명령줄에서 사람이 직접 만드는 예시는 다음과 같다. `#42`와 경로는 설명을 위한 예시이며 실제 Issue에 맞게 바꾼다.

```bash
git worktree add ../guardbench-issue-42 -b codex/42-create-test-suite dev
cd ../guardbench-issue-42
git status
```

원격의 최신 상태가 필요하다면 worktree 생성 전에 사람이 `git fetch origin`을 실행하거나 Codex에 별도로 허용한다. 이미 존재하는 branch를 새로 만들지 않는다. worktree 제거 전에는 변경이 커밋됐거나 안전하게 보존됐는지 확인한다.

```bash
git worktree list
git status
```

### Codex 앱에서 만드는 경우

1. 새 작업에서 `Worktree`를 선택한다.
2. 미커밋 변경이 없는 기준 브랜치를 선택한다. 현재 팀의 기본값은 `dev`다.
3. Codex가 Issue와 관련 계약을 확인하고 작업 시작 보고를 수행한다.
4. 첫 커밋 전에 앱의 `Create branch here`를 사용한다.
5. `codex/{issue-number}-{short-description}` 브랜치를 만든다.

Codex가 관리하는 worktree는 선택한 기준 브랜치의 커밋에서 시작하며 기본적으로 detached HEAD 상태일 수 있다. 따라서 커밋을 보존할 작업이라면 첫 커밋 전에 Issue 브랜치를 생성해야 한다. 같은 브랜치를 일반 작업 폴더와 worktree에 동시에 checkout하지 않는다. 작업 위치를 옮겨야 할 때는 앱의 handoff 기능을 사용하거나 변경이 안전하게 보존됐는지 사람이 확인한다.

앱이 기준 브랜치의 로컬 변경을 worktree로 가져올 수 있더라도 GuardBench Issue 작업에는 사용하지 않는다. Issue worktree는 미커밋 변경이 없는 기준 브랜치에서 시작해 기존 작업과 새 작업이 섞이지 않게 한다.

Codex 관리 worktree는 임시 작업 공간으로 정리될 수 있다. 병합이나 정리 전에 branch, commit, 미커밋 변경, 원격 반영 여부를 확인한다. worktree 제거는 구현 권한에 포함하지 않으며 기본적으로 사람이 수행한다.

자세한 앱 동작은 [OpenAI 공식 Worktrees 문서](https://learn.chatgpt.com/docs/environments/git-worktrees)를 참고한다.

## 6. 에이전트 작업 권한

Issue 템플릿에서 다음 중 하나를 선택한다. 선택된 권한은 해당 작업에서 Codex가 수행할 수 있는 최대 범위다.

| 권한 | 허용 범위 |
| --- | --- |
| 조사와 진단만 허용 — 파일 변경 금지 | 파일 읽기, 검색, 진단과 결과 보고만 허용한다. 파일을 변경하지 않는다. |
| 파일 수정과 검증까지 허용 — 커밋 금지 | 코드·문서 수정과 테스트를 허용한다. 커밋은 만들지 않는다. |
| 검증된 로컬 커밋까지 허용 | 수정, 테스트와 논리적 단위의 로컬 커밋을 허용한다. |

구현 Issue의 권장 기본값은 `검증된 로컬 커밋까지 허용`이다. 다만 실제로 선택된 Issue 권한을 우선한다. 권한이 불분명하면 더 좁은 권한을 추측해 작업하지 말고 사람에게 확인한다.

Issue의 어떤 선택지도 push, PR 생성, 병합, force push, 운영 데이터 변경, secret 취급 또는 파괴적인 Git 작업을 허용하지 않는다. 이러한 작업은 필요한 시점에 사람이 별도로 명시적으로 승인해야 한다.

## 7. Codex 작업 요청은 누가 언제 사용하는가

아래 요청은 **개발자가 Issue 작성과 worktree 준비를 마친 뒤, 새 Codex 작업의 첫 메시지로 전달한다.** `Issue: #42`는 고정 규칙이 아니라 예시다.

작업 요청의 목적은 상세 명세를 두 번 작성하는 것이 아니라 다음 세 가지를 분명히 하는 것이다.

1. 지금 수행할 Issue
2. Codex가 읽어야 할 계약과 변경 가능한 범위
3. Codex가 스스로 결정하지 말고 멈춰야 하는 승인 경계

### Codex가 Issue를 읽을 수 있는 경우

GitHub 연결이나 로컬에 동기화된 Issue 정보가 있어 Codex가 Issue 본문을 실제로 확인할 수 있다면 요청을 짧게 작성할 수 있다.

```text
Issue #42를 구현해줘.

기준 브랜치: dev
에이전트 권한: 검증된 로컬 커밋까지 허용
원격 작업: 금지

Issue와 관련 APPROVED 문서를 먼저 확인하고 작업 시작 보고 후 구현해줘.
새로운 공개 API, DB, 의존성 또는 아키텍처 판단이 필요하면 멈추고 알려줘.
```

Codex가 Issue 번호만으로 내용을 실제 읽었는지 확인하지 않은 채 구현을 시작하게 하지 않는다. 작업 시작 보고에서 Issue의 목적, 범위, Non-Goals와 완료 조건을 요약하도록 요구한다.

### Codex가 Issue를 읽을 수 없는 경우

Issue 접근이 없거나 확실하지 않다면 Issue 번호만 전달하지 말고 중요한 내용을 작업 요청에 포함한다.

```text
Issue: #42
목적: TestSuite 생성 API 구현

관련 계약:
- docs/api/openapi.yaml
- docs/domain/core-model.md

구현 범위:
- POST /api/v1/test-suites
- 해당 유스케이스에 필요한 계층 구현
- 관련 단위 테스트와 통합 테스트

Non-Goals:
- TestSuite 목록, 상세, 수정 API
- 기존 DB 계약 재설계
- OpenAPI 계약 변경

완료 조건:
- 유효한 요청은 계약된 HTTP 201 응답을 반환한다.
- Validation 실패는 계약된 오류 Envelope를 반환한다.
- 관련 테스트를 실행하고 결과를 보고한다.
- 검증하지 못한 항목을 명시한다.

승인 경계:
- 공개 API, DB 계약, 의존성 또는 아키텍처 변경이 필요하면 임의로 변경하지 않는다.
- 필요한 변경과 이유를 보고하고 승인을 받을 때까지 중단한다.

Git과 원격 작업:
- codex/42-create-test-suite 브랜치와 Issue 전용 worktree를 사용한다.
- 에이전트 권한은 `검증된 로컬 커밋까지 허용`이다.
- 검증된 변경을 논리적 단위로 로컬 커밋한다.
- push, PR 생성, 병합, force push는 하지 않는다.
```

### 요청 항목을 해석하는 기준

| 항목 | Codex에 전달하는 의미 |
| --- | --- |
| Issue | 작업 추적 단위. 번호 자체보다 실제 본문 확인이 중요하다. |
| 목적 | 구현이 달성해야 할 사용자·시스템 결과다. |
| 관련 계약 | 구현 전 반드시 읽어야 할 GitHub 기준 문서다. |
| 구현 범위 | 변경해도 되는 기능과 계층의 경계다. |
| Non-Goals | 함께 구현하면 안 되는 인접 기능이다. |
| 완료 조건 | 완료 여부를 판정할 수 있는 관찰·테스트 기준이다. |
| 승인 경계 | 해당 변경이 필요할 때 Codex가 중단하고 질문해야 한다는 뜻이다. |
| Git과 원격 작업 | 기준 branch, worktree, 커밋 권한과 원격 변경 금지를 명시한다. |

`승인 필요: 공개 계약 변경`처럼 명사만 나열하지 않는다. `변경이 필요하면 임의로 진행하지 말고 중단한 뒤 승인을 요청한다`처럼 Codex가 취할 행동까지 적는다.

## 8. Codex의 작업 시작 보고

Codex는 파일을 수정하기 전에 다음을 짧게 보고해야 한다.

- 현재 branch와 worktree가 Issue 규칙에 맞는지, detached HEAD라면 첫 커밋 전 생성할 branch
- 미커밋 변경이 있는지
- 확인한 Issue의 목적, 구현 범위, Non-Goals
- 읽은 APPROVED/DRAFT 계약 문서
- Issue에서 선택된 에이전트 작업 권한
- 예상 변경 영역과 검증 방법
- 구현 전에 해결해야 할 미결정 또는 승인 사항

이 보고가 Issue와 다르면 개발자는 구현 전에 범위를 바로잡는다.

## 9. 구현, 검증과 커밋

1. Issue 범위에 필요한 최소 파일만 변경한다.
2. 승인 경계에 해당하는 변경이 필요하면 구현을 멈춘다.
3. Issue에 적힌 테스트와 변경 위험에 비례한 관련 테스트·정적 검증을 실행한다.
4. `git diff`와 `git status`로 예상하지 않은 변경을 확인한다.
5. 커밋 권한이 있을 때만 한 목적씩 [커밋 컨벤션](../conventions/commits.md)에 맞춰 로컬 커밋한다.

```bash
git add <검토한 파일>
git commit -m "feat(testsuite): 테스트 스위트 생성 API 추가"
```

`git add .`보다 검토한 파일이나 경로를 명시한다. 하나의 커밋에는 하나의 논리적 목적만 담는다. 검증 실패나 미검증 항목은 완료로 숨기지 않고 작업 결과에 남긴다.

## 10. Codex의 작업 완료 보고

Codex는 작업을 마칠 때 다음을 보고한다.

- 변경한 내용과 변경하지 않은 영역
- 현재 branch와 권한에 따라 생성한 로컬 커밋
- 실행한 테스트·검증 명령과 결과
- 실패하거나 실행하지 못한 검증
- 공개 API, 도메인, DB, 의존성 계약 변경 여부
- 구현 과정에서 내린 판단과 사람이 집중 검토할 부분
- push, PR 생성, 병합을 수행하지 않았다는 확인

로컬 커밋이 없거나 테스트를 실행하지 못했다면 그 이유를 명시한다. 단순히 `완료했습니다`라고만 보고하지 않는다.

## 11. 사람 검토와 원격 반영

사람은 Issue 완료 조건, 전체 diff, 테스트 결과, 계약 변경 여부, 커밋 구성과 Codex의 판단을 검토한다. 승인 후에만 push와 PR을 수행한다. PR에는 저장소의 Pull Request 템플릿을 사용한다. 병합 후 worktree는 보존할 변경이 없는지 다시 확인한 뒤 정리한다.

## 12. Decision Issue

Decision Issue는 구현 권한을 부여하지 않는다. 제안 단계의 내용은 승인된 계약이 아니며 다음 흐름으로 처리한다.

```text
대안 조사
→ 팀 결정
→ ADR 작성과 승인
→ 별도의 Feature, Bug 또는 Engineering Task Issue 생성
→ 구현
```

Codex는 Decision Issue의 제안만을 근거로 공개 코드나 계약을 변경하지 않는다.

## 13. 중단 조건

다음 중 하나라도 해당하면 Codex는 추측으로 진행하지 않고, 현재 상태와 필요한 결정을 보고한 뒤 중단한다.

- DRAFT 문서의 해석에 따라 공개 동작이 달라지는 경우
- Issue 완료 조건끼리 충돌하거나 측정할 수 없는 경우
- APPROVED API, DB, 도메인 또는 아키텍처 계약 변경이 필요한 경우
- 새로운 production dependency가 필요한 경우
- 기존 미커밋 변경과 안전하게 분리할 수 없는 경우
- Issue 범위를 넘어선 대규모 리팩터링이 필요해 보이는 경우
- secret, 운영 데이터 또는 파괴적인 Git 작업이 필요한 경우
- 테스트 실패 원인이 현재 Issue 범위 밖에 있으며 안전하게 해결할 수 없는 경우
- Issue 본문이나 선택된 에이전트 권한을 실제로 확인할 수 없는 경우

중단은 실패가 아니다. 사람이 생각하지 못한 요소를 Codex가 임의로 결정하지 않도록 하는 정상적인 통제 절차다.

## 참고

- [OpenAI Docs: AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md)
- [OpenAI Docs: Prompting](https://learn.chatgpt.com/docs/prompting)
