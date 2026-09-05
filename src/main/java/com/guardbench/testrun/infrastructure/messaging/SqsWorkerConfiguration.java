package com.guardbench.testrun.infrastructure.messaging;

import java.util.EnumMap;
import java.util.Map;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.guardbench.testrun.application.ExecuteTestRunService;
import com.guardbench.testrun.application.ResolveTestRunService;
import com.guardbench.testrun.application.messaging.TestRunMessageCodec;
import com.guardbench.testrun.application.messaging.TestRunQueue;
import com.guardbench.testrun.application.port.in.HandleTestExecutionCompletedPort;

import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Worker 폴링 어댑터 빈 등록이다.
 *
 * <p>guardbench.worker.enabled=true일 때만 활성화한다.
 * 개발환경, 테스트에서 비활성화하여 불필요한 SQS 폴링을 방지한다.
 *
 * <p>{@code @EnableScheduling}은 이 Configuration에 선언하여
 * worker 비활성화 시 스케줄링 인프라가 로딩되지 않도록 보장한다.
 */
@Configuration
@ConditionalOnProperty(name = "guardbench.worker.enabled", havingValue = "true")
@EnableScheduling
@EnableConfigurationProperties(WorkerProperties.class)
class SqsWorkerConfiguration {

    @Bean
    WorkItemConcurrencyController workItemConcurrencyController(WorkerProperties properties) {
        WorkerProperties.WorkItems workItems = properties.workItems();
        return new WorkItemConcurrencyController(
                workItems.concurrency(),
                Duration.ofSeconds(workItems.shutdownTimeoutSeconds())
        );
    }

    @Bean
    TestRunMessageCodec testRunMessageCodec(ObjectMapper objectMapper) {
        return new TestRunMessageCodec(objectMapper);
    }

    @Bean
    Map<TestRunQueue, SqsInboundPollingAdapter> sqsPollingAdapters(
            SqsClient sqsClient,
            TestRunMessageCodec codec,
            SqsProperties properties,
            ResolveTestRunService resolveService,
            ExecuteTestRunService executeService,
            HandleTestExecutionCompletedPort handleCompletedPort,
            WorkItemConcurrencyController workItemConcurrencyController
    ) {
        Map<TestRunQueue, String> queueUrls = resolveQueueUrls(sqsClient, properties);
        Map<TestRunQueue, SqsInboundPollingAdapter> adapters = new EnumMap<>(TestRunQueue.class);

        adapters.put(TestRunQueue.RESOLVE, new SqsInboundPollingAdapter(
                sqsClient, codec, queueUrls.get(TestRunQueue.RESOLVE), TestRunQueue.RESOLVE,
                properties.polling(), resolveService, null, null, null
        ));
        adapters.put(TestRunQueue.WORK_ITEMS, new SqsInboundPollingAdapter(
                sqsClient, codec, queueUrls.get(TestRunQueue.WORK_ITEMS), TestRunQueue.WORK_ITEMS,
                properties.polling(), null, executeService, null, workItemConcurrencyController
        ));
        adapters.put(TestRunQueue.FINALIZE, new SqsInboundPollingAdapter(
                sqsClient, codec, queueUrls.get(TestRunQueue.FINALIZE), TestRunQueue.FINALIZE,
                properties.polling(), null, null, handleCompletedPort, null
        ));

        return adapters;
    }

    private Map<TestRunQueue, String> resolveQueueUrls(SqsClient sqsClient, SqsProperties properties) {
        Map<TestRunQueue, String> urls = new EnumMap<>(TestRunQueue.class);
        SqsProperties.QueueUrls configured = properties.queueUrls();

        urls.put(TestRunQueue.RESOLVE, resolveUrl(sqsClient, configured != null ? configured.resolve() : null, TestRunQueue.RESOLVE));
        urls.put(TestRunQueue.WORK_ITEMS, resolveUrl(sqsClient, configured != null ? configured.workItems() : null, TestRunQueue.WORK_ITEMS));
        urls.put(TestRunQueue.FINALIZE, resolveUrl(sqsClient, configured != null ? configured.runFinalize() : null, TestRunQueue.FINALIZE));

        return urls;
    }

    private String resolveUrl(SqsClient sqsClient, String explicitUrl, TestRunQueue queue) {
        if (explicitUrl != null && !explicitUrl.isBlank()) {
            return explicitUrl;
        }
        return sqsClient.getQueueUrl(r -> r.queueName(queue.queueName())).queueUrl();
    }
}
