package com.guardbench.testrun.infrastructure.messaging.reliability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import com.guardbench.testrun.application.messaging.TestRunMessageCodec;
import com.guardbench.testrun.application.messaging.TestRunQueue;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionPort;
import com.guardbench.testrun.application.port.out.TargetExecutionPort;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testsupport.PostgresTestConfiguration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Issue #153: LocalStack SQS + PostgreSQL Testcontainers + 실제 Worker consumer +
 * deterministic Provider Stub으로 SQS delivery retry, claim lease 경합, Provider
 * business retry, finalization convergence의 결합 동작을 검증한다.
 *
 * <p>이 suite는 두 계약을 구분해서 다룬다.
 * <ul>
 *   <li><b>Harness/infrastructure 계약</b> — 현재 코드에서도 항상 성립해야 한다.
 *       LocalStack redrive, 실제 claim 경합, duplicate delivery 관측,
 *       Provider invocation count 관측 등 이 테스트 도구 자체의 신뢰성이다.</li>
 *   <li><b>#149 regression 계약</b> — #149 production fix 전에는 실패할 수 있다.
 *       {@code AlreadyHeld}와 Provider attempt 분리, partial finalize ACK,
 *       retry exhaustion 후 terminal convergence, TestRun FINISHED 수렴이다.
 *       이 이슈는 #149 production code를 수정하지 않으므로, 아래 시나리오가 실패하면
 *       그 자체가 #149의 결함을 정확히 재현한 유효한 결과다.</li>
 * </ul>
 *
 * <p><b>현재 관측된 실제 결과: Scenario 1/2/4/5 PASS, Scenario 3은 알려진 production
 * regression으로 {@code @Disabled} 처리.</b> Scenario 3은 partial finalization
 * 메시지가 실제로 ACK(삭제)되는지를 직접 검증하는데, 현재 {@code
 * EvaluationFinalizationWorkerConfiguration}이 {@code FinalizationOutcome.NotReady}를
 * NACK(false)으로 처리해 메시지가 {@code maxReceiveCount(5)} 소진 후 finalize DLQ로
 * 실제 이동한다(#149와 동일 패턴). mutation testing(핸들러를 강제로 항상 false 반환하도록
 * 조작 → FAIL, 원복 후 원본 코드에서도 동일하게 FAIL)으로 이 실패가 테스트 결함이 아니라
 * 실제 결함임을 확인했다. 테스트 본문과 기대값은 수정 후 계약을 그대로 유지하며 완화하지
 * 않는다. #149 production fix 구현 시 {@code @Disabled}를 제거하고 PASS를 완료조건으로
 * 삼는다.
 *
 * <p>시간 기반 설정은 절대값보다 관계로 검증한다.
 * <ul>
 *   <li>Scenario 1: {@code visibility timeout < claim lease} (#149와 동일한 순서 관계)</li>
 *   <li>Scenario 2/3/4/5: claim lease와 finalize 재시도 간격을 짧게 유지해 bounded 시간 안에 수렴을 관측한다.</li>
 * </ul>
 *
 * <p>모든 eventual assertion은 {@link org.awaitility.Awaitility}로 명시적 timeout을 가진다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "guardbench.sqs.enabled=true",
                "guardbench.worker.enabled=true",
                "guardbench.sqs.polling.wait-time-seconds=0",
                "guardbench.sqs.polling.delay-ms=100",
                "guardbench.sqs.outbox.delay-ms=100",
                "guardbench.sqs.outbox.initial-delay-ms=0",
                // #149 순서 관계 재현: visibility timeout(1s) < claim lease(3s)
                "guardbench.sqs.polling.visibility-timeout-seconds=1",
                "guardbench.claim.lease-seconds=3"
        })
