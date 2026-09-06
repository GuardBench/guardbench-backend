# Issue #248 구현·검증 기록

이 문서는 [Issue #248](https://github.com/GuardBench/guardbench-backend/issues/248)의 구현 기록이며 새로운 계약을 승인하지 않는다.

## 변경과 원자성

`TestExecutionCompleted` handler는 `CheckTestRunCompletionService`로 TestRun 행을 잠근 뒤
Snapshot ID와 execution status만 LEFT JOIN 한 번으로 읽는다. Snapshot 준비가 완료된 부분
완료 상태에서는 기존 Domain 규칙으로 절대 진행도를 저장하고 ACK한다. 중복 메시지를
카운터 증가로 처리하지 않으므로 진행도가 중복 집계되지 않는다.

모든 실행이 terminal이면 기존 `FinalizeTestRunService`를 호출한다. readiness 트랜잭션과
최종화 트랜잭션 사이에는 잠금이 해제되지만 저장된 terminal 결과는 불변이다. 최종화는
다시 TestRun 행을 잠그고 현재 상태를 확인하며, QualityGateResult와 FINISHED를 함께
commit한다. readiness 이후 최종화에 실패하면 예외를 전파하여 메시지를 ACK하지 않는다.
재전달은 같은 검사를 거쳐 최종화를 재시도한다. FINISHED 중복 전달에서는 실행 전문을
읽지 않고 기존 QualityGateResult 또는 저장 불변식 위반을 확인한다.

완료 메시지 개수는 줄이지 않는다. 부분 완료마다 O(N)의 작은 상태 projection은 읽지만,
Snapshot·Execution 전문의 N+1 조회 및 부분 진행도 갱신을 위한 재조회는 제거한다.
마지막 terminal 이후의 최종 평가와 종료 계산은 기존 구현을 사용한다.

## 참조·호환성 조사

`src/main`, `src/test`, `docs`, `scripts`에서 handler, finalizer, facts port와 facade 참조를 검색했다.
production completion 호출부는 `EvaluationFinalizationWorkerConfiguration` 한 곳이며
SQS inbound adapter가 `HandleTestExecutionCompletedPort`를 사용한다. 실행 사실 조회의
production 소비자는 기존 Evaluation finalizer와 Integration Adapter다. 해당 경계는
최종 평가에 계속 필요하며 alias나 deprecated shim을 추가하지 않았다.

공개 HTTP API, SQS payload, migration, 의존성, 배포 설정은 변경하지 않는다.
현재 DB와 메시지 형식을 그대로 사용하므로 데이터 이관이나 배포 버전별 호환 경로는
필요하지 않다. 실제 배포 버전 변경·조회와 원격 Issue/PR 수정은 수행하지 않았다.

## 로컬 검증

- `testFast`: 단위·컨트롤러·계약·아키텍처 테스트 477건 통과.
- `CompletionFanInIntegrationTest`: PostgreSQL과 production handler로 78·491건의 부분 완료,
  종료 후 중복 전달, 실행 전문 미조회, 동시 완료, 저장 실패 후 재시도, 실패 execution,
  Snapshot 준비 미완료를 검증한다. 6건 통과.
- `PartialCompletionProgressIntegrationTest`, `TestRunFinalizationConcurrencyIntegrationTest`:
  기존 진행도 및 원자적 최종화 회귀 검증 4건 통과.
- `SqsReliabilityConvergenceIntegrationTest`: LocalStack SQS와 PostgreSQL에서 기존 5개 시나리오 통과.
  부분 완료 ACK와 retry/DLQ 비증폭 계약을 포함한다.

## 배포 후 남은 성능 검증

변경 코드의 배포는 로컬 커밋 권한에 포함되지 않아 실제 성능 재실험은 실행하지 않았다.
78·491건 로컬 기능 검증은 AWS 성능 측정을 대체하지 않는다.

동일 `large-suite-491-v1`, tuple `(8,1,4)`와 고정된 Provider 조건으로 재실행하여
기준 실행 `PERF-SMOKE-01-20260906T121045Z-91599862`와 비교해야 한다.
TestRun completion time(기준 약 58.76s), finalize queue drain(기준 약 124.5s),
completion message 처리 횟수·시간, execution failure/retry/DLQ를 기록한다.
Run 완료 성능 비악화와 queue drain 120s 이하 및 마지막 terminal 직후 수렴 여부는
이 실험 전까지 미검증이다.
