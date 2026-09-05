package com.guardbench.testrun.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import com.guardbench.common.support.fixture.LogCapture;
import com.guardbench.testrun.application.ExecuteTestRunService;
import com.guardbench.testrun.application.ResolveTestRunService;
import com.guardbench.testrun.application.messaging.TestRunMessageCodec;
import com.guardbench.testrun.application.messaging.TestRunQueue;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Worker-level E2E 테스트.
 *
 * <p>LocalStack SQS + Polling Adapter + TestDouble Application Service로
 * 정상 메시지의 ack, retryable 메시지의 nack을 검증한다.
 * 각 테스트는 독립된 SQS queue를 사용하여 격리한다.
 */
class SqsWorkerEndToEndTest {

    private static final LocalStackContainer LOCAL_STACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.0"))
            .withServices(LocalStackContainer.Service.SQS);

    private static SqsClient sqs;

    @BeforeAll
    static void setup() {
        LOCAL_STACK.start();
        sqs = SqsClient.builder()
                .endpointOverride(URI.create(LOCAL_STACK.getEndpoint().toString()))
                .region(Region.of(LOCAL_STACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCAL_STACK.getAccessKey(), LOCAL_STACK.getSecretKey())))
                .build();
    }

    @AfterAll
    static void teardown() {
        if (sqs != null) sqs.close();
        LOCAL_STACK.stop();
    }

    @Test
    @DisplayName("RESOLVED outcome인 TestRunRequested 메시지는 ack되어 queue에서 제거된다")
    void resolvedMessageIsAcknowledgedAndRemovedFromQueue() {
        String queueUrl = createIsolatedQueue("resolve-ack-test");
        ObjectMapper objectMapper = JsonMapper.builder().build();
        TestRunMessageCodec codec = new TestRunMessageCodec(objectMapper);
        SqsProperties.Polling pollingConfig = new SqsProperties.Polling(1, 0, 30);

        // TestDouble: 항상 RESOLVED를 반환하는 ResolveTestRunService
        ResolveTestRunService stubResolveService = new StubResolveService(
                ResolveTestRunService.ResolutionOutcome.RESOLVED
        );

        SqsInboundPollingAdapter adapter = new SqsInboundPollingAdapter(
                sqs, codec, queueUrl, TestRunQueue.RESOLVE,
                pollingConfig, stubResolveService, null, null
        );

        // v2 TestRunRequested 메시지 전송
        String payload = codec.encode(new com.guardbench.testrun.application.messaging.TestRunRequestedMessage(
                UUID.randomUUID(), 42L, Instant.parse("2026-08-25T10:00:00Z")
        ));
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(payload)
                .build());

        // 폴링 1회
        int polled = adapter.poll();
        assertEquals(1, polled, "메시지 1건을 처리해야 한다");

        // Queue가 비어야 함
        var remaining = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(1)
                .build()).messages();
        assertTrue(remaining.isEmpty(), "ack된 메시지는 queue에서 제거돼야 한다");
    }

    @Test
    @DisplayName("nack 결과(CLAIM_HELD_BY_OTHER)인 메시지는 삭제되지 않고 재전달된다")
    void nackResultLeavesMessageForRedelivery() throws InterruptedException {
        String queueUrl = createIsolatedQueue("resolve-nack-test");
        ObjectMapper objectMapper = JsonMapper.builder().build();
        TestRunMessageCodec codec = new TestRunMessageCodec(objectMapper);
        SqsProperties.Polling pollingConfig = new SqsProperties.Polling(1, 0, 1);

        // TestDouble: 항상 CLAIM_HELD_BY_OTHER를 반환 (nack)
        ResolveTestRunService stubResolveService = new StubResolveService(
                ResolveTestRunService.ResolutionOutcome.CLAIM_HELD_BY_OTHER
        );

        SqsInboundPollingAdapter adapter = new SqsInboundPollingAdapter(
                sqs, codec, queueUrl, TestRunQueue.RESOLVE,
                pollingConfig, stubResolveService, null, null
        );

        // v2 TestRunRequested 메시지 전송
        String payload = codec.encode(new com.guardbench.testrun.application.messaging.TestRunRequestedMessage(
                UUID.randomUUID(), 99L, Instant.parse("2026-08-25T11:00:00Z")
        ));
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(payload)
                .build());

        // 폴링 1회 — nack이므로 삭제 안 됨
        adapter.poll();

        // visibility timeout (1초) 후 재전달 확인
        TimeUnit.SECONDS.sleep(2);

        var redelivered = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(2)
                .build()).messages();
        assertTrue(!redelivered.isEmpty(), "nack된 메시지는 재전달돼야 한다");
    }

    @Test
    @DisplayName("WorkItem의 실제 SQS enqueue timing을 기록하고 ack outcome이면 삭제한다")
    void executionOutcomeAckDeletesMessage() {
        String queueUrl = createIsolatedQueue("workitems-ack-test");
        ObjectMapper objectMapper = JsonMapper.builder().build();
        TestRunMessageCodec codec = new TestRunMessageCodec(objectMapper);
        SqsProperties.Polling pollingConfig = new SqsProperties.Polling(1, 0, 30);

        // TestDouble: 항상 ALREADY_TERMINAL (ack)
        ExecuteTestRunService stubExecuteService = new StubExecuteService(
                ExecuteTestRunService.ExecutionOutcome.ALREADY_TERMINAL
        );

        SqsInboundPollingAdapter adapter = new SqsInboundPollingAdapter(
                sqs, codec, queueUrl, TestRunQueue.WORK_ITEMS,
                pollingConfig, null, stubExecuteService, null
        );

        // v2 TestExecutionRequested 메시지 전송
        String payload = codec.encode(
                new com.guardbench.testrun.application.messaging.TestExecutionRequestedMessage(
                        UUID.randomUUID(), 42L, 100L,
                        Instant.parse("2026-08-25T10:00:01Z")
                )
        );
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(payload)
                .build());

        LogCapture capture = LogCapture.attach(SqsInboundPollingAdapter.class);
        try {
            int polled = adapter.poll();
            assertEquals(1, polled);
            String timing = capture.firstMessageContaining("WorkItem 수신 timing");
            assertTrue(timing.contains("testRunId=42 snapshotId=100"));
            assertTrue(timing.matches(".*sentTimestamp=\\d+ queueWaitMs=\\d+"), timing);
        } finally {
            capture.detach();
        }

        var remaining = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(1)
                .build()).messages();
        assertTrue(remaining.isEmpty(), "ALREADY_TERMINAL은 ack되어 삭제돼야 한다");
    }

    // ─── Helpers ───

    private String createIsolatedQueue(String name) {
        return sqs.createQueue(CreateQueueRequest.builder()
                .queueName(name)
                .attributes(Map.of(QueueAttributeName.VISIBILITY_TIMEOUT, "30"))
                .build()).queueUrl();
    }

    // ─── Test Doubles ───

    /**
     * ResolveTestRunService의 stub. resolve()에 대해 고정 outcome을 반환한다.
     */
    private static final class StubResolveService extends ResolveTestRunService {

        private final ResolutionOutcome fixedOutcome;

        StubResolveService(ResolutionOutcome fixedOutcome) {
            super(
                    new com.guardbench.testrun.application.port.out.ResolutionClaimPort() {
                        @Override public com.guardbench.testrun.application.port.out.ClaimResult tryAcquire(long testRunId) { return null; }
                        @Override public boolean isHeldBy(long testRunId, java.util.UUID token) { return false; }
                    },
                    new com.guardbench.testrun.domain.repository.TestRunRepository() {
                        @Override public void save(com.guardbench.testrun.domain.TestRun testRun) {}
                        @Override public java.util.Optional<com.guardbench.testrun.domain.TestRun> findById(com.guardbench.testrun.domain.TestRunId id) { return java.util.Optional.empty(); }
                    },
                    request -> { },
                    testRunId -> java.util.List.of(),
                    new com.guardbench.testrun.application.port.out.OutboxPort() {
                        @Override public void save(com.guardbench.testrun.application.port.out.OutboxEventRecord event) {}
                        @Override public java.util.List<com.guardbench.testrun.application.port.out.OutboxEventRecord> findPendingBatch(int batchSize) { return java.util.List.of(); }
                        @Override public void markPublished(java.util.Collection<java.util.UUID> eventIds) {}
                    },
                    new com.guardbench.testrun.domain.repository.TestExecutionRepository() {
                        @Override public void save(com.guardbench.testrun.domain.TestExecution execution) {}
                        @Override public java.util.Optional<com.guardbench.testrun.domain.TestExecution> findById(com.guardbench.testrun.domain.TestExecutionId id) { return java.util.Optional.empty(); }
                    },
                    testRunId -> {},
                    Runnable::run,
                    Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC)
            );
            this.fixedOutcome = fixedOutcome;
        }

        @Override
        public ResolutionOutcome resolve(long testRunId) {
            return fixedOutcome;
        }
    }

    /**
     * ExecuteTestRunService의 stub. execute()에 대해 고정 outcome을 반환한다.
     */
    private static final class StubExecuteService extends ExecuteTestRunService {

        private final ExecutionOutcome fixedOutcome;

        StubExecuteService(ExecutionOutcome fixedOutcome) {
            super(
                    new com.guardbench.testrun.application.port.out.ExecutionClaimPort() {
                        @Override public com.guardbench.testrun.application.port.out.ClaimResult tryAcquire(long snapshotId) { return null; }
                        @Override public boolean isHeldBy(long snapshotId, java.util.UUID token) { return false; }
                    },
                    new com.guardbench.testrun.domain.repository.TestExecutionRepository() {
                        @Override public void save(com.guardbench.testrun.domain.TestExecution execution) {}
                        @Override public java.util.Optional<com.guardbench.testrun.domain.TestExecution> findById(com.guardbench.testrun.domain.TestExecutionId id) { return java.util.Optional.empty(); }
                    },
                    snapshotId -> java.util.Optional.empty(),
                    request -> null,
                    request -> com.guardbench.testrun.application.port.out.EvaluatorExecutionResult.succeeded("ALLOW"),
                    new com.guardbench.testrun.application.port.out.OutboxPort() {
                        @Override public void save(com.guardbench.testrun.application.port.out.OutboxEventRecord event) {}
                        @Override public java.util.List<com.guardbench.testrun.application.port.out.OutboxEventRecord> findPendingBatch(int batchSize) { return java.util.List.of(); }
                        @Override public void markPublished(java.util.Collection<java.util.UUID> eventIds) {}
                    },
                    Runnable::run,
                    Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC)
            );
            this.fixedOutcome = fixedOutcome;
        }

        @Override
        public ExecutionOutcome execute(long snapshotId) {
            return fixedOutcome;
        }
    }
}
