# 핵심 도메인 모델

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-30
> Canonical source: GitHub
> Related: [ADR 0010](../decisions/0010-single-target-test-run-model.md)

## 핵심 객체

| 객체 | 책임 |
| --- | --- |
| `TestSuite` | 관련 TestCase를 묶는 정책 테스트 자산 |
| `TestCase` | 현재 편집 가능한 input, ExpectedResult, severity, category 정의 |
| `TestCaseSnapshot` | TestRun 접수 시 TestCase 이름과 실행 정의를 불변 복제한 실행 기준 |
| `TestRun` | 하나의 불투명 `TargetReference`와 Snapshot 집합의 수명주기 관리 |
| `TargetReference` | Target Context가 소유한 실행 대상을 재식별하는 TestRun 소유 ID VO |
| `TestExecution` | 한 Snapshot을 단일 Target에 실행한 터미널 결과 |
| `AssertionResult` | ExpectedResult와 Target ActualResult의 일치 여부 |
| `QualityGateResult` | 비교 집계 가능 여부와 최종 판정 |

`BaselineTarget`, `CandidateTarget`, `CandidateSource`, `SnapshotExecutionPair`는 현재 모델에 사용하지 않는다. 실시간 comparison과 regression 생성도 단일 Target TestRun의 범위가 아니다.

## 핵심 불변식

- TestCase는 현재 정의만 보유하고 과거 실행 기준은 Snapshot이 보존한다.
- TestCase 삭제는 논리 삭제이며 기존 Snapshot과 실행·판정 결과에 전파하지 않는다.
- 하나의 TestRun은 단일 `TargetReference`만 보유하고, TestCase당 Snapshot 하나와 Snapshot당 TestExecution 하나만 생성한다.
- TestRun은 provider type, 외부 identifier, revision, DRAFT lifecycle을 소유하지 않는다. 이 값은 Target Context가 소유하고 Integration Adapter가 매핑한다.
- DRAFT 준비가 필요한 Target은 `PREPARING`에서 불변 revision으로 고정한 뒤 실행한다.
- Target ActualResult가 있으면 ExpectedResult와 비교해 AssertionResult를 생성하고, 실패·timeout·미시작이면 생성하지 않는다.
- 단일 Target 실행에서 `ChangeResult`를 새로 생성하지 않는다. 비교 가능한 변화 결과가 없으므로 Quality Gate는 `NOT_EVALUATED`, metrics는 `null`이다.
- 실행 오류, Assertion FAIL, Quality Gate `NOT_EVALUATED`는 서로 다른 상태다.
- TestRun 수명주기는 `QUEUED → PREPARING → RUNNING → FINISHED`이며 `FINISHED`만 터미널 상태다.
- Snapshot의 단일 TestExecution이 터미널 상태에 도달하면 해당 Snapshot을 처리 완료로 계산한다.
- `COMPLETED`는 모든 실행의 정상 완료, `INCOMPLETE`는 성공과 실패·timeout의 혼재, `ERROR`는 의미 있는 실행 결과를 만들지 못한 종료를 의미한다.
- `SUCCEEDED`일 때만 ActualResult가 있고 `FAILED`, `TIMED_OUT`, `NOT_STARTED`에는 ActualResult가 없다.
- AWS SDK 요청·응답은 `target/infrastructure/bedrock`에서 소비자 소유 Target Port의 scalar/value 계약으로 변환한다.

## 수명주기

```text
TestSuite + TestCase
        ↓ TestRun 요청(target)
QUEUED: TargetReference + TestCaseSnapshot + OutboxEvent(v2)
        ↓ Worker 점유
PREPARING: TargetPreparationPort → Target revision 고정
        ↓
RUNNING: Snapshot당 단일 TestExecution
        ↓ normalize
ActualResult → AssertionResult
        ↓
QualityGateResult(NOT_EVALUATED)
        ↓
FINISHED
```
