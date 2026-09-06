package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.guardbench.common.support.fixture.LogCapture;
import com.guardbench.testrun.application.port.out.ClaimResult;
import com.guardbench.testrun.application.port.out.ExecutionClaimPort;
import com.guardbench.testrun.application.port.out.ExecutionContext;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionPort;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionRequest;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionResult;
import com.guardbench.testrun.application.port.out.EvaluatorFailureCode;
import com.guardbench.testrun.application.port.out.TargetExecutionPort;
import com.guardbench.testrun.application.port.out.TargetExecutionRequest;
import com.guardbench.testrun.application.port.out.TargetExecutionResult;
import com.guardbench.testrun.application.port.out.TargetFailureCode;
import com.guardbench.testrun.application.port.out.TargetProviderException;
import com.guardbench.testrun.application.port.out.LoadExecutionContextPort;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.ApplicationResponse;
import com.guardbench.testrun.domain.EvaluationResult;
import com.guardbench.testrun.domain.TestExecutionErrorStage;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestExecution;
import com.guardbench.testrun.domain.TestExecutionErrorCode;
import com.guardbench.testrun.domain.TestExecutionId;
import com.guardbench.testrun.domain.TestExecutionStatus;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;

class ExecuteTestRunServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-26T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final long SNAPSHOT_ID = 100L;
    private static final long TEST_RUN_ID = 1L;
    private static final String TARGET_REFERENCE = "target-ref-1";
    private static final String INPUT_TEXT = "Hello, block this content";

    private FakeExecutionClaimPort claimPort;
    private FakeTestExecutionRepository executionRepository;
    private FakeLoadExecutionContextPort contextPort;
    private FakeTargetExecutionPort guardrailPort;
    private FakeEvaluatorExecutionPort evaluatorPort;
    private FakeOutboxPort outboxPort;
    private ExecuteTestRunService service;

    @BeforeEach
    void setUp() {
        claimPort = new FakeExecutionClaimPort();
        executionRepository = new FakeTestExecutionRepository();
        contextPort = new FakeLoadExecutionContextPort();
        guardrailPort = new FakeTargetExecutionPort();
        evaluatorPort = new FakeEvaluatorExecutionPort();
        outboxPort = new FakeOutboxPort();
        service = new ExecuteTestRunService(
                claimPort, executionRepository, contextPort,
                guardrailPort, evaluatorPort, outboxPort, new InlineTransactionalPhase(), FIXED_CLOCK
        );
    }

    @Nested
    @DisplayName("Application Target 호출 timing")
    class TargetTiming {
        @Test
        @DisplayName("Target 시작과 완료 timing을 응답·classifier·terminal 로그에 순서대로 연결한다")
        void logsTargetTimingBeforeResponseAndClassifier() {
            LogCapture capture = LogCapture.attach(ExecuteTestRunService.class);
            try {
                claimPort.willAcquire(SNAPSHOT_ID);
                contextPort.setContext(SNAPSHOT_ID, defaultContext());
                evaluatorPort.willReturn(EvaluatorExecutionResult.succeeded("ALLOW"));
                TargetExecutionPort targetStub = request -> {
                    assertTrue(capture.hasMessageContaining("Application Target 호출을 시작합니다"));
                    assertFalse(capture.hasMessageContaining("Application Target 호출을 완료했습니다"));
                    java.util.concurrent.locks.LockSupport.parkNanos(20_000_000);
                    return TargetExecutionResult.succeeded("safe response");
                };
                ExecuteTestRunService timedService = new ExecuteTestRunService(
                        claimPort, executionRepository, contextPort, targetStub, evaluatorPort,
                        outboxPort, new InlineTransactionalPhase(), FIXED_CLOCK);

                timedService.execute(SNAPSHOT_ID);

                String start = capture.firstMessageContaining("Application Target 호출을 시작합니다");
                String complete = capture.firstMessageContaining("Application Target 호출을 완료했습니다");
                assertTrue(start.contains("testRunId=1 snapshotId=100 attemptCount=1"));
                assertTrue(complete.contains("testRunId=1 snapshotId=100 attemptCount=1"));
                assertTrue(Long.parseLong(complete.split("durationMs=")[1]) >= 10);
                assertFalse(start.contains(INPUT_TEXT));
                assertFalse(complete.contains("safe response"));
                List<String> messages = capture.messages();
                List<String> ordered = List.of(start, complete,
                        capture.firstMessageContaining("Application response 진단 정보"),
                        capture.firstMessageContaining("Classifier 호출을 시작합니다"),
                        capture.firstMessageContaining("Classifier 판정을 완료했습니다"),
                        capture.firstMessageContaining("terminal 결과를 저장했습니다"));
                for (int i = 1; i < ordered.size(); i++) {
                    assertTrue(messages.indexOf(ordered.get(i - 1)) < messages.indexOf(ordered.get(i)));
                }
            } finally {
                capture.detach();
            }
        }

        @Test
        @DisplayName("Target 실패 반환과 provider 예외 모두 timing과 안전한 오류 코드를 남긴다")
        void logsReturnedAndThrownTargetFailures() {
            for (boolean throwsException : List.of(false, true)) {
                LogCapture capture = LogCapture.attach(ExecuteTestRunService.class);
                try {
                    claimPort.willAcquire(SNAPSHOT_ID);
                    contextPort.setContext(SNAPSHOT_ID, defaultContext());
                    if (throwsException) guardrailPort.willThrow(TargetFailureCode.PROVIDER_TIMEOUT);
                    else guardrailPort.willReturn(TargetExecutionResult.failed(TargetFailureCode.PROVIDER_TIMEOUT));

                    assertEquals(ExecuteTestRunService.ExecutionOutcome.PROVIDER_FAILED_RETRYABLE,
                            service.execute(SNAPSHOT_ID));

                    String failure = capture.firstMessageContaining("Application Target 호출에 실패했습니다");
                    assertTrue(failure.contains("testRunId=1 snapshotId=100 attemptCount=1 durationMs="));
                    assertTrue(failure.contains("errorStage=APPLICATION_TARGET errorCode=PROVIDER_TIMEOUT"));
                    assertFalse(failure.contains(INPUT_TEXT));
                    assertFalse(capture.hasMessageContaining("Application Target 호출을 완료했습니다"));
                    assertEquals(0, evaluatorPort.callCount());
                } finally {
                    capture.detach();
                }
            }
        }

        @Test
        @DisplayName("예상하지 못한 Target 예외의 원문과 credential은 timing 로그에 노출하지 않는다")
        void sanitizesUnexpectedTargetFailureTiming() {
            LogCapture capture = LogCapture.attach(ExecuteTestRunService.class);
            try {
                claimPort.willAcquire(SNAPSHOT_ID);
                contextPort.setContext(SNAPSHOT_ID, defaultContext());
                TargetExecutionPort targetStub = request -> {
                    throw new IllegalStateException("Authorization: Bearer secret https://secret-endpoint " + INPUT_TEXT);
                };
                ExecuteTestRunService failedService = new ExecuteTestRunService(
                        claimPort, executionRepository, contextPort, targetStub, evaluatorPort,
                        outboxPort, new InlineTransactionalPhase(), FIXED_CLOCK);

                failedService.execute(SNAPSHOT_ID);

                String failure = capture.firstMessageContaining("Application Target 호출에 실패했습니다");
                assertTrue(failure.contains("errorCode=PROVIDER_UNAVAILABLE"));
                assertTrue(failure.contains("testRunId=1 snapshotId=100 attemptCount=1 durationMs="));
                for (String message : capture.messages()) {
                    assertFalse(message.contains("secret"));
                    assertFalse(message.contains("Authorization"));
                    assertFalse(message.contains(INPUT_TEXT));
                }
            } finally {
                capture.detach();
            }
        }
    }

    @Nested
    @DisplayName("Application → Evaluator 분리 흐름")
    class ApplicationEvaluatorFlow {

        @Test
        @DisplayName("Application response와 Evaluator verdict를 별도 저장한다")
        void storesApplicationResponseAndEvaluatorVerdictSeparately() {
            claimPort.willAcquire(SNAPSHOT_ID);
            contextPort.setContext(SNAPSHOT_ID, new ExecutionContext(
                    TARGET_REFERENCE, INPUT_TEXT, TEST_RUN_ID, "evaluator-ref"));
            guardrailPort.willReturn(TargetExecutionResult.succeeded("natural language response"));
            evaluatorPort.willReturn(EvaluatorExecutionResult.succeeded("BLOCK"));

            service.execute(SNAPSHOT_ID);

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals("natural language response", saved.applicationResponse().value());
            assertEquals(Action.BLOCK, saved.evaluationResult().action());
            assertEquals(1, evaluatorPort.callCount());
            assertEquals("natural language response", evaluatorPort.lastRequest().applicationResponse());
            assertEquals("evaluator-ref", evaluatorPort.lastRequest().evaluatorReference().value());
        }

        @Test
        @DisplayName("짧은 Application response는 길이만 로그에 남기고 원문을 기록하지 않는다")
        void doesNotLogShortApplicationResponse() {
            String response = "sensitive response that must not be logged";
            LogCapture logCapture = LogCapture.attach(ExecuteTestRunService.class);
            try {
                claimPort.willAcquire(SNAPSHOT_ID);
                contextPort.setContext(SNAPSHOT_ID, defaultContext());
                guardrailPort.willReturn(TargetExecutionResult.succeeded(response));
                evaluatorPort.willReturn(EvaluatorExecutionResult.succeeded("ALLOW"));

                service.execute(SNAPSHOT_ID);

                assertTrue(logCapture.hasMessageContaining("responseLength=" + response.length()));
                assertFalse(logCapture.hasMessageContaining(response));
                assertFalse(logCapture.hasMessageContaining("applicationResponsePreview"));
                assertEquals(response, executionRepository.savedExecutions().getFirst().applicationResponse().value());
                assertEquals(response, evaluatorPort.lastRequest().applicationResponse());
            } finally {
                logCapture.detach();
            }
        }

        @Test
        @DisplayName("긴 Application response도 원문이나 preview 없이 길이만 로그에 남긴다")
        void doesNotLogLongApplicationResponse() {
            String response = "가".repeat(600);
            LogCapture logCapture = LogCapture.attach(ExecuteTestRunService.class);
            try {
                claimPort.willAcquire(SNAPSHOT_ID);
                contextPort.setContext(SNAPSHOT_ID, defaultContext());
                guardrailPort.willReturn(TargetExecutionResult.succeeded(response));
                evaluatorPort.willReturn(EvaluatorExecutionResult.succeeded("ALLOW"));

                service.execute(SNAPSHOT_ID);

                assertTrue(logCapture.hasMessageContaining("responseLength=600"));
                assertFalse(logCapture.hasMessageContaining(response));
                assertFalse(logCapture.hasMessageContaining("applicationResponsePreview"));
                assertEquals(response, executionRepository.savedExecutions().getFirst().applicationResponse().value());
                assertEquals(response, evaluatorPort.lastRequest().applicationResponse());
                String classifierLog = logCapture.firstMessageContaining("Classifier 판정을 완료했습니다");
                assertTrue(classifierLog.contains("classifierOutput=ALLOW"));
                assertTrue(classifierLog.contains("evaluatorVerdict=ALLOW"));
            } finally {
                logCapture.detach();
            }
        }

        @Test
        @DisplayName("Application Target 실패 시 Evaluator를 호출하지 않는다")
        void doesNotEvaluateTargetFailure() {
            claimPort.willAcquire(SNAPSHOT_ID);
            contextPort.setContext(SNAPSHOT_ID, new ExecutionContext(
                    TARGET_REFERENCE, INPUT_TEXT, TEST_RUN_ID, "evaluator-ref"));
            guardrailPort.willReturn(TargetExecutionResult.failed(TargetFailureCode.TARGET_NOT_FOUND));

            service.execute(SNAPSHOT_ID);

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.FAILED, saved.status());
            assertEquals(TestExecutionErrorStage.APPLICATION_TARGET, saved.error().stage());
            assertEquals(0, evaluatorPort.callCount());
        }

        @Test
        @DisplayName("Evaluator 실패 시 response만 보존하고 verdict는 저장하지 않는다")
        void storesEvaluatorFailureWithoutVerdict() {
            LogCapture logCapture = LogCapture.attach(ExecuteTestRunService.class);
            try {
                claimPort.willAcquire(SNAPSHOT_ID);
                contextPort.setContext(SNAPSHOT_ID, new ExecutionContext(
                        TARGET_REFERENCE, INPUT_TEXT, TEST_RUN_ID, "evaluator-ref"));
                guardrailPort.willReturn(TargetExecutionResult.succeeded("natural language response"));
                evaluatorPort.willReturn(EvaluatorExecutionResult.failed(EvaluatorFailureCode.EVALUATOR_NOT_FOUND));

                service.execute(SNAPSHOT_ID);

                TestExecution saved = executionRepository.savedExecutions().getFirst();
                assertEquals(TestExecutionStatus.FAILED, saved.status());
                assertEquals("natural language response", saved.applicationResponse().value());
                assertEquals(TestExecutionErrorStage.EVALUATOR, saved.error().stage());
                assertEquals(null, saved.evaluationResult());
                String classifierLog = logCapture.firstMessageContaining("Classifier 판정에 실패했습니다");
                assertTrue(classifierLog.contains("classifierOutput=null"));
                assertTrue(classifierLog.contains("evaluatorVerdict=null"));
                assertTrue(classifierLog.contains("errorCode=EVALUATOR_NOT_FOUND"));
            } finally {
                logCapture.detach();
            }
        }
    }

    @Nested
    @DisplayName("정상 실행 흐름")
    class HappyPath {

        @Test
        @DisplayName("ALLOW 결과의 Target 실행을 SUCCEEDED로 저장하고 Outbox를 생성한다")
        void executesAllow() {
            claimPort.willAcquire(SNAPSHOT_ID);
            contextPort.setContext(SNAPSHOT_ID, defaultContext());
            guardrailPort.willReturn(TargetExecutionResult.succeeded("ALLOW"));

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);
            assertTrue(outcome.shouldAcknowledge());

            // terminal TestExecution이 저장되었다
            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.SUCCEEDED, saved.status());
            assertEquals(new EvaluationResult(Action.ALLOW), saved.evaluationResult());
            assertEquals(FIXED_NOW, saved.startedAt());
            assertEquals(FIXED_NOW, saved.completedAt());

            // TestExecutionCompleted Outbox가 저장되었다
            assertEquals(1, outboxPort.savedEvents().size());
            OutboxEventRecord event = outboxPort.savedEvents().getFirst();
            assertEquals("TestExecutionCompleted", event.eventType());
            assertTrue(event.payload().contains("\"snapshotId\":" + SNAPSHOT_ID));
            assertTrue(!event.payload().contains("targetType"));
            assertTrue(event.payload().contains("\"testRunId\":" + TEST_RUN_ID));
        }

        @Test
        @DisplayName("BLOCK 결과의 Target 실행을 SUCCEEDED로 저장한다")
        void executesBlock() {
            claimPort.willAcquire(SNAPSHOT_ID);
            contextPort.setContext(SNAPSHOT_ID, defaultContext());
            guardrailPort.willReturn(TargetExecutionResult.succeeded("BLOCK"));

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.SUCCEEDED, saved.status());
            assertEquals(new EvaluationResult(Action.BLOCK), saved.evaluationResult());
        }

        @Test
        @DisplayName("Provider 호출에 올바른 target reference와 input을 전달한다")
        void passesCorrectRequestToProvider() {
            claimPort.willAcquire(SNAPSHOT_ID);
            contextPort.setContext(SNAPSHOT_ID, defaultContext());
            guardrailPort.willReturn(TargetExecutionResult.succeeded("ALLOW"));

            service.execute(SNAPSHOT_ID);

            TargetExecutionRequest request = guardrailPort.lastRequest();
            assertNotNull(request);
            assertEquals(TARGET_REFERENCE, request.targetReference().value());
            assertEquals(INPUT_TEXT, request.input());
        }
    }

    @Nested
    @DisplayName("멱등성과 중복 처리")
    class Idempotency {

        @Test
        @DisplayName("이미 terminal TestExecution이 있으면 ALREADY_TERMINAL을 반환한다")
        void alreadyTerminal() {
            TestExecutionId id = new TestExecutionId(new TestCaseSnapshotId(SNAPSHOT_ID));
            executionRepository.store(
                    TestExecution.succeeded(id, new ApplicationResponse("response"),
                            new EvaluationResult(Action.ALLOW), FIXED_NOW, FIXED_NOW)
            );

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.ALREADY_TERMINAL, outcome);
            assertTrue(outcome.shouldAcknowledge());
            // Provider가 호출되지 않았다
            assertTrue(guardrailPort.callCount() == 0);
            // Outbox가 추가되지 않았다
            assertTrue(outboxPort.savedEvents().isEmpty());
        }

        @Test
        @DisplayName("FAILED terminal이 존재해도 ALREADY_TERMINAL을 반환한다")
        void alreadyTerminalFailed() {
            TestExecutionId id = new TestExecutionId(new TestCaseSnapshotId(SNAPSHOT_ID));
            executionRepository.store(
                    TestExecution.failed(
                            id,
                            new com.guardbench.testrun.domain.TestExecutionError(
                                    TestExecutionErrorStage.APPLICATION_TARGET,
                                    TestExecutionErrorCode.TARGET_NOT_FOUND,
                                    "Guardrail target was not found."
                            ),
                            FIXED_NOW.minusSeconds(10),
                            FIXED_NOW
                    )
            );

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.ALREADY_TERMINAL, outcome);
            assertTrue(outcome.shouldAcknowledge());
        }
    }

    @Nested
    @DisplayName("Claim 경합")
    class ClaimContention {

        @Test
        @DisplayName("다른 Worker가 유효한 claim을 보유하면 CLAIM_HELD_BY_OTHER를 반환한다")
        void claimHeldByOther() {
            claimPort.willBeHeld(SNAPSHOT_ID);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.CLAIM_HELD_BY_OTHER, outcome);
            assertTrue(!outcome.shouldAcknowledge());
            assertEquals(0, guardrailPort.callCount());
        }

        @Test
        @DisplayName("Provider 호출 후 claim을 잃으면 CLAIM_LOST_AFTER_EXECUTION을 반환한다")
        void claimLostAfterExecution() {
            claimPort.willAcquire(SNAPSHOT_ID);
            claimPort.setIsHeldByResult(false); // 재검증 실패
            contextPort.setContext(SNAPSHOT_ID, defaultContext());
            guardrailPort.willReturn(TargetExecutionResult.succeeded("ALLOW"));

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.CLAIM_LOST_AFTER_EXECUTION, outcome);
            assertTrue(!outcome.shouldAcknowledge());
            // 결과가 저장되지 않았다
            assertTrue(executionRepository.savedExecutions().isEmpty());
            assertTrue(outboxPort.savedEvents().isEmpty());
        }
    }

    @Nested
    @DisplayName("Provider 실패 처리")
    class ProviderFailure {

        @Test
        @DisplayName("retryable 오류 + attempt 미초과 시 PROVIDER_FAILED_RETRYABLE을 반환한다")
        void retryableUnavailable() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, 1);
            contextPort.setContext(SNAPSHOT_ID, defaultContext());
            guardrailPort.willThrow(TargetFailureCode.PROVIDER_UNAVAILABLE);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.PROVIDER_FAILED_RETRYABLE, outcome);
            assertTrue(!outcome.shouldAcknowledge());
            // terminal 결과가 저장되지 않았다
            assertTrue(executionRepository.savedExecutions().isEmpty());
        }

        @Test
        @DisplayName("retryable TIMEOUT + attempt 미초과 시 PROVIDER_FAILED_RETRYABLE을 반환한다")
        void retryableTimeout() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, 2);
            contextPort.setContext(SNAPSHOT_ID, defaultContext());
            guardrailPort.willThrow(TargetFailureCode.PROVIDER_TIMEOUT);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.PROVIDER_FAILED_RETRYABLE, outcome);
        }

        @Test
        @DisplayName("retryable 오류 + attempt 소진 시 TIMED_OUT으로 저장한다 (PROVIDER_TIMEOUT)")
        void exhaustedTimeout() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, 3);
            contextPort.setContext(SNAPSHOT_ID, defaultContext());
            guardrailPort.willThrow(TargetFailureCode.PROVIDER_TIMEOUT);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);
            assertTrue(outcome.shouldAcknowledge());

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.TIMED_OUT, saved.status());
            assertEquals(TestExecutionErrorCode.PROVIDER_TIMEOUT, saved.error().code());
            assertEquals(1, outboxPort.savedEvents().size());
        }

        @Test
        @DisplayName("retryable 오류(PROVIDER_UNAVAILABLE) + attempt 소진 시 FAILED로 저장한다")
        void exhaustedUnavailable() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, 3);
            contextPort.setContext(SNAPSHOT_ID, defaultContext());
            guardrailPort.willThrow(TargetFailureCode.PROVIDER_UNAVAILABLE);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.FAILED, saved.status());
            assertEquals(TestExecutionErrorCode.PROVIDER_UNAVAILABLE, saved.error().code());
        }

        @Test
        @DisplayName("Run 201 재현: PROVIDER_UNAVAILABLE 재시도 중에는 WARN으로, attempt 소진 후 최종 저장 시 "
                + "errorCode/retryable=true가 구조화 로그에 남는다")
        void providerUnavailableRetryIsObservableInApplicationLog() {
            LogCapture logCapture = LogCapture.attach(ExecuteTestRunService.class);
            try {
                claimPort.willAcquireWithAttempt(SNAPSHOT_ID, 1);
                contextPort.setContext(SNAPSHOT_ID, defaultContext());
                guardrailPort.willThrow(TargetFailureCode.PROVIDER_UNAVAILABLE);

                ExecuteTestRunService.ExecutionOutcome retryOutcome = service.execute(SNAPSHOT_ID);
                assertEquals(ExecuteTestRunService.ExecutionOutcome.PROVIDER_FAILED_RETRYABLE, retryOutcome);

                String retryLog = logCapture.firstMessageContaining("실패로 재시도합니다");
                assertTrue(retryLog.contains("errorCode=PROVIDER_UNAVAILABLE"));
                assertTrue(retryLog.contains("retryable=true"));
                assertTrue(retryLog.contains("testRunId=" + TEST_RUN_ID));
                assertTrue(retryLog.contains("snapshotId=" + SNAPSHOT_ID));

                claimPort.willAcquireWithAttempt(SNAPSHOT_ID, 3);
                ExecuteTestRunService.ExecutionOutcome terminalOutcome = service.execute(SNAPSHOT_ID);
                assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, terminalOutcome);

                String terminalLog = logCapture.firstMessageContaining("terminal 결과를 저장했습니다");
                assertTrue(terminalLog.contains("errorCode=PROVIDER_UNAVAILABLE"));
                assertTrue(terminalLog.contains("errorStage=APPLICATION_TARGET"));
                assertTrue(terminalLog.contains("retryable=true"));
            } finally {
                logCapture.detach();
            }
        }

        @Test
        @DisplayName("영구 오류(TARGET_NOT_FOUND)는 첫 시도에서 FAILED로 저장한다")
        void permanentFailureFirstAttempt() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, 1);
            contextPort.setContext(SNAPSHOT_ID, defaultContext());
            guardrailPort.willThrow(TargetFailureCode.TARGET_NOT_FOUND);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);
            assertTrue(outcome.shouldAcknowledge());

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.FAILED, saved.status());
            assertEquals(TestExecutionErrorCode.TARGET_NOT_FOUND, saved.error().code());
        }

        @Test
        @DisplayName("TARGET_ACCESS_DENIED는 영구 실패로 첫 시도에서 저장한다")
        void permanentAccessDenied() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, 1);
            contextPort.setContext(SNAPSHOT_ID, defaultContext());
            guardrailPort.willThrow(TargetFailureCode.TARGET_ACCESS_DENIED);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.FAILED, saved.status());
            assertEquals(TestExecutionErrorCode.TARGET_ACCESS_DENIED, saved.error().code());
        }

        @Test
        @DisplayName("TARGET_CONFIGURATION_INVALID는 영구 실패로 저장한다")
        void permanentConfigInvalid() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, 2);
            contextPort.setContext(SNAPSHOT_ID, defaultContext());
            guardrailPort.willThrow(TargetFailureCode.TARGET_CONFIGURATION_INVALID);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.FAILED, saved.status());
            assertEquals(TestExecutionErrorCode.TARGET_CONFIGURATION_INVALID, saved.error().code());
        }

        @Test
        @DisplayName("Run 101 재현: TARGET_CONFIGURATION_INVALID는 재시도 없이 첫 시도에서 terminal 로그에 "
                + "errorStage/errorCode/retryable=false로 남는다")
        void targetConfigurationInvalidIsObservableInApplicationLogWithoutRetry() {
            LogCapture logCapture = LogCapture.attach(ExecuteTestRunService.class);
            try {
                claimPort.willAcquireWithAttempt(SNAPSHOT_ID, 1);
                contextPort.setContext(SNAPSHOT_ID, defaultContext());
                guardrailPort.willThrow(TargetFailureCode.TARGET_CONFIGURATION_INVALID);

                ExecuteTestRunService.ExecutionOutcome outcome = service.execute(SNAPSHOT_ID);

                assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);
                assertFalse(logCapture.hasMessageContaining("실패로 재시도합니다"));

                String terminalLog = logCapture.firstMessageContaining("terminal 결과를 저장했습니다");
                assertTrue(terminalLog.contains("testRunId=" + TEST_RUN_ID));
                assertTrue(terminalLog.contains("snapshotId=" + SNAPSHOT_ID));
                assertTrue(terminalLog.contains("errorStage=APPLICATION_TARGET"));
                assertTrue(terminalLog.contains("errorCode=TARGET_CONFIGURATION_INVALID"));
                assertTrue(terminalLog.contains("retryable=false"));
            } finally {
                logCapture.detach();
            }
        }

        @Test
        @DisplayName("PROVIDER_RESPONSE_INVALID는 영구 실패로 저장한다")
        void permanentResponseInvalid() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, 1);
            contextPort.setContext(SNAPSHOT_ID, defaultContext());
            guardrailPort.willReturn(TargetExecutionResult.failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID));

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.FAILED, saved.status());
            assertEquals(TestExecutionErrorCode.PROVIDER_RESPONSE_INVALID, saved.error().code());
        }
    }

    @Nested
    @DisplayName("컨텍스트 조회 실패")
    class ContextNotFound {

        @Test
        @DisplayName("Snapshot이 없으면 CONTEXT_NOT_FOUND를 반환한다")
        void snapshotNotFound() {
            claimPort.willAcquire(SNAPSHOT_ID);
            // contextPort에 아무것도 설정하지 않음

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.CONTEXT_NOT_FOUND, outcome);
            assertTrue(outcome.shouldAcknowledge());
            assertEquals(0, guardrailPort.callCount());
        }
    }

    @Nested
    @DisplayName("Deduplication key 계약")
    class DeduplicationKeys {

        @Test
        @DisplayName("Outbox dedup key는 TestExecutionCompleted:snapshotId 형식이다")
        void deduplicationKeyFormat() {
            claimPort.willAcquire(SNAPSHOT_ID);
            contextPort.setContext(SNAPSHOT_ID, defaultContext());
            guardrailPort.willReturn(TargetExecutionResult.succeeded("BLOCK"));

            service.execute(SNAPSHOT_ID);

            OutboxEventRecord event = outboxPort.savedEvents().getFirst();
            assertEquals("TestExecutionCompleted:" + SNAPSHOT_ID, event.deduplicationKey());
        }
    }

    @Nested
    @DisplayName("Ack/Nack 결과")
    class AckNack {

        @Test
        @DisplayName("EXECUTED는 ack 결과다")
        void executedIsAck() {
            assertTrue(ExecuteTestRunService.ExecutionOutcome.EXECUTED.shouldAcknowledge());
        }

        @Test
        @DisplayName("ALREADY_TERMINAL은 ack 결과다")
        void alreadyTerminalIsAck() {
            assertTrue(ExecuteTestRunService.ExecutionOutcome.ALREADY_TERMINAL.shouldAcknowledge());
        }

        @Test
        @DisplayName("CONTEXT_NOT_FOUND는 ack 결과다")
        void contextNotFoundIsAck() {
            assertTrue(ExecuteTestRunService.ExecutionOutcome.CONTEXT_NOT_FOUND.shouldAcknowledge());
        }

        @Test
        @DisplayName("CLAIM_HELD_BY_OTHER는 nack 결과다")
        void claimHeldIsNack() {
            assertTrue(!ExecuteTestRunService.ExecutionOutcome.CLAIM_HELD_BY_OTHER.shouldAcknowledge());
        }

        @Test
        @DisplayName("CLAIM_LOST_AFTER_EXECUTION은 nack 결과다")
        void claimLostIsNack() {
            assertTrue(!ExecuteTestRunService.ExecutionOutcome.CLAIM_LOST_AFTER_EXECUTION.shouldAcknowledge());
        }

        @Test
        @DisplayName("PROVIDER_FAILED_RETRYABLE은 nack 결과다")
        void providerRetryableIsNack() {
            assertTrue(!ExecuteTestRunService.ExecutionOutcome.PROVIDER_FAILED_RETRYABLE.shouldAcknowledge());
        }
    }

    // ─── Test Fixtures ────────────────────────────────────────────────────────

    private static ExecutionContext defaultContext() {
        return new ExecutionContext(TARGET_REFERENCE, INPUT_TEXT, TEST_RUN_ID, "evaluator-ref");
    }

    // ─── Fake Adapters ────────────────────────────────────────────────────────

    private static final class FakeExecutionClaimPort implements ExecutionClaimPort {
        private final Map<Long, ClaimResult> acquireResults = new HashMap<>();
        private boolean isHeldByResult = true;

        void willAcquire(long snapshotId) {
            willAcquireWithAttempt(snapshotId, 1);
        }

        void willAcquireWithAttempt(long snapshotId, int attemptCount) {
            UUID token = UUID.randomUUID();
            acquireResults.put(snapshotId, new ClaimResult.Acquired(token, attemptCount));
        }

        void willBeHeld(long snapshotId) {
            acquireResults.put(snapshotId, new ClaimResult.AlreadyHeld());
        }

        void setIsHeldByResult(boolean result) {
            this.isHeldByResult = result;
        }

        @Override
        public ClaimResult tryAcquire(long snapshotId) {
            return acquireResults.getOrDefault(snapshotId, new ClaimResult.AlreadyHeld());
        }

        @Override
        public boolean isHeldBy(long snapshotId, UUID claimToken) {
            return isHeldByResult;
        }
    }

    private static final class FakeTestExecutionRepository implements TestExecutionRepository {
        private final Map<TestExecutionId, TestExecution> preLoaded = new HashMap<>();
        private final List<TestExecution> saved = new ArrayList<>();

        void store(TestExecution execution) {
            preLoaded.put(execution.id(), execution);
        }

        List<TestExecution> savedExecutions() {
            return saved;
        }

        @Override
        public Optional<TestExecution> findById(TestExecutionId id) {
            if (preLoaded.containsKey(id)) {
                return Optional.of(preLoaded.get(id));
            }
            return saved.stream()
                    .filter(e -> e.id().equals(id))
                    .findFirst();
        }

        @Override
        public void save(TestExecution execution) {
            saved.add(execution);
        }
    }

    private static final class FakeLoadExecutionContextPort implements LoadExecutionContextPort {
        private final Map<Long, ExecutionContext> contexts = new HashMap<>();

        void setContext(long snapshotId, ExecutionContext context) {
            contexts.put(snapshotId, context);
        }

        @Override
        public Optional<ExecutionContext> load(long snapshotId) {
            return Optional.ofNullable(contexts.get(snapshotId));
        }
    }

    private static final class FakeTargetExecutionPort implements TargetExecutionPort {
        private TargetExecutionResult successResult;
        private TargetFailureCode throwFailureCode;
        private TargetExecutionRequest lastRequest;
        private int callCount;

        void willReturn(TargetExecutionResult result) {
            this.successResult = result;
            this.throwFailureCode = null;
        }

        void willThrow(TargetFailureCode failureCode) {
            this.throwFailureCode = failureCode;
            this.successResult = null;
        }

        TargetExecutionRequest lastRequest() {
            return lastRequest;
        }

        int callCount() {
            return callCount;
        }

        @Override
        public TargetExecutionResult execute(TargetExecutionRequest request) {
            this.lastRequest = request;
            this.callCount++;
            if (throwFailureCode != null) {
                throw new TargetProviderException(throwFailureCode);
            }
            return successResult;
        }
    }

    private static final class FakeEvaluatorExecutionPort implements EvaluatorExecutionPort {
        private EvaluatorExecutionResult result;
        private EvaluatorExecutionRequest lastRequest;
        private int callCount;

        void willReturn(EvaluatorExecutionResult result) {
            this.result = result;
        }

        EvaluatorExecutionRequest lastRequest() {
            return lastRequest;
        }

        int callCount() {
            return callCount;
        }

        @Override
        public EvaluatorExecutionResult evaluate(EvaluatorExecutionRequest request) {
            lastRequest = request;
            callCount++;
            return result != null ? result : EvaluatorExecutionResult.succeeded(request.applicationResponse());
        }
    }

    private static final class FakeOutboxPort implements OutboxPort {
        private final List<OutboxEventRecord> events = new ArrayList<>();

        List<OutboxEventRecord> savedEvents() {
            return events;
        }

        @Override
        public void save(OutboxEventRecord event) {
            events.add(event);
        }

        @Override
        public List<OutboxEventRecord> findPendingBatch(int batchSize) {
            return events.stream().limit(batchSize).toList();
        }

        @Override
        public void markPublished(java.util.Collection<UUID> eventIds) {
            // not used
        }
    }
}
