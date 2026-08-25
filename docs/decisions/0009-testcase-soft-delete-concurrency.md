# 0009. TestCase 논리 삭제의 동시 요청 처리

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-25
> Canonical source: GitHub
> Origin: [GitHub Issue #60](https://github.com/GuardBench/guardbench-backend/issues/60)

- ADR Status: ACCEPTED
- Decision date: 2026-08-25
- Related Issue: #60
- Extends: ADR 0002

## Context

승인된 OpenAPI 계약은 같은 TestCase ID를 다시 삭제하면 `404 TEST_CASE_NOT_FOUND`로 응답하도록 요구한다. 현재처럼 활성 여부를 읽은 뒤 Aggregate를 삭제하고 저장하면, 동시 DELETE 요청 두 건이 각각 활성 Aggregate를 읽어 둘 다 성공할 수 있다. 최종 데이터는 삭제 상태로 수렴하지만 한 요청만 성공해야 한다는 공개 API 계약을 위반한다.

다음 제약을 유지해야 한다.

- ADR 0002의 활성 행 정의인 `deleted_at IS NULL`을 사용한다.
- 삭제 시각은 Application이 주입된 `Clock`으로 정하고, Aggregate가 `deleted_at`과 `updated_at`을 같은 값으로 전이한다.
- 이미 적용된 V1 Migration을 수정하거나 새 schema, version 컬럼을 추가하지 않는다.
- Domain을 JPA·Spring·SQL에 의존시키거나 Repository Port를 동시성 구현 세부사항으로 확장하지 않는다.
- `TestCase.delete()`의 삭제 전 상태 검증을 유지한다.

## Decision

TestCase 논리 삭제 저장은 PostgreSQL의 조건부 UPDATE 한 건으로 수행한다. Persistence Adapter는 Aggregate가 결정한 `deletedAt`과 `updatedAt` 값을 사용하고 활성 행에만 갱신을 허용한다.

```sql
UPDATE test_case
   SET deleted_at = :deletedAt,
       updated_at = :updatedAt
 WHERE id = :id
   AND deleted_at IS NULL
```

영향받은 행이 1이면 삭제에 성공한다. 0이면 해당 ID가 존재하지 않거나 이미 논리 삭제된 것이므로 `ApplicationException(TEST_CASE_NOT_FOUND)`로 변환한다. 두 경우를 외부에 구분하지 않는다.

이 UPDATE와 영향 행 판정은 같은 삭제 유스케이스 트랜잭션 안에서 수행한다. PostgreSQL 기본 `READ COMMITTED` 격리수준을 전제로, 같은 활성 행을 동시에 갱신하는 후행 UPDATE는 선행 트랜잭션의 종료를 기다린 뒤 `deleted_at IS NULL` 조건을 다시 평가한다. 따라서 정확히 한 요청만 1행을 갱신하고 나머지는 0행으로 끝난다. 이 동작의 근거는 [PostgreSQL Read Committed 문서](https://www.postgresql.org/docs/current/transaction-iso.html#XACT-READ-COMMITTED)다.

이 결정은 삭제 상태 전이를 Adapter로 옮기지 않는다. Application은 먼저 `TestCase.delete(now)`를 호출하고 Adapter는 Aggregate가 이미 만든 상태를 조건부로 영속화한다. `TestCase.delete()`의 `requireNotDeleted`는 동일 Aggregate 인스턴스의 잘못된 재사용을 방어하도록 유지한다.

Repository Port의 공개 method 목록과 signature, Domain 모델, 공개 API, DB schema와 Migration은 변경하지 않는다. 낙관적 락과 version 컬럼은 도입하지 않는다.

## Alternatives

| 선택지 | 판단 |
| --- | --- |
| 무조치 | 동시 DELETE 두 건이 모두 성공할 수 있어 승인된 404 계약을 위반하므로 기각한다. |
| 조건부 UPDATE | 기존 활성 행 조건만으로 API 계약을 원자적으로 만족하고 변경 범위가 Persistence Adapter에 한정되어 선택한다. |
| `SELECT ... FOR UPDATE` 비관적 잠금 | 계약을 만족하지만 별도 조회 Port와 더 긴 잠금 보유 구간이 필요해 선택하지 않는다. |
| version 컬럼 기반 낙관적 락 | V2 Migration과 Entity·Mapper 변경이 필요하고 optimistic conflict를 404로 해석하는 의미도 부정확해 선택하지 않는다. |
| 분산 락 | 단일 PostgreSQL 행 갱신으로 해결되는 문제에 운영 복잡도를 추가하므로 선택하지 않는다. |

## Consequences

- 동시 DELETE에서도 한 요청만 `204`이고 나머지는 `404 TEST_CASE_NOT_FOUND`가 된다.
- 존재하지 않는 ID와 이미 삭제된 ID는 기존 공개 계약대로 같은 404 결과를 낸다.
- 논리 삭제 시각과 상태 전이 규칙은 계속 Aggregate가 소유하고, Adapter는 원자적 저장 경쟁만 판정한다.
- schema, Migration과 Repository Port 변경이 없어 되돌릴 범위가 작다.
- 삭제 저장 경로는 일반적인 entity merge나 무조건 UPDATE가 아니라 affected-row count를 신뢰할 수 있는 조건부 UPDATE를 사용해야 한다.
- 격리수준을 `READ COMMITTED`가 아닌 값으로 변경하거나 PostgreSQL 외 DB로 이전하면 이 동시성 보장을 다시 검토해야 한다. `REPEATABLE READ` 또는 `SERIALIZABLE`을 채택할 때는 serialization failure의 트랜잭션 재시도와 최종 404 매핑을 함께 결정한다.

동시 PATCH의 last-write-wins는 이 결정의 범위가 아니며 MVP에서 수용한다. PATCH 동시성 의미는 승인된 계약에 없고 저장 전략에 따라 충돌 양상이 달라지므로, 필요하면 별도 Issue와 ADR에서 다룬다. TestSuite 편집, 실행 경로 동시성, Aggregate 시각 단조성과 분산 락도 범위에 포함하지 않는다.

되돌릴 때는 schema rollback 없이 조건부 삭제 Adapter 변경만 되돌린다. 단, 그러면 동시 DELETE의 공개 API 계약 위반이 다시 발생하므로 대체 동시성 전략을 함께 적용해야 한다.

## Validation

1. Testcontainers PostgreSQL의 기본 `READ COMMITTED`에서 같은 활성 TestCase를 대상으로 두 삭제 트랜잭션을 겹쳐 실행한다.
2. 두 조건부 UPDATE의 affected-row count가 순서와 무관하게 정확히 `1`과 `0`인지 확인한다.
3. API 수준 결과가 정확히 하나의 `204`와 하나의 `404 TEST_CASE_NOT_FOUND`인지 확인한다.
4. 존재하지 않는 ID와 이미 삭제된 ID가 `404 TEST_CASE_NOT_FOUND`이고, 정상 단일 삭제는 `deleted_at == updated_at`인지 확인한다.
5. 실패한 후행 삭제가 선행 삭제의 값을 덮어쓰지 않고, 기존 Snapshot과 실행·평가 결과에 영향을 주지 않는지 확인한다.
6. Repository Port, Domain 타입, V1 Migration과 공개 OpenAPI가 변경되지 않았는지 확인한다.
