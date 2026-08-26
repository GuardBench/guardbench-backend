package com.guardbench.testrun.infrastructure.persistence;

import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.guardbench.testrun.application.port.out.TransactionalPhasePort;

/**
 * {@link TransactionalPhasePort}를 Spring 트랜잭션 관리자에 연결하는 Adapter다.
 *
 * <p>기본 전파(REQUIRED)를 사용하므로 Worker처럼 진행 중인 트랜잭션이 없으면
 * phase마다 새 트랜잭션을 시작하고, 이미 트랜잭션이 있으면 그 범위에 참여한다.
 */
@Component
class SpringTransactionalPhaseAdapter implements TransactionalPhasePort {

    private final TransactionTemplate transactionTemplate;

    SpringTransactionalPhaseAdapter(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
    }

    @Override
    public void runInTransaction(Runnable phase) {
        Objects.requireNonNull(phase, "phase must not be null");
        transactionTemplate.executeWithoutResult(status -> phase.run());
    }
}
