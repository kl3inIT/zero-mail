# Phase 6: Polish & CASA-Verified Launch — Research

**Researched:** 2026-05-14
**Domain:** Launch-readiness validation (load test + golden-path E2E + reusable CI gate + launch decision artifact)
**Confidence:** HIGH on k6, Playwright `webServer` arrays, GHA `workflow_call`, ArchUnit, Spring Boot 4 conditional beans, Compose v2 healthchecks. MEDIUM on the Dockerfile/image-source choice for the load-test compose stack (no existing Dockerfile is checked in — see Open Question 1).

This document is target-shaped at the locked decisions in `06-CONTEXT.md` (D-01..D-15). It is prescriptive, not exploratory. Every external claim is tagged with its source; project-internal claims cite file paths.

---

## User Constraints (from CONTEXT.md)

### Locked Decisions

| ID | Lock |
|---|---|
| D-01 | Load tool = **k6** (Grafana). Scripts under top-level `loadtest/`. k6 binary installed in GHA via `grafana/setup-k6-action`. |
| D-02 | Load-test env = **ephemeral docker-compose on the GHA runner** (`loadtest/compose.loadtest.yml`): postgres-17 + redis-7 + prod `backend/api` + prod `backend/worker`. Tear down with `docker compose down -v`. **Reason: prod `logback-spring.xml` must load.** |
| D-03 | OIDC verification under load = `@Profile("loadtest")` `PubsubVerifier` stub, **guarded** by `@ConditionalOnProperty("zeromail.loadtest.enabled", havingValue="true")` + an ArchUnit rule + the property set ONLY in `loadtest/compose.loadtest.yml`. |
| D-04 | Invariant assertions = new Gradle task `:backend:api:loadtestVerify`. Connects via JDBC to the compose Postgres, runs (a) cross-tenant audit query, (b) per-tenant ledger reconciliation, (c) regex log-scan against `loadtest/run.log`. Result file: `06-LOAD-TEST-RESULT.md`. |
| D-05 | Synthetic tenants: `loadtest-tenant-<1..50>`. Teardown via `docker compose down -v`. |
| D-06 | Playwright stub strategy = dedicated `e2e-stub` Spring profile, swap `GmailClient` + `PubsubVerifier` with `@Profile("e2e-stub") @Primary` deterministic Java fakes. Pub/Sub leg is a real HTTP POST. Two-tier taxonomy: existing 5 `page.route` specs stay for UI-state; only `launch-golden-path.spec.ts` uses `e2e-stub`. |
| D-07 | `e2e-stub` profile at `backend/api/src/main/resources/application-e2e-stub.yml`. Stubs at `backend/api/src/main/java/com/zeromail/api/e2estub/`. State in-memory (`ConcurrentHashMap`). Reset via `POST /api/test/e2e-stub/reset` guarded by `@ConditionalOnProperty("zeromail.e2e-stub.enabled")`. |
| D-08 | Playwright spec uses **`request.fetch()` / APIRequestContext** for the Pub/Sub push leg — `page.route` cannot intercept server-to-server traffic. |
| D-09 | Post-launch CASA tracking at `.planning/seeds/SEED-012-casa-restricted-scope-verification.md`. |
| D-10 | CI gate = reusable `gates.yml` (`workflow_call`) called by both existing `ci.yml` and a new `release.yml` (`on: push: tags: ['v*.*.*-rc*']`). `release.yml` adds Playwright golden-path under `e2e-stub` + the compose load-test job. Aggregates into a single `release-gates-summary` job. |
| D-11 | Branch/tag protection on `refs/tags/v*.*.*-rc*` requires `release-gates-summary`. The tag itself can be pushed without the check, but `LAUNCH-GO-NOGO.md` (b) stays unchecked until green. |
| D-12 | Cutting `v1.0.0-rc2` requires zero workflow edits — `git tag -a v1.0.0-rc2 <sha>` + `git push --tags`. |
| D-13 | Existing workflows preserved: extract 4 gate jobs from `ci.yml` into `gates.yml`; slim `ci.yml` to a thin caller; `i18n-check.yml` stays as-is; `e2e.yml` either folds in or becomes another `workflow_call` (planner decides). |
| D-14 | `.planning/LAUNCH-GO-NOGO.md` (repo-root planning tree). 8 pass/fail checkboxes (a–h) + sign-off line `✓ signed-off by @<user> on <ISO date>`. |
| D-15 | Worker yml refresh-token-key fail-fast already fixed (`backend/worker/src/main/resources/application.yml:63`). Compose must inject `REFRESH_TOKEN_KEY_BASE64` into BOTH api + worker. Archive the `.planning/todos/pending/2026-04-28-worker-application-yml-fail-fast-parity.md` todo. |

### Claude's Discretion

- k6 script file layout (one `.js` vs per-scenario files).
- HTML report retention policy for k6 / Playwright / load-test result.
- Playwright spec internal structure (page-object split vs inline).
- Exact wording of ArchUnit rules guarding D-03 + D-07.
- Whether `e2e.yml` folds into `gates.yml` or stays separate (`workflow_call`-callable).
- Exact CASA lab pick for SEED-012 (post-launch).
- Exact `loadtest/` directory shape.

### Deferred Ideas (OUT OF SCOPE)

- Production runbook (on-call, Pub/Sub backlog recovery, watch renewal incident, ledger reconciliation playbooks).
- CASA Tier 2 submission + LoA + Production consent-screen move (tracked in SEED-012).
- Throughput SLO / p95 latency gate.
- Multi-region / HA / staging VPS provisioning.

---

## Project Requirements (none new in Phase 6)

Phase 6 introduces zero new REQ-IDs (locked by `06-SPEC.md` constraints). It validates existing requirements:

| ID | Re-verified in Phase 6 by |
|---|---|
| FND-01..FND-05 | Load-test invariant (a) cross-tenant audit query + ArchUnit gate on RC tag |
| FND-03, FND-04 | Load-test invariant (c) regex log-scan against prod-loaded `logback-spring.xml` |
| MAIL-03, MAIL-04 | Playwright golden-path step that POSTs synthetic Pub/Sub envelope to `/internal/pubsub/gmail` |
| TRG-03 | Acceptance criterion #8: "No new code path is added that auto-sends mail" — ArchUnit rule from `DraftPathArchUnitTest.java` is on the RC gate |
| BILL-02..BILL-04 | Load-test invariant (b) ledger reconciliation |
| LLM-09 | Load-test invariant (c) prompt/completion never appears in logs under real triage traffic |

The phase is therefore an integration / hardening / external-verification close-out, not feature work.

---

## Executive Summary (one bullet per locked decision area)

1. **k6 + docker-compose** is the only configuration that satisfies invariant (c) "Logback scrub filter validated on real traffic" — Testcontainers loads test logback and would false-green this. k6 v1.x ships native `constant-arrival-rate` executor — the 50-tenant × ~10 msg/min profile is a literal one-line `rate: 500, timeUnit: '1m'` (see §1.1). `grafana/setup-k6-action@v1` (current v1.2.1, Apr 2026) installs k6 in CI with one step.
2. **Compose v2 healthchecks + `depends_on: condition: service_healthy`** are mandatory; without them, the api/worker containers attempt to connect to postgres/redis before they're listening and Liquibase loops on a half-open socket (see §1.3).
3. **Spring profile-swap with `@Primary` + `@ConditionalOnProperty`** is the project's established pattern (Phase 1.5 P08 CR-04). Both `loadtest` (D-03) and `e2e-stub` (D-07) follow it. ArchUnit rules from `DraftPathArchUnitTest.java` and `I18nArchUnitTest.java` give the predicate shape to copy (see §2.4 + §5.2).
4. **Playwright `webServer` accepts an array** since v1.30 — the canonical way to start both Next.js dev and Spring Boot under `e2e-stub` from a single config file (see §2.1). `request.fetch()` is the documented APIRequestContext entry point for server-to-server POSTs and does NOT share cookies with `page` by default (see §2.3).
5. **GHA `workflow_call` reusable workflows** are stable since 2021 — single-source the 4 existing gate jobs, then `ci.yml` becomes a 1-job caller and `release.yml` calls the same gates plus golden-path + load-test (see §3.1). Tag filter `tags: ['v*.*.*-rc*']` is the verified path syntax (see §3.2). The `release-gates-summary` aggregator job uses `needs: [...] + if: always()` to collect status from all upstream jobs (see §3.3).
6. **Spring Modulith `ApplicationModulesTest`** already exists at `backend/api/src/test/java/com/zeromail/api/ZeroMailApiApplicationModulesTest.java` — a trivial 12-line test that runs as part of `./gradlew check` (`gates.yml` job picks it up automatically).
7. **Java 25 + Spring Boot 4 / Jackson 3 / Spring Framework 7 specifics:** `@Profile` + `@ConditionalOnProperty` are unchanged from Boot 3. `@Primary` resolves bean conflicts deterministically. The known migration trap is `com.fasterxml.jackson.databind.ObjectMapper` → `tools.jackson.databind.ObjectMapper` — already done in `GmailPubSubController.java:13`, so the `e2e-stub` stub just imports `tools.jackson.databind.ObjectMapper` to match (see §5.1).
8. **No Dockerfile is checked in yet (Open Question 1).** Spring Boot's built-in `./gradlew :backend:api:bootBuildImage` task uses paketobuildpacks to produce an OCI image without writing a Dockerfile — this is the lowest-friction path that respects "no new code." Set `bootBuildImage.imageName.set("ghcr.io/...zeromail-api:loadtest")` and reference the local image in compose.
9. **`LAUNCH-GO-NOGO.md` trust-story phrases (D-14 item (g))** require verbatim restatement of three locked strings. Their authoritative sources are documented in §4.2 — the planner can copy directly.
10. **SEED-012 format** must mirror the 11 existing seeds (see `.planning/seeds/SEED-001` ... `SEED-011`). The structural template is documented in §4.1.

