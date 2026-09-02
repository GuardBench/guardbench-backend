# 에이전트 코드 리뷰

> Status: APPROVED
> Owner: Team
> Last reviewed: 2026-09-02
> Canonical source: GitHub
> Origin: PR #20·#23 리뷰 사례, Issue #33 및 PR #77 회귀 개선 사례

리뷰의 목적은 중요한 결함을 빠르게 찾고 사람이 승인 여부를 판단하게 돕는 것이다. 리뷰 요청만 받았다면 파일 수정, commit, push, 승인과 병합을 하지 않는다.

## 검토 순서

1. PR의 Issue, base/head, 변경 파일과 완료 조건을 확인한다.
2. 변경과 직접 관련된 APPROVED 계약, 테스트와 공개 코드를 확인한다. DRAFT는 확정 근거로 사용하지 않는다.
3. 실제 diff를 기준으로 계약 회귀, 데이터 손실, 보안, 동시성·트랜잭션, 주요 기능 오류를 먼저 찾는다.
4. SQS, Outbox, Worker, 외부 Provider 등 비동기/retry 변경이 있으면 retry 계층 중복, idempotency, transient/permanent failure 구분, retry exhaustion의 terminal convergence, timeout/visibility/lease 관계를 함께 검토한다.
5. 리뷰 단위 커밋(Domain/Port, Adapter, Test, Docs)이 논리적으로 분리되어 있는지 확인한다.
6. 테스트가 결함을 잡는지 확인하고, 실행하지 못한 검증은 남은 위험으로 기록한다.
7. 스타일과 선호는 정확성 문제와 분리한다.

PR 설명이나 주석만 믿지 않는다. 결함으로 단정하려면 재현 가능한 동작, 코드 경로, 테스트 또는 계약 근거가 있어야 한다. 가능성만 있는 우려는 `질문` 또는 `미검증 위험`으로 표시한다.

### 비동기/retry 리뷰 지침

- delivery retry와 business retry가 같은 counter/state로 결합되어 있지 않은지 확인한다.
- 동일 작업의 재시도가 side effect를 중복시키지 않도록 idempotency 경계가 있는지 확인한다.
- 여러 계층이 같은 실패를 동시에 retry해 호출량이나 메시지 수를 증폭시키지 않는지 확인한다.
- retryable failure가 bounded되어 있고 최종 시도 이후 `FAILED`, `TIMED_OUT` 등 terminal 상태로 수렴하는지 확인한다.
- 정상 중간 상태(`AlreadyHeld`, partial finalization 등)가 NACK/retry/DLQ 사유로 잘못 해석되지 않는지 확인한다.
- timeout, SQS visibility timeout, claim lease처럼 서로 영향을 주는 값은 개별 설정이 아니라 cross-component contract로 검토한다.
- 단순한 retry 횟수 증가 또는 `maxReceiveCount` 증가만으로 원인을 덮지 않았는지 확인한다.
- 개별 상태 테스트뿐 아니라 duplicate delivery, timeout, claim contention 이후 eventual convergence를 검증하는 테스트가 있는지 확인한다.

상세 배경과 테스트 모델은 [비동기 신뢰성 및 테스트 원칙](../architecture/async-reliability-and-testing.md)을 참고한다. 문서가 `DRAFT`인 동안에는 새로운 구현 요구사항의 단독 근거로 사용하지 않고, 현재 Issue/승인 계약과 함께 판단한다.

### 문맥 경계 지침 (PR #77 회귀 예시 반영)

