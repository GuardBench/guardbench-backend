package com.guardbench.testrun.presentation.dto;

import java.time.Instant;

import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.QualityGateMetricsView;
import com.guardbench.testrun.application.port.out.QualityGateView;
import com.guardbench.testrun.application.port.out.TestExecutionView;
import com.guardbench.testrun.application.port.out.TestRunDetail;
import com.guardbench.testrun.application.port.out.TestRunListItem;
import com.guardbench.testrun.application.port.out.TestRunProgress;
import com.guardbench.testrun.application.port.out.TestRunResultItem;
import com.guardbench.common.presentation.dto.PageMetaRes;

/**
 * Application 조회 모델을 API DTO로 변환한다. Domain·Application Enum과 API Enum은 문자열 값이 같아도
 * 경계에서 명시적으로 변환한다.
 *
 * @see <a href="../../../../../../../../docs/conventions/code-style.md">코드 컨벤션</a>
 */
public final class TestRunQueryResponseMapper {

    private TestRunQueryResponseMapper() {
    }

    public static TestRunListRes toListRes(PageResult<TestRunListItem> page) {
        return new TestRunListRes(
                page.items().stream().map(TestRunQueryResponseMapper::toListItemRes).toList(),
                toPageMetaRes(page));
    }

    public static TestRunDetailRes toDetailRes(TestRunDetail detail) {
        return new TestRunDetailRes(
                detail.id(),
                detail.testSuiteId(),
                detail.status().name(),
                detail.testCaseCount(),
                toProgressRes(detail.progress()),
                new TargetReferenceRes(detail.target().referenceId(), detail.target().type(), detail.target().identifier(), detail.target().revision(), detail.target().model()),
                TestRunCreateRes.toResponse(detail.evaluationProfile()),
                detail.executionOutcome() != null ? detail.executionOutcome().name() : null,
                detail.qualityGate() != null ? toQualityGateRes(detail.qualityGate()) : null,
                toIso(detail.createdAt()),
                toIso(detail.startedAt()),
                toIso(detail.completedAt()),
                toIso(detail.updatedAt()));
    }

    public static TestRunResultListRes toResultListRes(PageResult<TestRunResultItem> page) {
        return new TestRunResultListRes(
                page.items().stream().map(TestRunQueryResponseMapper::toResultItemRes).toList(),
                toPageMetaRes(page));
    }

    private static TestRunListItemRes toListItemRes(TestRunListItem item) {
        return new TestRunListItemRes(
                item.id(),
                item.testSuiteId(),
                item.status().name(),
                item.testCaseCount(),
                toProgressRes(item.progress()),
                item.executionOutcome() != null ? item.executionOutcome().name() : null,
                item.qualityGateStatusCode(),
                toIso(item.createdAt()),
                toIso(item.startedAt()),
                toIso(item.completedAt()),
                toIso(item.updatedAt()));
    }

    private static TestRunResultListItemRes toResultItemRes(TestRunResultItem item) {
        return new TestRunResultListItemRes(
                item.snapshotId(),
                item.testCaseId(),
                item.name(),
                item.input(),
                item.expectedAction().name(),
                item.severity().name(),
                item.category(),
                toExecutionRes(item.execution()),
                item.assertionStatusCode());
    }

    private static TestExecutionResultRes toExecutionRes(TestExecutionView execution) {
        ExecutionErrorDetailRes error = execution.errorCode() != null
                ? new ExecutionErrorDetailRes(execution.errorCode(), execution.errorMessage())
                : null;
        return new TestExecutionResultRes(
                execution.status().name(),
                execution.actualAction() != null ? execution.actualAction().name() : null,
                error);
    }

    private static TestRunProgressRes toProgressRes(TestRunProgress progress) {
        return new TestRunProgressRes(progress.processedTestCaseCount(), progress.percent());
    }

    private static QualityGateRes toQualityGateRes(QualityGateView qualityGate) {
        QualityGateMetricsView metrics = qualityGate.metrics();
        QualityGateMetricsRes metricsRes = metrics != null
                ? new QualityGateMetricsRes(
                        metrics.assertionPassRate(),
                        metrics.executionSuccessRate())
                : null;
        return new QualityGateRes(qualityGate.statusCode(), metricsRes);
    }

    private static PageMetaRes toPageMetaRes(PageResult<?> page) {
        return new PageMetaRes(
                page.number(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.hasPrevious(),
                page.hasNext());
    }

    private static String toIso(Instant instant) {
        return instant != null ? instant.toString() : null;
    }
}
