// Managed by the centralized infra load-test profile.
import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.PERF_BASE_URL || 'http://backend:8080';
const endpointMode = __ENV.PERF_ENDPOINT_MODE || 'cursor';
const loadProfile = __ENV.PERF_LOAD_PROFILE || 'steady';
const rate = Number(__ENV.PERF_RATE || 20);
const preAllocatedVUs = Number(__ENV.PERF_PRE_ALLOCATED_VUS || 20);
const maxVUs = Number(__ENV.PERF_MAX_VUS || 100);
const pageSize = Number(__ENV.PERF_PAGE_SIZE || 20);

if (!Number.isInteger(rate) || rate <= 0) {
  throw new Error('PERF_RATE must be a positive integer.');
}
if (!['offset', 'cursor'].includes(endpointMode)) {
  throw new Error('PERF_ENDPOINT_MODE must be offset or cursor.');
}
if (!['steady', 'spike', 'soak', 'saturation'].includes(loadProfile)) {
  throw new Error('PERF_LOAD_PROFILE must be steady, spike, soak, or saturation.');
}

function positiveInteger(name, fallback) {
  const value = Number(__ENV[name] || fallback);
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} must be a positive integer.`);
  }
  return value;
}

function scenario() {
  if (loadProfile === 'spike') {
    const spikeRate = positiveInteger('PERF_SPIKE_RATE', 100);
    return {
      executor: 'ramping-arrival-rate',
      startRate: rate,
      timeUnit: '1s',
      preAllocatedVUs,
      maxVUs,
      stages: [
        { target: rate, duration: __ENV.PERF_SPIKE_WARMUP || '10s' },
        { target: spikeRate, duration: __ENV.PERF_SPIKE_RAMP || '10s' },
        { target: spikeRate, duration: __ENV.PERF_SPIKE_HOLD || '20s' },
        { target: rate, duration: __ENV.PERF_SPIKE_RECOVERY || '10s' },
      ],
    };
  }

  if (loadProfile === 'saturation') {
    const saturationRate = positiveInteger('PERF_SATURATION_RATE', 1000);
    return {
      executor: 'ramping-arrival-rate',
      startRate: rate,
      timeUnit: '1s',
      preAllocatedVUs,
      maxVUs,
      stages: [
        { target: rate, duration: __ENV.PERF_SATURATION_WARMUP || '10s' },
        { target: saturationRate, duration: __ENV.PERF_SATURATION_RAMP || '20s' },
        { target: saturationRate, duration: __ENV.PERF_SATURATION_HOLD || '30s' },
      ],
    };
  }

  return {
    executor: 'constant-arrival-rate',
    rate,
    timeUnit: '1s',
    duration: loadProfile === 'soak'
      ? (__ENV.PERF_SOAK_DURATION || '10m')
      : (__ENV.PERF_DURATION || '30s'),
    preAllocatedVUs,
    maxVUs,
  };
}

const p95Ms = endpointMode === 'cursor'
  ? Number(__ENV.PERF_CURSOR_P95_MS || 100)
  : Number(__ENV.PERF_OFFSET_P95_MS || 250);
const p99Ms = endpointMode === 'cursor'
  ? Number(__ENV.PERF_CURSOR_P99_MS || 200)
  : Number(__ENV.PERF_OFFSET_P99_MS || 500);

export const options = {
  scenarios: {
    [`diary_${endpointMode}_${loadProfile}`]: scenario(),
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    dropped_iterations: ['count==0'],
    'http_req_duration{endpoint:diary-read}': [
      `p(95)<${p95Ms}`,
      `p(99)<${p99Ms}`,
    ],
  },
};

export function setup() {
  const email = __ENV.PERF_USER_EMAIL;
  const password = __ENV.PERF_USER_PASSWORD;
  if (!email || !password) {
    throw new Error('PERF_USER_EMAIL and PERF_USER_PASSWORD are required.');
  }

  const response = http.post(
    `${baseUrl}/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login' } },
  );
  const authenticated = check(response, {
    'login status is 200': (result) => result.status === 200,
    'access token exists': (result) => Boolean(result.json('data.accessToken')),
  });
  if (!authenticated) {
    throw new Error(`Performance user login failed with HTTP ${response.status}.`);
  }
  return { accessToken: response.json('data.accessToken') };
}

export default function (data) {
  let url;
  if (endpointMode === 'cursor') {
    const cursor = __ENV.PERF_CURSOR;
    if (!cursor) {
      throw new Error('PERF_CURSOR is required for the cursor scenario.');
    }
    url = `${baseUrl}/api/diaries/my-diaries/cursor?size=${pageSize}&cursor=${encodeURIComponent(cursor)}`;
  } else {
    const deepPage = Number(__ENV.PERF_DEEP_PAGE || 4000);
    url = `${baseUrl}/api/diaries/my-diaries?page=${deepPage}&size=${pageSize}`;
  }

  const response = http.get(url, {
    headers: { Authorization: `Bearer ${data.accessToken}` },
    tags: {
      endpoint: 'diary-read',
      pagination: endpointMode,
      load_profile: loadProfile,
    },
  });

  check(response, {
    'diary response is 200': (result) => result.status === 200,
    'diary page contains data': (result) => Array.isArray(result.json('data.content')),
  });
}