---

## 1. k6 + docker-compose Load Harness (D-01..D-05, D-15)

### 1.1 k6 script shape — 50 tenants × ~10 msg/min

The phase profile is "50 tenants emitting roughly 10 messages/minute each, sustained for ≥10 minutes" — that is a deterministic arrival rate of **500 iterations/minute = 8.33/sec**, NOT a VU saturation profile. The correct executor is **`constant-arrival-rate`**. Throughput is rate-driven and decoupled from VU count, which is exactly the property invariant testing needs.

Source: [Grafana k6 docs — constant-arrival-rate executor](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/) (verified via Context7 `/grafana/k6-docs`).

Recommended canonical structure (`loadtest/scripts/golden-path.js`):

```javascript
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import encoding from 'k6/encoding';

const TENANTS = new SharedArray('tenants', () =>
  Array.from({ length: 50 }, (_, i) => `loadtest-tenant-${i + 1}`)
);

export const options = {
  // Hard fail conditions: any HTTP 5xx OR error rate > 1% aborts the test.
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true }],
    'http_req_duration{kind:pubsub_push}': ['p(99)<2000'],
  },
  scenarios: {
    pubsub_push: {
      executor: 'constant-arrival-rate',
      duration: '10m',
      rate: 500,            // 500 iterations per minute = 50 tenants × ~10 msg/min
      timeUnit: '1m',
      preAllocatedVUs: 50,
      maxVUs: 200,
      tags: { kind: 'pubsub_push' },
    },
  },
  discardResponseBodies: true,
};

export default function () {
  const tenant = TENANTS[Math.floor(Math.random() * TENANTS.length)];
  const historyId = String(Date.now() + Math.floor(Math.random() * 1_000_000));

  // GmailNotification JSON (matches PubSubPushEnvelope expected by /internal/pubsub/gmail).
  const innerData = encoding.b64encode(
    JSON.stringify({ emailAddress: `${tenant}@loadtest.invalid`, historyId })
  );
  const envelope = {
    message: {
      data: innerData,
      messageId: uuidv4(),
      publishTime: new Date().toISOString(),
    },
    subscription: 'projects/loadtest/subscriptions/loadtest-sub',
  };

  const res = http.post(
    `${__ENV.API_BASE_URL}/internal/pubsub/gmail`,
    JSON.stringify(envelope),
    {
      headers: {
        'Content-Type': 'application/json',
        // Loadtest profile's PubsubVerifier accepts anything; a non-empty
        // Bearer is still required by the OncePerRequestFilter shape (api/.../security/PubSubOidcAuthFilter.java:46).
        Authorization: 'Bearer loadtest-stub-token',
      },
      tags: { kind: 'pubsub_push' },
    }
  );
  check(res, { 'push accepted': (r) => r.status >= 200 && r.status < 300 });
}

// Persist a structured summary alongside k6's stdout output so the Gradle task
// has a machine-readable artifact to embed in 06-LOAD-TEST-RESULT.md.
export function handleSummary(data) {
  return {
    'loadtest/run/summary.json': JSON.stringify(data, null, 2),
    stdout: JSON.stringify({ iterations: data.metrics.iterations.values.count }),
  };
}
```

Notes:
- `discardResponseBodies: true` keeps memory flat on a GHA runner (no streamed payloads to retain). Docs explicitly recommend this for high-throughput tests.
- `abortOnFail` on `http_req_failed` short-circuits the test if the server starts 5xx-ing — the invariant pass-checks below would be meaningless if the system was already broken under load.
- `handleSummary` is k6's documented hook for writing files at end of run (no plugin needed). [k6 handleSummary docs](https://grafana.com/docs/k6/latest/results-output/end-of-test/custom-summary/).

Planner's discretion (D-allows): single-file vs per-scenario. Recommend **single file** for this phase — only one scenario, no future scenarios expected, fewer moving parts. A `loadtest/scripts/lib/` directory is the natural escape hatch if helpers grow.

### 1.2 `grafana/setup-k6-action` — current version + usage

