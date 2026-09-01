# 에이전트 개발 워크플로

> Status: APPROVED
> Owner: Team
> Last reviewed: 2026-09-01
> Canonical source: GitHub
> Origin: 현재 팀의 에이전트 운영 논의 및 PR #77 회귀 개선 사례

이 문서는 GitHub Issue를 에이전트 작업으로 안전하게 전달하고 리뷰 가능한 커밋 단위를 유지하는 절차다. 저장소의 상시 규칙은 `AGENTS.md`, 기능별 요구사항은 Issue, 구현 계약은 `docs/`에 둔다.

## 전체 흐름

```text
Issue 작성 → 계약 상태 확인 → 전용 worktree와 branch 생성 → 시작 보고
→ 최소 변경과 검증 → 커밋 전 점검 & 로컬 commit 분리 → 사람 검토 → push 전 검증 보고 → 별도 승인 후 push·PR
```

## 1. Issue 작성

Feature, Bug 또는 Engineering Task 템플릿을 사용한다. 목적, 관련 계약과 상태, 범위, Non-Goals, 검증 가능한 완료 조건, 계약 영향, 예상 검증, 미결정, branch slug와 에이전트 권한을 적는다. 개별 요구사항을 `AGENTS.md`에 복제하지 않는다.

### 문서 우선순위

1. 현재 Issue의 승인된 요구사항과 사용자의 명시적 지시
2. `APPROVED` GitHub 계약
3. 테스트와 현재 공개 코드 계약
4. `DRAFT` GitHub 문서
5. Notion 참고자료

