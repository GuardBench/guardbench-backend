package com.guardbench.testrun.presentation.controller;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.guardbench.common.presentation.dto.ApiResponse;
import com.guardbench.common.presentation.dto.FieldErrorDetail;
import com.guardbench.common.presentation.dto.ValidationErrorDetail;
import com.guardbench.testrun.application.CompareTestRunsService;
import com.guardbench.testrun.application.GetComparableTestRunsService;
import com.guardbench.testrun.application.GetTestRunDetailService;
import com.guardbench.testrun.application.GetTestRunEvaluatorMetricsService;
import com.guardbench.testrun.application.GetTestRunListService;
import com.guardbench.testrun.application.GetTestRunResultListService;
import com.guardbench.testrun.application.port.out.PageCriteria;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.EvaluatorMetricsView;
import com.guardbench.testrun.application.port.out.SortOrder;
import com.guardbench.testrun.application.port.out.TestRunDetail;
import com.guardbench.testrun.application.port.out.TestRunListCriteria;
import com.guardbench.testrun.application.port.out.TestRunListItem;
import com.guardbench.testrun.application.port.out.TestRunListSortField;
import com.guardbench.testrun.application.port.out.TestRunResultAttentionType;
import com.guardbench.testrun.application.port.out.TestRunResultListCriteria;
import com.guardbench.testrun.application.port.out.TestRunResultListView;
import com.guardbench.testrun.application.port.out.TestRunResultSortField;
import com.guardbench.testrun.application.port.out.TestRunComparison;
import com.guardbench.testrun.application.port.out.TestRunRegressionView;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.Severity;
import com.guardbench.testrun.domain.TestExecutionStatus;
import com.guardbench.testrun.domain.TestRunExecutionOutcome;
import com.guardbench.testrun.domain.TestRunStatus;
import com.guardbench.testrun.presentation.dto.TestRunDetailRes;
import com.guardbench.testrun.presentation.dto.TestRunListRes;
import com.guardbench.testrun.presentation.dto.TestRunQueryResponseMapper;
import com.guardbench.testrun.presentation.dto.TestRunResultListRes;
import com.guardbench.testrun.presentation.dto.EvaluatorMetricsRes;
import com.guardbench.testrun.presentation.dto.ComparableTestRunListRes;
import com.guardbench.testrun.presentation.dto.TestRunComparisonRes;
import com.guardbench.testrun.presentation.dto.TestRunComparisonSummaryRes;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.springframework.beans.propertyeditors.StringArrayPropertyEditor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TestRun 목록·상세·개별 결과 조회 API다. Envelope, HTTP 상태, DTO 매핑만 담당하며 Domain 객체나 JPA
 * Entity를 직접 반환하지 않는다.
 *
 * @see <a href="../../../../../../../../docs/api/README.md">GuardBench API V1</a>
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 OpenAPI 명세</a>
 */
@RestController
@RequestMapping("/api/v1/test-runs")
public class TestRunQueryController {

    private static final String LIST_SORT_PATTERN =
            "^(createdAt|startedAt|completedAt|updatedAt|testCaseCount|id),(asc|desc)$";
    private static final String RESULT_SORT_PATTERN =
            "^(name|category|severity|expectedAction|snapshotId),(asc|desc)$";
    private static final String SUCCESS_MESSAGE_LIST = "TestRun 목록 조회에 성공했습니다.";
    private static final String SUCCESS_MESSAGE_DETAIL = "TestRun 조회에 성공했습니다.";
    private static final String SUCCESS_MESSAGE_RESULTS = "TestRun 개별 결과 조회에 성공했습니다.";
    private static final String SUCCESS_MESSAGE_EVALUATOR_METRICS = "Evaluator 지표 조회에 성공했습니다.";
    private static final String SUCCESS_MESSAGE_COMPARABLE = "비교 가능한 과거 TestRun 조회에 성공했습니다.";
    private static final String SUCCESS_MESSAGE_COMPARISON = "TestRun 비교에 성공했습니다.";

    private final GetTestRunListService getTestRunListService;
    private final GetTestRunDetailService getTestRunDetailService;
    private final GetTestRunResultListService getTestRunResultListService;
    private final GetTestRunEvaluatorMetricsService getTestRunEvaluatorMetricsService;
    private final GetComparableTestRunsService getComparableTestRunsService;
    private final CompareTestRunsService compareTestRunsService;

