---
title: Admin Load Probe Scaffold
phase: 08-admin-console-operator-tooling
plan: 8A
---

# Admin Load Probe Scaffold

This probe is a manual validation scaffold for Phase 8 admin-path safety checks.
It is not part of `./gradlew test`; run it only against a disposable staging
environment with seeded admin credentials.

## k6 outline

Save the script as `/tmp/admin-body-ban-probe.js` on the operator workstation:

```js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 25,
  duration: '2m',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<750'],
  },
};

const adminBaseUrl = __ENV.ADMIN_BASE_URL || 'https://admin.zeromail.vn';
const sessionCookie = __ENV.ZEROMAIL_ADMIN;

export default function () {
  const headers = {
    Cookie: `ZEROMAIL_ADMIN=${sessionCookie}`,
    Accept: 'application/json',
  };

  const auditResponse = http.get(`${adminBaseUrl}/api/admin/audit/events?limit=20`, { headers });
  check(auditResponse, {
    'audit status is 200': (response) => response.status === 200,
    'no raw email body marker': (response) => !response.body.includes('bodyText'),
    'no sentinel key marker': (response) => !/sk-ant-|sk-or-|AIza/.test(response.body),
  });

  sleep(1);
}
```

## Invocation

```sh
ADMIN_BASE_URL=https://admin-staging.zeromail.com \
ZEROMAIL_ADMIN='<cookie value from staging login>' \
k6 run /tmp/admin-body-ban-probe.js
```

Record the k6 summary, API logs around the run, and the database count for
`admin_read_event` if the probe touches tenant-read endpoints in later Phase 8
plans.