`DRAFT`에 따라 공개 동작이 달라지면 미결정을 Issue에 남기고 중단한다. GitHub와 Notion이 충돌하면 GitHub를 우선하고 차이를 보고한다. 현재 Issue가 기존 계약이나 API를 명시적으로 대체하면 `AGENTS.md`의 [계약 대체와 호환성](../../AGENTS.md#계약-대체와-호환성) 규칙을 따른다.

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

## 3. 작업 요청 및 문맥 경계

에이전트가 Issue를 실제로 읽을 수 있다면 번호, 기준 branch, 권한과 승인 경계만 전달해도 된다. 접근할 수 없다면 목적, 계약, 범위, Non-Goals와 완료 조건도 요청에 포함한다. 번호만 보고 구현하지 않는다.

```text
Issue #42를 구현해줘.
기준 브랜치: dev
에이전트 권한: 검증된 로컬 커밋까지 허용
원격 작업: 금지

Issue와 관련 APPROVED 문서를 먼저 확인해줘.
공개 API, DB, 의존성 또는 아키텍처 변경이 필요하면 멈추고 알려줘.
```

### 문맥 경계 지침

- 작업 및 대화 과정에서 **현재 Issue/PR와 관련 없는 이슈, 다른 worktree, 다른 커밋을 불필요하게 언급하거나 혼동하지 않는다.**
- 다른 이슈나 PR와의 연관성이 꼭 필요하다면, 현재 Issue와의 관계 및 선후관계를 명시한 후 언급한다.

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

## 6. 구현과 커밋 분리 가이드

### 커밋 분리 판단 기준

변경 사항은 리뷰어가 독립적으로 검토할 수 있도록 아래의 **독립 리뷰 단위**로 분리하여 커밋한다:

1. **Domain & Port**: 도메인 엔티티, VO, Domain Service, Inbound/Outbound Port 계약
2. **Persistence Adapter**: Repository 구현체, JPA 엔티티, 데이터베이스 매핑
3. **Integration & Tests**: 통합 테스트, 컨트롤러 테스트, End-to-End 검증 코드
4. **Docs & Config**: 문서(`docs/`), 설정 파일(`application.yml`), CI/CD 템플릿

> **다수 파일 단일 커밋 제한**: 10개 이상의 파일 변경이 부득이하게 하나의 커밋으로 묶여야 할 경우, 커밋을 실행하기 전에 **단일 커밋이 필요한 이유와 리뷰 단위**를 사용자에게 미리 설명해야 한다.

### 커밋 사전 점검 체크리스트

커밋을 실행하기 전 다음 항목을 확인한다:

- [ ] 현재 변경된 파일들이 작업 중인 Issue의 범위와 일치하는가?
- [ ] 현재 Issue와 무관한 다른 worktree나 임의의 파일이 포함되어 있지 않은가?
- [ ] `git status` 및 staged diff가 의도한 검토 단위 파일만 포함하고 있는가?
- [ ] 관련 테스트가 성공하고 코드 정렬이 완료되었는가?

```bash
git add <검토한 단위 파일>
git commit -m "feat(evaluation): SnapshotEvaluation Persistence Adapter 구현"
```

## 7. push 전 검증 및 원격 작업 절차

1. **push 전 사전 검증 보고**:
   - 원격 push를 실행하기 전, 다음 항목을 사용자에게 명시적으로 제시하고 보고한다:
     - 커밋 로그 (`git log -n 5 --oneline`)
     - 파일 통계 (`git diff --stat dev..HEAD` 또는 `git diff --stat origin/dev..HEAD`)
     - Staged/Committed diff 변경 범위
2. **이미 push된 커밋 재구성 가이드**:
   - 원격에 이미 push된 커밋을 재구성/분리해야 할 경우:
     - 현재 branch와 worktree가 격리되어 있는지 확인한다.
     - `--force-with-lease` 옵션의 필요성과 다른 사용자의 작업을 덮어쓸 위험성을 사전에 명확히 안내한다.

## 8. 완료 보고와 사람 검토

변경·비변경 영역, branch와 commit, 검증 결과와 미검증, 계약 영향, 판단과 리뷰 지점을 보고한다. push·PR·병합 여부도 명시한다. 사람은 Issue 완료 조건, 전체 diff, 테스트, 계약 영향과 commit을 확인한 뒤 원격 작업을 승인한다.

## 회귀 방지 예시: PR #77 사례

> **사례 배경 (PR #77 회귀)**:
> - **단일 커밋 묶음 문제**: Issue #14 작업 중 20개 파일 변경(Domain/Port, Persistence Adapter, 통합 테스트, 문서)이 하나의 대형 커밋으로 커밋되어 코드 리뷰 진행이 어려웠음. 이로 인해 이후 4개의 리뷰 단위 커밋(Domain/Port, Persistence, Test, Docs)으로 히스토리를 재구성해야 했음.
> - **문맥 혼동 문제**: PR #77 리뷰 진행 중 현재 PR 범위 밖인 Issue #15의 로컬 커밋을 언급하여 대화 문맥 혼동이 발생함.
>
> **교훈 및 적용 수칙**:
> 1. 20개 파일 등 다수 파일 변경 시 결코 단일 커밋으로 묶지 않고, Domain/Port ➔ Adapter ➔ Test ➔ Docs 로 분리 커밋한다.
> 2. PR/리뷰 대화 시 현재 Issue/PR에 집중하며 무관한 이슈/커밋을 언급하지 않는다.

## 중단 조건

- Issue 내용이나 권한을 확인할 수 없음
- 완료 조건이 충돌하거나 측정할 수 없음
- DRAFT 해석에 따라 공개 동작이 달라짐
- APPROVED API, DB, 도메인 또는 아키텍처 변경이 필요함
- 새 production dependency가 필요함
- 기존 변경과 안전하게 분리할 수 없음
- 범위를 넘는 대규모 수정이 필요함. 현재 Issue가 승인한 계약 대체에 따르는 호출부·테스트 수정은 범위 초과가 아니다.
- secret, 운영 데이터 또는 파괴적인 Git 작업이 필요함
- 범위 밖 원인으로 검증이 실패함

추측하지 말고 현재 상태와 필요한 결정을 보고한다.

## 참고

- [OpenAI Docs: AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md)
- [OpenAI Docs: Worktrees](https://learn.chatgpt.com/docs/environments/git-worktrees)
