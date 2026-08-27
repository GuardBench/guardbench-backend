package com.guardbench.testrun.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import com.guardbench.testrun.application.OutboxPublisher;
import com.guardbench.testrun.application.messaging.TestRunQueue;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testsupport.PostgresTestConfiguration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * Outbox Publisher 운영 경로 통합 테스트다.
 *
 * <p>PENDING row가 실제 SQS queue로 이동하고 PUBLISHED로 전이되는지,
 * 병렬 Publisher가 같은 batch를 중복 발행하지 않는지 LocalStack + PostgreSQL로 검증한다.
 */
@SpringBootTest(properties = {
        "guardbench.sqs.enabled=true",
        "guardbench.worker.enabled=false",
        "guardbench.sqs.outbox.delay-ms=3600000",
        "guardbench.sqs.outbox.initial-delay-ms=3600000"
})
@Import(PostgresTestConfiguration.class)
class OutboxPublisherWiringIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-08-26T00:00:00Z");

    private static final LocalStackContainer LOCAL_STACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.SQS);

    private static SqsClient verificationClient;

    static {
        LOCAL_STACK.start();
        verificationClient = SqsClient.builder()
                .endpointOverride(URI.create(LOCAL_STACK.getEndpoint().toString()))
                .region(Region.of(LOCAL_STACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCAL_STACK.getAccessKey(), LOCAL_STACK.getSecretKey())))
                .build();
        for (TestRunQueue queue : TestRunQueue.values()) {
            verificationClient.createQueue(CreateQueueRequest.builder().queueName(queue.queueName()).build());
        }
        // 운영 코드는 DefaultCredentialsProvider 체인을 사용하므로 테스트에서는 시스템 속성으로 공급한다.
        System.setProperty("aws.accessKeyId", LOCAL_STACK.getAccessKey());
        System.setProperty("aws.secretAccessKey", LOCAL_STACK.getSecretKey());
    }

    @DynamicPropertySource
    static void sqsProperties(DynamicPropertyRegistry registry) {
        registry.add("guardbench.sqs.endpoint-override", () -> LOCAL_STACK.getEndpoint().toString());
        registry.add("guardbench.sqs.region", LOCAL_STACK::getRegion);
    }

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxPort outboxPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        new TestRunPersistenceFixture(jdbcTemplate).clearPersistenceTables();
        drain(TestRunQueue.RESOLVE);
    }

    @Test
    @DisplayName("Publisher 빈은 트랜잭션 프록시로 등록되어 선택·발행·상태 전이가 한 트랜잭션에서 실행된다")
    void publisherBeanIsTransactionalProxy() {
        assertThat(AopUtils.isAopProxy(outboxPublisher))
                .as("publishPending의 @Transactional 경계가 적용되어야 한다")
                .isTrue();
    }

    @Test
    @DisplayName("PENDING Outbox row가 event type queue로 발행되고 PUBLISHED로 전이된다")
    void pendingEventMovesToQueueAndBecomesPublished() {
        UUID eventId = UUID.randomUUID();
        outboxPort.save(pendingRequested(eventId, 1));

        int published = outboxPublisher.publishPending(10);

        assertThat(published).isEqualTo(1);
        assertThat(statusOf(eventId)).isEqualTo("PUBLISHED");

        List<Message> delivered = receive(TestRunQueue.RESOLVE, 10);
        assertThat(delivered).hasSize(1);
        assertThat(delivered.getFirst().body()).contains(eventId.toString());
    }

    @Test
    @DisplayName("병렬 Publisher가 같은 PENDING batch를 중복 발행하지 않는다")
    void concurrentPublishersDoNotPublishSameEventTwice() throws InterruptedException {
        int eventCount = 6;
        Set<UUID> savedIds = new HashSet<>();
        for (int index = 0; index < eventCount; index++) {
            UUID eventId = UUID.randomUUID();
            savedIds.add(eventId);
            outboxPort.save(pendingRequested(eventId, 100L + index));
        }

        AtomicInteger publishedTotal = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<Thread> publishers = new ArrayList<>();
        for (int worker = 0; worker < 2; worker++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    int published;
                    do {
                        published = outboxPublisher.publishPending(3);
                        publishedTotal.addAndGet(published);
                    } while (published > 0);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            publishers.add(thread);
            thread.start();
        }

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        for (Thread thread : publishers) {
            thread.join();
        }

        assertThat(publishedTotal.get())
                .as("각 event는 정확히 한 번만 발행돼야 한다")
                .isEqualTo(eventCount);
        assertThat(pendingCount()).isZero();

        List<Message> delivered = receive(TestRunQueue.RESOLVE, eventCount + 4);
        Set<String> deliveredBodies = new HashSet<>();
        for (Message message : delivered) {
            deliveredBodies.add(message.body());
        }
        assertThat(delivered).hasSize(eventCount);
        assertThat(deliveredBodies).hasSize(eventCount);
        for (UUID eventId : savedIds) {
            assertThat(statusOf(eventId)).isEqualTo("PUBLISHED");
        }
    }

    private static OutboxEventRecord pendingRequested(UUID eventId, long testRunId) {
        String payload = "{\"eventId\":\"%s\",\"eventType\":\"TestRunRequested\",\"schemaVersion\":1,\"testRunId\":%d,\"occurredAt\":\"2026-08-26T00:00:00Z\"}"
                .formatted(eventId, testRunId);
        return OutboxEventRecord.pending(eventId, "TestRunRequested", payload, "TestRunRequested:" + testRunId, BASE);
    }

    private String statusOf(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_event WHERE event_id = ?::uuid", String.class, eventId.toString());
    }

    private int pendingCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE status = 'PENDING'", Integer.class);
        return count == null ? 0 : count;
    }

    private static List<Message> receive(TestRunQueue queue, int maxTotal) {
        String queueUrl = verificationClient.getQueueUrl(r -> r.queueName(queue.queueName())).queueUrl();
        List<Message> collected = new ArrayList<>();
        while (collected.size() < maxTotal) {
            List<Message> batch = verificationClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(1)
                    .build()).messages();
            if (batch.isEmpty()) {
                break;
            }
            collected.addAll(batch);
        }
        return collected;
    }

    private static void drain(TestRunQueue queue) {
        String queueUrl = verificationClient.getQueueUrl(r -> r.queueName(queue.queueName())).queueUrl();
        while (true) {
            List<Message> batch = verificationClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(1)
                    .build()).messages();
            if (batch.isEmpty()) {
                return;
            }
            for (Message message : batch) {
                verificationClient.deleteMessage(r -> r.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            }
        }
    }
}
