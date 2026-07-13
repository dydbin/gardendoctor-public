// Managed by the centralized infra load-test profile.
import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.PERF_BASE_URL || 'http://backend:8080';

export const options = {
  scenarios: {
    health_smoke: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 5,
      maxDuration: '30s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    'http_req_duration{endpoint:health}': ['p(95)<1000'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}/actuator/health/readiness`, {
    tags: { endpoint: 'health' },
  });

  check(response, {
    'health status is 200': (result) => result.status === 200,
    'readiness is UP': (result) => result.json('status') === 'UP',
  });
  sleep(0.2);
}
