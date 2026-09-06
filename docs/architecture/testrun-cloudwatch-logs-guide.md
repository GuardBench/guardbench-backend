# TestRun CloudWatch Logs Insights 조회 가이드

> Status: DRAFT
> Owner: Backend
> Last reviewed: 2026-09-05
> Canonical source: GitHub
> Related: [Issue #214](https://github.com/GuardBench/guardbench-backend/issues/214), [Issue #169](https://github.com/GuardBench/guardbench-backend/issues/169), [Issue #172](https://github.com/GuardBench/guardbench-backend/issues/172), [비동기 신뢰성 및 테스트 원칙](async-reliability-and-testing.md)

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

## 3. Snapshot 실행 — 응답·classifier·재시도와 최종 결과 추적

```
fields @timestamp, @message
| filter @message like /testRunId=<TEST_RUN_ID>\b/ and (@message like /WorkItem 수신 timing을 기록합니다/ or @message like /Application Target 호출/ or @message like /Application response 진단 정보를 기록합니다/ or @message like /Classifier 호출을 시작합니다/ or @message like /Classifier 판정을 완료했습니다/ or @message like /Classifier 판정에 실패했습니다/ or @message like /실패로 재시도합니다/ or @message like /terminal 결과를 저장했습니다/)
| sort @timestamp asc
```

- `실패로 재시도합니다` (WARN): `snapshotId`, `attemptCount`, `errorStage`, `errorCode`, `retryable=true`, `reason`(sanitized)
- `Application response 진단 정보를 기록합니다` (INFO): `testRunId`, `snapshotId`, `responseLength`
- `Classifier 판정을 완료했습니다` (INFO): `classifierOutput`, `evaluatorVerdict`, `responseLength`
- `Classifier 판정에 실패했습니다` (WARN): `errorStage`, `errorCode`, `retryable`, `responseLength`
- `terminal 결과를 저장했습니다` (INFO): 성공 시 `evaluatorVerdict`, 실패 시 `errorStage`/`errorCode`/`retryable`

Application response 원문과 preview는 운영 로그에 기록하지 않는다. `responseLength`만
Unicode code point 기준 길이로 기록하며, classifier 입력과 TestExecution persistence에는 원문을 그대로 전달한다.

특정 Snapshot만 보고 싶으면 `snapshotId=<SNAPSHOT_ID>`를 filter에 추가한다.

```
fields @timestamp, @message
| filter @message like /testRunId=<TEST_RUN_ID>\b/ and @message like /snapshotId=<SNAPSHOT_ID>\b/
| sort @timestamp asc
```

Run 201처럼 `PROVIDER_UNAVAILABLE`이 재시도된 뒤 소진되는 경우, 같은 `snapshotId`에 대해 `실패로 재시도합니다`가 여러 번 나오고 마지막 `terminal 결과를 저장했습니다`의 `retryable=true`로 "재시도했지만 소진되었다"는 것을 확인한다.

## 4. Assertion·Quality Gate 최종화 — PASS/FAIL 판정 근거 확인

```
fields @timestamp, @message
| filter @message like /finalization을 완료했습니다/ and @message like /testRunId=<TEST_RUN_ID>\b/
```

Assertion까지 함께 조회하려면 다음 filter를 사용한다.

```
fields @timestamp, @message
| filter @message like /testRunId=<TEST_RUN_ID>\b/ and (@message like /Classifier 판정을/ or @message like /Snapshot assertion을/ or @message like /terminal 결과를 저장했습니다/)
| sort @timestamp asc
```

`Snapshot assertion을 판정했습니다` 로그에서 `testRunId`, `snapshotId`, `expectedAction`, `evaluatorVerdict`,
`assertionStatus`, `evaluated`, `evaluationReused`를 확인할 수 있다. 이를 통해 Application response 길이 진단 로그와
classifier verdict가 Assertion으로 변환된 결과를 Snapshot 단위로 연결한다.

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

## 7. Snapshot 병목 분석 — Queue wait와 Application Target latency

#214는 기존 수신/response/classifier/terminal 로그를 유지하고 다음 timing 필드만 추가한다.

| 로그 | 필드와 측정 경계 |
| --- | --- |
| `WorkItem 수신 timing을 기록합니다` | `testRunId`, `snapshotId`, `eventId`, `messageId`, `receivedAt`, `sentTimestamp`, `queueWaitMs` |
| `Application Target 호출을 시작합니다` | `testRunId`, `snapshotId`, `attemptCount` |
| `Application Target 호출을 완료했습니다` | 동일 correlation 필드 + `durationMs` |
| `Application Target 호출에 실패했습니다` | 동일 correlation 필드 + `durationMs`, sanitized `errorStage`, `errorCode` |

`receivedAt`은 SQS ReceiveMessage 응답 직후의 UTC 시각이다. 같은 batch의 모든 메시지에
동일한 값을 사용하므로 앞 메시지의 순차 처리 시간이 뒤 메시지 queue wait에 합산되지 않는다.
로그의 `@timestamp`는 각 메시지를 처리하기 시작할 때의 로그 발행 시각이므로, queue wait를
다시 계산할 때는 `receivedAt`을 사용한다. `receivedAt`과 Target 시작 로그 사이에는 batch 내
대기, decode, claim, context 조회가 포함될 수 있다.

`sentTimestamp`는 [SQS SentTimestamp](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html)의
epoch milliseconds다. `queueWaitMs = receivedAt(epoch ms) - sentTimestamp`이며, Outbox의
`occurredAt`부터 SQS publish까지의 대기는 포함하지 않는다. 재전달에서는 최초 enqueue 이후의
누적 경과 시간이므로 visibility 대기와 이전 처리 시도도 포함될 수 있다. 같은 `eventId`와
`messageId`의 여러 수신을 독립 신규 workload로 합산하지 않는다. SQS delivery와 Target의
business `attemptCount`는 서로 다른 횟수다.

Timestamp 누락/파싱 오류/음수이면 `sentTimestamp=null`, `queueWaitMs=null`로 기록한다.
미래 timestamp이면 원래 숫자는 보존하고 `queueWaitMs=null`로 기록한다. 누락을 0ms로
해석하지 않으며 진단 metadata 오류는 dispatch/ack를 바꾸지 않는다. AWS와 Worker의 wall clock
차이는 queue wait 오차에 영향을 준다.

`durationMs`는 monotonic clock으로 측정한 TargetExecutionPort 호출 시간이다. Adapter의 Target
설정 조회/요청 준비, HTTP 호출, 응답 읽기·파싱을 포함하며 순수 네트워크 왕복 시간과는 다르다.
Classifier 호출과 terminal persistence는 포함하지 않는다. 성공/실패 모두 같은
`testRunId + snapshotId + attemptCount`로 연결한다. prompt, endpoint, credential과 예외 원문은
추가 로그에 포함하지 않으며 Application response 원문과 preview도 기록하지 않는다.

### 개별 수신의 Queue wait

```
fields @timestamp
| filter @message like /WorkItem 수신 timing을 기록합니다/ and @message like /testRunId=<TEST_RUN_ID>\b/
| parse @message /snapshotId=(?<snapshotId>\d+)/
| parse @message /eventId=(?<eventId>\S+)/
| parse @message /messageId=(?<messageId>\S+)/
| parse @message /receivedAt=(?<receivedAt>\S+)/
| parse @message /sentTimestamp=(?<sentTimestamp>\S+)/
| parse @message /queueWaitMs=(?<queueWaitMs>\S+)/
| display @timestamp, snapshotId, eventId, messageId, receivedAt, sentTimestamp, queueWaitMs
| sort @timestamp asc
```

### Target 호출별 latency와 실패 코드

```
fields @timestamp
| filter @message like /testRunId=<TEST_RUN_ID>\b/ and (@message like /Application Target 호출을 완료했습니다/ or @message like /Application Target 호출에 실패했습니다/)
| parse @message /snapshotId=(?<snapshotId>\d+)/
| parse @message /attemptCount=(?<attemptCount>\d+)/
| parse @message /durationMs=(?<durationMs>\d+)/
| parse @message /errorStage=(?<errorStage>\S+)/
| parse @message /errorCode=(?<errorCode>\S+)/
| display @timestamp, snapshotId, attemptCount, durationMs, errorStage, errorCode
| sort @timestamp asc
```

동일 Snapshot의 전체 순서는 3절의 query에 `snapshotId=<SNAPSHOT_ID>\b` filter를 추가해
`WorkItem 수신 timing → Target start → complete/fail → response → classifier → terminal`로 확인한다.
Target 실패로 retry하면 해당 시도에는 response/classifier/terminal 로그가 없을 수 있다.
처리량 원인을 비교할 때는 첫 수신 queue wait, Target duration, 기존 classifier 시작/완료 시각을
분리해 읽는다. terminal persistence 자체의 정밀 duration은 현재 instrumentation 범위가 아니다.

## 알려진 한계

- 이 문서의 쿼리는 로그 원문 문자열에 의존한다. 로그 메시지나 필드명을 변경하면 이 문서도 함께 갱신해야 한다.
- Metric Filter, CloudWatch Alarm, 대시보드 구성은 이 문서의 범위가 아니다.
- 여러 TestRun에 걸친 FPR/FNR 추세 등 장기 집계는 이 문서의 단건 조사 쿼리로 다루지 않는다.