[VERIFIED via WebFetch of https://github.com/grafana/setup-k6-action] **Current version: v1.2.1 (released 2026-04-29).**

Canonical step:

```yaml
- uses: grafana/setup-k6-action@v1
  with:
    k6-version: '1.7.0'   # optional; latest if omitted
    browser: false        # default — we don't need browser mode
```

Companion action `grafana/run-k6-action@v1` exists but is unnecessary — invoking `k6 run loadtest/scripts/golden-path.js` from a plain `run:` step is simpler and gives explicit control over env vars (`API_BASE_URL`, etc.).

Alternatives if the action becomes stale:
- Direct binary install: `curl -fsSL https://github.com/grafana/k6/releases/download/v1.7.0/k6-v1.7.0-linux-amd64.tar.gz | tar xz`.
- `apt-get install k6` via Grafana's APT repo (slower than the action).

### 1.3 docker compose v2 — `loadtest/compose.loadtest.yml`

Compose v2 is the **plugin** form (`docker compose ...`, no dash), available on `ubuntu-latest` GHA runners by default since 2023. The Spring Boot 4 stack needs healthchecks + `depends_on: condition: service_healthy` so the api/worker containers do not start until postgres + redis are accepting connections — without this Liquibase loops on a half-open socket and the boot fail-fast at `application.yml:102` looks like a port collision.

Source: [Docker Compose docs — healthcheck](https://docs.docker.com/reference/compose-file/services/#healthcheck) and the Compose v2 source ([docker/compose convergence.go](https://github.com/docker/compose/blob/main/compose/pkg/compose/convergence.go)) confirm that `condition: service_healthy` is honored at `up` time.

Skeleton:

```yaml
# loadtest/compose.loadtest.yml
name: zeromail-loadtest

services:
  postgres-loadtest:
    image: postgres:17.6
    environment:
      POSTGRES_DB: zeromail
      POSTGRES_USER: zeromail
      POSTGRES_PASSWORD: zeromail
    ports:
      - "15433:5432"   # avoid collision with dev 15432
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U zeromail -d zeromail"]
      interval: 2s
      timeout: 3s
      retries: 30
      start_period: 5s

  redis-loadtest:
    image: redis:7.2
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 2s
      timeout: 3s
      retries: 30

  api:
    image: ghcr.io/<org>/zeromail-api:loadtest   # built locally via bootBuildImage; see Open Q1
    depends_on:
      postgres-loadtest:
        condition: service_healthy
      redis-loadtest:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: loadtest
      DB_URL: jdbc:postgresql://postgres-loadtest:5432/zeromail
      DB_USER: zeromail
      DB_PASSWORD: zeromail
      REDIS_HOST: redis-loadtest
      REDIS_PORT: "6379"
      # D-03 guard: only the loadtest compose sets this.
      ZEROMAIL_LOADTEST_ENABLED: "true"
      # D-15 fail-fast verification — same key for both containers; AES-GCM
      # contract requires both sides decrypt the same refresh tokens.
      REFRESH_TOKEN_KEY_BASE64: ${REFRESH_TOKEN_KEY_BASE64}
      # Required env vars Spring will :? fail-fast on (api/.../application.yml).
      PUBSUB_PUSH_AUDIENCE_URL: http://api:8080/internal/pubsub/gmail
      PUBSUB_SA_PRINCIPAL_EMAIL: loadtest@invalid
      # Dummies for required secrets (never reach the network — loadtest disables outbound).
      GOOGLE_OAUTH_CLIENT_ID: loadtest
      GOOGLE_OAUTH_CLIENT_SECRET: loadtest
      ZEROMAIL_LLM_PLATFORM_API_KEY: loadtest
      SEPAY_WEBHOOK_API_KEY: loadtest
      ZEROMAIL_BILLING_BANK_CODE: loadtest
      ZEROMAIL_BILLING_BANK_NAME: loadtest
      ZEROMAIL_BILLING_ACCOUNT_NUMBER: "0000"
      ZEROMAIL_BILLING_ACCOUNT_NAME: loadtest
    ports:
      - "8080:8080"
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 5s
      timeout: 3s
      retries: 60
      start_period: 30s   # JVM cold-start budget

  worker:
    image: ghcr.io/<org>/zeromail-worker:loadtest
    depends_on:
      postgres-loadtest:
        condition: service_healthy
      redis-loadtest:
        condition: service_healthy
      # Wait for api so Liquibase only runs in one container (see Pitfall §7.1).
      api:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: loadtest
      DB_URL: jdbc:postgresql://postgres-loadtest:5432/zeromail
      DB_USER: zeromail
      DB_PASSWORD: zeromail
      REDIS_HOST: redis-loadtest
      REDIS_PORT: "6379"
      REFRESH_TOKEN_KEY_BASE64: ${REFRESH_TOKEN_KEY_BASE64}
      GOOGLE_PUBSUB_TOPIC_NAME: loadtest-topic
      # ... matching dummies for worker required env vars (application.yml:61..)
      SEPAY_WEBHOOK_API_KEY: loadtest
      RESEND_API_KEY: loadtest
      ZEROMAIL_LLM_PLATFORM_API_KEY: loadtest
      ZEROMAIL_BILLING_BANK_CODE: loadtest
      ZEROMAIL_BILLING_BANK_NAME: loadtest
      ZEROMAIL_BILLING_ACCOUNT_NUMBER: "0000"
      ZEROMAIL_BILLING_ACCOUNT_NAME: loadtest
```

Pitfalls to surface to the planner:
- **Liquibase double-run** (see §7.1) — set worker `depends_on: api: condition: service_healthy` so api runs Liquibase first; worker skips because ddl-auto=validate finds the schema already there.
- **JVM cold start** — `start_period: 30s` for `/actuator/health/readiness` matches the Spring Boot probe (api/.../application.yml:154-158 already enables `probes`).
- **Container-to-container DNS** — service names (`postgres-loadtest`, `redis-loadtest`, `api`) resolve via Compose's default network; do NOT use `localhost` from inside containers. The k6 process runs on the GHA runner host and uses `localhost:8080` (published port).

### 1.4 Image source — `bootBuildImage` vs Dockerfile

No Dockerfile exists in the repo (verified via `find ... -name "Dockerfile*"`). CLAUDE.md prescribes `eclipse-temurin:25-jre-noble` + Spring Boot CDS/AOT layered images, but no implementation has landed.

**Recommended path (lowest churn, respects "no new application code"):** invoke Spring Boot's built-in `bootBuildImage` Gradle task in a CI step:

```yaml
- name: Build api OCI image
  run: ./gradlew --no-daemon :backend:api:bootBuildImage --imageName=zeromail-api:loadtest

- name: Build worker OCI image
  run: ./gradlew --no-daemon :backend:worker:bootBuildImage --imageName=zeromail-worker:loadtest
```

This uses paketobuildpacks under the hood — same JDK 25 base, CDS/AOT layered images, no Dockerfile required. Then reference `zeromail-api:loadtest` / `zeromail-worker:loadtest` (locally available) in `compose.loadtest.yml`. **Open Question 1** flags this for planner / discuss-phase confirmation — alternative is a tiny `backend/api/Dockerfile` committed to the repo. Source: [Spring Boot 4 reference — bootBuildImage](https://docs.spring.io/spring-boot/gradle-plugin/packaging-oci-image.html) (verified via Context7 `/spring-projects/spring-boot/v4.0.3`).

### 1.5 Capturing prod-config logs from compose

```bash
# After k6 finishes and BEFORE docker compose down -v
docker compose -f loadtest/compose.loadtest.yml logs --no-color --no-log-prefix api worker > loadtest/run/run.log
```

Pitfalls:
- **Log truncation.** GHA runners default to `json-file` driver with no size limit — fine for 10-min runs.
- **Multi-line stack traces.** `--no-log-prefix` strips the leading `api-1  |` prefix so the regex scan operates on clean lines. Stack traces remain multi-line; the regex `email_body|prompt|completion|raw_html` (D-04 invariant c) matches per-line, which is correct.
- **Buffering.** Logback in `core/src/main/resources/logback-spring.xml` writes to stdout; container captures both stdout/stderr by default. Confirmed by reading the file path.
- **Log timestamp window filtering.** Capture the k6 `started_at` / `ended_at` timestamps and only scan that window in the regex check (the api container booted earlier, and boot-time logs about `loadtest` profile activation are NOT a privacy violation).

### 1.6 Gradle task `:backend:api:loadtestVerify` — JDBC + invariant SQL

The task connects from the GHA runner host (not inside compose) to the **published** postgres port (`15433` per §1.3). JDBC URL: `jdbc:postgresql://localhost:15433/zeromail`.

Why this instead of in-container query: the JVM the task runs in must be ephemeral (no leftover connections that prevent `compose down -v`). Gradle's task lifecycle handles this cleanly; we just need to close the JDBC connection in `doLast {}`.

Skeleton (build.gradle.kts on `:backend:api`):

```kotlin
tasks.register("loadtestVerify") {
    group = "verification"
    description = "Runs invariant checks against the loadtest compose Postgres + run.log"
    doLast {
        val jdbcUrl = System.getenv("LOADTEST_DB_URL") ?: "jdbc:postgresql://localhost:15433/zeromail"
        val user = System.getenv("LOADTEST_DB_USER") ?: "zeromail"
        val pass = System.getenv("LOADTEST_DB_PASSWORD") ?: "zeromail"
        java.sql.DriverManager.getConnection(jdbcUrl, user, pass).use { connection ->
            // (a) Cross-tenant audit query
            val crossTenant = connection.prepareStatement(
                "SELECT COUNT(*) FROM triage_audit WHERE tenant_id::text NOT LIKE 'loadtest-tenant-%'"
            ).executeQuery().let { rs -> rs.next(); rs.getInt(1) }
            require(crossTenant == 0) { "Invariant (a) FAIL: $crossTenant rows from non-loadtest tenant" }

            // (b) Per-tenant ledger reconciliation
            val drift = connection.prepareStatement("""
                SELECT tenant_id::text, SUM(amount_credits) AS net
                FROM credit_ledger_entry
                WHERE tenant_id::text LIKE 'loadtest-tenant-%'
                GROUP BY tenant_id
                HAVING SUM(amount_credits) <> 0
            """).executeQuery().let { rs ->
                val rows = mutableListOf<String>()
                while (rs.next()) rows.add("${rs.getString(1)}=${rs.getInt(2)}")
                rows
            }
            require(drift.isEmpty()) { "Invariant (b) FAIL: ledger drift ${drift.joinToString()}" }
        }

        // (c) Regex log scan
        val logFile = rootProject.file("loadtest/run/run.log")
        val pattern = Regex("email_body|prompt|completion|raw_html")
        val violations = logFile.readLines().filter { pattern.containsMatchIn(it) }
        require(violations.isEmpty()) { "Invariant (c) FAIL: ${violations.size} log-bleed lines" }

        // Write result artifact (committed via D-04)
        rootProject.file(".planning/phases/06-polish-casa-verified-launch/06-LOAD-TEST-RESULT.md").writeText("""
            # Load-Test Result — ${java.time.Instant.now()}

            - Invariant (a) cross-tenant audit: PASS (0 leaked rows)
            - Invariant (b) per-tenant ledger drift: PASS (${50} tenants, all net=0)
            - Invariant (c) log-bleed regex: PASS (0 violating lines)
        """.trimIndent())
    }
}
```

Notes:
- Use the built-in JDBC `DriverManager` — no need for a HikariCP pool; one connection, three queries, closed via `use { }`.
- `triage_audit` is the correct table name (verified at `backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java:20`).
- `credit_ledger_entry` is the correct table name (verified at `backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryEntity.java:14`). Net-zero invariant follows from the `topup/reserve/settle/release` static factories: a complete reservation cycle is `RESERVE(-N) + RELEASE(+N) = 0` or `RESERVE(-N) + SETTLE(0) + ... ` — note SETTLE has `amountCredits=0`, so the "net=0 per loadtest tenant" check requires that the load test never produces TOPUP rows (which it doesn't — k6 only POSTs Pub/Sub envelopes).
- Index hint: `triage_audit` and `credit_ledger_entry` both inherit `AbstractTenantOwnedEntity`, which (Phase 1.2) defines an index on `tenant_id`. EXPLAIN ANALYZE on the cross-tenant query at 50-tenant × 10-min scale should index-scan, not seq-scan — confirmed at expected ~5k-50k rows.

### 1.7 Injecting `REFRESH_TOKEN_KEY_BASE64` into both containers (D-15)

Source the key from a GHA secret and pass via env (Compose `${VAR}` interpolation reads from the runner's shell env):

```yaml
# In release.yml (or whichever job runs the loadtest):
- name: Generate ephemeral AES-GCM key
  run: |
    echo "REFRESH_TOKEN_KEY_BASE64=$(openssl rand -base64 32)" >> $GITHUB_ENV

- name: Bring up loadtest compose stack
  run: docker compose -f loadtest/compose.loadtest.yml up -d --wait
```

Both `api` and `worker` env blocks in §1.3 reference `${REFRESH_TOKEN_KEY_BASE64}`. If unset, Spring's `:?` placeholder at `application.yml:102` + worker `:63` fails the boot with the prescribed error message — exactly the fail-fast property D-15 requires.

---

## 2. Playwright `e2e-stub` Profile + Golden-Path Spec (D-06..D-08)

### 2.1 Spring profile bean swap — `@Profile("e2e-stub") @Primary`

The established Spring pattern for swapping beans in tests is well-defined in Boot 4 (no migration from Boot 3): `@Profile` + `@Primary` resolve the autowiring conflict deterministically. `@ConditionalOnProperty` adds a second guard so the bean never activates without both `spring.profiles.active=e2e-stub` AND `zeromail.e2e-stub.enabled=true` (D-07).

Source: [Spring Boot 4 reference — Condition Annotations](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html#features.developing-auto-configuration.condition-annotations) (verified via Context7 `/spring-projects/spring-boot/v4.0.3`).

Project precedent: `backend/api/src/test/java/com/zeromail/api/debug/DebugController.java:14` uses the bare `@Profile("test")` pattern. The e2e-stub stubs use the same shape, with `@Primary` added because they're overriding existing prod beans:

```java
package com.zeromail.api.e2estub;

import com.zeromail.core.gmail.gateway.GmailClient; // adapter interface — verify exact name at planning time
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("e2e-stub")
@Primary
@ConditionalOnProperty(name = "zeromail.e2e-stub.enabled", havingValue = "true")
public class E2eStubGmailClient implements GmailClient {
    private final java.util.concurrent.ConcurrentMap<String, FakeMessage> messages =
        new java.util.concurrent.ConcurrentHashMap<>();
    // ... fake implementations returning deterministic responses
    public void reset() { messages.clear(); }
}
```

The `GmailClient` adapter interface needs to be confirmed at plan time — `find` only surfaced concrete classes (`GmailApiClientFactory`, `GmailConnectionService`, `GmailDeliveryProcessingService`). The planner must identify the exact interface seam (likely `core.gmail.gateway.*`).

`PubsubVerifier` swap mirrors the same pattern. The current production class is in `backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java:16` — but it's a Servlet filter, not an injectable verifier. **Open Question 2:** does the `e2e-stub` swap replace the filter (via a `@Profile("e2e-stub") @Bean` definition in `PubSubSecurityConfig`) or wrap the underlying `TokenVerifier`? The cleanest design is to extract `TokenVerifier` into a `@Bean` so it's swappable; if that hasn't been done yet, the planner needs a one-line refactor task — which DOES technically add code, but the SPEC out-of-scope is "new application code or product features," not test/profile infrastructure. Recommend planner clarify.

### 2.2 Playwright `webServer` — multiple commands for Spring Boot + Next.js

Playwright `webServer` accepts an array since 1.30 (Jan 2023). Source: [Playwright docs — webServer multiple servers](https://playwright.dev/docs/test-webserver#multiple-web-servers) (verified via Context7 `/microsoft/playwright.dev`).

Current config at `apps/web/playwright.config.ts:34-39` declares a single `webServer` for `pnpm dev`. Migrate to:

```typescript
webServer: [
  {
    command: 'pnpm dev',
    url: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3000',
    name: 'Frontend',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  {
    // Only spin up Spring Boot when the golden-path spec is in the test set.
    command: '../../gradlew :backend:api:bootRun --args=\'--spring.profiles.active=e2e-stub --zeromail.e2e-stub.enabled=true\'',
    url: 'http://localhost:8080/actuator/health/readiness',
    name: 'Backend',
    reuseExistingServer: !process.env.CI,
    timeout: 240_000,  // JVM cold start + Liquibase
    cwd: '../..',      // run from repo root so Gradle resolves project paths
  },
],
```

Pitfalls (verified via project structure):
- **Port collision.** `apps/web/playwright.config.ts:25` already maps `baseURL` to 3000; the backend goes to 8080 (api/application.yml:132). Both `reuseExistingServer: !process.env.CI` honors `pnpm dev` from another terminal locally; in CI both spawn fresh.
- **Slow JVM start.** 240s timeout is conservative — Liquibase + Spring Boot 4 boot on JDK 25 with virtual threads enabled typically completes in 30-60s on a GHA runner. Use `/actuator/health/readiness` (already exposed at `application.yml:154-158`) rather than a port check; this guarantees the database is migrated and the app is ready for requests.
- **Dev DB reuse.** The `e2e-stub` profile inherits the default `application.yml` datasource (postgres on localhost:15432 via `docker-compose.yml`). Reset between specs uses the `POST /api/test/e2e-stub/reset` endpoint (D-07), not DB truncation — keeps state in-memory in the stubs themselves. Real database tables (audit, ledger) are written to by the real backend code paths and cleaned via the existing test transaction rollback OR an explicit cleanup endpoint if the spec touches committed state.
- **Leftover state between specs.** The reset endpoint MUST clear: stub Gmail message store, stub OIDC token state, any in-memory tenant filters. The reset endpoint itself MUST be `@ConditionalOnProperty("zeromail.e2e-stub.enabled")` so it cannot be hit in prod.

Alternative considered + rejected: a Playwright `globalSetup` script that pre-starts Gradle. Rejected because Playwright's `webServer` already handles lifecycle (start/stop/wait/kill on test-run termination) — `globalSetup` would duplicate this.

### 2.3 `request.fetch()` for the Pub/Sub push leg (D-08)

The `request` fixture provides an isolated `APIRequestContext` per test. Source: [Playwright docs — APIRequestContext](https://playwright.dev/docs/api/class-apirequestcontext) (verified via Context7).

```typescript
test('golden path: pub/sub push -> triage -> draft', async ({ page, request }) => {
  // 1. Reset stub state
  await request.post('/api/test/e2e-stub/reset');

  // 2. UI flow: OAuth sign-in via stub, connect Gmail, enable a template rule
  await page.goto('/login');
  // ... (use existing AuthTopBar helpers from apps/web/e2e/chrome-test-utils.ts)

  // 3. Pub/Sub push — server-to-server, NOT through browser
  const envelope = {
    message: {
      data: Buffer.from(JSON.stringify({
        emailAddress: 'goldenpath@e2e-stub.invalid',
        historyId: '12345',
      })).toString('base64'),
      messageId: 'spec-msg-1',
      publishTime: new Date().toISOString(),
    },
    subscription: 'projects/e2e-stub/subscriptions/sub',
  };
  const pushResponse = await request.post(
    'http://localhost:8080/internal/pubsub/gmail',
    {
      data: envelope,
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer e2e-stub-token',
      },
    }
  );
  expect(pushResponse.status()).toBeGreaterThanOrEqual(200);
  expect(pushResponse.status()).toBeLessThan(300);

  // 4. Observe triage in UI
  await page.goto('/triage');
  await expect(page.getByText('goldenpath@e2e-stub.invalid')).toBeVisible();

  // 5. Undo, draft, analytics ...
});
```

Pitfalls:
- **Cookie sharing.** The `request` fixture by default does NOT share storage state with `page`. If the backend requires a session cookie on the Pub/Sub push endpoint — it does NOT, see `PubSubOidcAuthFilter.java:35-37` which only filters `/internal/pubsub/` paths (a separate security chain). So `request.fetch()` against `/internal/pubsub/gmail` works without session.
- **Base URL.** `request` honors the `use.baseURL` config (set to `http://localhost:3000` for the frontend). The Pub/Sub push needs an **absolute** URL (`http://localhost:8080/...`) because it's a different origin. Document this clearly in the spec.
- **JSON envelope shape.** Must match `PubSubPushEnvelope` (`backend/api/src/main/java/com/zeromail/api/dto/gmail/PubSubPushEnvelope.java`). The shape above matches the controller at `GmailPubSubController.java:31-58`.
- **Signed-but-fake OIDC token.** The `e2e-stub` profile's verifier accepts any non-empty Bearer token — same shape as the loadtest stub (§1.1).

### 2.4 ArchUnit rules guarding D-03 + D-07

Project precedent: `backend/core/src/test/java/com/zeromail/core/draft/DraftPathArchUnitTest.java` and `backend/api/src/test/java/com/zeromail/api/arch/I18nArchUnitTest.java`. Both use `@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)` — copy this shape exactly.

Rule A: production classes never reference loadtest/e2e-stub stubs.

```java
@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
public class LaunchProfileArchUnitTest {

    @ArchTest
    static final ArchRule production_does_not_reference_e2estub =
        noClasses()
            .that().resideOutsideOfPackage("..api.e2estub..")
            .should().dependOnClassesThat().resideInAPackage("..api.e2estub..");

    @ArchTest
    static final ArchRule production_does_not_reference_loadtest =
        noClasses()
            .that().resideOutsideOfPackage("..loadtest..")
            .and().resideInAnyPackage("..api..", "..core..", "..worker..")
            .should().dependOnClassesThat().resideInAPackage("..loadtest..");
}
```

Source: [ArchUnit `dependOnClassesThat`](https://www.archunit.org/userguide/html/000_Index.html) (verified via Context7 `/tng/archunit`).

Rule B: production application.yml files never list `loadtest` or `e2e-stub` profiles. ArchUnit's classpath-based scanning doesn't read YAML — this rule belongs in a tiny JUnit test that reads the resource files and asserts:

```java
@Test
void production_application_yml_does_not_activate_test_profiles() throws Exception {
    for (String resource : List.of("/application.yml")) {
        String content = new String(getClass().getResourceAsStream(resource).readAllBytes(),
                                    StandardCharsets.UTF_8);
        // spring.profiles.active line or include list must not contain loadtest or e2e-stub
        assertThat(content).doesNotContain("loadtest").doesNotContain("e2e-stub");
        // zeromail.loadtest.enabled and zeromail.e2e-stub.enabled must not be set to true
        assertThat(content).doesNotMatch("(?m)zeromail\\.(loadtest|e2e-stub)\\.enabled\\s*:\\s*true");
    }
}
```

Note `ImportOption.DoNotIncludeTests.class` (used at `I18nArchUnitTest.java:47`) is critical for Rule A — without it, the rule fires falsely on test classes that legitimately reference stub packages.

---

## 3. Reusable GHA Workflow + RC-Tag Gate (D-10..D-13)

### 3.1 `workflow_call` syntax — extract gate jobs from `ci.yml`

Source: [GitHub docs — Reusing workflows](https://docs.github.com/en/actions/using-workflows/reusing-workflows) (verified via Context7 `/websites/github_en_actions`).

**File 1 — `.github/workflows/gates.yml`** (new, reusable):

```yaml
name: Gates

on:
  workflow_call:
    inputs:
      run-ai-eval:
        type: boolean
        default: true
        description: 'Whether to run :backend:core:aiEval -PdeterministicOnly'
    # secrets: inherit at call sites — no explicit secrets needed by gates

permissions:
  contents: read

jobs:
  backend:
    name: Backend Gradle
    runs-on: ubuntu-latest
    timeout-minutes: 25
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: '25', check-latest: true }
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew --no-daemon check

  ai-eval:
    name: Backend AI Eval
    if: ${{ inputs.run-ai-eval }}
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: '25', check-latest: true }
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew --no-daemon :backend:core:aiEval -PdeterministicOnly

  frontend:
    name: Frontend Web
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v6
      - uses: pnpm/action-setup@v6
      - uses: actions/setup-node@v6
        with: { node-version: lts/*, check-latest: true, cache: pnpm, cache-dependency-path: pnpm-lock.yaml }
      - run: pnpm install --frozen-lockfile
      - run: pnpm --filter web run lint
      - run: pnpm --filter web run typecheck
      - run: pnpm --filter web run test
      - run: pnpm --filter web run build

  e2e:
    name: Playwright
    runs-on: ubuntu-latest
    timeout-minutes: 25
    steps:
      - uses: actions/checkout@v6
      - uses: pnpm/action-setup@v6
      - uses: actions/setup-node@v6
        with: { node-version: lts/*, check-latest: true, cache: pnpm, cache-dependency-path: pnpm-lock.yaml }
      - run: pnpm install --frozen-lockfile
      - run: pnpm --filter web exec playwright install --with-deps chromium
      - run: pnpm --filter web run test:e2e
      - if: ${{ !cancelled() }}
        uses: actions/upload-artifact@v7
        with: { name: playwright-report, path: apps/web/playwright-report/, retention-days: 30 }
```

**File 2 — `.github/workflows/ci.yml`** (slimmed to a caller):

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  gates:
    uses: ./.github/workflows/gates.yml
    secrets: inherit
```

D-13 discretion: `e2e.yml` either folds in (above) or stays separate as another `workflow_call`-callable. **Recommend folding in** — the e2e job is functionally identical to what's already in `e2e.yml:16-50` (verified), and folding it eliminates the duplication risk.

### 3.2 `release.yml` — tag trigger + golden-path + load test

```yaml
name: Release Gates

on:
  push:
    tags: ['v*.*.*-rc*']   # matches v1.0.0-rc1, v1.0.0-rc2, v2.3.0-rc12, ...

permissions:
  contents: read

concurrency:
  group: release-${{ github.ref }}   # one run per tag; no cancel-in-progress for tagged runs
  cancel-in-progress: false

jobs:
  gates:
    uses: ./.github/workflows/gates.yml
    secrets: inherit

  golden-path:
    name: Playwright Golden Path (e2e-stub)
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: '25', check-latest: true }
      - uses: gradle/actions/setup-gradle@v6
      - uses: pnpm/action-setup@v6
      - uses: actions/setup-node@v6
        with: { node-version: lts/*, check-latest: true, cache: pnpm, cache-dependency-path: pnpm-lock.yaml }
      - run: pnpm install --frozen-lockfile
      - run: pnpm --filter web exec playwright install --with-deps chromium
      - name: Bring up dev compose (postgres + redis)
        run: docker compose up -d
      - run: pnpm --filter web exec playwright test launch-golden-path.spec.ts
        env:
          PLAYWRIGHT_BASE_URL: http://localhost:3000

  loadtest:
    name: 50-Tenant Load Test
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: '25', check-latest: true }
      - uses: gradle/actions/setup-gradle@v6
      - uses: grafana/setup-k6-action@v1
      - name: Build api + worker OCI images
        run: |
          ./gradlew --no-daemon :backend:api:bootBuildImage --imageName=zeromail-api:loadtest
          ./gradlew --no-daemon :backend:worker:bootBuildImage --imageName=zeromail-worker:loadtest
      - name: Generate ephemeral AES-GCM key
        run: echo "REFRESH_TOKEN_KEY_BASE64=$(openssl rand -base64 32)" >> $GITHUB_ENV
      - name: Bring up loadtest compose
        run: docker compose -f loadtest/compose.loadtest.yml up -d --wait
      - name: Run k6 load test
        run: k6 run loadtest/scripts/golden-path.js
        env:
          API_BASE_URL: http://localhost:8080
      - name: Capture container logs
        if: always()
        run: |
          mkdir -p loadtest/run
          docker compose -f loadtest/compose.loadtest.yml logs --no-color --no-log-prefix api worker > loadtest/run/run.log
      - name: Verify invariants
        run: ./gradlew --no-daemon :backend:api:loadtestVerify
      - name: Tear down
        if: always()
        run: docker compose -f loadtest/compose.loadtest.yml down -v
      - if: ${{ !cancelled() }}
        uses: actions/upload-artifact@v7
        with: { name: loadtest-run, path: loadtest/run/, retention-days: 30 }

  release-gates-summary:
    name: Release Gates Summary
    needs: [gates, golden-path, loadtest]
    if: always()
    runs-on: ubuntu-latest
    steps:
      - name: Assert all upstream jobs succeeded
        run: |
          if [[ "${{ needs.gates.result }}" != "success" ]] || \
             [[ "${{ needs.golden-path.result }}" != "success" ]] || \
             [[ "${{ needs.loadtest.result }}" != "success" ]]; then
            echo "::error::One or more release gates failed"
            exit 1
          fi
          echo "All release gates green for ${{ github.ref_name }} @ ${{ github.sha }}"
```

Tag filter `tags: ['v*.*.*-rc*']` is verified syntax. Source: [GitHub docs — events that trigger workflows](https://docs.github.com/en/actions/writing-workflows/choosing-when-your-workflow-runs/events-that-trigger-workflows) (verified via Context7).

### 3.3 `release-gates-summary` aggregation

`needs: [...] + if: always()` is the canonical aggregator pattern. The job runs regardless of upstream success/failure (so it always reports), and uses `needs.<job>.result` to check each upstream status. Source: [GitHub docs — using jobs in a workflow](https://docs.github.com/en/actions/using-jobs/using-jobs-in-a-workflow#using-needs-to-control-the-execution-order-of-jobs).

In the GitHub UI on the commit / tag, this surfaces as a single check named "Release Gates / Release Gates Summary." That's the URL `LAUNCH-GO-NOGO.md` item (b) references.

### 3.4 Concurrency

`ci.yml` uses `cancel-in-progress: true` (matches existing pattern at `ci.yml:11-13`). `release.yml` uses `cancel-in-progress: false` — tagged runs are explicit launch checkpoints; cancelling one because a newer tag was pushed would corrupt the launch record.

### 3.5 Branch / tag protection (D-11)

GitHub introduced **repository rulesets** in 2023, which support tag patterns (the older "tag protection rules" UI is being deprecated). The ruleset to configure:

- Target: Tags matching `refs/tags/v*.*.*-rc*` (and `refs/tags/v*.*.*` if going to GA).
- Required status checks: `Release Gates / Release Gates Summary`.
- The check name comes from `release.yml`'s `name: Release Gates` + job name `release-gates-summary`.

**Important limitation noted in D-11:** GitHub rulesets CANNOT block a tag push itself — they only block downstream actions (merges, environment deploys). Tag-protection-by-status-check requires a separate mechanism (e.g., a GitHub App that watches tag pushes and removes the tag if checks fail). For Phase 6's purposes, D-11 accepts this gap: the tag exists, but `LAUNCH-GO-NOGO.md` item (b) is the human gate — until summary is green, item (b) stays unchecked. No ruleset / no GitHub App is strictly required to ship Phase 6.

Source: [GitHub Rulesets — about rules](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets). [CITED]

### 3.6 Annotated tag conventions (D-12)

```bash
# Cut the RC tag locally:
git fetch origin main
git tag -a v1.0.0-rc1 origin/main -m "Zero Mail v1.0.0-rc1

SHA: $(git rev-parse origin/main)
Date: $(date -u +%Y-%m-%dT%H:%M:%SZ)
Four-suite gate: <fill in from gh run view after release.yml completes>
"
git push origin v1.0.0-rc1

# After release.yml completes, copy its run URL:
gh run view <run-id> --json url -q .url
# Then amend the tag message with `git tag -af v1.0.0-rc1 -m "..."` and re-push if desired,
# OR leave the original and link the run URL from LAUNCH-GO-NOGO.md (preferred; tag history stays clean).
```

D-12's "zero workflow edits for rc2" property holds because the trigger pattern is `v*.*.*-rc*` — any rc number matches.

### 3.7 Tag-push vs `ci.yml` ordering (planner's pitfall question)

When a tag is pushed to `main` simultaneously with a commit, GitHub fires both `push: branches: [main]` (ci.yml) and `push: tags: [...]` (release.yml). They run in parallel, NOT sequentially. The `gates` job in `release.yml` is a separate run from `gates` in `ci.yml`. There's no aggregation issue — both runs are independent. `LAUNCH-GO-NOGO.md` references the **release.yml** run URL, not ci.yml's.

If the user is worried about cost: yes, gates runs twice (once for the commit push, once for the tag push). Acceptable for RC tags (rare). Alternative: add `if: github.event_name == 'push' && !startsWith(github.ref, 'refs/tags/')` to ci.yml's gates job — but that creates branching complexity for marginal benefit.

---

## 4. `LAUNCH-GO-NOGO.md` + SEED-012 (D-09, D-14)

### 4.1 SEED-012 structural template (mirror existing seeds)

Verified by reading `SEED-011-admin-support-and-compliance-console.md`. All 11 existing seeds use this exact structure:

```markdown
---
id: SEED-012
status: dormant
planted: 2026-05-14
planted_during: Phase 6 launch (CASA deferral)
trigger_when: "when Vietnam beta exceeds 100 users OR when the CASA submission is funded/scheduled"
scope: large
---

# SEED-012: CASA Restricted-Scope Verification

## Why This Matters

Zero Mail uses `https://www.googleapis.com/auth/gmail.modify` (a Google restricted scope).
Phase 6 launches into OAuth "Testing" mode (100-user cap) — sufficient for the Vietnam beta.
Moving the consent screen to "Production" requires CASA Tier 2 verification.

(See `.planning/research/PITFALLS.md` §"Pitfall 1: Restricted-scope OAuth verification".)

## When to Surface

**Trigger:** when the Vietnam beta exceeds the 100-user OAuth Testing cap OR when CASA budget + lab engagement are committed.

## Scope Estimate

**Large.** 4–12 weeks end-to-end; a few hundred to several thousand USD in lab fees; annual recertification required.

## Required Evidence Package

- Privacy policy URL (already at `apps/web` privacy page)
- In-product "why we need this scope" screen
- Demo video showing every restricted scope (`gmail.modify`, `gmail.settings.basic`) in use
- Data-flow diagram (extend `.planning/research/ARCHITECTURE.md` diagram)
- MFA evidence on all prod consoles (Google Cloud, GitHub, Sepay, OpenRouter, Resend)
- Key-rotation evidence: AES-GCM refresh-token key rotation procedure (currently single-key per VPS env file)
- Employee-access policy (single-owner project today)

## Candidate CASA Labs

- CREST-accredited labs (e.g., Bishop Fox, NCC Group, Cure53) — exact pick TBD when budget + timeline are committed.

## Closure Trigger

The CASA Letter of Assessment is filed with Google → consent screen Production move is allowed → `FND-07` status flips from `Pending` to `Done`.

## Safety Rules

- No CASA "shortcuts" — restricted-scope verification is non-negotiable for any user count >100.
- Do not move the OAuth consent screen to Production until CASA LoA is filed.

## Breadcrumbs

- `.planning/REQUIREMENTS.md` `FND-07` stays Pending until this seed closes.
- `.planning/research/PITFALLS.md` §"Pitfall 1: Restricted-scope OAuth verification" — full pitfall analysis.
- `.planning/LAUNCH-GO-NOGO.md` item (h) — records launch mode = OAuth Testing.
- `docs/casa/` — existing CASA evidence artifacts.

## Notes

This is the highest-priority post-launch track. Without CASA, Zero Mail cannot publicly onboard users beyond the 100-cap.
```

### 4.2 `LAUNCH-GO-NOGO.md` trust-story phrases (item (g))

D-14 + `<specifics>` in CONTEXT.md require verbatim phrases. Authoritative sources confirmed via Grep:

| Phrase | Authoritative source |
|---|---|
| `auto-send forbidden` | This exact phrase is the project promise — paraphrased in `CLAUDE.md` "Write actions allowed in v1: ... Auto-send is forbidden." (verbatim, line containing `Auto-send is forbidden.`); and reinforced in `REQUIREMENTS.md` `DRFT-04`: "Draft generation never auto-sends and always requires user review in Gmail" |
| `no stored bodies / prompts / completions` | `CLAUDE.md` "Privacy: No long-term storage of raw email bodies, LLM prompts/completions, or embeddings." (verbatim); `REQUIREMENTS.md` `LLM-09`: "No raw email body, LLM prompt, or LLM completion is persisted beyond a short-lived in-memory cache" |
| `every triage action undoable` | `.planning/PROJECT.md:102`: "every triage action must be reversible (labels, archive, draft) and every autonomous action must leave an auditable trail" (paraphrased — D-14 mandates the shorter form for item (g)) |

Recommended `LAUNCH-GO-NOGO.md` skeleton:

```markdown
# Zero Mail — Launch Go/No-Go

**RC tag:** `v1.0.0-rc1`
**SHA:** `<filled in at sign-off>`
**Decision date:** `<filled in at sign-off>`

- [ ] (a) Playwright golden-path spec green on RC tag — [CI run](<url>)
- [ ] (b) 50-tenant load test invariants all PASS — [06-LOAD-TEST-RESULT.md](phases/06-polish-casa-verified-launch/06-LOAD-TEST-RESULT.md)
- [ ] (c) Prompt-injection regression suite green on RC tag
- [ ] (d) ArchUnit suite green on RC tag
- [ ] (e) Spring Modulith `ApplicationModulesTest` green on RC tag
- [ ] (f) LLM golden-set drift check green on RC tag
- [ ] (g) Trust story re-affirmed:
  - auto-send forbidden (CLAUDE.md constraint; REQUIREMENTS.md DRFT-04)
  - no stored bodies / prompts / completions (CLAUDE.md Privacy; REQUIREMENTS.md LLM-09)
  - every triage action undoable (PROJECT.md safety posture)
- [ ] (h) Launch mode = OAuth Testing (100-user cap) — Production move deferred to [SEED-012](seeds/SEED-012-casa-restricted-scope-verification.md)

---

✓ signed-off by @<user> on <ISO date>
```

### 4.3 Sign-off line precedent

No prior signed launch artifact exists in this repo (verified by absence of `signed-off by` in `.planning/`). The format in D-14 is original to Phase 6.

---

## 5. Spring Boot 4 / Java 25 / Modulith Specifics

### 5.1 Jackson 3 trap

Boot 4 ships Jackson 3, where core/databind moved to `tools.jackson.*` but `jackson-annotations` stayed at `com.fasterxml.jackson.annotation.*`. **Verified in this repo:** `GmailPubSubController.java:13` already imports `tools.jackson.databind.ObjectMapper`. So the e2e-stub Java fakes use the same import; copy from the controller.

This trap is documented in `CLAUDE.md` "do not use" list and project memory `feedback_spring_boot_4_breaking_changes.md`.

### 5.2 `@Profile` + `@Primary` + `@ConditionalOnProperty` ordering

All three annotations work together cleanly in Boot 4 (no migration). Resolution order at context startup:
1. `@Profile` filters: if the active profile set doesn't include `e2e-stub`, the bean class is not even loaded.
2. `@ConditionalOnProperty` filters: if `zeromail.e2e-stub.enabled=true` is absent, the bean is not registered.
3. `@Primary` resolves: if both the prod bean AND the stub end up registered (e.g. someone forgot to disable the prod bean), `@Primary` makes the stub win autowiring.

This is exactly the **belt-and-suspenders** D-03 + D-07 demand.

Verified via Context7 `/spring-projects/spring-boot/v4.0.3` — conditional annotation behavior is unchanged from Boot 3.

### 5.3 ArchUnit on JDK 25

ArchUnit 1.4.x supports JDK 21+ class file format. `DraftPathArchUnitTest.java` (the existing test in this repo) runs cleanly on JDK 25 already — no Phase 6 work needed beyond writing the rules.

### 5.4 `@SpringBootTest` + `e2e-stub` profile

The `e2e-stub` profile activates the same beans whether the JVM is launched via `bootRun` or `@SpringBootTest`. If the planner wants a backend integration test that exercises the e2e-stub bean wiring (recommended, cheap), use:

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "zeromail.e2e-stub.enabled=true")
@ActiveProfiles("e2e-stub")
class E2eStubWiringTest extends ApiPostgresTestBase {
    @Autowired private GmailClient gmailClient;
    @Test void primary_bean_is_the_stub() {
        assertThat(gmailClient).isInstanceOf(E2eStubGmailClient.class);
    }
}
```

`ApiPostgresTestBase` is already in `backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java` and provides Testcontainers Postgres via `@ServiceConnection` (per TESTING.md §3).

### 5.5 `RestClient + LocalServerPort` under `e2e-stub`

The project memory says ScopedValue + TenantContext does NOT work with MockMvc — use `RestClient + @LocalServerPort` instead. The `e2e-stub` profile doesn't change this. The Playwright spec drives via real HTTP anyway, so this only matters for backend-only integration tests written alongside.

### 5.6 Logback scrub filter under prod config

Verified at `backend/core/src/main/resources/logback-spring.xml` — this is the production logback. The compose stack loads the prod JAR which loads this file. The scrub filter at `backend/core/src/main/java/com/zeromail/core/shared/privacy/SensitiveMarkerScrubFilter.java:31` runs as appender-level filter on every event. **This is what invariant (c) exercises** — if the filter ever fails to redact a sensitive token, the regex scan catches it.

### 5.7 Spring Modulith `ApplicationModulesTest` (SPEC item 3 + 4)

Verified at `backend/api/src/test/java/com/zeromail/api/ZeroMailApiApplicationModulesTest.java`. Trivial 12-line test:

```java
ApplicationModules.of(ZeroMailApiApplication.class).verify();
```

Picked up by `./gradlew :backend:api:check` automatically (the test class is in the standard test source set). The `gates.yml` `backend` job (`./gradlew --no-daemon check`) already runs it — no extra wiring needed.

---

## 6. Validation Architecture

> Phase 6 itself IS validation infrastructure. Validation dimensions the phase's artifacts must measurably prove:

| Dimension | What proves it on the RC tag | Where measured |
|---|---|---|
| **Privacy (LLM-09, FND-03, FND-04)** | Load-test invariant (c) finds 0 lines matching `email_body\|prompt\|completion\|raw_html` in prod-config logs over 10-min sustained traffic | `loadtest/run/run.log` scanned by `:backend:api:loadtestVerify` |
| **Multi-tenant isolation (FND-01, FND-05)** | Load-test invariant (a) finds 0 audit rows whose `tenant_id` is not `loadtest-tenant-%` | `triage_audit` table query in `:backend:api:loadtestVerify` |
| **Ledger consistency (BILL-02..BILL-04)** | Load-test invariant (b) finds net=0 across all 50 loadtest tenants | `credit_ledger_entry` table query in `:backend:api:loadtestVerify` |
| **Regression-suite green-status on RC commit** | All 4 gate jobs in `gates.yml` pass on the SHA tagged `v1.0.0-rc1` | `release-gates-summary` job aggregation |
| **Trust-story restatement (D-14 item g)** | Three verbatim phrases appear in `LAUNCH-GO-NOGO.md` | Manual verification at sign-off |
| **OAuth Testing-mode launch (D-14 item h)** | Item (h) checkbox + SEED-012 file exists | Manual verification at sign-off |
| **Tag is annotated + on main** | `git cat-file -t v1.0.0-rc1` returns `tag` (not `commit`); `git merge-base --is-ancestor v1.0.0-rc1 main` succeeds | `release.yml` golden-path job can add an early step assertion |
| **No auto-send code-path added** | `DraftPathArchUnitTest.java::draft_and_triage_paths_never_send_or_update_gmail_drafts` passes in `gates.yml` | Acceptance criterion #8 |
| **No new long-term storage of email bodies / prompts / completions** | Schema review at plan-checker level (no new Liquibase changelog touches body/prompt columns); FND-04 ArchUnit rule still passes | Acceptance criterion #9 |

These map 1:1 to the 9 acceptance criteria in `06-SPEC.md`. The planner should design tasks so each acceptance criterion has a single, measurable verification step on the RC tag.

### Test Framework Inventory (per project conventions)

| Property | Value |
|----------|-------|
| Framework (backend) | JUnit 5 + AssertJ + Mockito; ArchUnit 1.x; Spring Modulith |
| Framework (frontend) | Vitest (unit), Playwright (e2e) |
| Framework (load) | k6 v1.7.x |
| Quick run (backend) | `./gradlew :backend:api:check` |
| Full suite | `./gradlew check && pnpm -r test && pnpm -r test:e2e` |

### Sampling Rate (this phase only)

- **Per task commit:** the appropriate slice test (e.g., a task touching the e2e-stub stubs runs `./gradlew :backend:api:test --tests '*E2eStub*'`).
- **Per wave merge:** `./gradlew check && pnpm -r test`.
- **Phase gate:** `release.yml` on the RC tag.

### Wave 0 Gaps

None — Phase 6 adds infrastructure, not new test files for existing requirements.

---

## 7. Pitfalls + Open Questions (RESOLVED)

### 7.1 Pitfall — Liquibase double-run in compose

Both `api` and `worker` images include `spring-boot-starter-liquibase` and run the same changelog. If both containers start simultaneously they race on the `databasechangeloglock` table. **Mitigation:** `worker` `depends_on: api: condition: service_healthy` (§1.3) — api wins, worker finds locked + done.

Alternative if this becomes brittle: set `spring.liquibase.enabled=false` in the worker's loadtest env override. This is invasive (worker's prod application.yml has `enabled: true`); recommend the depends_on approach.

### 7.2 Pitfall — k6 driving 50 VUs into a stub Pub/Sub controller

At 8.33 req/sec for 10 minutes (~5000 requests total), the api container processes 5k Pub/Sub envelopes. Each spawns a real triage decision (which in `loadtest` profile must still NOT call the LLM — the `loadtest` profile inherits the existing `chat: none` config at `application.yml:62`, and BYOK / platform `api-key` are dummies that fail before any HTTP egress).

**Real risk:** if a real LLM call slips through (e.g. someone forgot to disable a model bean), the load test will try 5000 OpenRouter calls and burn the platform credit. Mitigation: pre-loadtest assertion in the Gradle task that confirms `spring.ai.*.api-key=loadtest` is set on both containers. Or: a firewall rule on the compose network blocking outbound 443 (overkill).

### 7.3 Pitfall — ArchUnit false-positives on test classes

`ImportOption.DoNotIncludeTests.class` MUST be on the `@AnalyzeClasses` annotation, otherwise Rule A in §2.4 fires false-positively on the test that DOES legitimately reference `..api.e2estub..` to assert the bean is the stub. Verified at `I18nArchUnitTest.java:47` — the project pattern is correct.

### 7.4 Pitfall — Playwright `webServer` race conditions

If `pnpm dev` (Next.js) and `bootRun` (Spring Boot) both bind ports during startup, and one fails (say port collision because someone has IntelliJ running locally), Playwright's lifecycle kills the surviving one — but if you have `reuseExistingServer: true` set locally and a stale Spring Boot is still on 8080 from a previous run, the spec will hit the stale backend. Mitigation: explicit health-check URL (not port-check) — already in the snippet above.

### 7.5 Pitfall — Tag-push fires `release.yml` BEFORE `ci.yml`?

They fire **independently** (different event types). No ordering dependency. The aggregation in `release-gates-summary` only references its own `release.yml` upstream jobs, not anything from ci.yml. No issue.

### 7.6 Pitfall — Disk space / log volume on a GHA runner

`ubuntu-latest` runners have 14 GB free disk. 10-minute load test producing 5000 log lines × ~500 bytes/line = ~2.5 MB; even with verbose Spring Boot startup logs the run.log stays under 50 MB. No disk pressure.

### 7.7 Pitfall — k6 unauthenticated load triggering Spring Security on `/internal/pubsub/`

`PubSubOidcAuthFilter.java:46` rejects requests without `Authorization: Bearer ...`. The loadtest stub `LoadtestPubsubVerifier` must replace the **filter's underlying `TokenVerifier`** OR the filter itself. Confirm filter swap design at plan time (see Open Question 2). The k6 script sends `Authorization: Bearer loadtest-stub-token` — the stub accepts anything non-empty.

### 7.8 Open Question 1 — Image source for compose stack

CLAUDE.md prescribes `eclipse-temurin:25-jre-noble` + CDS/AOT layered images, but no `Dockerfile` exists. Options:
1. **Use `./gradlew :backend:api:bootBuildImage`** (paketobuildpacks, no Dockerfile, ~60s on a cold GHA runner). Recommended.
2. **Write `backend/api/Dockerfile` + `backend/worker/Dockerfile`** with the prescribed base image. ~2 small files, but adds "new code."
3. **Hybrid: pre-built image in ghcr.io** (requires release.yml to also push to ghcr first). Slower iteration.

Recommend Option 1 — SPEC out-of-scope says "new application code or product features," buildpack image is neither. Planner should confirm.

**RESOLVED:** Plan 06-02 Task 3 — `./gradlew :backend:api:bootBuildImage` (paketobuildpacks, no Dockerfile committed).

### 7.9 Open Question 2 — Filter vs Verifier swap for Pub/Sub OIDC

`PubSubOidcAuthFilter` directly constructs a `TokenVerifier` in its constructor (`api/security/PubSubOidcAuthFilter.java:23-32`). To swap behavior under `loadtest` and `e2e-stub` profiles, the cleanest design is:
- Extract `TokenVerifier` into a `@Bean` in `PubSubSecurityConfig`.
- Filter constructor takes `TokenVerifier` instead of constructing it.
- Profile beans override the `@Bean` definition.

This is a small refactor (≤ 30 lines). SPEC says "no new application code." Planner must decide:
- Refactor (strict reading violates SPEC, pragmatic reading is fine — it's test infrastructure).
- Add a separate `@Profile("loadtest|e2e-stub") @Bean` `OncePerRequestFilter` that registers ahead of `PubSubOidcAuthFilter` and short-circuits — no refactor, but two filter chains.

Recommend planner raise this at plan-checker time.

**RESOLVED:** Plan 06-01 Task 2 — extract `TokenVerifier` as a `@Bean` in `PubSubSecurityConfig`; `PubSubOidcAuthFilter` receives it via constructor injection.

### 7.10 Open Question 3 — Drift check + prompt-injection regression in `gates.yml`?

`gates.yml`'s `backend` job runs `./gradlew check`, which includes ALL tests including `PromptInjectionCorpusTest.java` (verified at `backend/core/src/test/java/com/zeromail/core/llm/gateway/sanitization/PromptInjectionCorpusTest.java`) and `PromptInjectionSentinelTest.java`. The drift check (`DriftDetectionJobDriftDetectedTest`, `DriftDetectionJobNoDriftTest` at `backend/worker/`) also runs under `./gradlew check`. The `ai-eval` job runs the deterministic golden-set drift via `:backend:core:aiEval -PdeterministicOnly`. So **all four regression suites** (item 3 in SPEC) are covered by `gates.yml` jobs `backend` + `ai-eval`. Good — no extra wiring needed for SPEC item 3.

**RESOLVED:** Plan 06-04 Task 1 — all four regression suites already run inside `gates.yml`'s `backend` + `ai-eval` jobs; no extra wiring required.

### 7.11 Open Question 4 — Where do the existing Playwright specs run in the new world?

Currently `e2e.yml` runs ALL specs in `apps/web/e2e/`. After Phase 6, `launch-golden-path.spec.ts` is added but it requires the `e2e-stub` Spring Boot — the other 22 specs do NOT. Options:
- **Single test:e2e command, single webServer config** — slow boot for every spec run, but only one config.
- **Test tagging** — `@e2e-stub` annotation on golden-path; default `pnpm test:e2e` excludes it; `pnpm test:e2e:golden` includes it. Cleaner separation, more pnpm scripts.

Recommend tagging (Option 2). Planner decides exact tag mechanism (Playwright projects with `grep` / `grepInvert`, or `--project=golden-path`).

**RESOLVED:** Plan 06-04 Task 1 — `e2e.yml` folds into `gates.yml` as a single Playwright job covering all 22 existing specs plus `launch-golden-path.spec.ts` (Chromium only, single project to keep gate time bounded).

---

## 8. Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `bootBuildImage` is the right image source (Open Q1) | §1.4 | If planner picks a Dockerfile instead, image step changes — invariants unchanged |
| A2 | TokenVerifier should be extracted to a @Bean (Open Q2) | §7.9 | If planner picks the parallel-filter approach, e2e-stub package contents change |
| A3 | k6 v1.7.x will be the installed version when `setup-k6-action@v1` resolves "latest" in CI | §1.2 | k6 v1.x is stable; constant-arrival-rate has not changed shape in years |
| A4 | The drift check's `@Tag("llm-eval")` mode is NOT what `ci.yml` runs (it runs `-PdeterministicOnly`) | §7.10 | If `-PdeterministicOnly` actually triggers real-LLM calls, the gate is non-hermetic; reading `ci.yml:58` shows this is the established pattern |
| A5 | `GmailClient` adapter interface exists at `core.gmail.gateway.*` | §2.1 | Planner needs to confirm exact interface seam — `find` showed concrete classes, not an interface |
| A6 | `pnpm test:e2e` exists as a script in `apps/web/package.json` | §3.1 + §3.2 | Verified via `e2e.yml:43` calling `pnpm --filter web run test:e2e`; exists |

---

## 9. Project Constraints (from CLAUDE.md + CONVENTIONS.md + TESTING.md)

These were checked during research; the planner must honor each:

- **No `spring-cloud-gcp`** — neither `loadtest` nor `e2e-stub` may pull GCP starters for OIDC verification. Use a Java fake.
- **No Lombok** — stubs use Java 25 records / explicit constructors.
- **No real LLM calls in `./gradlew test`** — load test runs OUTSIDE `gradlew test`; the gradle task `:backend:api:loadtestVerify` is JDBC-only.
- **No new long-term storage** of bodies / prompts / completions — load-test fixtures must use synthetic content only; the regex scan validates this in observed log output.
- **Enterprise readability** — variable names in the Gradle task and any stub Java code use explicit names (`connection` not `conn`, `request` not `req`, `tenantId` not `tid`).
- **Liquibase YAML only** — no schema changes in Phase 6.
- **Vietnamese-first i18n** — Phase 6 is unlikely to add UI; `LAUNCH-GO-NOGO.md` is internal planning English.
- **`tools.jackson.databind`** for ObjectMapper imports (not `com.fasterxml.jackson.databind`) — see §5.1.
- **JetBrains MCP first** for symbol-aware reads — planner / executor should prefer `mcp__jetbrains__*` over generic `Read` / `Grep` when touching Java.
- **Postgres MCP for DB introspection** — planner / executor should use `mcp__postgres__*` for invariant query design + EXPLAIN.
- **Playwright MCP for visual verification** — if the golden-path spec ever needs visual diff at plan time.

---

## Sources

### Primary (HIGH confidence)

- Context7 `/grafana/k6-docs` — k6 v1.7.x scenarios, executors, thresholds, handleSummary
- Context7 `/microsoft/playwright.dev` — webServer arrays, APIRequestContext, request fixture
- Context7 `/websites/github_en_actions` — workflow_call, tag triggers, concurrency
- Context7 `/spring-projects/spring-boot/v4.0.3` — @Profile, @ConditionalOnProperty, condition annotations
- Context7 `/spring-projects/spring-modulith` — ApplicationModules.verify() shape
- Context7 `/tng/archunit` — noClasses, dependOnClassesThat, predicates
- Context7 `/docker/compose` — healthcheck, depends_on conditions
- WebFetch — `github.com/grafana/setup-k6-action` README (v1.2.1 confirmed)
- Project file `backend/api/src/test/java/com/zeromail/api/arch/I18nArchUnitTest.java:47` (ImportOption pattern)
- Project file `backend/core/src/test/java/com/zeromail/core/draft/DraftPathArchUnitTest.java` (ArchCondition pattern)
- Project file `backend/api/src/main/java/com/zeromail/api/controllers/gmail/GmailPubSubController.java` (Jackson 3 import, envelope shape)
- Project file `backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java` (filter behavior, audit row contents)
- Project file `backend/worker/src/main/resources/application.yml:63` (worker fail-fast)
- Project file `backend/api/src/main/resources/application.yml:102` (api fail-fast)
- Project file `backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java` (triage_audit table)
- Project file `backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryEntity.java` (credit_ledger_entry table + net-zero invariant)
- Project file `apps/web/playwright.config.ts` (current webServer config)
- Project file `.github/workflows/ci.yml`, `e2e.yml` (existing gate jobs to extract)
- Project file `.planning/seeds/SEED-011-admin-support-and-compliance-console.md` (seed format template)
- Project file `.planning/research/PITFALLS.md` §1 (CASA risk profile)

### Secondary (MEDIUM confidence)

- [GitHub Rulesets docs](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets) — tag rulesets cannot block tag pushes (D-11 limitation)
- [Spring Boot 4 bootBuildImage](https://docs.spring.io/spring-boot/gradle-plugin/packaging-oci-image.html) — image source recommendation (Open Q1)
- Project memory `feedback_spring_boot_4_breaking_changes.md` — Jackson 3 import trap

### Tertiary (training-data, flagged)

- None — every load-bearing claim above traces to Context7 or a project file.

---

## Metadata

**Confidence breakdown:**
- k6 scripting + GHA action: HIGH (Context7 + WebFetch confirmation 2026-04-29)
- Playwright webServer + APIRequestContext: HIGH (Context7 stable since v1.30)
- GHA reusable workflows + tag triggers: HIGH (Context7)
- Spring Boot 4 conditional beans: HIGH (Context7 v4.0.3)
- Docker Compose v2 healthchecks: HIGH (Context7)
- ArchUnit predicates: HIGH (Context7 + project precedent)
- Image source for compose stack: MEDIUM (no existing Dockerfile; recommendation is bootBuildImage but planner should confirm)
- PubsubVerifier swap mechanism: MEDIUM (existing filter constructs verifier inline; planner needs to choose refactor vs parallel-filter)

**Research date:** 2026-05-14
**Valid until:** ~2026-06-13 (30 days; k6 and Playwright are stable, but rulesets / setup-k6-action may evolve)

---

## RESEARCH COMPLETE
