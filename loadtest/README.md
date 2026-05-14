# Phase 6 Load Test

## Purpose

Validate SPEC AC #2 and AC #9 with a 50-tenant Pub/Sub load harness that asserts tenant isolation, ledger integrity, and privacy log hygiene against a production-shaped compose stack.

## Prerequisites

- `k6 >= 1.7.0`
- Docker Compose v2 (`docker compose`)
- Java 25 and Gradle 9
- `psql` client on PATH; the invariant verifier uses psql shell-out, not JDBC (codex HIGH-8)
- OpenSSL for generating an ephemeral AES-GCM key

## Pre-tag commit step (codex HIGH-9 - committed evidence)

Run the full load test locally before cutting the `v1.0.0-rc1` tag and commit the generated `.planning/phases/06-polish-casa-verified-launch/06-LOAD-TEST-RESULT.md` file to `main`. The release workflow on the tag re-runs the same sequence for record, but the committed local result is the evidence file that breaks the tag/report circular dependency.

## Run

0. MED-4 pre-run cleanup:

```bash
docker compose -f loadtest/compose.loadtest.yml down -v || true
```

1. Build images:

```bash
./gradlew --no-daemon :backend:api:bootBuildImage --imageName=zeromail-api:loadtest && ./gradlew --no-daemon :backend:worker:bootBuildImage --imageName=zeromail-worker:loadtest
```

2. Generate an ephemeral AES key and bring up the stack:

```bash
export REFRESH_TOKEN_KEY_BASE64=$(openssl rand -base64 32); docker compose -f loadtest/compose.loadtest.yml up -d --wait
```

3. Seed 50 tenants (codex HIGH-6), then capture the comma-joined UUID list:

```bash
PGPASSWORD=zeromail psql -h localhost -p 15433 -U zeromail -d zeromail -f loadtest/scripts/seed-tenants.sql
export LOADTEST_TENANT_UUIDS=$(PGPASSWORD=zeromail psql -h localhost -p 15433 -U zeromail -d zeromail -tAc "SELECT string_agg(tenant_id::text, ',' ORDER BY slug) FROM loadtest_tenant")
```

4. Drive load:

```bash
API_BASE_URL=http://localhost:8080 LOADTEST_TENANT_UUIDS=$LOADTEST_TENANT_UUIDS k6 run loadtest/scripts/golden-path.js
```

5. MED-3 worker-drain wait:

```bash
bash loadtest/scripts/wait-for-worker-drain.sh
```

6. Capture logs:

```bash
mkdir -p loadtest/run && docker compose -f loadtest/compose.loadtest.yml logs --no-color --no-log-prefix api worker > loadtest/run/run.log
```

7. Run invariant verifier:

```bash
./gradlew --no-daemon :backend:api:loadtestVerify
```

8. PRE-TAG ONLY (codex HIGH-9):

```bash
git add .planning/phases/06-polish-casa-verified-launch/06-LOAD-TEST-RESULT.md && git commit -m "docs(06): commit load-test result for v1.0.0-rc1-candidate" && git push origin main
```

Skip step 8 when the sequence is triggered by CI on a tag commit.

## Teardown

```bash
docker compose -f loadtest/compose.loadtest.yml down -v
```

## Fail-Fast Property (D-15)

`REFRESH_TOKEN_KEY_BASE64` must be present in the shell before `docker compose up -d --wait`. The compose file injects the same value into both `api` and `worker`; if it is missing, Spring placeholder resolution fails during boot instead of producing unreadable AES-GCM ciphertext later.

## Result file

`.planning/phases/06-polish-casa-verified-launch/06-LOAD-TEST-RESULT.md` is committed evidence. `loadtest/run/*` is a local or CI runtime artifact and is gitignored.

## Invariants asserted

- (a) `triage_audit.tenant_id NOT IN (SELECT tenant_id FROM loadtest_tenant) COUNT = 0`
- (b1) per-reservation: not more than one SETTLE, not more than one RELEASE, never both
- (b2) per loadtest tenant: `SUM(amount_credits) >= 0`
- (c) regex `email_body|prompt|completion|raw_html` on `loadtest/run/run.log` returns zero matches

## Decision references

D-01 through D-05 and D-15 define the k6 tool choice, compose environment, profile guard, invariant verifier, deterministic tenant prefix, and refresh-token key injection contract.
