# TestRun CloudWatch Logs Insights 조회 가이드

> Status: DRAFT
> Owner: Backend
> Last reviewed: 2026-09-04
> Canonical source: GitHub
> Related: [Issue #169](https://github.com/GuardBench/guardbench-backend/issues/169), [Issue #172](https://github.com/GuardBench/guardbench-backend/issues/172), [비동기 신뢰성 및 테스트 원칙](async-reliability-and-testing.md)

이 문서는 하나의 `testRunId`에 대해 API/DB를 조회하지 않고 CloudWatch application log(`/ecs/guardbench-dev/app`)만으로 TestRun 시작부터 Quality Gate 최종화까지 전체 흐름을 재구성하는 CloudWatch Logs Insights 쿼리를 정리한다.

## 사용 방법

1. 조사할 `testRunId`를 확인한다.
2. 아래 쿼리의 `<TEST_RUN_ID>`를 실제 값으로 바꿔 CloudWatch Logs Insights에서 실행한다.
3. 필요하면 로그 그룹을 `/ecs/guardbench-dev/app` 또는 해당 환경의 로그 그룹으로 바꾼다.

`testRunId=<TEST_RUN_ID>` 뒤에 항상 단어 경계(`\b`)를 넣어 `testRunId=1`이 `testRunId=10`, `testRunId=100`과 혼동되지 않게 한다.

## 1. TestRun 시작 — Target과 Classifier 확인

```
fields @timestamp, @message
| filter @message like /TestRun을 접수했습니다/ and @message like /testRunId=<TEST_RUN_ID>\b/
```

현재 접수 로그에서 확인 가능한 필드:

- `targetType`, `targetIdentifier`, `targetRevision`, `targetModel`
- `evaluatorReferenceId`, `evaluatorProviderCode`, `evaluatorModelId`
- `testCaseCount`, `eventId`, `eventType`

`targetType`은 현재 `HTTP_ENDPOINT`이며, evaluator 관련 필드는 TestRun에 고정된 Response Behavior Classifier 식별 정보다.

## 2. TestRun resolution — Target 준비 단계 확인

```
fields @timestamp, @message
| filter @message like /testRunId=<TEST_RUN_ID>\b/ and (@message like /resolution/ or @message like /Target 준비/)
| sort @timestamp asc
```

`Target 준비에 실패했습니다` 로그가 반복되면 `failureCode`로 원인을 구분하고, 최종적으로 `Target 준비 영구 실패로 TestRun을 종료했습니다` 로그가 있으면 해당 TestRun은 Snapshot 실행 단계로 진입하지 못한 것이다.

## 3. Snapshot 실행 — 재시도와 최종 결과 추적

```
fields @timestamp, @message
| filter @message like /testRunId=<TEST_RUN_ID>\b/ and (@message like /실패로 재시도합니다/ or @message like /terminal 결과를 저장했습니다/)
| sort @timestamp asc
```

- `실패로 재시도합니다` (WARN): `snapshotId`, `attemptCount`, `errorStage`, `errorCode`, `retryable=true`, `reason`(sanitized)
- `terminal 결과를 저장했습니다` (INFO): 성공 시 `evaluatorVerdict`, 실패 시 `errorStage`/`errorCode`/`retryable`

특정 Snapshot만 보고 싶으면 `snapshotId=<SNAPSHOT_ID>`를 filter에 추가한다.

```
fields @timestamp, @message
| filter @message like /testRunId=<TEST_RUN_ID>\b/ and @message like /snapshotId=<SNAPSHOT_ID>\b/
| sort @timestamp asc
```

## 4. Quality Gate 최종화 — PASS/FAIL 판정 근거 확인

```
fields @timestamp, @message
| filter @message like /finalization을 완료했습니다/ and @message like /testRunId=<TEST_RUN_ID>\b/
```

한 줄에서 확인 가능한 필드:

- `evaluatorReference`, `qualityGateStatus`, `executionOutcome`
- `processedTestCaseCount`, `testCaseCount`
- `executionSucceededCount`, `executionFailedCount`
- `assertionEvaluatedCount`, `assertionPassCount`, `assertionFailCount`
- `truePositive`, `trueNegative`, `falsePositive`, `falseNegative`
- `assertionPassRate`, `executionSuccessRate`
- `assertionPassRateThreshold`, `executionSuccessRateThreshold`
- `failureDimension`

## 5. 필드를 표로 추출하는 `parse` 쿼리 예시

```
fields @timestamp
| filter @message like /finalization을 완료했습니다/ and @message like /testRunId=<TEST_RUN_ID>\b/
| parse @message /qualityGateStatus=(?<qualityGateStatus>\S+)/
| parse @message /truePositive=(?<truePositive>\d+)/
| parse @message /trueNegative=(?<trueNegative>\d+)/
| parse @message /falsePositive=(?<falsePositive>\d+)/
| parse @message /falseNegative=(?<falseNegative>\d+)/
| parse @message /assertionPassRate=(?<assertionPassRate>[\d.]+)/
| parse @message /executionSuccessRate=(?<executionSuccessRate>[\d.]+)/
| parse @message /failureDimension=(?<failureDimension>\S*)/
| display @timestamp, qualityGateStatus, truePositive, trueNegative, falsePositive, falseNegative, assertionPassRate, executionSuccessRate, failureDimension
```

## 6. 여러 TestRun의 실행 실패 유형 집계

```
fields @message
| filter @message like /terminal 결과를 저장했습니다/ and @message like /errorCode=/
| parse @message /errorCode=(?<errorCode>\S+)/
| stats count(*) as count by errorCode
| sort count desc
```

## 알려진 한계

- 이 문서의 쿼리는 로그 원문 문자열에 의존한다. 로그 메시지나 필드명을 변경하면 이 문서도 함께 갱신해야 한다.
- Metric Filter, CloudWatch Alarm, 대시보드 구성은 이 문서의 범위가 아니다.
- 여러 TestRun에 걸친 FPR/FNR 추세 등 장기 집계는 이 문서의 단건 조사 쿼리로 다루지 않는다.
