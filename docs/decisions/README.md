# Architecture Decision Records

> Status: APPROVED
> Owner: Team
> Last reviewed: 2026-08-30
> Canonical source: GitHub
> Origin: 없음

공개 API, DB, 의존성, 아키텍처 경계 또는 되돌리기 어려운 선택은 ADR로 남긴다. 모든 ADR을 순서대로 읽지 말고 아래 표에서 현재 작업에 필요한 문서부터 찾는다.

## 작업별 라우팅

`필수`만 먼저 읽고, 변경 범위가 조건에 해당할 때만 `조건부 추가`를 읽는다. 현재 Issue가 별도의 관련 ADR을 지정하면 함께 확인한다.

| 작업 | 필수 | 조건부 추가 |
| --- | --- | --- |
| 도메인 타입 소유권, 기본 Aggregate, 패키지 의존 방향 | [ADR 0001](0001-domain-type-ownership-and-aggregate-boundaries.md), [ADR 0006](0006-independent-domain-contract-boundaries.md) | 실행·평가 결과 경계도 바꾸면 [ADR 0003](0003-result-aggregate-and-write-port-boundaries.md), 최종화 경계도 바꾸면 [ADR 0004](0004-testrun-finalization-atomicity.md) |
| PostgreSQL, JPA, Flyway, 물리 스키마 | [ADR 0002](0002-postgresql-persistence-contract.md), [ADR 0006](0006-independent-domain-contract-boundaries.md) | 결과 테이블과 Repository 매핑은 [ADR 0003](0003-result-aggregate-and-write-port-boundaries.md), 최종화 제약은 [ADR 0004](0004-testrun-finalization-atomicity.md), Outbox·idempotency·claim DDL은 [ADR 0008](0008-async-testrun-persistence-contract.md) |
| TestCase 논리 삭제와 동시 DELETE | [ADR 0002](0002-postgresql-persistence-contract.md), [ADR 0009](0009-testcase-soft-delete-concurrency.md) | Domain·Port 경계를 바꾸면 [ADR 0001](0001-domain-type-ownership-and-aggregate-boundaries.md)과 [ADR 0006](0006-independent-domain-contract-boundaries.md) |
| TestRun Target HTTP 입력 | [ADR 0010](0010-single-target-test-run-model.md), [API 안내](../api/README.md) | 접수+Outbox 원자 저장은 ADR 0005와 ADR 0008을 추가한다. |
| TestExecution·SnapshotEvaluation·QualityGateResult Aggregate와 write-side Repository | [ADR 0001](0001-domain-type-ownership-and-aggregate-boundaries.md), [ADR 0003](0003-result-aggregate-and-write-port-boundaries.md), [ADR 0006](0006-independent-domain-contract-boundaries.md) | 물리 매핑은 [ADR 0002](0002-postgresql-persistence-contract.md), TestRun 최종화는 [ADR 0004](0004-testrun-finalization-atomicity.md), Worker 중복 처리는 [ADR 0005](0005-async-test-run-execution-contract.md) |
| TestRun `FINISHED` 전환과 Quality Gate 원자 저장 | [ADR 0003](0003-result-aggregate-and-write-port-boundaries.md), [ADR 0004](0004-testrun-finalization-atomicity.md), [ADR 0006](0006-independent-domain-contract-boundaries.md) | PostgreSQL 제약은 [ADR 0002](0002-postgresql-persistence-contract.md), Worker 선점·잠금/CAS·retry는 [ADR 0005](0005-async-test-run-execution-contract.md) |
| Context 간 Port, Integration Adapter, 로컬 ID·VO와 Java 타입 격리 | [ADR 0006](0006-independent-domain-contract-boundaries.md) | Aggregate 저장 경계는 [ADR 0001](0001-domain-type-ownership-and-aggregate-boundaries.md)과 [ADR 0003](0003-result-aggregate-and-write-port-boundaries.md), 최종화는 [ADR 0004](0004-testrun-finalization-atomicity.md) |
| Worker 선점, 동시 실행, 잠금/CAS, retry·timeout | [ADR 0005](0005-async-test-run-execution-contract.md), [ADR 0006](0006-independent-domain-contract-boundaries.md) | 결과 저장 경계는 [ADR 0003](0003-result-aggregate-and-write-port-boundaries.md), 최종화 불변식은 [ADR 0004](0004-testrun-finalization-atomicity.md), 기존 결과 스키마는 [ADR 0002](0002-postgresql-persistence-contract.md), claim·Outbox 물리 저장은 [ADR 0008](0008-async-testrun-persistence-contract.md) |

## 비동기 TestRun 계약 맵

[비동기 TestRun 계약 맵](../contracts/README.md)은 #14·#16·#17·#18·#19의 변경을 계약 키별 **Primary contract**와 필수 보조 참조로 연결한다. 이 인덱스는 탐색 보조물일 뿐 ADR을 대체하거나 새 DB 제약을 정하지 않는다. ADR 0007·0008로 승인되지 않은 물리 계약을 해석하지 않으며, 새 미결정만 관련 Issue에 기록한다.

## 결정 관계

```text
ADR 0001  기본 타입 소유권·Aggregate·의존 방향
├─ ADR 0003  0001이 남긴 실행·평가 결과 저장 경계를 확장
│  └─ ADR 0004  0003의 TestRun 최종화 트랜잭션을 구체화
└─ ADR 0002  현재 Domain 경계를 PostgreSQL 물리 구조에 매핑

ADR 0005  ADR 0002·0003·0004를 전제로 비동기 실행·Worker·메시지 계약을 결정
└─ ADR 0008  0005의 Outbox·claim·HTTP Idempotency를 물리 DDL·lease 계약으로 구체화

ADR 0007  Candidate HTTP 입력을 DRAFT 전용으로 확정

ADR 0009  ADR 0002의 활성 행 조건을 동시 TestCase 논리 삭제의 원자 저장 조건으로 구체화

ADR 0010  ADR 0002·0003·0005·0007·0008의 Baseline/Candidate 부분을 단일 Target 모델로 대체

ADR 0006  0001·0002·0003·0004·0005의 경계 간 Java 타입 공유와 직접 의존만 부분 대체
          Aggregate·Persistence·최종화·비동기 실행 결정은 유지
```

ADR 0010이 명시적으로 대체한 Baseline/Candidate·role 메시지·복합 key 부분은 이전 ADR을 현재 계약으로 사용하지 않는다. 그 외 ADR 0003의 결과 저장 경계, ADR 0004의 최종화 원자성, ADR 0008의 claim·Outbox·HTTP Idempotency 기술 보장, ADR 0006의 Context 간 타입 격리는 유지한다.

## 상태와 작성

- 문서 `Status: APPROVED`와 `ADR Status: ACCEPTED`가 모두 확인된 결정만 구현 근거로 사용한다.
- `DRAFT` 또는 `PROPOSED`는 검토 자료다. 해석에 따라 공개 동작이 달라지면 구현을 중단하고 Issue에 미결정을 기록한다.
- `SUPERSEDED`는 대체 ADR을 찾는 용도로만 읽고 현재 계약으로 사용하지 않는다.
- `REJECTED`는 선택하지 않은 결정이며 구현 근거가 아니다.
- 승인된 ADR을 바꾸려면 원문을 조용히 수정하지 말고 새 ADR로 대체한다.
- 새 ADR 파일은 `NNNN-short-title.md` 형식으로 순번을 붙이고 [template.md](template.md)를 복사한다.
