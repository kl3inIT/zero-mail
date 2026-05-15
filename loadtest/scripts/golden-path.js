// Phase 6 50-tenant load test. Tenants are 50 UUIDs from __ENV.LOADTEST_TENANT_UUIDS (seeded by loadtest/scripts/seed-tenants.sql).
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import encoding from 'k6/encoding';

const tenants = new SharedArray('tenants', () => {
  const raw = __ENV.LOADTEST_TENANT_UUIDS;
  if (!raw) {
    throw new Error(
      'LOADTEST_TENANT_UUIDS env var must be set (comma-separated 50 UUIDs from loadtest/scripts/seed-tenants.sql)'
    );
  }
  const uuids = raw
    .split(',')
    .map((tenantUuid) => tenantUuid.trim())
    .filter(Boolean);
  if (uuids.length !== 50) {
    throw new Error(`Expected 50 tenant UUIDs, got ${uuids.length}`);
  }
  return uuids;
});

export const options = {
  discardResponseBodies: true,
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true }],
    'http_req_duration{kind:pubsub_push}': ['p(99)<2000'],
  },
  scenarios: {
    pubsub_push: {
      executor: 'constant-arrival-rate',
      duration: '10m',
      rate: 500,
      timeUnit: '1m',
      preAllocatedVUs: 50,
      maxVUs: 200,
      tags: { kind: 'pubsub_push' },
    },
  },
};

export default function () {
  const tenantUuid = tenants[Math.floor(Math.random() * tenants.length)];
  const historyId = Math.floor(Math.random() * 1_000_000_000);
  const innerData = encoding.b64encode(
    JSON.stringify({
      emailAddress: `${tenantUuid}@loadtest.invalid`,
      historyId,
    })
  );
  const envelope = {
    message: {
      data: innerData,
      messageId: uuidv4(),
      publishTime: new Date().toISOString(),
    },
    subscription: 'projects/loadtest/subscriptions/loadtest-sub',
  };

  const response = http.post(
    `${__ENV.API_BASE_URL}/internal/pubsub/gmail`,
    JSON.stringify(envelope),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer loadtest-stub-token',
      },
      tags: { kind: 'pubsub_push' },
    }
  );

  check(response, { 'pubsub push accepted': (httpResponse) => httpResponse.status >= 200 && httpResponse.status < 300 });
}

export function handleSummary(data) {
  return {
    'loadtest/run/summary.json': JSON.stringify(data, null, 2),
  };
}
