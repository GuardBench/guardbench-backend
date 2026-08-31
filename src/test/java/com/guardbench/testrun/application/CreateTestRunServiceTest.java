package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.TestCaseSnapshotSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreateTestRunServiceTest {

    private static final String MODEL = "test-model";
    private static final TestCaseSnapshotSource SOURCE = new TestCaseSnapshotSource(
            1L, 501L, "case", "input", "ALLOW", "HIGH", "category");

    @Test
    @DisplayName("활성 TestCase가 있는 TestSuite로 접수하면 QUEUED TestRun과 Snapshot, Outbox 이벤트를 함께 저장한다")
    void createsTestRunSnapshotsAndOutboxEventTogether() {
        CreateTestRunFakeAdapters adapters = new CreateTestRunFakeAdapters();
        adapters.givenTestSuite(1L, List.of(SOURCE, SOURCE));
        CreateTestRunService service = newService(adapters);
        TestRunCreateCommand command = new TestRunCreateCommand(
                1L, "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", "v1", MODEL, profile(), null);

        TestRunCreateResult result = service.create(command);

        assertEquals(1L, result.testSuiteId());
        assertEquals("QUEUED", result.status());
        assertEquals(2, result.testCaseCount());
        assertEquals(CreateTestRunFakeAdapters.FIXED_NOW, result.createdAt());
        assertEquals(2, adapters.savedSnapshots().size());
        assertEquals(1, adapters.savedEvaluatorReferenceCount());
        assertEquals(1, adapters.savedOutboxEvents().size());
        OutboxEventRecord event = adapters.savedOutboxEvents().getFirst();
        assertEquals("TestRunRequested", event.eventType());
        assertEquals("TestRunRequested:" + result.id(), event.deduplicationKey());
        assertTrue(event.payload().contains("\"testRunId\":" + result.id()));
    }

    @Test
    @DisplayName("존재하지 않는 TestSuite로 접수하면 TEST_SUITE_NOT_FOUND 예외를 던진다")
    void throwsNotFoundWhenTestSuiteDoesNotExist() {
        CreateTestRunFakeAdapters adapters = new CreateTestRunFakeAdapters();
        CreateTestRunService service = newService(adapters);
        TestRunCreateCommand command = new TestRunCreateCommand(
                404L, "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", "v1", MODEL, profile(), null);

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.create(command));

        assertEquals(ApplicationErrorCode.TEST_SUITE_NOT_FOUND, exception.errorCode());
    }

    @Test
    @DisplayName("활성 TestCase가 없는 TestSuite로 접수하면 TEST_SUITE_EMPTY 예외를 던진다")
    void throwsEmptyWhenTestSuiteHasNoActiveTestCase() {
        CreateTestRunFakeAdapters adapters = new CreateTestRunFakeAdapters();
        adapters.givenTestSuite(2L, List.of());
        CreateTestRunService service = newService(adapters);
        TestRunCreateCommand command = new TestRunCreateCommand(
                2L, "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", "v1", MODEL, profile(), null);

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.create(command));

        assertEquals(ApplicationErrorCode.TEST_SUITE_EMPTY, exception.errorCode());
    }

    @Test
    @DisplayName("같은 Idempotency-Key와 같은 요청을 재전송하면 새 TestRun을 만들지 않고 기존 TestRun을 반환한다")
    void reusesExistingTestRunForSameKeyAndSameRequest() {
        CreateTestRunFakeAdapters adapters = new CreateTestRunFakeAdapters();
        adapters.givenTestSuite(1L, List.of(SOURCE));
        CreateTestRunService service = newService(adapters);
        TestRunCreateCommand command = new TestRunCreateCommand(
                1L, "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", "v1", MODEL, profile(), "idem-key-1");

        TestRunCreateResult first = service.create(command);
        TestRunCreateResult second = service.create(command);

        assertEquals(first.id(), second.id());
        assertEquals(1, adapters.savedOutboxEvents().size());
    }

    @Test
    @DisplayName("같은 Idempotency-Key를 다른 요청에 재사용하면 IDEMPOTENCY_KEY_CONFLICT 예외를 던진다")
    void throwsConflictWhenSameKeyReusedForDifferentRequest() {
        CreateTestRunFakeAdapters adapters = new CreateTestRunFakeAdapters();
        adapters.givenTestSuite(1L, List.of(SOURCE));
        adapters.givenTestSuite(2L, List.of(SOURCE));
        CreateTestRunService service = newService(adapters);
        TestRunCreateCommand first = new TestRunCreateCommand(
                1L, "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", "v1", MODEL, profile(), "idem-key-2");
        TestRunCreateCommand different = new TestRunCreateCommand(
                2L, "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", "v1", MODEL, profile(), "idem-key-2");

        service.create(first);
        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.create(different));

        assertEquals(ApplicationErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.errorCode());
    }

    @Test
    @DisplayName("같은 Idempotency-Key를 다른 model에 재사용하면 충돌로 거부한다")
    void throwsConflictWhenSameKeyReusedForDifferentModel() {
        CreateTestRunFakeAdapters adapters = new CreateTestRunFakeAdapters();
        adapters.givenTestSuite(1L, List.of(SOURCE));
        CreateTestRunService service = newService(adapters);
        TestRunCreateCommand firstModel = new TestRunCreateCommand(
                1L, "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", "v1", "model-a", profile(), "idem-model-key");
        TestRunCreateCommand secondModel = new TestRunCreateCommand(
                1L, "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", "v1", "model-b", profile(), "idem-model-key");

        service.create(firstModel);
        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.create(secondModel));

        assertEquals(ApplicationErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.errorCode());
    }

    @Test
    @DisplayName("Idempotency-Key를 생략하면 매 요청마다 새로운 TestRun을 생성한다")
    void createsNewTestRunWhenIdempotencyKeyOmitted() {
        CreateTestRunFakeAdapters adapters = new CreateTestRunFakeAdapters();
        adapters.givenTestSuite(1L, List.of(SOURCE));
        CreateTestRunService service = newService(adapters);
        TestRunCreateCommand command = new TestRunCreateCommand(
                1L, "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", "v1", MODEL, profile(), null);

        TestRunCreateResult first = service.create(command);
        TestRunCreateResult second = service.create(command);

        assertTrue(first.id() != second.id());
        assertEquals(2, adapters.savedOutboxEvents().size());
    }

    @Test
    @DisplayName("지원하지 않는 EvaluationProfile이면 EvaluatorReference와 TestRun을 저장하지 않고 거부한다")
    void rejectsUnsupportedEvaluationProfile() {
        CreateTestRunFakeAdapters adapters = new CreateTestRunFakeAdapters();
        adapters.givenTestSuite(1L, List.of(SOURCE));
        adapters.evaluatorCatalogDoesNotSupportProfile();

        ApplicationException exception = assertThrows(ApplicationException.class, () -> newService(adapters).create(
                new TestRunCreateCommand(1L, "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", null,
                        MODEL, profile(), null)));

        assertEquals(ApplicationErrorCode.EVALUATION_PROFILE_NOT_SUPPORTED, exception.errorCode());
        assertEquals(0, adapters.savedEvaluatorReferenceCount());
        assertEquals(0, adapters.savedOutboxEvents().size());
    }

    private static CreateTestRunService newService(CreateTestRunFakeAdapters adapters) {
        return new CreateTestRunService(
                adapters,
                adapters,
                adapters.nextTestRunIdPort(),
                adapters.nextTestCaseSnapshotIdPort(),
                adapters,
                adapters,
                adapters,
                adapters,
                adapters,
                adapters,
                adapters,
                adapters.clock
        );
    }

    private static com.guardbench.testrun.domain.EvaluationProfile profile() {
        return new com.guardbench.testrun.domain.EvaluationProfile(List.of("PROMPT_INJECTION"), "STANDARD");
    }
}
