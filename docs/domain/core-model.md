# 핵심 도메인 모델

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-24
> Canonical source: GitHub
> Origin: [Notion 도메인 모델 정의](https://app.notion.com/p/3c0eeed6b62d81b48c03ed6034440936)

## 핵심 객체

| 객체 | 책임 |
| --- | --- |
| `TestSuite` | 관련 TestCase를 묶는 정책 테스트 자산 |
| `TestCase` | 현재 편집 가능한 입력, ExpectedResult, severity, category 정의 |
| `TestCaseSnapshot` | TestRun 시작 시 TestCase 이름과 실행 정의를 불변 복제한 실행 기준 |
| `TestRun` | 하나의 Baseline/Candidate 검증 실행과 고정된 target을 관리 |
| `TestExecution` | 한 Snapshot을 한 target에 실행한 결과와 실행 오류 |
| `AssertionResult` | ExpectedResult와 Candidate ActualResult의 일치 여부 |
| `ChangeResult` | 비교 가능성 및 Baseline 대비 Candidate 변화 의미 |
| `QualityGateResult` | 집계 지표와 신뢰도를 바탕으로 한 최종 판정 |

`TestCaseRevision`, `ComparisonResult`, `RegressionResult`는 현재 모델에 사용하지 않는다. 변화 판정의 공식 이름은 `ChangeResult`다.

## 핵심 불변식

- TestCase는 현재 정의만 보유한다. 과거 실행 기준은 Snapshot이 보존한다.
- TestCase 삭제는 논리 삭제이며 삭제된 TestCase는 현재 조회와 이후 TestRun에서 제외한다.
- TestCase 삭제는 기존 Snapshot과 실행·판정 결과에 전파하지 않는다.
- Snapshot은 실행 당시 TestCase의 name, input, ExpectedResult, severity, category를 보존한다.
- 하나의 TestRun에는 TestCase당 Snapshot이 하나만 존재한다.
- Baseline과 Candidate 실행은 같은 Snapshot을 참조한다.
- Candidate DRAFT는 직접 실행하지 않고 numbered version으로 materialize한다.
- Snapshot 집합과 resolved target은 실행 전에 고정하며 이후 변경하지 않는다.
- Candidate ActualResult가 없으면 AssertionResult를 생성하지 않는다.
- 두 ActualResult가 모두 없거나 실행 기준이 다르면 ChangeResult를 생성하지 않는다.
- `COMPARABLE`인 ChangeResult에는 `changeType`이 있고, `NOT_COMPARABLE`에는 없다.
- 실행 오류, Assertion FAIL, 비교 불가, Quality Gate FAIL은 서로 다른 상태다.
- `TestRunExecutionOutcome`과 `QualityGateStatus`를 분리한다.
- AWS SDK 응답은 Adapter/Normalizer에서 Core의 `ActualResult`로 변환한다.
- TestRun 수명주기 상태는 `QUEUED → PREPARING → RUNNING → FINISHED`이며 `FINISHED`만 터미널 상태다.
- `QUEUED`는 접수 트랜잭션 완료, `PREPARING`은 Candidate materialization과 실행 대상 고정, `RUNNING`은 테스트 실행 중을 의미한다.
- Snapshot의 Baseline과 Candidate 처리가 모두 터미널 상태가 되면 해당 Snapshot을 처리 완료로 계산한다.
- 더 이상 재시도하지 않는 실행 실패와 timeout도 진행률의 처리 완료에는 포함한다.
- 진행률은 처리 완료 비율이며 실행 성공률이 아니다.
- `COMPLETED`는 필요한 모든 실행의 정상 완료, `INCOMPLETE`는 일부 성공과 일부 실패·timeout, `ERROR`는 의미 있는 실행 결과를 만들지 못한 종료를 의미한다.
- `INCOMPLETE`여도 계산 가능한 Metric이 있으면 QG를 평가할 수 있고, 평가할 데이터가 없으면 `NOT_EVALUATED`다.
- TestExecution의 항목별 터미널 결과는 `SUCCEEDED`, `FAILED`, `TIMED_OUT`, `NOT_STARTED`로 구분한다.
- `SUCCEEDED`일 때만 ActualResult가 있으며 실패·timeout·미시작 상태에는 ActualResult가 없다.
- 실패 TestCase를 TestRun 내부의 별도 배열로 중복 저장하지 않고 Snapshot별 Execution·Assertion·Change 결과에서 조회한다.

## 수명주기

```text
TestSuite + TestCase
        ↓ TestRun 요청
QUEUED: TestCaseSnapshot + OutboxEvent
        ↓ Worker 점유
PREPARING: Candidate DRAFT materialization + Target 고정
        ↓
RUNNING: Baseline/Candidate TestExecution
        ↓ normalize
ActualResult
        ├─ Candidate AssertionResult
        └─ ChangeResult (비교 자료가 있을 때)
                ↓
        QualityGateResult
                ↓
              FINISHED
```
