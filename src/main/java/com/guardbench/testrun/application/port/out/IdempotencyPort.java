package com.guardbench.testrun.application.port.out;

import java.util.Optional;

/**
 * HTTP Idempotency 레코드를 관리하는 consumer-owned 아웃바운드 Port다.
 * 만료 판정은 PostgreSQL clock_timestamp()를 사용하며 Application Clock에 의존하지 않는다.
 */
public interface IdempotencyPort {

    /**
     * 아직 만료되지 않은 Idempotency 레코드를 조회한다.
     * expires_at <= clock_timestamp()인 row는 만료된 것으로 간주하여 반환하지 않는다.
     */
    Optional<IdempotencyRecord> findActiveByKey(String idempotencyKey);

    /**
     * 새 Idempotency 레코드를 저장한다.
     * 동일 key에 만료되지 않은 레코드가 이미 있으면 예외가 발생할 수 있다.
     * 만료된 기존 row가 있을 경우 덮어쓴다(UPSERT on expired).
     */
    void save(IdempotencyRecord record);
}