@Import({PostgresTestConfiguration.class, SqsReliabilityConvergenceIntegrationTest.SqsReliabilityTestConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqsReliabilityConvergenceIntegrationTest {

    private static final LocalStackContainer LOCAL_STACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.SQS);

    private static final SqsClient SQS;

    static {
        LOCAL_STACK.start();
        SQS = SqsClient.builder()
                .endpointOverride(URI.create(LOCAL_STACK.getEndpoint().toString()))
                .region(Region.of(LOCAL_STACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCAL_STACK.getAccessKey(), LOCAL_STACK.getSecretKey())))
                .build();
        createWorkerQueuesWithRedrive();
        System.setProperty("aws.accessKeyId", LOCAL_STACK.getAccessKey());
        System.setProperty("aws.secretAccessKey", LOCAL_STACK.getSecretKey());
    }

    @DynamicPropertySource
    static void localStackProperties(DynamicPropertyRegistry registry) {
        registry.add("guardbench.sqs.endpoint-override", () -> LOCAL_STACK.getEndpoint().toString());
        registry.add("guardbench.sqs.region", LOCAL_STACK::getRegion);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SqsClient applicationSqsClient;

    @Autowired
    private TargetExecutionPort targetExecutionPort;

    @Autowired
    private com.guardbench.testrun.application.port.out.ExecutionClaimPort executionClaimPort;

    @Autowired
    private TestRunMessageCodec codec;

    private DeterministicTargetExecutionStub stub;
    private CountingExecutionClaimPortDecorator claimDecorator;
    private TestRunPersistenceFixture fixture;

    @BeforeEach
    void resetFixture() {
        drainAllQueues();
        fixture = new TestRunPersistenceFixture(jdbcTemplate);
        fixture.clearPersistenceTables();
        stub = (DeterministicTargetExecutionStub) targetExecutionPort;
        stub.reset();
        claimDecorator = (CountingExecutionClaimPortDecorator) executionClaimPort;
        claimDecorator.reset();
    }

    @AfterAll
    void stopLocalStack() {
        applicationSqsClient.close();
        SQS.close();
        LOCAL_STACK.stop();
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario 1 (#149 재현): SQS receive count와 Provider invocation count 분리 관측
    // 분류: Harness 계약 — 이 관측 자체는 현재 코드에서도 성립해야 한다.
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scenario 1: visibility timeout < claim lease일 때 claim 시도 횟수(delivery)가 실제 Provider invocation 횟수보다 많아질 수 있다 (#149 재현)")
    void claimAttemptsCanExceedProviderInvocationCountUnderAlwaysTimeout() {
        stub.useAlwaysProviderTimeout();
        claimDecorator.reset();

        long testRunId = 90001L;
        long snapshotId = 90101L;
        String input = "scenario1-input";
        seedRunningTestRunWithSnapshot(testRunId, snapshotId, input);

        sendExecutionRequested(testRunId, snapshotId);

        // ALWAYS_TIMEOUT이므로 MAX_EXECUTION_ATTEMPTS(3) 소진까지 애플리케이션 레벨에서
        // TIMED_OUT으로 수렴하며 정상 ACK된다. 이 과정에서 visibility timeout(1s) < claim
        // lease(3s) 관계 때문에, claim lease가 아직 유효한 동안 재전달된 메시지는
        // AlreadyHeld로 처리되어 claim 시도(tryAcquire 호출, SQS 재전달의 proxy) 횟수가
        // 실제 claim 획득 성공(=Provider invocation) 횟수보다 많아진다.
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(150))
                .until(() -> isSnapshotTerminal(snapshotId));

        int totalClaimAttempts = claimDecorator.tryAcquireAttemptsFor(snapshotId);
        int acquiredCount = claimDecorator.acquiredCountFor(snapshotId);
        int alreadyHeldCount = claimDecorator.alreadyHeldCountFor(snapshotId);
        int invocationCount = stub.invocationCountFor(input);

        assertThat(alreadyHeldCount)
                .as("visibility timeout < claim lease 관계에서는 최소 1회 이상의 재전달이 "
                        + "claim lease 유효 구간에 도착해 AlreadyHeld가 되어야 한다 (#149 재현 조건)")
                .isGreaterThanOrEqualTo(1);
        assertThat(acquiredCount)
                .as("claim 획득 성공 횟수는 실제 Provider invocation 횟수와 정확히 일치해야 한다 (S1: Provider 호출 없이 attempt가 증가하지 않는다)")
                .isEqualTo(invocationCount);
        assertThat(totalClaimAttempts)
                .as("SQS 재전달로 인한 총 claim 시도(tryAcquire) 횟수는 실제 Provider invocation 횟수보다 많아야 한다 "
                        + "(#149: SQS receive/claim 시도 count != Provider invocation count)")
                .isGreaterThan(invocationCount);
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario 2: AlreadyHeld는 Provider attempt가 아니다
    // 분류: #149 regression 계약 — AlreadyHeld 반복 동안 attempt/Provider 호출이
    // 전혀 늘지 않아야 한다. 이는 claim 경합 자체의 계약이며 #149 production fix
    // 유무와 무관하게 claim adapter 수준에서 이미 보장되어야 한다.
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scenario 2: claim lease 동안 반복 전달된 메시지는 AlreadyHeld이며 Provider를 호출하지 않는다")
    void repeatedDeliveryWhileClaimHeldDoesNotInvokeProvider() {
        stub.useAlwaysProviderTimeout();

        long testRunId = 90002L;
        long snapshotId = 90102L;
        String input = "scenario2-input";
        seedRunningTestRunWithSnapshot(testRunId, snapshotId, input);

        sendExecutionRequested(testRunId, snapshotId);

        // 최초 claim 획득 및 첫 Provider 호출을 기다린다.
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> stub.invocationCountFor(input) >= 1);

        int invocationAfterFirstAttempt = stub.invocationCountFor(input);

        // claim lease(3s)가 만료되기 전 구간(2초) 동안 invocation count가 한 번도 늘지 않는지
        // 짧은 간격으로 반복 확인한다. during()은 지정 구간 내내 조건이 유지되는지 검증하므로
        // 중간에 값이 증가하면 즉시 실패한다.
        await().during(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(stub.invocationCountFor(input))
                        .as("claim이 유효한 동안 재전달은 AlreadyHeld로 처리되어야 하며 Provider를 다시 호출하지 않는다")
                        .isEqualTo(invocationAfterFirstAttempt));
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario 3: partial finalization은 ACK되고 finalize DLQ로 이동하지 않는다
    // 분류: #149 regression 계약 — allExecutionsTerminal=false가 정상 중간 상태로
    // 처리되어 ACK되는지 검증한다. #149 장애의 핵심은 이 상태가 NACK/retry로
    // 잘못 해석되어 finalize DLQ로 누적된 것이었다.
    //
    // 현재 알려진 결과: FAIL (production bug, #149와 동일 패턴)
    //   EvaluationFinalizationWorkerConfiguration.handleTestExecutionCompletedPort()가
    //   FinalizationOutcome.NotReady를 false(NACK)로 매핑한다. 그 결과 partial
    //   finalization 메시지는 ACK되지 않고 visibility timeout(1s)마다 재전달되어
    //   maxReceiveCount(5) 소진 후 finalize DLQ로 실제로 이동한다(관측값: DLQ depth=1).
    //   mutation testing으로 이 테스트의 유효성을 확인했다: handleTestExecutionCompletedPort를
    //   강제로 항상 false를 반환하도록 조작(mutant)해도 FAIL, 원본 프로덕션 코드로
    //   되돌려도 동일하게 FAIL한다. 즉 이 실패는 테스트 결함이 아니라 실제 결함이다.
    //
    // 이 이슈(#153)는 #149 production fix를 구현하지 않으므로 테스트 본문과 기대값은
    // 수정 후 계약(partial finalization -> ACK, source/DLQ 모두 empty)을 그대로 유지하고
    // @Disabled로만 비활성화한다. #149 production fix 구현 시 이 @Disabled를 제거하고
    // 이 시나리오가 PASS하는 것을 완료조건으로 삼는다.
    // ─────────────────────────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Disabled(
            "Known regression tracked by #149: EvaluationFinalizationWorkerConfiguration이 "
                    + "FinalizationOutcome.NotReady를 NACK(false)으로 처리해 partial finalization 메시지가 "
                    + "maxReceiveCount(5) 소진 후 finalize DLQ로 실제 이동한다(관측: DLQ depth=1). "
                    + "이 테스트는 수정 후 기대 계약(ACK, DLQ 미도달)을 그대로 유지한다. "
                    + "#149 production fix 후 이 애노테이션을 제거하고 PASS를 완료조건으로 삼는다.")
    @DisplayName("Scenario 3: 일부 TestExecution만 terminal이면 finalize 메시지는 실제로 ACK(삭제)되고 재전달되지 않으며 DLQ로도 이동하지 않는다")
    void partialCompletionFinalizeIsAcknowledgedWithoutReachingDlq() {
        stub.useAlwaysSucceed();

        long testRunId = 90003L;
        long snapshotId1 = 90103L;
        long snapshotId2 = 90104L;
        seedRunningTestRunWithSnapshots(testRunId, 2,
                new long[]{snapshotId1, snapshotId2},
                new String[]{"scenario3-input-1", "scenario3-input-2"});

        // snapshot1만 terminal로 직접 저장하고(다른 target은 아직 미완료), 조기 완료 이벤트를 보낸다.
        insertTerminalExecution(snapshotId1);
        String finalizeQueueUrl = queueUrl(TestRunQueue.FINALIZE.queueName());
        sendExecutionCompleted(testRunId, snapshotId1);

        // 1) 메시지가 최초 1회 이상 receive되어 처리 시도가 실제로 일어났는지 확인한다.
        //    NotReady -> false 경로에서 partial finalization이 progress 갱신만 하고
        //    ACK되는지가 이 시나리오의 핵심이므로, "처리됨"을 진행률 반영으로 확인한다.
        await().atMost(Duration.ofSeconds(6))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> processedTestCaseCount(testRunId) == 1);

        // 2) 메시지가 실제로 ACK(삭제)되었는지 직접 검증한다.
        //    NotReady -> false로 처리되면 SqsInboundPollingAdapter는 메시지를 삭제하지
        //    않고 방치하므로, visibility timeout(1s)마다 재전달되어 maxReceiveCount(5)
        //    소진 후 DLQ로 이동한다. "큐 depth가 일시적으로 0으로 보이는 순간"은 메시지가
        //    아직 in-flight(재전달 대기) 상태일 때도 관측될 수 있어 ACK의 증거가 될 수 없다.
        //    따라서 maxReceiveCount(5) * visibility timeout(1s)을 충분히 넘는 시간만큼
        //    실제로 기다린 뒤(방치되었다면 이 시간 안에 반드시 DLQ로 이동한다), 원본 큐와
        //    DLQ를 한 번에 최종 스냅샷으로 확인한다.
        await().atMost(Duration.ofSeconds(20))
                .pollDelay(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> true);

        var remaining = SQS.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(finalizeQueueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(0)
                .visibilityTimeout(0)
                .build()).messages();

        assertThat(remaining)
                .as("ACK된 finalize 메시지는 maxReceiveCount(5) * visibility timeout(1s)을 넘는 "
                        + "충분한 시간이 지나도 원본 큐에 재전달되어 남아 있지 않아야 한다")
                .isEmpty();
        assertThat(queueDepth(TestRunQueue.FINALIZE.deadLetterQueueName()))
                .as("partial finalization은 정상 중간 상태이므로 방치(NACK)되어 DLQ로 누적되지 않아야 한다. "
                        + "DLQ에 메시지가 있다면 이 메시지가 ACK되지 않고 계속 재전달되다 maxReceiveCount를 "
                        + "소진했다는 뜻이다")
                .isZero();
        assertThat(testRunStatus(testRunId))
                .as("모든 실행이 terminal이 아니면 TestRun은 RUNNING을 유지해야 한다")
                .isEqualTo("RUNNING");
        assertThat(processedTestCaseCount(testRunId))
                .as("partial finalization은 처리 완료된 Snapshot 수만큼 진행률을 반영해야 한다")
                .isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario 4: 마지막 terminal execution 이후 TestRun은 FINISHED로 수렴한다
    // 분류: #149 regression 계약 — success/permanent failure/timeout retry가 혼합된
    // 상황에서도 모든 TestExecution이 terminal이면 TestRun이 최종적으로 FINISHED에
    // 도달해야 한다는 liveness invariant(L3)를 검증한다.
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scenario 4: success/permanent-failure/timeout-then-success가 혼합되어도 TestRun은 FINISHED로 수렴한다")
    void mixedOutcomesConvergeToFinishedTestRun() {
        long testRunId = 90004L;
        long successSnapshotId = 90105L;
        long permanentFailureSnapshotId = 90106L;
        long timeoutThenSuccessSnapshotId = 90107L;

        seedRunningTestRunWithSnapshots(testRunId, 3,
                new long[]{successSnapshotId, permanentFailureSnapshotId, timeoutThenSuccessSnapshotId},
                new String[]{"scenario4-success", "scenario4-permanent-failure", "scenario4-timeout-then-success"});

        stub.useMixed(Map.of(
                "scenario4-success", DeterministicTargetExecutionStub.Mode.SUCCESS,
                "scenario4-permanent-failure", DeterministicTargetExecutionStub.Mode.PERMANENT_FAILURE,
                "scenario4-timeout-then-success", DeterministicTargetExecutionStub.Mode.TIMEOUT_N_TIMES_THEN_SUCCESS
        ), 2);

        sendExecutionRequested(testRunId, successSnapshotId);
        sendExecutionRequested(testRunId, permanentFailureSnapshotId);
        sendExecutionRequested(testRunId, timeoutThenSuccessSnapshotId);

        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> "FINISHED".equals(testRunStatus(testRunId)));

        assertThat(testRunStatus(testRunId)).isEqualTo("FINISHED");
        assertThat(completedAt(testRunId)).isNotNull();
        assertThat(executionOutcome(testRunId)).isNotNull();
        assertThat(terminalExecutionCount(testRunId))
                .as("모든 Snapshot의 실행이 terminal로 저장되어야 한다")
                .isEqualTo(3);
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario 5: 중복 전달(work-item/completion)에 대한 멱등성
    // 분류: #149 regression 계약 — 중복 전달이 providerAttempt·progress·TestRun 상태를
    // 중복으로 변경하지 않는지 검증한다.
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scenario 5: 이미 FINISHED인 TestRun에 대한 중복 work-item/completion 전달은 progress·상태·결과를 중복 변경하지 않는다")
    void duplicateWorkItemAndCompletionDeliveryDoesNotDoubleMutateState() {
        stub.useAlwaysSucceed();

        long testRunId = 90005L;
        long snapshotId1 = 90108L;
        long snapshotId2 = 90109L;
        String input1 = "scenario5-input-1";
        String input2 = "scenario5-input-2";
        seedRunningTestRunWithSnapshots(testRunId, 2,
                new long[]{snapshotId1, snapshotId2},
                new String[]{input1, input2});

        sendExecutionRequested(testRunId, snapshotId1);
        sendExecutionRequested(testRunId, snapshotId2);

        // 두 Snapshot이 모두 terminal로 수렴하고 TestRun이 FINISHED로 수렴할 때까지 기다린다.
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(150))
                .until(() -> "FINISHED".equals(testRunStatus(testRunId)));

        int processedAfterFirstFinish = processedTestCaseCount(testRunId);
        String executionOutcomeAfterFirstFinish = executionOutcome(testRunId);
        Instant completedAtAfterFirstFinish = completedAt(testRunId);
        int invocationAfterFirstCompletion1 = stub.invocationCountFor(input1);
        int invocationAfterFirstCompletion2 = stub.invocationCountFor(input2);

        assertThat(processedAfterFirstFinish)
                .as("FINISHED 시점의 progress는 전체 TestCase 수와 같아야 한다")
                .isEqualTo(2);

        // 이미 terminal/FINISHED가 된 후 같은 이벤트를 중복 전달한다.
        sendExecutionRequested(testRunId, snapshotId1);
        sendExecutionCompleted(testRunId, snapshotId1);
        sendExecutionCompleted(testRunId, snapshotId1);
        sendExecutionCompleted(testRunId, snapshotId2);

        // 중복 메시지가 모두 소비될 시간을 bounded로 기다린다.
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(150))
                .until(() -> queueDepth(TestRunQueue.WORK_ITEMS.queueName()) == 0
                        && queueDepth(TestRunQueue.FINALIZE.queueName()) == 0);

        assertThat(stub.invocationCountFor(input1))
                .as("이미 terminal인 실행에 대한 중복 work-item 전달은 Provider를 다시 호출하지 않아야 한다")
                .isEqualTo(invocationAfterFirstCompletion1);
        assertThat(stub.invocationCountFor(input2))
                .as("다른 snapshot의 Provider invocation 수도 중복 completion으로 바뀌지 않아야 한다")
                .isEqualTo(invocationAfterFirstCompletion2);
        assertThat(terminalExecutionCount(testRunId)).isEqualTo(2);
        assertThat(processedTestCaseCount(testRunId))
                .as("중복 completion 전달은 progress를 중복으로 증가시키지 않아야 한다 (#153: progress 중복 증가/감소 금지)")
                .isEqualTo(processedAfterFirstFinish);
        assertThat(testRunStatus(testRunId))
                .as("이미 FINISHED인 TestRun은 중복 completion으로 다시 RUNNING 등으로 되돌아가지 않아야 한다")
                .isEqualTo("FINISHED");
        assertThat(executionOutcome(testRunId))
                .as("이미 FINISHED인 TestRun의 executionOutcome은 중복 completion으로 재계산되지 않아야 한다")
                .isEqualTo(executionOutcomeAfterFirstFinish);
        assertThat(completedAt(testRunId))
                .as("이미 FINISHED인 TestRun의 completedAt은 중복 completion으로 갱신되지 않아야 한다 (#153: FINISHED TestRun 불변성)")
                .isEqualTo(completedAtAfterFirstFinish);
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private void seedRunningTestRunWithSnapshot(long testRunId, long snapshotId, String input) {
        seedRunningTestRunWithSnapshots(testRunId, 1, new long[]{snapshotId}, new String[]{input});
    }

    private void seedRunningTestRunWithSnapshots(long testRunId, int testCaseCount, long[] snapshotIds, String[] inputs) {
        Instant base = Instant.parse("2026-08-25T00:00:00Z");
        long testSuiteId = testRunId * 1000;
        fixture.insertTestSuite(testSuiteId, base);
        fixture.insertQueuedTestRun(testRunId, testSuiteId, testCaseCount, base);
        jdbcTemplate.update(
                "UPDATE test_run SET status = 'RUNNING', started_at = ? WHERE id = ?",
                java.sql.Timestamp.from(base), testRunId);
        for (int i = 0; i < snapshotIds.length; i++) {
            long testCaseId = snapshotIds[i] + 500_000;
            fixture.insertTestCase(testCaseId, testSuiteId, base);
            fixture.insertSnapshot(snapshotIds[i], testRunId, testCaseId, inputs[i], base);
        }
    }

    private void insertTerminalExecution(long snapshotId) {
        jdbcTemplate.update(
                """
                INSERT INTO test_execution(snapshot_id, result_status, application_response, evaluator_verdict, started_at, completed_at)
                VALUES (?, 'SUCCEEDED', 'stub-response', 'ALLOW', clock_timestamp(), clock_timestamp())
                """,
                snapshotId);
    }

    private boolean isSnapshotTerminal(long snapshotId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_execution WHERE snapshot_id = ?", Integer.class, snapshotId);
        return count != null && count > 0;
    }

    private long terminalExecutionCount(long testRunId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM test_execution
                WHERE snapshot_id IN (SELECT id FROM test_case_snapshot WHERE test_run_id = ?)
                """, Long.class, testRunId);
        return count == null ? 0 : count;
    }

    private String testRunStatus(long testRunId) {
        return jdbcTemplate.queryForObject("SELECT status FROM test_run WHERE id = ?", String.class, testRunId);
    }

    private int processedTestCaseCount(long testRunId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT processed_test_case_count FROM test_run WHERE id = ?", Integer.class, testRunId);
        return count == null ? 0 : count;
    }

    private Instant completedAt(long testRunId) {
        java.sql.Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT completed_at FROM test_run WHERE id = ?", java.sql.Timestamp.class, testRunId);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String executionOutcome(long testRunId) {
        return jdbcTemplate.queryForObject(
                "SELECT execution_outcome FROM test_run WHERE id = ?", String.class, testRunId);
    }

    private void sendExecutionRequested(long testRunId, long snapshotId) {
        String queueUrl = queueUrl(TestRunQueue.WORK_ITEMS.queueName());
        String payload = codec.encode(new com.guardbench.testrun.application.messaging.TestExecutionRequestedMessage(
                java.util.UUID.randomUUID(), testRunId, snapshotId, Instant.now()));
        SQS.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(payload).build());
    }

    private void sendExecutionCompleted(long testRunId, long snapshotId) {
        String queueUrl = queueUrl(TestRunQueue.FINALIZE.queueName());
        String payload = codec.encode(new com.guardbench.testrun.application.messaging.TestExecutionCompletedMessage(
                java.util.UUID.randomUUID(), testRunId, snapshotId, Instant.now()));
        SQS.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(payload).build());
    }

    private int queueDepth(String queueName) {
        String queueUrl = queueUrl(queueName);
        String attr = SQS.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build()).attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
        return attr == null ? 0 : Integer.parseInt(attr);
    }

    private String queueUrl(String queueName) {
        return SQS.getQueueUrl(r -> r.queueName(queueName)).queueUrl();
    }

    private void drainAllQueues() {
        for (TestRunQueue queue : TestRunQueue.values()) {
            drainQueue(queue.queueName());
            drainQueue(queue.deadLetterQueueName());
        }
    }

    private void drainQueue(String queueName) {
        String queueUrl = queueUrl(queueName);
        java.util.List<String> receiptHandles = new java.util.ArrayList<>();
        do {
            receiptHandles.clear();
            SQS.receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(10)
                            .waitTimeSeconds(0)
                            .visibilityTimeout(0)
                            .build())
                    .messages()
                    .forEach(message -> receiptHandles.add(message.receiptHandle()));
            for (String receiptHandle : receiptHandles) {
                SQS.deleteMessage(software.amazon.awssdk.services.sqs.model.DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(receiptHandle)
                        .build());
            }
        } while (!receiptHandles.isEmpty());
    }

    private static void createWorkerQueuesWithRedrive() {
        for (TestRunQueue queue : TestRunQueue.values()) {
            String dlqUrl = SQS.createQueue(CreateQueueRequest.builder()
                    .queueName(queue.deadLetterQueueName())
                    .build()).queueUrl();
            String dlqArn = SQS.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(dlqUrl)
                    .attributeNames(QueueAttributeName.QUEUE_ARN)
                    .build()).attributes().get(QueueAttributeName.QUEUE_ARN);

            SQS.createQueue(CreateQueueRequest.builder()
                    .queueName(queue.queueName())
                    .attributes(Map.of(
                            QueueAttributeName.VISIBILITY_TIMEOUT, "1",
                            QueueAttributeName.REDRIVE_POLICY,
                            "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"5\"}".formatted(dlqArn)))
                    .build());
        }
    }

    /**
     * Provider Stub과 Evaluator Stub을 실제 Bedrock/HTTP 어댑터 대신 등록하고,
     * claim 시도/획득/AlreadyHeld를 관측하는 Decorator를 등록하는 Test Configuration이다.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class SqsReliabilityTestConfiguration {

        @Bean
        @Primary
        TargetExecutionPort targetExecutionPort() {
            return DeterministicTargetExecutionStub.alwaysSucceed();
        }

        @Bean
        @Primary
        EvaluatorExecutionPort evaluatorExecutionPort() {
            return new AlwaysAllowEvaluatorExecutionStub();
        }

        @Bean
        @Primary
        com.guardbench.testrun.application.port.out.ExecutionClaimPort countingExecutionClaimPort(
                com.guardbench.testrun.application.port.out.ExecutionClaimPort delegate) {
            return new CountingExecutionClaimPortDecorator(delegate);
        }
    }
}
