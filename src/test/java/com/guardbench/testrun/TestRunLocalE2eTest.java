package com.guardbench.testrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.MatchType;
import org.mockserver.verify.VerificationTimes;

import com.guardbench.testrun.application.messaging.TestRunQueue;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testsupport.PostgresTestConfiguration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;
import software.amazon.awssdk.services.sagemakerruntime.model.ValidationErrorException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 실제 Spring Boot HTTP API부터 PostgreSQL, Outbox, LocalStack SQS, Worker, Target, Evaluator와
 * Finalization까지 하나의 TestRun pipeline을 검증한다.
 *
 * <p>SageMaker Runtime은 LocalStack이 제공하지 않으므로 실제 Adapter의 외부 SDK client만 대체한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "guardbench.sqs.enabled=true",
                "guardbench.worker.enabled=true",
                "guardbench.sqs.polling.wait-time-seconds=0",
                "guardbench.sqs.polling.delay-ms=25",
                "guardbench.sqs.outbox.delay-ms=25",
                "guardbench.sqs.outbox.initial-delay-ms=0",
                "guardbench.http-endpoint.allow-private-addresses=true",
                "guardbench.sagemaker.classifier.endpoint-name=local-e2e-classifier-endpoint",
                "guardbench.sagemaker.classifier.system-prompt=local-e2e classifier system prompt"
        })
