# 에이전트 개발 워크플로

> Status: APPROVED
> Owner: Team
> Last reviewed: 2026-08-24
> Canonical source: GitHub
> Origin: 현재 팀의 에이전트 운영 논의

이 문서는 GitHub Issue를 에이전트 작업으로 안전하게 전달하는 절차다. 저장소의 상시 규칙은 `AGENTS.md`, 기능별 요구사항은 Issue, 구현 계약은 `docs/`에 둔다.

## 전체 흐름

```text
Issue 작성 → 계약 상태 확인 → 전용 worktree와 branch 생성 → 시작 보고
→ 최소 변경과 검증 → 허용된 경우 로컬 commit → 사람 검토 → 별도 승인 후 push·PR
```

## 1. Issue 작성

Feature, Bug 또는 Engineering Task 템플릿을 사용한다. 목적, 관련 계약과 상태, 범위, Non-Goals, 검증 가능한 완료 조건, 계약 영향, 예상 검증, 미결정, branch slug와 에이전트 권한을 적는다. 개별 요구사항을 `AGENTS.md`에 복제하지 않는다.

### 문서 우선순위

1. 현재 Issue의 승인된 요구사항과 사용자의 명시적 지시
2. `APPROVED` GitHub 계약
3. 테스트와 현재 공개 코드 계약
4. `DRAFT` GitHub 문서
5. Notion 참고자료

`DRAFT`에 따라 공개 동작이 달라지면 미결정을 Issue에 남기고 중단한다. GitHub와 Notion이 충돌하면 GitHub를 우선하고 차이를 보고한다.

## 2. worktree와 branch 준비

Issue 하나당 별도 worktree와 `agent/{issue-number}-{slug}` branch를 사용한다. 기준 branch는 기본적으로 `dev`다.

```bash
git worktree add ../guardbench-issue-42 -b agent/42-create-test-suite dev
cd ../guardbench-issue-42
git status
```

- 기준 작업 폴더의 미커밋 변경을 새 작업에 가져오지 않는다.
- 이미 다른 worktree에서 checkout한 branch를 재사용하지 않는다.
- 에이전트 앱 worktree가 detached HEAD라면 첫 commit 전에 Issue branch를 만든다.
- worktree 제거는 구현 권한이 아니다. 보존할 변경과 원격 반영 여부를 사람이 확인한 뒤 정리한다.

## 3. 작업 요청

에이전트가 Issue를 실제로 읽을 수 있다면 번호, 기준 branch, 권한과 승인 경계만 전달해도 된다. 접근할 수 없다면 목적, 계약, 범위, Non-Goals와 완료 조건도 요청에 포함한다. 번호만 보고 구현하지 않는다.

```text
Issue #42를 구현해줘.
기준 브랜치: dev
에이전트 권한: 검증된 로컬 커밋까지 허용
원격 작업: 금지

Issue와 관련 APPROVED 문서를 먼저 확인해줘.
공개 API, DB, 의존성 또는 아키텍처 변경이 필요하면 멈추고 알려줘.
```

## 4. 에이전트 권한

| Issue 선택값 | 허용 범위 |
| --- | --- |
| 조사와 진단만 허용 | 읽기·검색·보고. 파일 변경 금지 |
| 파일 수정과 검증까지 허용 | 수정과 검증. commit 금지 |
| 검증된 로컬 commit까지 허용 | 수정, 검증, 관련 로컬 commit |

권한이 불분명하면 시작하지 않는다. 어떤 선택값도 push, PR 생성, 병합, force push, 운영 데이터 변경, secret 취급을 허용하지 않는다. 필요하면 사람이 별도로 승인한다.

Decision Issue는 구현 권한을 주지 않는다. `대안 조사 → 팀 결정 → ADR 승인 → 구현 Issue → 구현` 순서를 따른다.

## 5. 작업 시작 보고

파일을 수정하기 전에 branch·worktree·미커밋 상태, Issue의 범위와 Non-Goals, 읽은 계약과 상태, 권한, 예상 변경·검증·미결정을 짧게 보고한다.

## 6. 구현과 검증

1. Issue에 필요한 최소 파일만 변경한다.
2. 승인 경계의 변경이 필요하면 멈추고 이유와 선택지를 보고한다.
3. Issue의 테스트와 위험에 비례한 관련 검증을 실행한다.
4. `git diff`와 `git status`로 예상하지 않은 변경을 확인한다.
5. commit 권한이 있으면 검토한 파일만 stage하고 목적별로 commit한다.

```bash
git add <검토한 파일>
git commit -m "feat(testsuite): 테스트 스위트 생성 API 추가"
```

실패하거나 실행하지 못한 검증은 숨기지 않는다.

## 7. 완료 보고와 사람 검토

변경·비변경 영역, branch와 commit, 검증 결과와 미검증, 계약 영향, 판단과 리뷰 지점을 보고한다. push·PR·병합 여부도 명시한다. 사람은 Issue 완료 조건, 전체 diff, 테스트, 계약 영향과 commit을 확인한 뒤 원격 작업을 승인한다.

## 중단 조건

- Issue 내용이나 권한을 확인할 수 없음
- 완료 조건이 충돌하거나 측정할 수 없음
- DRAFT 해석에 따라 공개 동작이 달라짐
- APPROVED API, DB, 도메인 또는 아키텍처 변경이 필요함
- 새 production dependency가 필요함
- 기존 변경과 안전하게 분리할 수 없음
- 범위를 넘는 대규모 수정이 필요함
- secret, 운영 데이터 또는 파괴적인 Git 작업이 필요함
- 범위 밖 원인으로 검증이 실패함

추측하지 말고 현재 상태와 필요한 결정을 보고한다.

## 참고

- [OpenAI Docs: AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md)
- [OpenAI Docs: Worktrees](https://learn.chatgpt.com/docs/environments/git-worktrees)