- **리뷰 작성 및 답변 시 현재 PR/Issue 범위에 집중한다.**
- 현재 PR과 무관한 다른 이슈, 다른 worktree, 다른 커밋을 불필요하게 대화에 언급하여 문맥 혼동을 일으키지 않는다. (예: PR #77 리뷰 논의 중 #15 로컬 커밋을 무관하게 언급했던 사례 방지)
- 연관성이 필요한 경우 현재 PR과의 관계 및 선후관계를 먼저 명시한 후 언급한다.

## 심각도와 차단 여부

심각도는 영향 크기이고, 차단 여부는 병합 전에 고쳐야 하는지다. 둘을 별도로 표시한다.

| 등급 | 기준 | 기본 처리 |
| --- | --- | --- |
| P0 | 보안 사고, 데이터 손실, 서비스 전체 장애 | Blocking |
| P1 | 공개 계약 회귀, 주요 기능 오류, 잘못된 권한·트랜잭션·동시성 | Blocking |
| P2 | 제한된 조건의 실제 오류 또는 완료 조건 누락 | 영향에 따라 Blocking 여부 명시 |
| P3 | 사소한 품질·가독성·유지보수 개선 | Non-blocking |

`Blocking`은 승인 전에 수정해야 한다는 뜻이다. 취향 차이, 더 큰 리팩터링 선호와 Issue 범위 밖 개선에는 사용하지 않는다.

## 발견 사항 작성법

발견 하나에는 다음 네 가지만 쓴다.

```text
[P1][Blocking] 짧은 제목 — path/to/File.java:42
어떤 입력이나 상태에서 무엇이 잘못되는지 설명한다.
근거가 되는 계약 또는 테스트를 연결하고 최소 수정 방향을 제시한다.
```

- 결론부터 쓰고 발견 하나는 제목을 제외해 최대 3문장으로 제한한다.
- 정확한 위치가 있으면 inline comment를 사용한다. 요약에서 같은 설명을 반복하지 않는다.
- 계약 원문, diff와 로그를 길게 복사하지 않고 필요한 경로나 값만 인용한다.
- 관련 문제는 하나로 묶고, 서로 다른 원인이나 수정 방법이면 나눈다.
- 해결책 전체를 대신 설계하지 않는다. 안전한 최소 수정 방향만 제시한다.
- 칭찬, 검토 과정, 읽은 파일 목록과 AI 사용 고지는 발견 사이에 넣지 않는다.

## 리뷰 결과 형식

항상 권고 상태부터 쓴다. 요약은 발견 본문을 제외하고 12줄 안팎으로 유지한다. 아래 상태는 리뷰 결과 표기이며 에이전트가 GitHub 승인 버튼을 누른다는 뜻이 아니다.

```text
Changes requested — Blocking 2건

- [P1] Idempotency-Key 필수 여부가 OpenAPI와 다릅니다.
- [P1] TestRun 요청에 사용자가 Evaluator provider를 직접 지정하는 필드가 추가됐습니다.

미검증: PostgreSQL 통합 테스트는 실행하지 못했습니다.
```

- `Approve`: Blocking 발견 없음
- `Comment`: 질문이나 Non-blocking 제안만 있음
- `Changes requested`: Blocking 발견이 하나 이상 있음

발견이 없으면 장문의 검토 보고서를 만들지 않는다.

```text
Approve — Blocking 발견 없음.
관련 테스트는 통과했습니다. 실제 PostgreSQL 동시성은 이번 리뷰에서 검증하지 못했습니다.
```

## 리뷰 답변

리뷰 반영 답변은 수정 내용, commit, 검증 결과만 3문장 이내로 쓴다. 내부 구현 설명은 요청받을 때만 덧붙인다.

```text
`0d0dfd1`에서 설정을 `application.yml`로 옮겼습니다. 동작은 동일하며 전체 테스트 18개가 통과했습니다.
```

반영하지 않았다면 이유와 필요한 결정만 짧게 답한다. 같은 근거로 논쟁을 반복하거나 리뷰 본문 전체를 다시 요약하지 않는다.

## 완료 확인

- 모든 Blocking 발견에 위치, 영향과 근거가 있는가
- 검증된 결함과 질문·미검증 위험을 구분했는가
- Issue 범위 밖 개선을 승인 조건으로 만들지 않았는가
- inline과 요약을 중복하지 않았는가
- 발견이 없어도 미검증 영역을 숨기지 않았는가
- 비동기/retry 변경에서 idempotency, retry amplification, terminal convergence, timeout/lease/visibility 관계를 검토했는가
- 현재 PR/Issue 범위를 벗어난 무관한 이슈나 커밋을 언급하지 않았는가