    public TestRunQueryController(
            GetTestRunListService getTestRunListService,
            GetTestRunDetailService getTestRunDetailService,
            GetTestRunResultListService getTestRunResultListService,
            GetTestRunEvaluatorMetricsService getTestRunEvaluatorMetricsService,
            GetComparableTestRunsService getComparableTestRunsService,
            CompareTestRunsService compareTestRunsService) {
        this.getTestRunListService = getTestRunListService;
        this.getTestRunDetailService = getTestRunDetailService;
        this.getTestRunResultListService = getTestRunResultListService;
        this.getTestRunEvaluatorMetricsService = getTestRunEvaluatorMetricsService;
        this.getComparableTestRunsService = getComparableTestRunsService;
        this.compareTestRunsService = compareTestRunsService;
    }

    /**
     * {@code sort} 값 자체가 쉼표를 포함하므로 Spring 기본 {@code String}→배열 변환의 쉼표 분리 동작을
     * 비활성화한다. 이 Parameter는 반복(explode)으로만 여러 값을 받는다.
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String[].class, "sort", new StringArrayPropertyEditor(null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<TestRunListRes>> listTestRuns(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page는 1 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "size는 1 이상이어야 합니다.")
                    @Max(value = 100, message = "size는 100 이하여야 합니다.") int size,
            @RequestParam(name = "sort", required = false, defaultValue = "")
                    String[] sort,
            @RequestParam(required = false) @Positive(message = "testSuiteId는 양의 정수여야 합니다.") Long testSuiteId,
            @RequestParam(required = false, defaultValue = "") List<TestRunStatus> status,
            @RequestParam(required = false, defaultValue = "") List<TestRunExecutionOutcome> executionOutcome,
            @RequestParam(required = false, defaultValue = "")
                    List<@Pattern(regexp = "PASS|FAIL|NOT_EVALUATED", message = "허용되지 않은 값입니다.") String>
                            qualityGateStatus,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo) {
        List<SortOrder<TestRunListSortField>> sortOrders =
                SortParamParser.parse(sort, TestRunListSortField.class);
        TestRunListCriteria criteria = new TestRunListCriteria(
                testSuiteId,
                Set.copyOf(status),
                Set.copyOf(executionOutcome),
                Set.copyOf(qualityGateStatus),
                createdFrom,
                createdTo,
                sortOrders,
                new PageCriteria(page, size));

        PageResult<TestRunListItem> result = getTestRunListService.getTestRuns(criteria);
        TestRunListRes response = TestRunQueryResponseMapper.toListRes(result);
        return ApiResponse.entity(HttpStatus.OK, SUCCESS_MESSAGE_LIST, response);
    }

    @GetMapping("/{testRunId}")
    public ResponseEntity<ApiResponse<TestRunDetailRes>> getTestRun(
            @PathVariable @Positive(message = "testRunId는 양의 정수여야 합니다.") long testRunId) {
        TestRunDetail detail = getTestRunDetailService.getTestRun(testRunId);
        TestRunDetailRes response = TestRunQueryResponseMapper.toDetailRes(detail);
        return ApiResponse.entity(HttpStatus.OK, SUCCESS_MESSAGE_DETAIL, response);
    }

    @GetMapping("/{testRunId}/results")
    public ResponseEntity<ApiResponse<TestRunResultListRes>> listTestRunResults(
            @PathVariable @Positive(message = "testRunId는 양의 정수여야 합니다.") long testRunId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page는 1 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "size는 1 이상이어야 합니다.")
                    @Max(value = 100, message = "size는 100 이하여야 합니다.") int size,
            @RequestParam(name = "sort", required = false, defaultValue = "")
                    String[] sort,
            @RequestParam(required = false)
                    @Pattern(regexp = ".*\\S.*", message = "name은 공백일 수 없습니다.") String name,
            @RequestParam(required = false)
                    @Pattern(regexp = ".*\\S.*", message = "input은 공백일 수 없습니다.") String input,
            @RequestParam(required = false)
                    @Pattern(regexp = ".*\\S.*", message = "category는 공백일 수 없습니다.") String category,
            @RequestParam(required = false) Action expectedAction,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) TestExecutionStatus executionStatus,
            @RequestParam(required = false)
                    @Pattern(regexp = "PASS|FAIL", message = "허용되지 않은 값입니다.") String assertionStatus,
            @RequestParam(required = false)
                    @Pattern(regexp = "TRUE_POSITIVE|TRUE_NEGATIVE|FALSE_POSITIVE|FALSE_NEGATIVE",
                            message = "허용되지 않은 값입니다.") String evaluationOutcome,
            @RequestParam(required = false, defaultValue = "") List<TestRunResultAttentionType> attentionType,
            @RequestParam(required = false)
                    @Pattern(regexp = "attention", message = "허용되지 않은 값입니다.") String includeFacets) {
        for (int i = 0; i < attentionType.size(); i++) {
            if (attentionType.get(i) == null) {
                throw new QueryParamValidationException(List.of(
                        new FieldErrorDetail("attentionType[" + i + "]", "허용되지 않은 값입니다.")));
            }
        }
        List<SortOrder<TestRunResultSortField>> sortOrders =
                SortParamParser.parse(sort, TestRunResultSortField.class);
        TestRunResultListCriteria criteria = new TestRunResultListCriteria(
                name,
                input,
                category,
                expectedAction,
                severity,
                executionStatus,
                assertionStatus,
                evaluationOutcome,
                Set.copyOf(attentionType),
                "attention".equals(includeFacets),
                sortOrders,
                new PageCriteria(page, size));

        TestRunResultListView result = getTestRunResultListService.getResults(testRunId, criteria);
        TestRunResultListRes response = TestRunQueryResponseMapper.toResultListRes(result);
        return ApiResponse.entity(HttpStatus.OK, SUCCESS_MESSAGE_RESULTS, response);
    }

    @GetMapping("/{testRunId}/evaluator-metrics")
    public ResponseEntity<ApiResponse<EvaluatorMetricsRes>> getTestRunEvaluatorMetrics(
            @PathVariable @Positive(message = "testRunId는 양의 정수여야 합니다.") long testRunId) {
        EvaluatorMetricsView metrics = getTestRunEvaluatorMetricsService.getMetrics(testRunId);
        return ApiResponse.entity(HttpStatus.OK, SUCCESS_MESSAGE_EVALUATOR_METRICS,
                TestRunQueryResponseMapper.toEvaluatorMetricsRes(metrics));
    }

    @GetMapping("/{testRunId}/comparable-runs")
    public ResponseEntity<ApiResponse<ComparableTestRunListRes>> listComparableTestRuns(
            @PathVariable @Positive(message = "testRunId는 양의 정수여야 합니다.") long testRunId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page는 1 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "size는 1 이상이어야 합니다.")
                    @Max(value = 100, message = "size는 100 이하여야 합니다.") int size) {
        PageResult<TestRunRegressionView> result = getComparableTestRunsService.getComparableRuns(
                testRunId, new PageCriteria(page, size));
        return ApiResponse.entity(HttpStatus.OK, SUCCESS_MESSAGE_COMPARABLE,
                TestRunQueryResponseMapper.toComparableListRes(result));
    }

    @GetMapping("/{currentRunId}/comparisons/{comparisonRunId}")
    public ResponseEntity<ApiResponse<TestRunComparisonRes>> compareTestRuns(
            @PathVariable @Positive(message = "currentRunId는 양의 정수여야 합니다.") long currentRunId,
            @PathVariable @Positive(message = "comparisonRunId는 양의 정수여야 합니다.") long comparisonRunId) {
        TestRunComparison comparison = compareTestRunsService.compare(currentRunId, comparisonRunId);
        return ApiResponse.entity(HttpStatus.OK, SUCCESS_MESSAGE_COMPARISON,
                TestRunQueryResponseMapper.toComparisonRes(comparison));
    }

    @GetMapping("/{currentRunId}/comparisons/{comparisonRunId}/summary")
    public ResponseEntity<ApiResponse<TestRunComparisonSummaryRes>> summarizeTestRunComparison(
            @PathVariable @Positive(message = "currentRunId는 양의 정수여야 합니다.") long currentRunId,
            @PathVariable @Positive(message = "comparisonRunId는 양의 정수여야 합니다.") long comparisonRunId) {
        TestRunComparison comparison = compareTestRunsService.compare(currentRunId, comparisonRunId);
        return ApiResponse.entity(HttpStatus.OK, SUCCESS_MESSAGE_COMPARISON,
                TestRunQueryResponseMapper.toComparisonSummaryRes(comparison));
    }

    /**
     * {@code sort} Query Parameter 형식 오류를 승인된 {@code VALIDATION_ERROR} Envelope로 변환한다.
     */
    @ExceptionHandler(QueryParamValidationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorDetail>> handleQueryParamValidation(
            QueryParamValidationException ex) {
        List<FieldErrorDetail> errors = ex.errors();
        return ApiResponse.entity(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", ValidationErrorDetail.of(errors));
    }
}