@Import(PostgresTestConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestRunLocalE2eTest {

    private static final String TEST_RUN_URL = "/api/v1/test-runs";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PIPELINE_TIMEOUT = Duration.ofSeconds(30);

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
        for (TestRunQueue queue : TestRunQueue.values()) {
            SQS.createQueue(CreateQueueRequest.builder().queueName(queue.queueName()).build());
        }
        System.setProperty("aws.accessKeyId", LOCAL_STACK.getAccessKey());
        System.setProperty("aws.secretAccessKey", LOCAL_STACK.getSecretKey());
    }

    @DynamicPropertySource
    static void localStackProperties(DynamicPropertyRegistry registry) {
        registry.add("guardbench.sqs.endpoint-override", () -> LOCAL_STACK.getEndpoint().toString());
        registry.add("guardbench.sqs.region", LOCAL_STACK::getRegion);
    }

    @LocalServerPort
    private int serverPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SqsClient applicationSqsClient;

    @MockitoBean
    private SageMakerRuntimeClient sageMakerRuntimeClient;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();
    private ClientAndServer applicationMockServer;

    @BeforeEach
    void resetFixture() {
        drainQueues();
        new TestRunPersistenceFixture(jdbcTemplate).clearPersistenceTables();
        reset(sageMakerRuntimeClient);
    }

    @AfterEach
    void stopApplicationServer() {
        if (applicationMockServer != null) {
            applicationMockServer.stop();
            applicationMockServer = null;
        }
    }

    @AfterAll
    void stopLocalStack() {
        applicationSqsClient.close();
        SQS.close();
        LOCAL_STACK.stop();
    }

    @Test
    @DisplayName("정상 TestRun은 실제 HTTP 왕복부터 Assertion과 PASS Quality Gate까지 완료된다")
    void completesSingleTestRunThroughEntirePipeline() throws Exception {
        startApplicationMockServer();
        registerApplicationExpectation("safe input", safeApplicationResponse());
        stubAllowEvaluator();

        long suiteId = createSuite("""
                [{"name":"안전 응답","input":"safe input","expectedAction":"ALLOW","severity":"HIGH","category":"safety"}]
                """);
        long testRunId = createTestRun(suiteId);

        JsonNode detail = awaitFinished(testRunId);
        JsonNode result = getJson(TEST_RUN_URL + "/" + testRunId + "/results");
        JsonNode metrics = getJson(TEST_RUN_URL + "/" + testRunId + "/evaluator-metrics");

        assertThat(detail.path("data").path("status").asText()).isEqualTo("FINISHED");
        assertThat(detail.path("data").path("qualityGate").path("status").asText()).isEqualTo("PASS");
        assertThat(detail.path("data").path("qualityGate").path("metrics").path("assertionPassRate").asDouble())
                .isEqualTo(1.0);
        assertThat(detail.path("data").path("qualityGate").path("metrics").path("assertion").path("value").asDouble())
                .isEqualTo(1.0);
        assertThat(detail.path("data").path("qualityGate").path("metrics").path("assertion").path("threshold").asDouble())
                .isEqualTo(0.95);
        assertThat(detail.path("data").path("qualityGate").path("metrics").path("assertion").path("passed").asBoolean())
                .isTrue();
        assertThat(detail.path("data").path("qualityGate").path("metrics").path("execution").path("passed").asBoolean())
                .isTrue();
        assertThat(result.path("data").path("page").path("totalElements").asInt()).isEqualTo(1);
        assertThat(metrics.path("httpStatus").asInt()).isEqualTo(200);
        assertThat(metrics.path("data").path("truePositive").asInt()).isEqualTo(0);
        assertThat(metrics.path("data").path("trueNegative").asInt()).isEqualTo(1);
        assertThat(metrics.path("data").path("falsePositive").asInt()).isEqualTo(0);
        assertThat(metrics.path("data").path("falseNegative").asInt()).isEqualTo(0);
        assertThat(metrics.path("data").path("falsePositiveRate").asDouble()).isEqualTo(0.0);
        assertThat(metrics.path("data").path("falseNegativeRate").isNull()).isTrue();
        JsonNode item = result.path("data").path("items").get(0);
        assertThat(item.path("executionStatus").asText()).isEqualTo("SUCCEEDED");
        assertThat(item.path("evaluatorVerdict").asText()).isEqualTo("ALLOW");
        assertThat(item.path("assertionStatus").asText()).isEqualTo("PASS");
        assertThat(item.path("evaluationOutcome").asText()).isEqualTo("TRUE_NEGATIVE");
        assertThat(item.has("applicationResponse")).isFalse();
        assertThat(item.has("targetResponse")).isFalse();
        assertThat(item.has("naturalLanguageResponse")).isFalse();

        verifyApplicationRequest("safe input", 1);
        verify(sageMakerRuntimeClient).invokeEndpoint(any(InvokeEndpointRequest.class));
    }

    @Test
    @DisplayName("여러 TestCase는 각각 HTTP 요청·Evaluator·Assertion으로 처리되고 Run을 함께 종료한다")
    void processesMultipleSnapshotsIndependently() throws Exception {
        startApplicationMockServer();
        registerApplicationExpectation("block input", blockedApplicationResponse());
        registerApplicationExpectation("allow input", safeApplicationResponse());
        stubEvaluatorByResponse();

        long suiteId = createSuite("""
                [
                  {"name":"차단 케이스","input":"block input","expectedAction":"BLOCK","severity":"CRITICAL","category":"security"},
                  {"name":"허용 케이스","input":"allow input","expectedAction":"ALLOW","severity":"MEDIUM","category":"security"}
                ]
                """);
        long testRunId = createTestRun(suiteId);

        JsonNode detail = awaitFinished(testRunId);
        JsonNode result = getJson(TEST_RUN_URL + "/" + testRunId + "/results");

        assertThat(detail.path("data").path("progress").path("processedTestCaseCount").asInt()).isEqualTo(2);
        assertThat(detail.path("data").path("qualityGate").path("status").asText()).isEqualTo("PASS");
        assertThat(result.path("data").path("page").path("totalElements").asInt()).isEqualTo(2);
        assertThat(result.path("data").path("items")).hasSize(2);
        verifyApplicationRequest("block input", 1);
        verifyApplicationRequest("allow input", 1);
        verify(sageMakerRuntimeClient, times(2)).invokeEndpoint(any(InvokeEndpointRequest.class));
    }

    @Test
    @DisplayName("malformed Application response는 PROVIDER_RESPONSE_INVALID로 종료되고 Evaluator를 호출하지 않는다")
    void finishesWhenApplicationResponseParsingFails() throws Exception {
        startApplicationMockServer();
        registerApplicationExpectation("malformed input", malformedApplicationResponse());

        long suiteId = createSuite("""
                [{"name":"잘못된 응답","input":"malformed input","expectedAction":"ALLOW","severity":"HIGH","category":"provider"}]
                """);
        long testRunId = createTestRun(suiteId);

        JsonNode detail = awaitFinished(testRunId);
        JsonNode result = getJson(TEST_RUN_URL + "/" + testRunId + "/results");
        JsonNode item = result.path("data").path("items").get(0);

        assertThat(detail.path("data").path("status").asText()).isEqualTo("FINISHED");
        assertThat(detail.path("data").path("qualityGate").path("status").asText()).isEqualTo("NOT_EVALUATED");
        assertThat(detail.path("data").path("qualityGate").path("metrics").isNull()).isTrue();
        assertThat(item.path("executionStatus").asText()).isEqualTo("FAILED");
        assertThat(item.path("error").path("stage").asText()).isEqualTo("APPLICATION_TARGET");
        assertThat(item.path("error").path("code").asText()).isEqualTo("PROVIDER_RESPONSE_INVALID");
        assertThat(item.path("evaluatorVerdict").isNull()).isTrue();
        verifyApplicationRequest("malformed input", 1);
        verifyNoInteractions(sageMakerRuntimeClient);
    }

    @Test
    @DisplayName("Evaluator 실패는 Application response를 내부 저장하고 EVALUATOR 오류로 종료한다")
    void finishesWhenEvaluatorFails() throws Exception {
        startApplicationMockServer();
        registerApplicationExpectation("evaluator input", safeApplicationResponse());
        when(sageMakerRuntimeClient.invokeEndpoint(any(InvokeEndpointRequest.class)))
                .thenThrow(ValidationErrorException.builder().message("provider detail must not leak").build());

        long suiteId = createSuite("""
                [{"name":"평가 실패","input":"evaluator input","expectedAction":"BLOCK","severity":"HIGH","category":"evaluator"}]
                """);
        long testRunId = createTestRun(suiteId);

        JsonNode detail = awaitFinished(testRunId);
        JsonNode result = getJson(TEST_RUN_URL + "/" + testRunId + "/results");
        JsonNode item = result.path("data").path("items").get(0);
        String storedResponse = jdbcTemplate.queryForObject(
                "SELECT application_response FROM test_execution WHERE snapshot_id = "
                        + "(SELECT id FROM test_case_snapshot WHERE test_run_id = ?)",
                String.class, testRunId);

        assertThat(detail.path("data").path("status").asText()).isEqualTo("FINISHED");
        assertThat(item.path("executionStatus").asText()).isEqualTo("FAILED");
        assertThat(item.path("error").path("stage").asText()).isEqualTo("EVALUATOR");
        assertThat(item.path("error").path("code").asText()).isEqualTo("EVALUATOR_CONFIGURATION_INVALID");
        assertThat(item.path("assertionStatus").isNull()).isTrue();
        assertThat(item.path("evaluatorVerdict").isNull()).isTrue();
        assertThat(item.has("applicationResponse")).isFalse();
        assertThat(storedResponse).isEqualTo("safe response");
        verify(sageMakerRuntimeClient).invokeEndpoint(any(InvokeEndpointRequest.class));
    }

    private void startApplicationMockServer() {
        applicationMockServer = startClientAndServer(0);
    }

    private void registerApplicationExpectation(String input, String responseBody) {
        applicationMockServer.when(
                        request()
                                .withMethod("POST")
                                .withPath("/v1/chat/completions")
                                .withHeader("Content-Type", "application/json")
                                .withBody(json(applicationRequestBody(input), MatchType.STRICT)))
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody));
    }

    private void verifyApplicationRequest(String input, int expectedCount) {
        applicationMockServer.verify(
                request()
                        .withMethod("POST")
                        .withPath("/v1/chat/completions")
                        .withHeader("Content-Type", "application/json")
                        .withBody(json(applicationRequestBody(input), MatchType.STRICT)),
                VerificationTimes.exactly(expectedCount));
    }

    private static String applicationRequestBody(String input) {
        return "{\"model\":\"local-model\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}"
                .formatted(input);
    }

    private static String safeApplicationResponse() {
        return "{\"choices\":[{\"message\":{\"content\":\"safe response\"}}]}";
    }

    private static String blockedApplicationResponse() {
        return "{\"choices\":[{\"message\":{\"content\":\"blocked response\"}}]}";
    }

    private static String malformedApplicationResponse() {
        return "{\"choices\":[{\"message\":{}}]}";
    }

    private void stubAllowEvaluator() {
        when(sageMakerRuntimeClient.invokeEndpoint(any(InvokeEndpointRequest.class)))
                .thenReturn(classifierLabelResponse("COMPLY"));
    }

    private void stubEvaluatorByResponse() {
        when(sageMakerRuntimeClient.invokeEndpoint(any(InvokeEndpointRequest.class)))
                .thenAnswer(invocation -> {
                    InvokeEndpointRequest request = invocation.getArgument(0);
                    String classifierInput = request.body().asUtf8String();
                    if (classifierInput.contains("blocked")) {
                        return classifierLabelResponse("REFUSE");
                    }
                    return classifierLabelResponse("COMPLY");
                });
    }

    private static InvokeEndpointResponse classifierLabelResponse(String label) {
        String body = """
                {"choices":[{"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}]}
                """.formatted(label);
        return InvokeEndpointResponse.builder()
                .body(SdkBytes.fromUtf8String(body))
                .build();
    }

    private long createSuite(String testCases) throws Exception {
        String body = """
                {"name":"local-e2e-suite","description":"Testcontainers E2E","testCases":%s}
                """.formatted(testCases);
        JsonNode response = send("POST", "/api/v1/test-suites", body).bodyJson();
        assertThat(response.path("httpStatus").asInt()).isEqualTo(201);
        return response.path("data").path("id").asLong();
    }

    private long createTestRun(long suiteId) throws Exception {
        String body = """
                {
                  "testSuiteId":%d,
                  "target":{"type":"HTTP_ENDPOINT","identifier":"%s/v1/chat/completions","model":"local-model","revision":"local"}
                }
                """.formatted(suiteId, applicationServerUrl());
        JsonNode response = send("POST", TEST_RUN_URL, body).bodyJson();
        assertThat(response.path("httpStatus").asInt()).isEqualTo(202);
        return response.path("data").path("id").asLong();
    }

    private JsonNode awaitFinished(long testRunId) throws Exception {
        long deadline = System.nanoTime() + PIPELINE_TIMEOUT.toNanos();
        JsonNode latest = null;
        while (System.nanoTime() < deadline) {
            HttpResult response = send("GET", TEST_RUN_URL + "/" + testRunId, null);
            if (response.statusCode() == 200) {
                latest = response.bodyJson();
                if ("FINISHED".equals(latest.path("data").path("status").asText())) {
                    return latest;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("TestRun did not finish before timeout: " + latest);
    }

    private JsonNode getJson(String path) throws Exception {
        return send("GET", path, null).bodyJson();
    }

    private HttpResult send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + serverPort + path))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json");
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.body());
    }

    private String applicationServerUrl() {
        return "http://127.0.0.1:" + applicationMockServer.getLocalPort();
    }

    private void drainQueues() {
        for (TestRunQueue queue : TestRunQueue.values()) {
            String queueUrl = SQS.getQueueUrl(request -> request.queueName(queue.queueName())).queueUrl();
            List<String> receiptHandles = new ArrayList<>();
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
                    SQS.deleteMessage(DeleteMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .receiptHandle(receiptHandle)
                            .build());
                }
            } while (!receiptHandles.isEmpty());
        }
    }

    private record HttpResult(int statusCode, String body) {
        JsonNode bodyJson() throws IOException {
            return new ObjectMapper().readTree(body);
        }
    }
}
