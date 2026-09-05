import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const baseUrl = (__ENV.PERF_BASE_URL || '').replace(/\/$/, '');
const suiteId = __ENV.PERF_SUITE_ID;
const maxIterationsPerVu = Number(__ENV.PERF_MAX_ITERATIONS_PER_VU || 0);
const completionTimeoutSeconds = Number(__ENV.PERF_COMPLETION_TIMEOUT_SECONDS || 300);
const pollingIntervalSeconds = Number(__ENV.PERF_POLLING_INTERVAL_SECONDS || 2);

export const createLatency = new Trend('test_run_create_latency', true);
export const createErrors = new Rate('test_run_create_errors');
export const completionDuration = new Trend('test_run_completion_duration');
export const completionFailures = new Rate('test_run_completion_failures');

const payload = JSON.stringify({
  testSuiteId: Number(suiteId),
  target: {
    type: 'HTTP_ENDPOINT',
    identifier: __ENV.PERF_TARGET_URL,
    model: __ENV.PERF_TARGET_MODEL,
    revision: __ENV.PERF_TARGET_REVISION || null,
  },
});

function pollUntilFinished(testRunId, startedAt) {
  const deadline = Date.now() + completionTimeoutSeconds * 1000;
  let lastStatus = '';
  while (Date.now() < deadline) {
    const response = http.get(`${baseUrl}/api/v1/test-runs/${testRunId}`, {
      tags: { operation: 'test_run_poll' },
    });
    let body = null;
    try {
      body = response.json();
    } catch (_) {
      body = null;
    }
    lastStatus = body && body.data ? body.data.status : '';
    if (response.status !== 200 || !body || !body.data) {
      sleep(pollingIntervalSeconds);
      continue;
    }
    if (lastStatus === 'FINISHED') {
      completionDuration.add((Date.now() - startedAt) / 1000);
      const terminalFailure = body.data.executionOutcome !== 'COMPLETED';
      completionFailures.add(terminalFailure ? 1 : 0);
      return !terminalFailure;
    }
    sleep(pollingIntervalSeconds);
  }
  completionFailures.add(1);
  console.error(`TestRun ${testRunId} did not finish before timeout (last status: ${lastStatus})`);
  return false;
}

export const options = {
  scenarios: {
    workload: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: `${Number(__ENV.PERF_RAMP_UP_SECONDS || 0)}s`, target: Number(__ENV.PERF_CONCURRENT_TEST_RUNS || 1) },
        { duration: `${Number(__ENV.PERF_DURATION_SECONDS || 10)}s`, target: Number(__ENV.PERF_CONCURRENT_TEST_RUNS || 1) },
      ],
      gracefulRampDown: '0s',
      gracefulStop: `${Number(__ENV.PERF_COMPLETION_TIMEOUT_SECONDS || 300)}s`,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    test_run_create_latency: [
      `p(50)<${Number(__ENV.PERF_API_P50_MS || 1000)}`,
      `p(95)<${Number(__ENV.PERF_API_P95_MS || 2000)}`,
      `p(99)<${Number(__ENV.PERF_API_P99_MS || 3000)}`,
    ],
    test_run_create_errors: [`rate<=${Number(__ENV.PERF_API_ERROR_RATE || 0)}`],
    test_run_completion_failures: [`rate<=${Number(__ENV.PERF_COMPLETION_FAILURE_RATE || 0)}`],
    test_run_completion_duration: [`p(95)<=${Number(__ENV.PERF_COMPLETION_MAX_SECONDS || 300)}`],
  },
};

export default function () {
  if (maxIterationsPerVu > 0 && __ITER >= maxIterationsPerVu) {
    return;
  }
  const startedAt = Date.now();
  const response = http.post(`${baseUrl}/api/v1/test-runs`, payload, {
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      'Idempotency-Key': `perf-${__ENV.PERF_RUN_ID}-${__VU}-${__ITER}`,
    },
    tags: { operation: 'test_run_create' },
  });
  createLatency.add(response.timings.duration);
  let body = null;
  try {
    body = response.json();
  } catch (_) {
    body = null;
  }
  const accepted = check(response, {
    'TestRun create returns 202': (r) => r.status === 202,
    'TestRun create returns an id': () => Boolean(body && body.data && body.data.id),
  }, { operation: 'test_run_create' });
  createErrors.add(accepted ? 0 : 1);
  if (accepted) {
    pollUntilFinished(body.data.id, startedAt);
  }
}
