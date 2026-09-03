# TestRun CloudWatch Logs Insights 조회 가이드

> Status: DRAFT
> Owner: Backend
> Last reviewed: 2026-09-03
> Canonical source: GitHub
> Related: [Issue #169](https://github.com/GuardBench/guardbench-backend/issues/169), [Issue #172](https://github.com/GuardBench/guardbench-backend/issues/172), [비동기 신뢰성 및 테스트 원칙](async-reliability-and-testing.md)

이 문서는 하나의 `testRunId`에 대해 API/DB를 조회하지 않고 CloudWatch application log(`/ecs/guardbench-dev/app`)만으로 TestRun 시작부터 Quality Gate 최종화까지 전체 흐름을 재구성하는 CloudWatch Logs Insights 쿼리를 정리한다.

Issue #169에서 로그에 진단 필드를 추가했고, Issue #172에서 로그 메시지 언어를 한국어로 전환했다. 이 문서의 쿼리는 전환된 한국어 로그 문구를 기준으로 작성했다.

## 사용 방법

1. 조사할 `testRunId`를 확인한다.
2. 아래 쿼리의 `<TEST_RUN_ID>`를 실제 값으로 바꿔 CloudWatch Logs Insights에서 실행한다.
3. 필요하면 로그 그룹을 `/ecs/guardbench-dev/app`(dev) 또는 해당 환경의 로그 그룹으로 바꾼다.

`testRunId=<TEST_RUN_ID>` 뒤에 항상 단어 경계(`\b`)를 넣어 `testRunId=1`이 `testRunId=10`, `testRunId=100`과 혼동되지 않게 한다.

## 1. TestRun 시작 — 무엇으로 실행했는지 확인

```
fields @timestamp, @message
| filter @message like /TestRun을 접수했습니다/ and @message like /testRunId=<TEST_RUN_ID>\b/
```

한 줄에서 확인 가능한 필드:

- `targetType`, `targetIdentifier`, `targetRevision`, `targetModel`
- `evaluatorReferenceId`, `evaluatorTypeCode`, `evaluatorIdentifier`, `evaluatorRevision`
- `evaluationChecks`, `evaluationStrictness`
- `testCaseCount`, `eventId`, `eventType`

`targetIdentifier`가 `https://https://...`처럼 이중 스킴으로 저장된 경우(Run 101 유형) 이 시점에서 바로 드러난다.

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

Run 201처럼 `PROVIDER_UNAVAILABLE`이 재시도된 뒤 소진되는 경우, 같은 `snapshotId`에 대해 `실패로 재시도합니다`가 여러 번 나오고 마지막 `terminal 결과를 저장했습니다`의 `retryable=true`로 "재시도했지만 소진되었다"는 것을 확인한다.

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
- `failureDimension` (`assertion`, `execution`, 둘 다, 또는 PASS/NOT_EVALUATED면 빈 문자열)

Run 301처럼 실행 성공률이 100%인데 Quality Gate가 FAIL이면 `failureDimension=assertion`으로 원인이 assertion 미달임이 즉시 드러난다.

## 5. 필드를 표로 추출하는 `parse` 쿼리 예시

원문 텍스트를 그대로 스크롤하는 대신 `parse`로 필드를 추출해 표로 보고 싶을 때 사용한다.

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

## 6. 여러 TestRun의 실행 실패 유형 집계 (기간 기준)

특정 기간에 어떤 `errorCode`가 자주 발생했는지 보고 싶을 때 사용한다. `testRunId` 필터를 제거하고 시간 범위로만 조회한다.

```
fields @message
| filter @message like /terminal 결과를 저장했습니다/ and @message like /errorCode=/
| parse @message /errorCode=(?<errorCode>\S+)/
| stats count(*) as count by errorCode
| sort count desc
```

## 알려진 한계

- 이 문서의 쿼리는 로그 원문 문자열에 의존한다. 향후 로그 메시지 문구를 변경하면 이 문서도 함께 갱신해야 한다.
- Metric Filter, CloudWatch Alarm, 대시보드 구성은 이 문서의 범위가 아니다. 필요하면 별도 Issue로 분리한다.
- 여러 TestRun에 걸친 FPR/FNR 추세 등 장기 집계는 이 문서의 단건 조사 쿼리로는 다루지 않는다.

## 실측 검증 기록

2026-09-03 dev 환경(`/ecs/guardbench-dev/app`)에서 실제 TestRun(`testRunId=351`)을 생성해 위 filter/like/parse 쿼리를 CloudWatch Logs Insights에서 직접 실행하고 매칭·필드 추출 결과를 확인했다. 검증 시점 배포본은 Issue #169(PR #170) 기준 영어 로그 문구였으며, 이 문서의 한국어 문구 자체에 대한 실측 재현은 생략했다. 로그 메시지가 한국어로 배포된 뒤에는 동일한 방식으로 재확인해야 한다.
