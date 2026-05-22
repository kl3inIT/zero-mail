# Pitfalls Research — Zero Mail v1.2 (Admin Console Foundation + Settings UI on Curated Catalog)

**Domain:** Adding `/admin/*` console (`ROLE_ADMIN` RBAC + audit + tenant inspection + AES-GCM master-key management for OpenAI/Anthropic/Google/DeepSeek + provider catalog with Sync-from-`/models` + worker queue health + global spend dashboard) and a four-tab user Settings UI on the admin-curated catalog, on top of the v1.0 + v1.1 trust-first baseline (Gmail-only, auto-send architecturally blocked, no long-term storage of raw bodies/email-content LLM prompts/embeddings, tenant isolation via Scoped Values, single Gmail send call site enforced by ArchUnit + grep).
**Researched:** 2026-05-19
**Confidence:** HIGH on Zero Mail v1.0/v1.1 invariant landscape (sources read directly: `.planning/PROJECT.md`, `CLAUDE.md`, v1.1 PITFALLS.md, `NoGmailSendAllowedTest.java` referenced shape, `TenantAwareTaskScope` pattern). HIGH on the admin/key-management pitfall pattern (CWE-522, CWE-532, CWE-798, NIST SP 800-57 key rotation guidance, OWASP Top 10 A04:2021 Insecure Design, A09:2021 Logging Failures). HIGH on supply-chain risk from provider `/models` endpoints (OpenRouter / OpenAI `/v1/models` responses are vendor-controlled JSON, not pinned). MEDIUM-HIGH on Inbox Zero admin reference (read `apps/web/app/(app)/admin/AdminUserControls.tsx`, `AdminUpgradeUserForm.tsx`, `top-spenders/route.ts` shape — small surface, not a full curated-catalog reference). MEDIUM on Spring Security 7.0 `@PreAuthorize` + method-security composition with ScopedValue tenant context (verified via Spring Security 6.x/7.x docs; specific Scoped Values + `@PreAuthorize` ordering is product-specific risk).

> **Scope.** This document is the v1.2 delta only. It enumerates the pitfalls that appear when adding the admin console + Settings UI on the v1.0 + v1.1 baseline. v1.0 + v1.1 pitfalls (raw-body persistence, ThreadLocal tenant leaks, ordinal-based enum storage, send-call-site weakening, BYOK round-trip leaks, JSONB schema drift, etc.) are addressed in shipped phases or in the prior milestone delta; we surface only the new failure modes the admin surface and the curated-catalog Settings introduce, plus the **regression vectors** v1.2 features can use to silently undo v1.0/v1.1 trust invariants.

---

## Critical Pitfalls

### Pitfall 1: ROLE_ADMIN promoted via a misconfigured DB seed becomes a permanent backdoor

**What goes wrong:**
The naive way to ship admin in v1.2 is a Liquibase changeset that flips `user.role = 'ADMIN'` for the founder's email (`kythuatclaude@gmail.com` or a seeded admin account), plus a controller annotation `@PreAuthorize("hasRole('ADMIN')")`. Three failure modes appear immediately:

1. **Forever-admin via the seed.** The seed runs on every fresh environment (CI, staging, prod). Anyone who provisions a Zero Mail instance using the same seed inherits a hardcoded "founder admin." A staging DB snapshot restored into prod (a normal disaster-recovery drill) **silently re-grants the founder role** to whichever email was in the snapshot, even if it was rotated out in prod.
2. **No revocation path.** The admin role is a column on `user` with no audit trail of who granted it or when. If the founder account is compromised, there is no log of "admin granted at X, by Y, for reason Z" — the rotation playbook is unclear.
3. **Admin role bypasses tenant scoping by design.** v1.0 ships per-tenant `Scoped Values` and an ArchUnit rule against `ThreadLocal`, but admin endpoints **need** cross-tenant access (that's the point). The first admin controller writes `// @PreAuthorize("hasRole('ADMIN')") — bypassing TenantContext.currentOrThrow()` and now there are **two** code paths in the app: one that always asserts a tenant context, one that doesn't. A future refactor merges them. A bug in the merger drops the admin check but keeps the tenant bypass. Every tenant's data is now reachable from any authenticated user.

**Why it happens:**
- Path of least resistance: a single `role` column + `@PreAuthorize` is what every Spring Security tutorial demonstrates. Audit + grant provenance + revocation feel like "v1.3 problems."
- Admin endpoints conceptually live "outside" tenant isolation, so developers reflexively bypass `TenantContext.currentOrThrow()` instead of designing an explicit **admin-scoped** context that is loud about cross-tenant access.
- Liquibase seeds are "developer convenience" — nobody documents that they're security-critical.

**How to avoid:**

1. **Admin grant is a row in `admin_grant`, not a column on `user`.** Schema:
   ```yaml
   - createTable:
       tableName: admin_grant
       columns:
         - column: { name: id, type: UUID, constraints: { primaryKey: true } }
         - column: { name: user_id, type: UUID, constraints: { nullable: false, foreignKeyName: fk_admin_grant_user, references: app_user(id) } }
         - column: { name: role, type: VARCHAR(32), constraints: { nullable: false } }  # ADMIN, SUPPORT (future)
         - column: { name: granted_by_user_id, type: UUID, constraints: { nullable: true, foreignKeyName: fk_admin_grant_grantor, references: app_user(id) } }  # nullable for bootstrap
         - column: { name: granted_at, type: TIMESTAMPTZ, constraints: { nullable: false } }
         - column: { name: granted_reason, type: TEXT, constraints: { nullable: false } }
         - column: { name: revoked_at, type: TIMESTAMPTZ, constraints: { nullable: true } }
         - column: { name: revoked_by_user_id, type: UUID, constraints: { nullable: true, foreignKeyName: fk_admin_grant_revoker, references: app_user(id) } }
         - column: { name: revoked_reason, type: TEXT, constraints: { nullable: true } }
   - sql:
       sql: |
         CREATE UNIQUE INDEX uq_admin_grant_active
           ON admin_grant (user_id, role)
           WHERE revoked_at IS NULL;
   ```
   `user.role` does **not** exist. The application asks `adminGrantRepository.hasActiveGrant(userId, "ADMIN")` per request.

2. **Bootstrap admin via env var, not seed.** First-boot bootstrap reads `ZEROMAIL_BOOTSTRAP_ADMIN_EMAIL` from environment. If set and no active `admin_grant` row exists, the bootstrap inserts one with `granted_reason = "first-boot bootstrap from env"` and `granted_by_user_id = NULL`. **Bootstrap runs once and only once** — guarded by `SELECT EXISTS (SELECT 1 FROM admin_grant)` check. Subsequent boots with the same env var **log a WARN and do nothing**; they do not re-grant. CI / staging never sets this env var.

3. **Admin context is explicit, not "no tenant context."** Introduce a separate `AdminContext` (Scoped Value) populated only inside `@AdminEndpoint`-annotated controllers. The two contexts are mutually exclusive:
   - `TenantContext.currentOrThrow()` throws inside `AdminContext`.
   - `AdminContext.currentOrThrow()` throws inside `TenantContext`.
   - An ArchUnit rule forbids any service in `core.*.application.*` from reading both contexts in the same call chain.
   - Cross-tenant admin reads (e.g., "fetch tenant X's spend") go through `AdminTenantAccess.readOnly(tenantId, supplier)` which binds a **read-only** tenant context inside an admin context and records the access in `admin_audit_log` with the admin's user ID, the target tenant ID, the operation name, and a redacted parameter snapshot.

4. **Revocation works without DB surgery.** `POST /admin/grants/{id}/revoke` requires another admin (`ADMIN ≠ self`) and writes `revoked_at + revoked_by_user_id + revoked_reason`. The unique-partial-index ensures only one active grant per (user, role). Re-granting is an explicit new row, not an `UPDATE`.

5. **Two-admin rule for sensitive operations.** Operations that touch master keys or modify the catalog require an `AdminConfirmation` row co-signed by a second active admin (or, in single-admin bootstrap mode, an explicit `--allow-single-admin` server flag toggled per-operation via env). This is intentionally heavyweight — it's the difference between "I have admin" and "I can rotate the master key."

**Warning signs:**
- `user` table has a `role` column.
- A Liquibase changeset hardcodes an admin email.
- The bootstrap mechanism reads from a YAML/property file checked into the repo rather than a runtime env var.
- A controller mixes `@PreAuthorize("hasRole('ADMIN')")` with `TenantContext.currentOrThrow()` in the same handler.
- The codebase has no `AdminContext` Scoped Value separate from `TenantContext`.
- `admin_grant` has no `revoked_at` column.
- PR adds an admin endpoint without an `admin_audit_log` insert.

**Phase to address:** **Phase 8 / sub-phase 8A — Admin foundation (RBAC + audit + AdminContext)**, lands **BEFORE** any admin endpoint, before master-key management, before tenant-inspection views. The `admin_grant` schema + `AdminContext` Scoped Value + ArchUnit "mutually exclusive contexts" rule + bootstrap env-var contract must be in the same merge as the first admin controller.

---

### Pitfall 2: Master-key leak through the AES-GCM key-management UI (logs, response round-trip, test-connection probe, error message, ChatModel cache, Liquibase seed)

**What goes wrong:**
v1.2 introduces **master keys** for OpenAI/Anthropic/Google/DeepSeek — operator-supplied API keys the platform uses when a tenant has **no BYOK** for that provider. These are far higher-value than per-tenant BYOK: a single master key for OpenAI lets attackers spend the platform's pooled budget across all tenants, and rotation is operationally expensive. The same five regressions identified for BYOK in v1.1 Pitfall 8 re-surface — **plus** five admin-specific extensions:

1. **Admin "Test connection" logs the master key.** Mirror of BYOK Pitfall 8.1, but the blast radius is the entire platform's spend.
2. **Admin save endpoint returns the new master key.** Frontend caches in TanStack Query.
3. **Admin read endpoint returns full key** to render in the form for editing convenience.
4. **No per-provider ChatModel cache eviction on master-key rotation** — rotated master key takes effect for new tenants but in-flight cached `ChatModel` instances keep the old key until cache TTL. If the old key was rotated **because it was compromised**, the platform keeps using it.
5. **Master-key probe abused as a key oracle.** Admin "Test connection" calls the provider's `/models` endpoint with the key and returns success/failure to the UI. An attacker who reaches `/admin/master-keys/{provider}/test` (e.g., via a session-hijacked admin) can submit a candidate key and learn whether it's valid — i.e., turn the test endpoint into a key-validation oracle for keys harvested elsewhere.
6. **Master-key stored in `application.yml` "for development convenience"** — the dev profile checks a key into the repo for "easy testing." The same file gets pushed to GitHub via a bad `.gitignore` edit. Vendor revokes the leaked key; rotation cost = real.
7. **Master-key inserted via Liquibase seed for dev.** The seed runs in CI with a placeholder; nobody notices when an engineer "temporarily" replaces the placeholder with a real key locally and accidentally commits.
8. **Error responses echo the key.** A 401 from the provider returns a JSON body like `{"error":"invalid api key sk-proj-abc..."}` which the admin proxies into a Vercel-style error envelope. Now the key is in the admin's browser console / error reporter, in nginx access logs (if the response was logged), and possibly in error-reporting tools like Sentry.
9. **Master-key value compared via `equals()` in not-constant-time** — leaks the prefix via timing if exposed in a high-RPS code path. Lower priority but a real concern in the test-connection endpoint if the key check is naive.
10. **Master-key shown on an admin page that doesn't have CSP frame-ancestors / clickjacking protection** — admin page rendered inside an attacker's iframe via UI redress.

**Why it happens:**
"Encryption at rest" is the part developers remember. The admin UI is where the key crosses **every** other boundary: HTTP request, response, browser memory, error reporter, log proxy, retry queue, dev seed, prod config, dev/prod parity drift.

**How to avoid:**

1. **Save endpoint never returns the saved key.** Contract: `PUT /admin/master-keys/{provider}` accepts `{ apiKey, reason }`, returns `{ provider, apiKeyMasked: "sk-...XYZ4", rotatedAt, rotatedByUserId, validatedAt }`. No plaintext in response, no plaintext in TanStack Query cache. Same as BYOK Pitfall 8.1.
2. **Read endpoint returns mask only + metadata.** `GET /admin/master-keys` returns `[{ provider, apiKeyMasked, rotatedAt, rotatedByUserId, lastValidatedAt, currentlyEncrypted: true }]`. No "show me the key" flow exists. Replacement = full re-enter.
3. **Test-connection endpoint:**
   - Runs server-side only; never echoes the key back.
   - Uses a dedicated HTTP client configured with `.proxy(NO_PROXY)` and explicit `.userAgent("zero-mail-admin-probe")` — never the shared logging-proxy client used for tenant traffic.
   - **Rate-limited per admin user** (max 10 test-connection requests / hour / admin) to neutralize the validation-oracle attack.
   - **Test-connection requires an "active edit session" token** — admin must first click "Edit master key" which mints a short-lived (5 min) edit session, and test-connection is bound to that session. Random "POST /admin/master-keys/openai/test" with arbitrary keys outside an edit session is rejected.
   - Returns only `{ ok: boolean, latencyMs: int, providerErrorCode: "INVALID_KEY" | "RATE_LIMITED" | "NETWORK_ERROR" }` — never the provider's raw error message.
4. **`@Sensitive` propagation.** Master keys typed as `Sensitive<String>` everywhere off the storage path. ArchUnit:
   ```java
   noClasses().that().resideInAPackage("..admin.masterkey..")
     .should().callMethodWhere(target ->
       "format".equals(target.getName())
       || "toString".equals(target.getName())
       || "info".equals(target.getName()) || "debug".equals(target.getName()) || "warn".equals(target.getName()) || "error".equals(target.getName()))
     .andShould().haveRawParameterTypes(thatIncludeASensitiveField())
     .because("Master keys must not be formatted into strings or log messages.");
   ```
5. **Per-provider ChatModel cache eviction on master-key rotation.** A `MASTER_KEY_ROTATED` Spring Modulith event fires from `MasterKeyService.rotate(...)` after commit. The LLM gateway adapter handles the event by evicting **every** cached ChatModel for that provider across **every** tenant (not just one) — because the cached client may have been instantiated with the old master key for any tenant currently on the platform default. Pseudocode:
   ```java
   @ApplicationModuleListener
   void onMasterKeyRotated(MasterKeyRotatedEvent event) {
       chatModelCache.evictAllForProvider(event.provider());
       meterRegistry.counter("master_key.cache_eviction", "provider", event.provider().name()).increment();
   }
   ```
6. **Master-key never lives in `application.yml`.** Reads only from an opaque KMS / Vault / env-var-backed `MasterKeyVault` SPI. Dev profile uses a `StubMasterKeyVault` that throws `IllegalStateException("dev profile has no master key for provider X; use BYOK or fail loudly")` unless a developer explicitly sets `ZEROMAIL_DEV_MASTER_KEY_OPENAI=...` in their **local shell** — never in any checked-in file. An ArchUnit + grep test fails the build if `application*.yml` contains any string matching `sk-[a-zA-Z0-9_-]{20,}` or `AIza[a-zA-Z0-9_-]{35}`.
7. **Liquibase: no master-key seed exists.** A test asserts no Liquibase changeset INSERTs into `master_key_credential`. The only way a row appears is via `PUT /admin/master-keys/{provider}` through the live admin API.
8. **Error-response sanitization.** A `ProviderErrorTranslator` strips the provider's raw error body and maps to enum codes. Test: a forced provider 401 response with body `{"error":"invalid api key sk-real-key-12345"}` results in an admin-API response containing **none** of the original error body — only the enum code.
9. **Constant-time comparison only where the key is used to authenticate** (currently nowhere in master-key flow because keys are sent outbound to the provider, not used to authenticate inbound; defer until use-case appears, document the rule).
10. **CSP + frame-ancestors enforced on `/admin/*`.** `Content-Security-Policy: frame-ancestors 'none'; default-src 'self'; ...`. Verify via Playwright test.
11. **Sentinel-leak test extended for master keys.** Set master key for OpenAI = `sk-MASTER-SENTINEL-NEVER-LOG-99999`. Run save → test-connection → tenant triage (which routes through the master key by default) → admin list → rotation → logout. The sentinel must appear **only** in `master_key_credential.api_key_cipher` (encrypted) — not in app logs, access logs, HTTP responses, Redis dumps, JFR recordings, Playwright HAR captures, error-reporter snapshots, or `pg_dump | grep`.

**Warning signs:**
- `PUT /admin/master-keys` response includes `apiKey`, even briefly.
- A controller / service does `String.format(... key ...)` anywhere in `admin.masterkey.*`.
- Master-key rotation does not emit a `MASTER_KEY_ROTATED` event.
- Test suite has no master-key sentinel test.
- Frontend `admin-master-key-api.ts` returns a typed `apiKey: string` field.
- Test-connection endpoint is reachable without an edit-session token, with no rate limit, and proxies provider error bodies verbatim.
- `application*.yml` grep for `sk-` / `AIza` matches anywhere.
- Liquibase changesets INSERT into `master_key_credential`.

**Phase to address:** **Phase 8 / sub-phase 8B — Master-key management**, gated behind 8A admin foundation. The sentinel-leak test runs in CI before any master-key endpoint is wired into the frontend.

---

### Pitfall 3: ROLE_ADMIN session reused for self-service user actions (no role pivot)

**What goes wrong:**
The founder admin opens `/admin` to inspect a problem tenant. The admin session cookie is the same cookie used for the **user-facing app**. The admin browses to `/rules` mid-session and creates a rule on their own tenant. Without explicit role pivoting:

1. **Admin permissions silently apply to user-facing endpoints.** A bug in `/rules` route handler that filters by `TenantContext.currentOrThrow()` is fine for users, but if a developer "helpfully" adds a fallback `if admin: skip tenant filter` to handle the admin's own inspection, the route now reads any tenant's rules when accessed by an admin. The admin clicks "Save Rule" while looking at tenant X's rule — the rule is saved against the admin's own account, or worse, against tenant X (depending on the bug shape).
2. **Audit log conflates admin and user actions.** The same user (`admin@zeromail.local`) creates a rule. Did the admin create it as a user, or did the admin escalate to admin and use a cross-tenant write? The audit log can't distinguish without an explicit pivot.
3. **CSRF / clickjacking blast radius is doubled.** An admin who is also logged into the user app has a cookie that — if leaked — grants both surfaces. An attacker who CSRFs the admin while they browse a malicious page gets admin-level reach.
4. **The admin's own user data is harder to debug** — when supporting their own account, the admin can't reproduce what a normal user sees because their session is admin.

**Why it happens:**
- Spring Security default is "one authentication, all granted authorities." Splitting into a "user session" and an "admin session" requires explicit design.
- The founder is **also** a normal Zero Mail user (they have their own Gmail connected to their own account). The path of least resistance is one cookie, one session.

**How to avoid:**

1. **Two distinct session cookies, two distinct mount points.**
   - User app: `apps/web` mounts at `/`, cookie `zm_session`, scope `Path=/; Domain=app.zero-mail.invalid`.
   - Admin app: `apps/web` admin routes mount at `/admin`, cookie `zm_admin_session`, scope `Path=/admin; Domain=app.zero-mail.invalid` (or on a separate subdomain `admin.zero-mail.invalid` if reverse proxy supports it).
   - Logging in as admin requires a **second** OAuth round-trip from `/admin/login` even if the user is already authenticated on `/`.
   - The user session does not carry admin authorities; the admin session does not carry tenant authorities for the admin's own tenant. To act on their own tenant the admin signs into `/` separately.
2. **`@AdminEndpoint` annotation enforces `zm_admin_session` cookie**, not `zm_session`. A separate Spring Security filter chain validates each.
3. **Audit log records the session cookie type** in every entry: `admin_audit_log.session_type = 'admin'`. Cross-checking shows whether a given action happened via admin escalation or as a user.
4. **CSP frame-ancestors + SameSite=Strict on the admin cookie**, vs `SameSite=Lax` on the user cookie. Admin actions cannot be triggered from any cross-site context.
5. **Auto-logout on admin session.** The admin session has a much shorter idle timeout (e.g., 30 min) than the user session (24 hours).
6. **Admin login displays an unambiguous banner.** `/admin/*` pages render a persistent red/yellow chrome bar "ADMIN MODE — actions affect all tenants" so the admin never confuses admin context with user context.
7. **Architectural test: every admin controller asserts `AdminContext.currentOrThrow()`** as the first statement; ArchUnit enforces. No admin controller may invoke `TenantContext.currentOrThrow()` directly — cross-tenant reads route through `AdminTenantAccess`.

**Warning signs:**
- Admin endpoints use the same `zm_session` cookie as the user app.
- A user-facing endpoint contains `if (isAdmin) { skip tenant filter }`.
- Audit log has no `session_type` field.
- Admin login is "automatic" when an admin user authenticates at `/login`.
- Admin pages don't visually differ from user pages.

**Phase to address:** **Phase 8 / sub-phase 8A — Admin foundation**, alongside `AdminContext`. Two-cookie + two-filter-chain setup must land with the first admin endpoint.

---

### Pitfall 4: Tenant read-only "convenience" view leaks email body / completion content the v1.0/v1.1 privacy contract forbids

**What goes wrong:**
The admin console needs **tenant inspection** to support users — "tenant X reports triage misbehaving, let me see what their last 20 messages were classified as." The well-meaning implementation:

1. Admin opens `/admin/tenants/{id}/triage-history` — backend reads `triage_action_audit` rows + joins to `gmail_message_metadata` for subject / sender — this is correct (metadata-only).
2. Admin clicks a row to "see what the AI did" — backend goes one further and calls Gmail API on behalf of the tenant (using the tenant's stored OAuth refresh token) to fetch the **live body** for "context."
3. Backend returns the body to the admin UI, which renders it.

The privacy contract — "no long-term storage of raw email bodies, email-content LLM prompts/completions, or embeddings" — is **technically** preserved because the body is fetched live, not stored. **But the trust contract is broken**:

- The body **transits the admin's browser**, ends up in browser memory, possibly in the admin's session HAR / Playwright recordings / error reporter snapshots.
- Admin nginx access logs may capture the response body if the proxy is configured for diagnostic mode.
- The admin's "tenant inspection" capability now silently includes "read any tenant's email content" — a privilege the v1.0 trust posture explicitly forbade in PROJECT.md ("the AI has write access; the human operator does NOT have read access to bodies").
- Compliance / CASA / data-retention story is silently inconsistent: "we don't store bodies" but "any admin can read any user's body on demand." The trust story is "we can't see your mail" — the admin endpoint contradicts it.
- **Tenant OAuth tokens are misused.** Using the tenant's refresh token to make a Gmail call **driven by an admin click**, not by the tenant, violates the OAuth grant: the user authorized Zero Mail to access their mail for triage on their behalf, not for support inspection.

A worse variant: the admin "convenience" endpoint also exposes the **last LLM prompt + completion** for that triage action, fetched from in-memory cache or reconstructed from logs. Now the privacy contract is broken twice — bodies AND prompts AND completions visible to the admin.

**Why it happens:**
- Customer support tooling for triage SaaS naturally wants to see "what the AI saw." The privacy invariant fights this.
- v1.0's `LlmRepositoryContentBanTest` proves no **column** stores bodies; it does not prove no **endpoint response** contains bodies.
- The line between "metadata read" and "body read" is not architecturally enforced — both go through the same `GmailClient`.

**How to avoid:**

1. **Hard rule, written in PROJECT.md and Constraints:** "Admin endpoints MUST NOT return email body content, LLM prompts, LLM completions, or any content the privacy contract bans from long-term storage. The metadata-only triage history is sufficient for support; tenant body access is forbidden by design."

2. **Two architectural enforcement layers:**

   a) **ArchUnit `AdminPathBodyBanTest`.** Any class in `core.admin.*` or `api.controllers.admin.*` may NOT call any method on `GmailClient` that returns a body field (e.g., `gmailClient.getMessage(...)` returns `Message` which has `payload.body` — those methods are explicitly listed in a `BodyExposingGmailMethods` constant set, and ArchUnit forbids admin packages from calling them). Admin paths may only call `GmailClient.listMessageMetadata(...)` or other metadata-only methods.

   b) **Response sanitizer `AdminResponseBodyBanFilter`.** A Spring `OncePerRequestFilter` on admin endpoints inspects every JSON response for fields named `body`, `bodyHtml`, `bodyText`, `payload`, `snippet`, `prompt`, `completion`, `content` longer than 200 chars. On match, log `event=admin_response_body_ban_triggered adminUserId=... endpoint=... fieldName=... contentLength=...` at WARN and **strip the field, replace with `{"truncatedForPrivacy": true}`**. This is the failsafe: even if ArchUnit is bypassed, the filter scrubs.

3. **No admin endpoint may use a tenant's OAuth refresh token to call Gmail on the tenant's behalf** for support inspection. Tenant OAuth tokens are usable only for the original purposes the user consented to: triage, draft, send-via-chat. ArchUnit:
   ```java
   noClasses().that().resideInAPackage("..admin..")
     .should().callMethodWhere(target ->
       target.getOwner().getName().endsWith("GmailOAuthCredentialResolver"))
     .because("Admin paths must not resolve tenant OAuth credentials.");
   ```

4. **What admin CAN see (allow-list):**
   - Triage audit rows (`triage_action_audit`): which messageId, what action, what rule fired, when, undo state.
   - Gmail message metadata as already cached in `gmail_message_metadata`: subject, from, date, threadId. No body, no snippet.
   - Tenant's rule list (the rule itself is user-config, not email content).
   - Tenant's chat session list — **but not chat message contents** (carve-out: chat messages contain user-typed config which is privacy-allowed, but admin viewing them is still a tenant-content boundary violation; default-deny, justify per surfaced support need).
   - Spend per tenant (credit ledger).
   - Connection health: last sync, last Pub/Sub message, scope status.

5. **What admin explicitly CANNOT see:**
   - Email bodies, ever.
   - LLM prompts, completions, intermediate model outputs.
   - BYOK keys (only mask + last-validated-at).
   - Chat message text / tool outputs (privacy-allowed for the tenant, not for the admin without an explicit support ticket-bound grant; out of scope for v1.2).

6. **Per-tenant data access audit.** Every admin endpoint that reads tenant data writes an `admin_audit_log` row with `target_tenant_id`, `operation`, `field_set_accessed`, `reason` (admin must supply a free-text reason on form submit). The tenant has a Settings page later (v1.3+) showing "admin accessed your data on date X for reason Y."

**Warning signs:**
- An admin controller imports `GmailClient`.
- An admin response DTO has a field named `body`, `bodyHtml`, `snippet`, `prompt`, `completion`.
- A code review says "we need to show the admin what the email said for support."
- The `AdminResponseBodyBanFilter` is not on the admin filter chain.
- ArchUnit `AdminPathBodyBanTest` does not exist.
- Admin can see chat message contents.

**Phase to address:** **Phase 8 / sub-phase 8C — Tenant read-only views.** The ArchUnit ban + response filter + allow-list contract land **before** any tenant-inspection view is rendered. PROJECT.md policy entry logged in the same PR.

---

### Pitfall 5: Catalog Sync-from-`/models` is a supply-chain trust boundary that's invisible to the admin clicking "Sync"

**What goes wrong:**
The admin clicks "Sync from /models" on the OpenAI catalog page. Backend calls `GET https://api.openai.com/v1/models` and updates `catalog_model` rows. The naïve implementation trusts:

1. The provider's `/models` response JSON shape (fields, types).
2. The model IDs returned (used directly as the foreign key on per-tenant model selection).
3. The provider's claim that a returned model "exists" (no validation against actual chat-completion success).
4. The transport (assumes TLS verification handles MITM).
5. The provider's response is deterministic between syncs.

The trap: each of these assumptions can be silently violated:

1. **Schema drift.** OpenAI adds a new field `deprecated: true` on legacy models. Code that does `JsonNode.get("modelId").asText()` doesn't break, but `JsonNode.get("supportsToolUse").asBoolean()` on a missing field returns `false` and silently disables tool-calling for models that actually support it. Or worse: a typo in the provider's response (`modelid` lowercased) silently creates a separate catalog entry.
2. **Adversarial model IDs.** A future provider may return a model ID like `../../../etc/passwd` or `<script>` or `gpt-4o';DROP TABLE catalog_model;--`. If the catalog UI renders the ID without escaping (likely — model IDs are "trusted strings"), stored XSS. If the catalog DB upsert uses string concatenation, SQL injection.
3. **Ghost models / phantom additions.** Provider response includes a model ID that doesn't actually work for chat completion (e.g., `whisper-1` showing up under the chat-completion compatible list because the endpoint mixes use cases). Admin curates it, exposes it to users, users select it, runtime errors at completion time.
4. **Sync removes models silently.** OpenAI deprecates `gpt-3.5-turbo-0613` overnight. Sync runs, the catalog removes the row. Every per-tenant `assistant_settings.preferred_model_id = gpt-3.5-turbo-0613` row now points at a non-existent FK. Worker chat completion fails for every affected tenant simultaneously.
5. **Sync is irreversible.** Admin clicks "Sync" by accident at 3am, prod catalog state diverges from manually curated state, hours of curation lost.
6. **No diff preview.** Admin clicks "Sync" and the catalog mutates in place. No "9 added, 2 removed, 4 changed — confirm?" dialog.
7. **`/models` endpoint requires the master key.** If the master key is wrong / expired / rate-limited, sync errors. The error is shown to the admin including (possibly) the provider's raw error response containing key fragments.
8. **Sync writes during normal user traffic.** Admin clicks sync; the catalog upsert holds locks on `catalog_model` that conflict with `SELECT ... FOR UPDATE` from a worker building a chat request → request latency spikes.
9. **No per-feature curation in /models response.** The provider gives one flat list; the admin needs to mark which models are allowed for "chat assistant" vs "triage" vs "draft generation" — that's a Zero Mail editorial decision, not a provider one. Sync must not overwrite the per-feature toggles.
10. **OpenRouter / DeepSeek / smaller providers vary wildly in `/models` shape.** A code path coded for OpenAI's response shape will misbehave on Anthropic's response (Anthropic doesn't even have a public `/models` endpoint — Spring AI's Anthropic adapter doesn't expose one; the admin must enter Anthropic models manually).

**Why it happens:**
- Provider docs make `/models` look like a trusted source — it's literally the provider telling you "these are our models." Developers don't treat it as untrusted input.
- Schema validation is "boring" — gets skipped.
- A "Sync" button is one click; the admin doesn't see the diff in advance.

**How to avoid:**

1. **Sync is a three-step flow, not one click.**
   - **Step 1: Fetch + validate.** Backend calls `/models`, parses against a strict JSON Schema (per-provider — `openai-models-v1.schema.json`, `openrouter-models-v1.schema.json`, etc.). Unknown fields warn (logged at WARN with field name); missing required fields fail. Result: a `SyncDraft` row with the parsed result, no live mutation yet. SyncDraft has `created_at`, `provider`, `raw_response_hash`, `parsed_count`, `parse_warnings`.
   - **Step 2: Diff + preview.** Admin sees a diff page: "9 new models will be added (list), 2 will be marked removed (list), 4 attribute changes (table)." Per-feature toggles (`allow_for_chat_assistant`, `allow_for_triage`, `allow_for_draft`) are preserved from the existing catalog row — sync only updates provider-derived fields.
   - **Step 3: Confirm + commit.** Admin clicks "Apply diff." Sync writes happen inside a single transaction with `SELECT ... FOR UPDATE` on a small lock table, not the catalog itself, so user-traffic reads aren't blocked.

2. **Strict per-provider response schemas.** A `ModelsResponseSchema` per provider, validated via `jakarta.json.bind` + json-schema-validator on every fetch. Unknown shapes reject the entire sync; never partial-apply.

3. **Model ID validation.** Regex allow-list: `^[a-zA-Z0-9._:/\-]{1,128}$`. Reject anything else with a structured error. Add to the diff preview: "1 invalid model ID rejected — see audit log."

4. **Soft-delete, never hard-delete.** Removed models in the provider's response → `catalog_model.deprecated_at = now()` + `deprecated_reason = "no longer in provider /models response"`. Tenants currently using a deprecated model keep working (FK still resolves) but the model is hidden from new selection in the Settings UI. Background email to affected tenants: "Model X has been deprecated; please select an alternative."

5. **Per-feature curation is preserved by sync.**
   ```sql
   INSERT INTO catalog_model (provider, model_id, display_name, ...)
   VALUES (?, ?, ?, ...)
   ON CONFLICT (provider, model_id) DO UPDATE SET
       display_name = EXCLUDED.display_name,
       provider_metadata = EXCLUDED.provider_metadata,
       last_synced_at = now()
       -- explicitly DO NOT update: allow_for_chat_assistant, allow_for_triage, allow_for_draft, custom_display_order
   ```

6. **Diff preview is rendered server-side from the SyncDraft, escaped via the standard JSX/React XSS protection** — model IDs and display names always rendered as text content via React children, never via raw-HTML injection escape hatches.

7. **Connection-test path reused for ghost models.** Before a model is enabled for any feature, admin must click "Test" on it — backend issues a minimal chat completion (e.g., "ping" → expect any non-empty response). Models that fail test stay disabled. This catches `/models` lying or schema mismatches.

8. **Admin-supplied notes per model.** Admin can add a `editorial_note` per model (e.g., "Reasoning model — use for complex queries only"). Surfaced in the Settings UI dropdown subtitle. This is admin-only content, sanitized for HTML.

9. **Anthropic / providers with no `/models` endpoint:** the catalog UI for these providers shows a "Manual entry" form. The admin types model IDs explicitly. Sync button is disabled for these providers. No fake `/models` call is fabricated.

10. **Audit every catalog mutation.** `catalog_audit_log` table: `id, admin_user_id, provider, action (sync_applied | manual_create | manual_update | manual_delete | toggle_feature), diff_summary, applied_at`. Reversible — admin can click "Revert last sync" within 24h, which re-applies the previous state from `SyncDraft` history.

11. **Provider response stored hashed, not verbatim.** The raw `/models` response can contain provider rate-limit data, account context that shouldn't be logged. Store `SHA-256(response_body)` + parsed-result summary; not the full JSON.

**Warning signs:**
- Sync is a one-click action with no preview.
- The sync code uses `JsonNode.get(...)` without schema validation.
- Hard-deletes happen on the catalog.
- No `editorial_note` / per-feature toggles preserved across sync.
- Anthropic provider page has a `/models` Sync button.
- Diff preview renders model IDs via any raw-HTML React escape hatch (the one explicitly forbidden by React for untrusted input).
- No "ping completion" test before a model is enabled.

**Phase to address:** **Phase 8 / sub-phase 8D — Catalog management + Sync flow.** Three-step flow + schema validation + audit + soft-delete land together. The Anthropic-no-sync rule is documented at the same time.

---

### Pitfall 6: Admin audit log used as exfiltration channel OR admin can edit their own audit

**What goes wrong:**
The admin audit log (`admin_audit_log`) is meant as the trust backstop: "what did the admin do, when, on which tenant, with what reason." Two failure modes:

1. **Admin can edit / delete their own audit rows.** An admin who does something untoward (reads a tenant body via a forgotten endpoint, exports a key) deletes the audit row from `admin_audit_log` via raw SQL or a misguided "Cleanup" admin endpoint. The audit log is now unreliable; the trust backstop fails.
2. **Audit log itself is an exfiltration channel.** Admin writes audit entries with a `reason` field they control. They embed sensitive data (an exfiltrated tenant body, a stolen key) inside the reason text. The audit log is **append-only** (good!) but is now the long-term storage of the very thing that's supposed to be banned. Worse: the audit log is exportable / queryable by other admins, so the exfiltrator can later "ask for the audit log" from any admin user.
3. **Audit log writes inside the same transaction as the admin action.** If the admin action succeeds but the audit insert fails (DB hiccup, constraint violation), the action rolls back. Convenient for atomicity, but an admin could **deliberately** craft a `reason` that violates a constraint (e.g., overlong, contains a forbidden pattern) to make their action "untraceable" by ensuring the audit fails AND the action is silently rolled back AND nothing tells anyone they tried.
4. **Audit log is silently swallowed on async paths.** If admin actions emit a Spring Modulith event for audit, and the listener fails after-commit, the action happened but no audit row exists. v1.0 uses Spring Modulith — the trap is real.
5. **No tamper-evidence.** A DBA can write directly to `admin_audit_log`. Without per-row hashing or external write-ahead log, tampering is invisible.

**Why it happens:**
- "Audit" is usually treated as a write-once table with no further design. Editability via SQL is "obviously bad" but never tested.
- Free-text `reason` fields are tempting for flexibility.
- Atomicity-via-same-transaction is a textbook pattern, but the failure-mode-as-feature isn't explored.

**How to avoid:**

1. **DB-level append-only enforcement.** A Postgres trigger:
   ```sql
   CREATE OR REPLACE FUNCTION admin_audit_log_no_update_no_delete() RETURNS trigger AS $$
   BEGIN
     RAISE EXCEPTION 'admin_audit_log is append-only';
   END;
   $$ LANGUAGE plpgsql;
   CREATE TRIGGER admin_audit_log_no_update
     BEFORE UPDATE OR DELETE ON admin_audit_log
     FOR EACH ROW EXECUTE FUNCTION admin_audit_log_no_update_no_delete();
   ```
   Combined with: the application DB user has **no** `UPDATE` / `DELETE` privilege on the table (only `INSERT` + `SELECT`). The trigger is a belt-and-braces last line; the missing grant is the primary defence.

2. **`reason` field length cap + content sanitization.** `reason VARCHAR(500)`. Reject control characters, anything matching key-prefix regex (`sk-[a-zA-Z0-9_-]{16,}`, `AIza[a-zA-Z0-9_-]{16,}`), email-address shaped content, or longer-than-500-char content. Sanitization happens **before** the audit insert, not after; if sanitization triggers, the admin action **also** rejects (because the admin tried to write something suspicious in their reason — that's a signal). Log a separate `admin_audit_sanitization_triggered` row in a separate `admin_audit_meta_log` table.

3. **Audit insert outside the admin-action transaction, in a separate same-request transaction with required success.** Pattern:
   ```java
   public AdminActionResult performAdminAction(AdminCommand command) {
       AdminAuditId auditId = adminAuditLogService.beginAction(command);  // own tx, must succeed
       try {
           AdminActionResult result = doAction(command);  // own tx
           adminAuditLogService.completeAction(auditId, result);  // own tx, must succeed
           return result;
       } catch (Throwable failure) {
           adminAuditLogService.failAction(auditId, failure);  // own tx, must succeed
           throw failure;
       }
   }
   ```
   The audit row is inserted **before** the action starts (with `state = "started"`) and updated only via INSERT of a follow-up row (`state = "completed"` or `state = "failed"`). Three rows per action — start, end, optional retry — all append-only. An attempt to "fail the audit insert to roll back the action" doesn't work because the START insert happens first; if it fails, the action never starts.

4. **Per-row HMAC-chained hashing.** Each audit row includes a `chain_hash = HMAC-SHA256(server_secret, previous_chain_hash || current_row_content)`. A nightly job verifies the chain. Tampering with any historical row breaks the chain. The server_secret is in `MasterKeyVault`, not the DB.

5. **External replication.** Audit rows replicated (via logical replication or a CDC sink) to an off-host log store (Loki / S3) within seconds. A DBA who tampers with the local copy doesn't tamper with the replica. Detection via replica vs primary diff job.

6. **Audit log is read-only in the admin UI.** No "Edit reason" / "Delete entry" actions exist. Surface the chain-hash verification status prominently — green "verified" / red "tamper detected" banner.

7. **Admin cannot read their own audit log without a co-admin.** A separate admin role `AUDIT_AUDITOR` (or use the existing two-admin rule) is required to view `admin_audit_log`. The admin who took the action sees only their own action confirmations, not the full log. (This prevents an attacker with admin access from "checking what they left behind" to refine their cover-up.)

**Warning signs:**
- The app DB user has `UPDATE` / `DELETE` on `admin_audit_log`.
- No append-only trigger.
- `reason` field has no length cap or content sanitization.
- Audit insert is wrapped in `@Transactional` together with the admin action.
- No chain-hash or external replication.
- Admin UI surfaces an "Edit entry" or "Delete entry" button.
- An admin can read the full audit log without a co-admin.

**Phase to address:** **Phase 8 / sub-phase 8A — Admin foundation** (audit primitive lands with the first admin endpoint). External replication is a follow-up but the per-row HMAC chain + append-only trigger + reason sanitization land in 8A.

---

### Pitfall 7: Stale catalog cache races with admin edits — user sees a model the admin just removed (or fails to see one the admin just added)

**What goes wrong:**
The Settings UI shows the curated catalog (Phase 9, AI Provider/Model tab). To avoid a DB hit on every Settings open, the catalog is cached — in TanStack Query on the frontend, in Redis (or in-memory `ConcurrentHashMap`) on the backend. Four race scenarios:

1. **Admin removes a model; user opens Settings; user sees model.** Cached backend response was minted before the removal; user selects the model; backend write fails with FK violation ("model not in catalog"); user sees a confusing error.
2. **Admin adds a model; user opens Settings within cache TTL; user does not see it.** Slow propagation feels broken; admin re-clicks Sync repeatedly trying to "make it appear."
3. **Admin saves catalog edit; user is mid-save on Settings.** Concurrent: admin disables model X, user clicks "Save settings" with X selected. Last-write-wins: user save commits first → admin disable applies but user is now on a disabled model. Or admin disable commits first → user save tries to write disabled FK → constraint violation.
4. **Frontend caches the catalog AND user settings independently.** TanStack Query has `['catalog']` and `['settings']`. Admin changes the catalog; user changes their settings. Without coordinated invalidation, the Settings dropdown shows stale options against fresh settings.
5. **Per-tenant `ChatModel` cache holds a model ID** the admin just deprecated — the cache is keyed by `(tenantId, modelId)`. Cache hit returns the old cached client which still successfully calls a now-deprecated model.

**Why it happens:**
- Caching the catalog seems obviously cheap. It is, until edits land.
- Coordinated invalidation across (admin write → backend cache → frontend cache → per-tenant ChatModel cache) requires explicit wiring that no single feature owner thinks about.

**How to avoid:**

1. **Backend catalog cache invalidation on every admin write.** A `CATALOG_UPDATED` Spring Modulith event fires from `CatalogService.applyDiff(...)` after commit. The catalog cache (Redis key `catalog:{provider}`) is evicted in the listener. Per-tenant `ChatModel` cache is **not** automatically evicted (different concern — see step 5).

2. **Frontend cache invalidation via ETag + admin-visible "catalog version" cookie.** Catalog endpoint returns `ETag: "catalog-v-{monotonic_counter}"`. The counter increments on each admin write (`CATALOG_UPDATED` event also bumps a Redis counter). Settings page uses `If-None-Match` on revalidation. Stale frontend caches get a fresh response within one network roundtrip of the admin edit.

3. **User Settings save validates against fresh catalog inside the same transaction.** Pattern:
   ```java
   @Transactional
   public AssistantSettings save(EmailAccountId emailAccountId, AssistantSettingsCommand command) {
       CatalogModel selectedModel = catalogRepository.findActiveByProviderAndModelId(
           command.preferredProvider(), command.preferredModelId())
           .orElseThrow(() -> new ModelNotInCatalogException(...));
       if (!selectedModel.isAllowedForFeature(Feature.CHAT_ASSISTANT)) {
           throw new ModelNotAllowedForFeatureException(...);
       }
       return assistantSettingsRepository.save(...);
   }
   ```
   The `findActiveByProviderAndModelId` bypasses the cache and goes straight to DB (or uses a versioned read inside the same tx). If the model was just deprecated, the save fails loud with a structured error code `MODEL_DEPRECATED_DURING_SAVE`. Frontend renders "The model you selected was just removed by an admin. Refresh and pick another." with a "Refresh catalog" button.

4. **TanStack Query coordinated invalidation.** Settings mutation `onSuccess` invalidates both `['settings']` and `['catalog']`. Conversely, a frontend WebSocket / SSE / poll-on-focus mechanism (whichever lands first) bumps `['catalog']` when the server-side counter changes.

5. **Per-tenant `ChatModel` cache eviction on `MODEL_DEPRECATED` event.** When a catalog model is deprecated, all per-tenant `ChatModel` entries keyed by that model ID are evicted. New chats next request mints a fresh client.

6. **Optimistic-concurrency on `assistant_settings.updated_at`.** User save reads `updated_at`, writes with `WHERE updated_at = $observed`. Conflict resurfaces as 409 "Settings were updated elsewhere; refresh and try again." (Catches the case where two tabs of the same user race their own save.)

7. **Frontend Settings form does not double-submit.** Submit button disabled after first click until response returns; per-form idempotency key sent as header `X-Idempotency-Key: <uuid>` so a network-retried request hits a Redis-backed dedup and returns the original result. (Specifically addresses BYOK form double-submit which would otherwise issue two encrypt operations and two DB writes for the same key.)

8. **Race-test in CI.** Test: admin disables model X in one HTTP session; in another, user-A's settings save with model X. Assert (a) one wins cleanly, (b) the other gets a structured error, (c) no orphan `assistant_settings` row with a disabled model.

**Warning signs:**
- Catalog cache TTL is the only invalidation mechanism (no event-driven eviction).
- User Settings save reads from the cached catalog, not from DB inside the transaction.
- Frontend has no coordinated invalidation between `['catalog']` and `['settings']`.
- Settings submit button has no disable-during-submit logic.
- No race test in CI.
- Per-tenant `ChatModel` cache is keyed by tenant only, not by `(tenantId, modelId)`.

**Phase to address:** **Phase 8 / sub-phase 8D (catalog write contract) + Phase 9 / Settings save handlers.** The event + ETag scheme lands in 8D; the Settings save validation + frontend coordinated invalidation lands in 9. Race test runs against both.

---

### Pitfall 8: Worker queue health view DoS's the DB or leaks job payload contents

**What goes wrong:**
v1.2 admin includes a worker queue health view: outbox lag, processing-job depth, failed-job list. Two failure modes:

1. **Naïve queries.** Admin opens `/admin/queue/health` — backend runs `SELECT COUNT(*) FROM outbox WHERE status = 'pending'` + `SELECT COUNT(*) FROM processing_job WHERE state = 'running'`. On a healthy small instance, this is fine. At scale (50k+ rows in outbox after a Pub/Sub catch-up burst), the unindexed `COUNT(*)` scans 50k rows + holds locks against worker `SKIP LOCKED` queries. Worker throughput drops; the admin clicking refresh repeatedly amplifies the problem.
2. **Failed-job list shows payload.** "Investigation convenience": admin clicks a failed job, sees `processing_job.payload_json` — which for triage jobs contains the `messageId` + (worst case) cached body content + LLM prompt fragments that were captured at job-enqueue time before privacy sanitization. Privacy contract is broken via the admin convenience surface.
3. **Failed-job retry button re-enqueues with a stale tenant context.** Admin clicks "Retry"; the retry handler reconstructs `TenantContext` from the job row's `tenant_id` and re-fires the worker, but does so via the admin's HTTP request thread. ScopedValue propagation is wrong; the retry runs with admin context, not tenant context; tenant data writes might end up cross-tenant.
4. **Admin can drain the queue.** A "Cancel all pending" admin button (or worse, an inadvertent SQL admin endpoint) wipes the outbox. Tenant data loss.
5. **Queue stats expose tenant identifiers** in aggregate views: "30 jobs pending for tenant-id-abc, 5 for tenant-id-xyz." Even tenant IDs are PII in a multi-tenant context.

**Why it happens:**
- Operations tooling is built ad-hoc; admin features ride on whatever queries are easy.
- The queue tables grow much larger than other tables; queries that are fast in dev are slow in prod.
- "Show me what failed" naturally wants the payload.

**How to avoid:**

1. **Pre-computed queue stats, not live aggregation.** A `queue_stats_snapshot` table updated by a 30s scheduled job: rows like `(captured_at, queue_name, status, count_total, count_per_tenant_redacted_top_10)`. Admin reads the latest snapshot, not live `COUNT(*)`. Drilling into a specific row triggers a targeted query bounded by `LIMIT 100`.

2. **Job payload is metadata-only in admin views.** `processing_job.payload_json` is not exposed; instead, admin sees `processing_job.payload_summary` — a computed column / view containing `{ jobType, tenantId, messageId, lastAttemptAt, attemptCount, lastErrorCode }`. The full payload is hidden; reading it requires a separate explicit admin endpoint with audit-row + reason-required.

3. **Failed-job retry uses the worker's normal enqueue path.** "Retry" admin endpoint inserts a new outbox row with the same payload; the actual retry runs via the worker `SKIP LOCKED` poll in the worker JVM, with correct ScopedValue propagation. Admin's HTTP thread never executes the job.

4. **No "drain queue" admin action exists.** Cancellation is per-job, audited, with reason required, and limited to specific job types (e.g., never cancel a `gmail_send` job). An ArchUnit rule forbids any DELETE on outbox / processing_job tables from admin paths.

5. **Tenant IDs in admin queue views are pseudonymized to short prefixes** (`tenant-abc...`) unless the admin clicks "reveal" with reason. Per-tenant counts above a threshold (e.g., >100) are surfaced as concrete numbers; below are bucketed (`< 10`, `10-100`).

6. **Index every WHERE clause used in admin queue queries.** Schema review verifies indexes on `(status, last_attempt_at)`, `(tenant_id, status)`, etc. Postgres MCP `analyze_query_indexes` run as part of the phase acceptance check.

7. **Read replica for admin queries** — defer to v1.3+ unless load testing proves blocking, but document the option.

**Warning signs:**
- Admin queue endpoints do `COUNT(*)` on outbox / processing_job.
- `processing_job.payload_json` is returned in any admin response.
- A "Retry" admin endpoint calls the worker handler directly on the admin's request thread.
- A "Drain queue" or "Cancel all" admin action exists.
- Tenant IDs are surfaced verbatim in queue stats.

**Phase to address:** **Phase 8 / sub-phase 8E — Worker queue health view.** Pre-computed snapshots + payload-summary view + retry-via-enqueue land together. ArchUnit rule for no-delete-from-queue-in-admin lands at the same time.

---

### Pitfall 9: Global spend dashboard leaks cross-tenant info via aggregation, naïve drill-down, or unsecured raw rows

**What goes wrong:**
v1.2 promotes the global spend dashboard (previously deferred OPS-02) — total LLM spend across all tenants, broken down by provider, by feature, by time. Three failure modes:

1. **Aggregation reveals individual tenants.** Total spend "$1,234.56" — single tenant in the data set; the total IS the tenant's spend. A "top 10 spenders" view (Inbox Zero's `top-spenders/route.ts` does this — hashes the email!) exposes per-tenant identity. Even hashed, a hash is a stable identifier that can be correlated with external signals.
2. **Drill-down endpoint exposes raw rows.** Admin clicks a date — backend returns every spend row for that date including `tenant_id`, `model_id`, `cost_credits`, `messageId` (if accidentally captured), prompt-token counts that, at unit prices, reverse-engineer to actual prompt sizes.
3. **Spend rows contain prompt / completion fragments.** Privacy invariant says no LLM prompts / completions are persisted; the spend ledger correctly stores **token counts** only. But a previous engineer added a `last_failure_reason TEXT` column for debugging and it occasionally contains a snippet of the provider's error response which mirrors the prompt.

**Why it happens:**
- Aggregation is a textbook anonymization trap (k-anonymity violations).
- Drill-down exists because the dashboard is useless without it.
- Debug columns added long ago drift in scope.

**How to avoid:**

1. **k-anonymity threshold on aggregates.** Any displayed bucket must contain at least 5 distinct tenants; sub-5 buckets are merged into "Other (N tenants)" or suppressed. Backend enforces; frontend renders the suppressed buckets visibly so admin understands why.

2. **No per-tenant drill-down without an explicit support ticket reference.** "Drill into this row" requires admin to enter a ticket ID (free-text validated against `^TICKET-\d+$` pattern) which is logged in `admin_audit_log`. Surfaces a friction step; trains admins not to drill casually.

3. **`spend_ledger` schema review.** Audit every column. Allowed: `tenant_id`, `provider`, `model_id`, `feature` (chat/triage/draft), `input_tokens`, `output_tokens`, `cost_credits`, `created_at`. Forbidden: any free-text column; any column that could store prompt/completion fragments. ArchUnit + schema test enforces.

4. **Per-tenant identifiers hashed in admin views by default.** `tenant_id` rendered as `t-{hash[:6]}`. Reveal requires explicit per-row click with audit + reason. Inbox Zero's hashing approach is the right pattern, but Zero Mail goes one step further: the reveal is auditable and the hashed value is **not** stable across admin sessions (per-session salt) so admins can't easily correlate across views.

5. **Spend rows are admin-readable only via aggregate APIs.** No `GET /admin/spend/raw` endpoint exists; only `GET /admin/spend/aggregate?groupBy=provider,date&from=...&to=...` and `GET /admin/spend/drill?ticketId=...`. ArchUnit forbids admin controllers from reading `spend_ledger` directly except via `SpendAggregateService`.

6. **Total-spend rendering uses appropriate precision.** Don't render `$1234.5678` (high-precision values can reverse-engineer prompt sizes); round to `$1,234.57`. Render percentage shares not raw totals where possible.

**Warning signs:**
- A "top spenders" view exposes individual tenants (hashed or not) without k-anonymity threshold.
- `spend_ledger` has a free-text column.
- Drill-down is one click with no ticket-reference / audit step.
- An admin endpoint returns raw rows from `spend_ledger`.
- Tenant identifiers are rendered as full UUIDs in admin views.

**Phase to address:** **Phase 8 / sub-phase 8F — Global spend dashboard.** Aggregation API + k-anonymity threshold + drill-down ticket-required land together. Spend ledger schema audit (ArchUnit + Liquibase) is verified in the same phase.

---

### Pitfall 10: Admin "send on behalf of tenant" feature regresses the single Gmail send call site

**What goes wrong:**
"Helpful" admin feature requests appear: "let me send an apology email from the tenant's account when an outage affected them" or "let me forward a debugging email to the tenant from their own inbox." Implementing this requires the admin path to call `Gmail.Users.Messages.send` — which means a **second** send call site, regressing v1.1's `OnlyOneGmailSendCallSiteTest` invariant. The naïve developer:

1. Adds `AdminGmailSendExecutor` under `core.admin.gmail.send.*`.
2. "Fixes" the ArchUnit `OnlyOneGmailSendCallSiteTest` by changing `isEqualTo(1L)` to `isLessThanOrEqualTo(2L)`.
3. Updates the grep gate threshold to 2.
4. Now two send paths exist; the "we have exactly one send path" trust property is broken; future code can add a third path more easily.

A subtler variant: admin doesn't directly send, but **simulates a user-confirmed send** by replaying a `chat_message.parts` tool call. This re-uses `AssistantSendExecutor.send(...)` but with `AdminContext` instead of `TenantContext`. Now `AssistantSendExecutor` accepts admin invocation — its tenant-isolation assumptions are silently broken; the executor was designed assuming `TenantContext.currentOrThrow()` is bound.

**Why it happens:**
- The trust posture is a property maintained by a human-readable rule. Each individual "we just need to send one email as an admin" feels small.
- The ArchUnit number is "just a number." Bumping it from 1 to 2 looks reasonable.
- AssistantSendExecutor is the existing call site — "why duplicate it?"

**How to avoid:**

1. **Hard product decision, written in PROJECT.md:** "Admin users may NOT send email on behalf of any tenant. There is exactly one Gmail send call site (`AssistantSendExecutor`), invoked only with a bound `TenantContext` from the user-confirmed chat preview flow. v1.2 does NOT introduce admin-initiated email actions; if support needs to communicate with a tenant, support sends from a separate operator mailbox, not the tenant's account."

2. **Reaffirm v1.1's `OnlyOneGmailSendCallSiteTest`.** The number stays at exactly 1. The grep gate stays at exactly 1.

3. **ArchUnit extension: forbid `AdminContext` in the send call chain.**
   ```java
   noClasses().that().resideInAPackage("..chat.confirm.send..")
     .should().callMethodWhere(target ->
       target.getOwner().getName().endsWith("AdminContext")
       && Set.of("current", "currentOrThrow").contains(target.getName()))
     .because("The single send call site must run only with a tenant context; admin send is forbidden.");
   ```

4. **Reaffirm via a v1.2 acceptance test** in Phase 8 that no admin endpoint touches Gmail send. Test: scan every controller bean in `api.controllers.admin.*`; assert no transitive call graph reaches `AssistantSendExecutor` or `GmailClient.send`.

5. **PR review checklist item:** every v1.2 PR explicitly answers "Does this PR change the count of Gmail send call sites? If yes, escalate to founder + log a Key Decisions row in PROJECT.md with rationale."

**Warning signs:**
- A PR adds `AdminGmailSendExecutor` or any class under `core.admin.*gmail.send*` or `core.admin.*mail.send*`.
- The `OnlyOneGmailSendCallSiteTest` count is bumped above 1.
- The grep gate threshold is bumped above 1.
- A PR makes `AssistantSendExecutor` callable from an admin context.
- Feature requests mentioning "send on behalf of tenant" appear without a PROJECT.md decision logged.

**Phase to address:** **Phase 8 / sub-phase 8A — Admin foundation** (reaffirm at the start). The ArchUnit extension lands with the first admin endpoint. The PROJECT.md decision is logged in the phase-opening commit.

---

### Pitfall 11: ScopedValue + `@PreAuthorize` ordering — admin authority granted before tenant context bound

**What goes wrong:**
Spring Security's `@PreAuthorize("hasRole('ADMIN')")` runs **before** the controller method body. The admin role is checked, the method executes, the method's first line tries to read `AdminContext.currentOrThrow()` — but `AdminContext` was never bound because the binding code lives in a Spring `OncePerRequestFilter` ordered **after** `MethodSecurityInterceptor`. Result: `AdminContext.currentOrThrow()` throws "no admin context bound." A panicked developer fixes this by adding a fallback "if no admin context, but @PreAuthorize passed, fall back to user context" — and now admin endpoints execute with the **user's** tenant context, silently filtering admin reads through the admin's own tenant scope.

A worse variant: the developer adds a `@PreAuthorize` SpEL expression that reads `#tenantId` from the request and calls `tenantAccessChecker.canRead(#tenantId)`. The check runs before any tenant context binding. The checker has its own DB query that doesn't know it's running in an admin context — defaults to "yes, the requesting user can read their own tenant" and the admin reads tenant X's data because tenant X **is** the admin's own tenant ID (collision).

**Why it happens:**
- Spring Security filter ordering is not obvious from controller code.
- Scoped Values are new (Java 25 finalization); developer mental model isn't fully formed.
- The `@PreAuthorize` + `@AdminEndpoint` combination is product-specific; Spring guides don't cover it.

**How to avoid:**

1. **Single `AdminEndpointFilter` ordered BEFORE `MethodSecurityInterceptor`.** The filter:
   - Validates the admin session cookie.
   - Loads the admin user from `app_user` + `admin_grant`.
   - Binds `AdminContext`.
   - Allows the request to proceed.
   - `MethodSecurityInterceptor` then runs `@PreAuthorize`, which reads `AdminContext` to check authority.

   ```java
   @Configuration
   @EnableMethodSecurity
   public class AdminSecurityConfig {
       @Bean
       SecurityFilterChain adminFilterChain(HttpSecurity http, AdminEndpointFilter adminEndpointFilter) throws Exception {
           return http
               .securityMatcher("/admin/**")
               .addFilterBefore(adminEndpointFilter, AuthorizationFilter.class)
               .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
               .build();
       }
   }
   ```

2. **`@PreAuthorize` reads `AdminContext`, not `Authentication`.** A custom `PermissionEvaluator` reads `AdminContext.currentOrThrow()` to determine authorities. Forces the order: filter binds context → method security checks context.

3. **ArchUnit rule: no `@PreAuthorize` SpEL expression may reference request-bound parameters** for authorization decisions (`#tenantId`, `#userId`). Admin authority is exclusively derived from `AdminContext` which is filter-populated.

4. **Integration test: admin endpoint with no admin session cookie returns 401 BEFORE controller body runs.** Test asserts the filter rejects, the controller `beforeAdvice` log is not present (verifying the controller method was never entered).

5. **Integration test: admin endpoint with a valid admin session reads `AdminContext` in the first line of every controller method.** ArchUnit checks: every method annotated `@AdminEndpoint` has `AdminContext.currentOrThrow()` as its first executable statement.

**Warning signs:**
- `@PreAuthorize` SpEL references `#tenantId` or `#userId` from request params.
- An admin controller's first line is anything other than `AdminContext.currentOrThrow()`.
- A fallback exists: "if admin context unbound, use tenant context."
- The admin filter is registered without explicit ordering.

**Phase to address:** **Phase 8 / sub-phase 8A — Admin foundation.** Filter ordering + custom PermissionEvaluator + ArchUnit rules land before the first admin controller is wired.

---

### Pitfall 12: Settings UI BYOK form double-submit + concurrent admin master-key rotation produce key chaos

**What goes wrong:**
A user types a new BYOK key for OpenAI in the Settings UI. They click Save. The button isn't disabled. They click Save again 100ms later. Two POST requests fire to `PUT /settings/byok/openai`. Concurrently, an admin clicks "Rotate master key" in the admin console. Three writes happen within the same ~500ms window:

1. User write A: encrypts `byok-key-v2` for tenant T, persists.
2. User write B: encrypts `byok-key-v2` (same content, different IV due to AES-GCM random IV) for tenant T, persists — but the table has `ON CONFLICT (tenant_id, provider) DO UPDATE`, so this overwrites write A with a different ciphertext.
3. Admin write: rotates the master key. Emits `MASTER_KEY_ROTATED` event, which evicts every cached `ChatModel` for the provider.

Result: per-tenant ChatModel cache is evicted, **but** the in-flight chat request for tenant T (already past the cache lookup) keeps using the just-replaced ChatModel which was instantiated with `byok-key-v2 from write A` (now superseded by write B's ciphertext). If write A's ciphertext somehow can't be decrypted (extremely unlikely, but possible if the key derivation changes mid-flight), the in-flight request fails mysteriously.

A simpler version of the same trap without the admin: user-side double-submit on BYOK alone — two AES-GCM encrypt operations, two DB writes, redundant audit rows, one is "lost" but its audit row remains, audit reads "2 BYOK updates within 200ms" — not strictly wrong but confusing.

A worse version: the BYOK form encrypts client-side (which it shouldn't! see Pitfall 8 v1.1) but the developer adds it "for defense in depth." Two encrypts with two different client-side IVs. Server receives two different ciphertexts of the same plaintext. Race resolution is non-obvious.

**Why it happens:**
- Frontend submit buttons rarely disable on first click in admin/internal SaaS.
- Master-key rotation and BYOK save are designed independently; their interaction is invisible.
- Idempotency is "obvious" but easy to skip.

**How to avoid:**

1. **Idempotency key on every Settings mutation.** Frontend mints `X-Idempotency-Key: <uuidv7>` on form submit. Backend dedups via Redis (`SET NX EX 60`); duplicate request returns the original 200/4xx response. The same uuid is logged in `admin_audit_log` / `settings_audit_log` so retries don't create duplicate audit rows.

2. **Submit button disabled during in-flight request.** Standard TanStack Query `isPending` gating + a `data-testid="settings-submit"` for Playwright to assert disabled state.

3. **Frontend never encrypts BYOK** (reaffirm v1.1 Pitfall 8 rule). Plaintext over HTTPS to the backend; backend is the only encrypt site.

4. **BYOK save serializes per `(tenantId, provider)` via Postgres `pg_advisory_xact_lock`.**
   ```sql
   SELECT pg_advisory_xact_lock(hashtext('byok:' || $1 || ':' || $2));  -- tenantId, provider
   ```
   Two simultaneous writes serialize; the second sees the first's commit and either no-ops (idempotency-key match) or proceeds against the updated state.

5. **Master-key rotation is decoupled from BYOK** — they share the `MasterKeyVault` / `ByokCredentialRepository` only at the chat-model-instantiation layer. Rotation event evicts cached ChatModels for the **master-key** provider; BYOK changes evict cached ChatModels for the **(tenant, provider)** pair. Distinct events, distinct eviction scopes.

6. **In-flight request safety: ChatModel instance is short-lived.** Per chat request the LLM gateway resolves the current ChatModel from cache (or creates fresh) and uses it for the whole request. A mid-request master-key rotation doesn't affect the in-flight request because the request already holds its ChatModel reference. The next request gets the new one.

7. **Race test in CI.** Spawn 5 concurrent BYOK saves + 1 master-key rotation; assert (a) final BYOK state matches the last save with an `Idempotency-Key` resolution, (b) exactly one audit row per distinct idempotency key, (c) no decryption errors observed in subsequent chat requests.

**Warning signs:**
- BYOK form doesn't disable submit during in-flight.
- No `Idempotency-Key` header on Settings mutations.
- No advisory lock on BYOK save.
- The same `MASTER_KEY_ROTATED` event eviction also drops per-tenant BYOK ChatModels.
- No race test covering BYOK + master-key rotation.

**Phase to address:** **Phase 9 / Settings page** (idempotency + submit disable + advisory lock) + **Phase 8 / sub-phase 8B (master-key rotation)** for the event-scoping decision. Race test lives in Phase 9 acceptance.

---

### Pitfall 13: Admin-curated `editorial_note` becomes a stored-XSS sink in the user-facing Settings dropdown

**What goes wrong:**
The catalog includes admin-supplied editorial notes per model ("Reasoning model — use for complex queries only"). These notes render in the user-facing Settings dropdown as a subtitle. An admin types HTML / `<script>` / event handlers in the note. The user's Settings dropdown executes the script.

Variants:
- Markdown rendering on the user side parses `[click](javascript:...)`.
- Admin paste of a "helpful template" containing prompt-injection-like content that, when the user reads it, manipulates the user's perception.
- Notes longer than UI accommodates push other UI elements off-screen / cover Save button (UI redress).

**Why it happens:**
- Admin-supplied content is "trusted" by reflex.
- Markdown features get added without auditing.
- Length limits are usually skipped.

**How to avoid:**

1. **Editorial notes are plain text, server-side sanitized.** Length cap 280 chars. Strip control characters, HTML, markdown special characters that render (`<`, `>`, `&`, `[`, `]`, `(`, `)` reformatted to escaped HTML entities). Stored sanitized; rendered as plain text content via React text children. No raw-HTML React escape hatches.

2. **Editorial notes display in a fixed-height container** in the Settings dropdown — overflow is ellipsis + tooltip; cannot push other UI off-screen.

3. **ArchUnit + frontend test: any rendering of admin-supplied content** (model `display_name`, `editorial_note`, `provider_metadata.description`) uses React text-children only — never the React raw-HTML escape hatch, never `<div innerHTML={...}>`, never markdown renderers that allow inline HTML.

4. **CSP `default-src 'self'`** + nonce-based inline script policy on user pages — even if a script slips through, browser refuses to execute.

5. **XSS test for editorial note injection.** Test set of hostile editorial notes (`<script>alert(1)</script>`, `<img src=x onerror=...>`, `javascript:alert(1)`, Markdown `[x](javascript:alert(1))`); render each in the Settings dropdown via Playwright; assert no `dialog`, no console errors, all content rendered as text.

**Warning signs:**
- Editorial note rendered via any React raw-HTML escape hatch or a markdown renderer.
- No length cap on editorial note.
- No sanitization on save.
- CSP allows `unsafe-inline` on user pages.
- No XSS test.

**Phase to address:** **Phase 8 / sub-phase 8D — Catalog editorial fields** (server sanitization) + **Phase 9 / Settings dropdown render** (frontend safe-rendering verification).

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Single `role` column on `user` instead of `admin_grant` table | Two-line Liquibase changeset | No revocation history; bootstrap-via-snapshot creates permanent backdoors; no granted_by audit; co-signed rotation impossible | **Never** — `admin_grant` is one extra changeset and trivially worth it |
| Reuse the `zm_session` cookie for admin authentication | One auth wiring, single OAuth flow | Admin role silently applies to user endpoints; CSRF + clickjacking surface doubled; can't distinguish admin-action from user-action in audit | **Never** — two cookies + two filter chains is the v1.2 baseline |
| Skip the `AdminContext` Scoped Value; rely on `@PreAuthorize` alone | Less infrastructure to write | Filter ordering bugs let admin authority leak into user-context endpoints; tenant isolation invariant becomes "trust me bro" | **Never** — Scoped Value is consistent with v1.0 tenant isolation pattern |
| Hard-delete catalog rows on Sync | Keeps the table small | Tenants on deprecated models see immediate breakage; no soft-rollback; FK violations on user save | **Never** — soft-delete with `deprecated_at` is the same code with one column |
| One-click Sync without diff preview | Faster admin workflow | Accidental sync loses curation in seconds; no audit-friendly preview; admin can't reason about "what will change" | **Never** — 3-step flow is essentially free once SyncDraft exists |
| `payload_json` exposed on admin failed-job page | Easy debugging | Body / prompt fragments leak via admin convenience; admin reads tenant content; privacy story silently false | **Never** — payload_summary view + explicit per-job endpoint with audit |
| Frontend caches BYOK / master key plaintext in TanStack Query | Trivial "Saved!" display | Browser DevTools snapshot, error-reporter capture, session-hijack escalation — same as v1.1 Pitfall 8 | **Never** — mask-only response contract |
| Skip k-anonymity threshold on spend aggregates | Sharper drill-downs | Single-tenant buckets reveal individual spend; tenant identity correlation across views | **Never** — threshold is one SQL clause |
| Mutate `admin_audit_log` via the same DB user the app uses | Single connection string | Admin can delete their own audit via raw SQL; DBA leak deletes traces; chain-hash useless if mutability allowed | **Never** — DB-grant scoping is one Liquibase changeset |
| Bump `OnlyOneGmailSendCallSiteTest` count from 1 to 2 for an "admin send" feature | One feature shipped | The "we have exactly one send path" trust property dies; every future PR can add a send site more easily | **Never** — admin send is forbidden by product decision |
| Pre-emptively cache `/models` Sync results as authoritative | Sync feels instant | Schema drift hides under cache; provider-side correctness issues never surface | **Never** — Sync is a 3-step explicit flow |
| Allow admin to view chat message contents for "support" | Faster bug triage | Privacy contract on user-typed content silently widened; UI promise "no admin can read your data" is silently false | **Defer to v1.3 with explicit tenant-grant flow** — never as a default admin capability |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| OpenAI `/v1/models` | Trusting model IDs as safe strings | Allow-list regex `^[a-zA-Z0-9._:/\-]{1,128}$`; reject anything else |
| OpenAI `/v1/models` | Treating the response as schema-stable | JSON Schema validation per provider; unknown fields warn but parse continues; missing required fail loud |
| OpenRouter `/v1/models` | Assuming response shape matches OpenAI | Separate `openrouter-models-v1.schema.json`; routing-specific fields (`pricing.prompt`, `pricing.completion`) require their own validation |
| Anthropic | Assuming `/v1/models` exists | No public `/models` endpoint; admin must enter model IDs manually; Sync button disabled for Anthropic |
| Google GenAI | Assuming model list is short and stable | Vertex / Gemini model list churns frequently; treat as live data, sync more often (cron-able) |
| DeepSeek | Treating as OpenAI-compatible without verification | DeepSeek's `/models` differs subtly; validate against a DeepSeek-specific schema |
| Spring Security 7 method security | `@PreAuthorize` SpEL with request parameters for tenant-scope decisions | Authority derived exclusively from filter-bound `AdminContext`; SpEL reads context, not request |
| Spring Security 7 filter chains | Single SecurityFilterChain for `/` + `/admin/**` | Two distinct chains via `securityMatcher`; distinct cookies; distinct timeout |
| Spring Modulith events | Emit `MASTER_KEY_ROTATED` synchronously inside the rotation transaction | Emit after-commit (`@ApplicationModuleListener`); rotation transaction commits first, eviction runs after |
| Liquibase YAML | INSERT admin grants or master keys via changesets | All bootstrap goes through env-var-driven runtime path; no Liquibase data seeds for secrets or grants |
| Postgres `admin_audit_log` | Grant `UPDATE` / `DELETE` to the app DB user | App user has `INSERT, SELECT` only; trigger blocks `UPDATE`/`DELETE` as last line; replica catches local tampering |
| Postgres triggers on `admin_audit_log` | Raise on UPDATE/DELETE only, allow TRUNCATE | Trigger covers UPDATE OR DELETE OR TRUNCATE (or revoke TRUNCATE privilege) |
| TanStack Query | Cache `catalog` and `settings` independently with no cross-invalidation | `onSuccess` of settings mutation invalidates both keys; catalog ETag-driven refetch |
| TanStack Query | Cache BYOK / master-key plaintext | Mask-only contract; even mask is non-cached if it changes per session |
| Spring Session Redis | Use the same session backing for admin and user sessions | Different cookie names + distinct session attribute namespaces; consider separate Redis logical DBs for clarity |
| Reverse proxy (nginx) | Log full response bodies for `/admin/*` | `/admin/*` location: `access_log off` for bodies; only status + size + URI logged |
| Reverse proxy | Set `X-Forwarded-For` without trusting it | Use Spring's `ForwardedHeaderFilter` with explicit trusted proxy IP allow-list |
| Provider HTTP client | Reuse the shared LLM-gateway HTTP client for `/models` Sync | Use a dedicated `AdminProbeHttpClient` with no logging proxy, no retry, no shared connection pool with tenant LLM traffic |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Admin queue-health endpoint runs `COUNT(*)` on outbox / processing_job | Worker throughput drops when admin refreshes the page; `SKIP LOCKED` poll latency rises | Pre-computed `queue_stats_snapshot` table updated every 30s by a scheduled job; admin reads the snapshot | At ~50k pending rows |
| Catalog cache TTL is the only invalidation | Stale Settings UI options after admin edit; user save fails with `MODEL_DEPRECATED_DURING_SAVE` confusion | Event-driven eviction (`CATALOG_UPDATED` → cache evict + ETag bump) | Always observable after first admin edit |
| `admin_audit_log` chain-hash recomputed on every read | Audit log view becomes slow once >100k rows | Cache chain validity per N rows; verify on a schedule, not per-read | At ~100k rows |
| Spend aggregate query scans entire `spend_ledger` per dashboard load | Dashboard load time grows linearly with spend rows | Materialized view / precomputed daily rollups; index on `(created_at, provider)` | At ~1M spend rows |
| Master-key cache eviction iterates all per-tenant ChatModel cache entries on rotation | Brief latency spike on rotation; OK for occasional event | Accept the cost; rotation is infrequent | Never problematic at v1.2 scale |
| Sync-from-/models fetches all model details synchronously inside the admin's HTTP request | Admin sees a 30s spinner on Sync; provider rate-limits | Sync is async — admin clicks Sync, gets a SyncDraft ID, polls or waits for SSE completion; UI shows progress | At >50 models or slow providers |
| Catalog `editorial_note` rendered to every Settings dropdown without server-side caching | Dropdown render latency grows with catalog size | Server caches the rendered Settings UI catalog payload; ETag-invalidated | At ~200 models per provider |
| Admin audit insertion blocks admin-action commits if external replica is slow | Admin actions stall during replica lag | Audit insert is local + async replication; replica lag is observed but doesn't block the local insert | Replica-dependent |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Admin role as a column on `user` instead of `admin_grant` table (Pitfall 1) | Permanent backdoors via snapshot restore; no revocation audit; no co-sign requirement on rotation | `admin_grant` schema with revocation columns + partial unique index |
| Bootstrap admin via Liquibase data seed (Pitfall 1) | Repo-checked-in admin grants leak; CI/staging snapshots become prod backdoors | Bootstrap via runtime env var only; guarded by `EXISTS (SELECT 1 FROM admin_grant)` |
| Master key returned in save response (Pitfall 2) | Browser-side capture via DevTools, error reporters, session hijack | Save response returns mask + metadata only |
| Master key in `application.yml` for dev (Pitfall 2) | Repo-checked-in key leaks via bad `.gitignore` edit | StubMasterKeyVault for dev; live keys only via runtime env var; ArchUnit + grep test fails build on `sk-` / `AIza` in YAML |
| Master-key Test-connection used as validation oracle (Pitfall 2) | Attacker who reaches the endpoint validates harvested keys | Edit-session-token required; rate-limited per admin; no provider error body echoed |
| Provider error body proxied to admin (Pitfall 2) | Provider error responses leak key fragments | `ProviderErrorTranslator` maps to enum codes; raw body never returned |
| Admin session cookie shared with user session (Pitfall 3) | Admin authority bleeds into user endpoints; doubled CSRF/clickjacking blast radius | Two distinct cookies + two filter chains + auto-logout on admin side |
| Admin "convenience" tenant body access (Pitfall 4) | Privacy contract silently broken; admin reads any tenant's mail; OAuth grant misused | ArchUnit ban on Gmail body methods from admin path; response body-ban filter; OAuth refresh-token access forbidden from admin path |
| Sync-from-/models accepts arbitrary model IDs (Pitfall 5) | Stored XSS / SQL injection via adversarial model ID strings | Allow-list regex; JSON Schema validation; admin diff preview |
| Hard-delete catalog rows on Sync (Pitfall 5) | Tenant FK violations; immediate breakage for users on deprecated models | Soft-delete with `deprecated_at`; tenant grace period |
| `admin_audit_log` mutable by app user (Pitfall 6) | Admin can erase their own audit; trust backstop fails | DB user has INSERT + SELECT only; append-only trigger as last line; external replication |
| Free-text `reason` field in admin audit unsanitized (Pitfall 6) | Audit becomes exfiltration channel | Length cap + content sanitization + separate `admin_audit_meta_log` for triggered-sanitization events |
| Catalog stale cache races with admin edit (Pitfall 7) | User saves a model the admin just deprecated; confusing error | Event-driven invalidation; same-transaction fresh-read validation on user save |
| Admin queue endpoint exposes `payload_json` (Pitfall 8) | Body / prompt fragments leak via failed-job inspection | `payload_summary` view; full payload behind audit-required endpoint |
| Global spend dashboard reveals single-tenant buckets (Pitfall 9) | Cross-tenant inference; admin learns individual tenant spend | k-anonymity threshold ≥5; pseudonymized identifiers; drill-down ticket-required |
| Admin "send on behalf of tenant" feature (Pitfall 10) | Single send call site invariant dies; trust posture regresses | Hard product decision: no admin send; ArchUnit reaffirms count=1 |
| Filter ordering: `@PreAuthorize` runs before `AdminContext` binding (Pitfall 11) | Admin authority + user tenant context combined silently | Custom filter ordered before `AuthorizationFilter`; SpEL reads AdminContext only |
| BYOK form double-submit + master-key rotation race (Pitfall 12) | Confusing audit; partial cache eviction; mid-flight key chaos | Idempotency-Key + advisory lock + scope-separated cache eviction |
| Stored XSS in catalog `editorial_note` (Pitfall 13) | Script execution in user Settings UI | Server sanitize on save; React text-only rendering; CSP nonce |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Admin and user pages look visually identical | Admin confuses contexts; accidental cross-tenant writes | Persistent red/yellow chrome bar on `/admin/*`; distinct admin layout |
| Master-key rotation has no "are you sure" + reason field | Accidental rotation breaks every tenant relying on the master key | Confirm modal with required reason text + 5-second cooldown + co-admin notification |
| Catalog Sync is a single button with no diff preview | Admin loses curation in one click | 3-step flow: Fetch → Diff → Apply; cancel at any step |
| Settings UI shows a model the admin just deprecated | User selects it, save fails with cryptic error | Frontend ETag-driven catalog refresh on focus; structured error code maps to "Refresh and pick another" |
| BYOK / master-key save form has no submit-disable during in-flight | Double-submit chaos; confusing audit rows | TanStack `isPending` disable + idempotency key |
| Editorial notes pushed off-screen by long admin text | Other UI elements obscured | Fixed height + ellipsis + tooltip |
| Spend dashboard shows "Total: $1,234.56" with 1 tenant | Single-tenant context inferable | k-anonymity bucketing; "Other (3 tenants)" rollups |
| Worker queue health view auto-refreshes every second | Worker throughput drops under admin load | Manual refresh + 30s pre-computed snapshot |
| Test-connection endpoint shows raw provider error | Provider error bodies (with key fragments) appear in admin browser | Translate to enum codes; friendly error string per code |
| Admin "view audit log" shows their own entries first | Admin self-checks before doing something untoward | Audit log accessible only via co-admin; own actions shown in a separate confirmations panel |
| "Revoke admin" UI doesn't require reason or co-admin | Quiet revocation hides intent | Reason required; co-admin click required (except in single-admin bootstrap) |
| Catalog model dropdown shows full provider description and editorial note inline | Cluttered Settings tab | Provider description in tooltip; editorial note as subtitle (truncated) |
| Settings save success surfaces immediately without confirming server-side state | User assumes save worked; race vs admin disable surfaces later as bug report | Optimistic UI rollback on 4xx; explicit "Saved at HH:MM" timestamp from server |

---

## "Looks Done But Isn't" Checklist

- [ ] **Admin foundation:** Bootstrap admin grant exists only via env var `ZEROMAIL_BOOTSTRAP_ADMIN_EMAIL`; second boot with the same env var logs WARN and does not re-grant; CI / staging do not set this var.
- [ ] **`admin_grant` schema:** `revoked_at` column exists; partial unique index on `(user_id, role) WHERE revoked_at IS NULL`; FK to `app_user` enforced.
- [ ] **AdminContext + TenantContext mutually exclusive:** Test asserts `TenantContext.currentOrThrow()` throws inside an admin context and vice versa; ArchUnit asserts no service reads both in the same call chain.
- [ ] **Two-cookie session split:** `/admin/*` uses `zm_admin_session`; `/` uses `zm_session`; Playwright test verifies they are independent (logging in to one does not authenticate the other).
- [ ] **Master-key sentinel leak test:** `sk-MASTER-SENTINEL-NEVER-LOG-99999` set as OpenAI master key; sentinel appears only in `master_key_credential.api_key_cipher` (encrypted) — verified against app logs, access logs, HTTP responses, Redis dumps, JFR, Playwright HAR, Sentry mock.
- [ ] **Master-key edit-session token + rate limit:** test-connection endpoint outside an edit session returns 403; >10 tests/hour returns 429; provider error bodies are not echoed in any response.
- [ ] **ChatModel cache eviction on master-key rotation:** Force rotation; assert every cached ChatModel for the provider is evicted; new chat requests instantiate fresh clients.
- [ ] **Admin path body-ban:** ArchUnit `AdminPathBodyBanTest` green; `AdminResponseBodyBanFilter` strips body-like fields >200 chars; admin endpoints do NOT call `GmailClient.getMessage` or other body-exposing methods; admin paths do NOT resolve tenant OAuth refresh tokens.
- [ ] **Catalog Sync 3-step flow:** Fetch creates a `SyncDraft`; diff preview shows added/removed/changed counts; Apply mutates inside a single tx; per-feature toggles + `editorial_note` preserved across sync.
- [ ] **Catalog schema validation:** Hostile `/models` response (malformed model ID, unknown field) handled correctly — model ID rejected with audit row; unknown field logged as WARN with field name.
- [ ] **Catalog soft-delete:** Deprecated models retain FK validity; tenant with deprecated model selection can still send messages; new selection in Settings filters out deprecated models.
- [ ] **`admin_audit_log` append-only:** Direct `DELETE FROM admin_audit_log` via psql raises trigger exception; app DB user has no `UPDATE` / `DELETE` grant; chain-hash verification job runs and passes.
- [ ] **`admin_audit_log` start/end pattern:** Every admin action produces a "started" row before action execution + "completed" or "failed" row after; failure to insert started row aborts action.
- [ ] **Spend dashboard k-anonymity:** Force test data with 4 tenants in a bucket; assert bucket is suppressed / merged into "Other"; 5+ tenants → bucket visible.
- [ ] **Spend dashboard drill-down requires ticket ID:** Drill click without `TICKET-\d+` reason rejected with structured error; rejection logged in `admin_audit_log`.
- [ ] **Single Gmail send call site preserved:** `OnlyOneGmailSendCallSiteTest` count = 1 unchanged in v1.2; grep gate threshold = 1 unchanged; no admin send path exists; ArchUnit forbids admin packages from invoking `AssistantSendExecutor`.
- [ ] **AdminEndpointFilter ordered before MethodSecurityInterceptor:** Test asserts admin endpoint with no admin session cookie returns 401 BEFORE controller body runs; ArchUnit asserts every `@AdminEndpoint` method's first statement is `AdminContext.currentOrThrow()`.
- [ ] **Settings idempotency:** BYOK save form double-submit produces one DB write + one audit row; `X-Idempotency-Key` header round-trips correctly.
- [ ] **Catalog cache + Settings race:** Concurrent admin disable + user save produces (a) one wins, (b) other gets `MODEL_DEPRECATED_DURING_SAVE` 409, (c) no orphan `assistant_settings` row.
- [ ] **Editorial note XSS resistance:** Playwright test loads 10 hostile editorial notes; renders each via the Settings dropdown; no console errors, no dialog, no script execution; content rendered as plain text.
- [ ] **Worker queue payload not exposed:** Admin queue page shows `payload_summary` only; full payload behind audit-gated endpoint with reason; `payload_json` does not appear in any admin response by default.
- [ ] **Two-admin co-sign for master-key rotation:** Single-admin bootstrap allows the rotation with `--allow-single-admin` flag; otherwise blocks until second admin confirms; integration test covers both paths.
- [ ] **Stale catalog ETag refresh:** Frontend Settings tab open; admin disables a model; within one network roundtrip the Settings UI updates the dropdown options.
- [ ] **CSP `frame-ancestors 'none'` on `/admin/*`:** Playwright loads admin page inside an iframe; browser refuses to render; CSP header verified.
- [ ] **Admin login banner persistent:** Visible red/yellow chrome on every `/admin/*` page; Playwright asserts presence on Tenant view, Catalog view, Master-key view, Queue view, Spend view.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Bootstrap admin email rotated but old admin still active in restored DB snapshot | MEDIUM | (1) `UPDATE admin_grant SET revoked_at = now(), revoked_reason = 'snapshot restore — stale grant'` for the old admin; (2) re-run bootstrap with current env var; (3) audit `admin_audit_log` for actions by the stale admin during the gap; (4) document snapshot-restore runbook to include grant audit |
| Master key leaked to logs / access logs / response bodies | HIGH | (1) Rotate the master key at the provider immediately (revoke + mint new); (2) update `master_key_credential` via `PUT /admin/master-keys/{provider}`; (3) ChatModel cache evicts via `MASTER_KEY_ROTATED` event; (4) deploy the missing sanitizer / ArchUnit rule / Sentry scrubber; (5) audit access during the leak window; (6) disclosure decision per provider's billing transparency |
| Admin reads a tenant body via a forgotten convenience endpoint | HIGH (trust) | (1) Remove the endpoint immediately; (2) deploy `AdminPathBodyBanTest` + `AdminResponseBodyBanFilter`; (3) audit which admins hit the endpoint via `admin_audit_log`; (4) tenant-side disclosure per privacy policy; (5) PROJECT.md policy entry confirming admin-no-body-access |
| Catalog Sync hard-deleted models still in use by tenants | MEDIUM | (1) Restore deleted rows from `catalog_audit_log` JSON snapshot of pre-sync state; (2) mark them `deprecated_at` instead of deleted; (3) deploy soft-delete + sync 3-step flow; (4) communicate to affected tenants |
| `admin_audit_log` row deleted by a DBA | HIGH | (1) Verify chain-hash via the integrity job; (2) restore deleted rows from external replica (Loki / S3); (3) compute the missing entries; (4) tighten DB grants (remove DBA `DELETE` privilege on the table); (5) post-mortem on how the DBA had the privilege |
| Spend dashboard showed individual-tenant spend before k-anonymity threshold deployed | MEDIUM | (1) Disable the dashboard immediately; (2) audit who viewed it via `admin_audit_log`; (3) deploy k-anonymity threshold; (4) re-enable with verified bucketing; (5) consider per-admin attestation that no individual-tenant data was extracted |
| Admin sent an email on behalf of a tenant via a "send on behalf" endpoint that should never have shipped | HIGH | (1) Remove the endpoint immediately; (2) audit `assistant_send_audit` (or the second audit table if it was added) for affected tenants; (3) reaffirm `OnlyOneGmailSendCallSiteTest` count = 1; (4) tenant notification per affected user; (5) post-mortem |
| Stored XSS in editorial note executed in user Settings | MEDIUM | (1) Sanitize all existing notes via a one-off migration; (2) deploy server-side sanitizer; (3) audit for affected sessions in access logs; (4) CSP hardening; (5) tenant-side disclosure if any session compromise observed |
| BYOK double-submit landed two writes; final state is the second submit not the first | LOW | (1) Audit logs reveal both writes; if the user wanted the second value (typical), no action; if the second was a retry that "should have been idempotent," deploy `X-Idempotency-Key` + advisory lock |
| Admin filter ordering bug let `@PreAuthorize` run before AdminContext bound | HIGH | (1) Patch the filter ordering; (2) deploy the ArchUnit "first statement = AdminContext.currentOrThrow()" rule; (3) audit `admin_audit_log` for any cross-context anomalies during the affected deploy window; (4) regression test ensuring the order is structurally enforced |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1. ROLE_ADMIN seed backdoor | **Phase 8 / sub-phase 8A — Admin foundation** | `admin_grant` schema + bootstrap env-var contract + ArchUnit "mutually exclusive contexts" rule green; bootstrap re-run is no-op test |
| 2. Master-key leak through key-management UI | **Phase 8 / sub-phase 8B — Master-key management** (after 8A) | Master-key sentinel leak test in CI; edit-session-token + rate-limit tests; `application*.yml` grep test for `sk-`/`AIza` |
| 3. ROLE_ADMIN session reused for user actions | **Phase 8 / sub-phase 8A — Admin foundation** | Playwright two-cookie test; audit `session_type` column present and populated |
| 4. Admin tenant view leaks body / completion | **Phase 8 / sub-phase 8C — Tenant read-only views** | `AdminPathBodyBanTest` + `AdminResponseBodyBanFilter` integration test; admin response sweep for `body`/`prompt`/`completion` fields |
| 5. Catalog Sync supply-chain trust | **Phase 8 / sub-phase 8D — Catalog management** | Hostile `/models` response test; SyncDraft 3-step flow integration test; per-provider schema validation |
| 6. Admin audit log used as exfiltration / editable | **Phase 8 / sub-phase 8A — Admin foundation** | DB-grant test (app user has only INSERT+SELECT); trigger-rejection test; chain-hash verification job; reason-sanitization test |
| 7. Stale catalog cache races with admin edit | **Phase 8 / sub-phase 8D (write contract) + Phase 9 (Settings save)** | Concurrent admin-disable + user-save race test; ETag-driven refresh Playwright test |
| 8. Worker queue health DoS / payload leak | **Phase 8 / sub-phase 8E — Worker queue health view** | Pre-computed snapshot freshness test; admin-no-payload-json response test; retry-via-enqueue thread-of-execution test |
| 9. Spend dashboard cross-tenant leakage | **Phase 8 / sub-phase 8F — Global spend dashboard** | k-anonymity threshold test; drill-down ticket-required test; spend_ledger schema audit |
| 10. Admin "send on behalf" regresses single send call site | **Phase 8 / sub-phase 8A — Admin foundation (reaffirm)** | `OnlyOneGmailSendCallSiteTest` count = 1 unchanged; ArchUnit "admin packages do not call `AssistantSendExecutor`" rule; PROJECT.md policy entry |
| 11. Filter ordering / `@PreAuthorize` before AdminContext | **Phase 8 / sub-phase 8A — Admin foundation** | Integration test: no-admin-cookie → 401 before controller; ArchUnit first-statement test |
| 12. BYOK double-submit + master-key rotation race | **Phase 8 / sub-phase 8B + Phase 9 — Settings page** | Concurrent BYOK + rotation race test; idempotency-key dedup test; advisory-lock test |
| 13. Editorial note XSS | **Phase 8 / sub-phase 8D (server sanitize) + Phase 9 (frontend render)** | Hostile editorial note Playwright suite (10 vectors); CSP header verification |

---

**Recommended phase ordering (forced by dependencies):**

1. **Phase 8A — Admin foundation: RBAC + AdminContext + Audit** (no admin endpoints visible yet)
   - `admin_grant` + `admin_audit_log` schema (append-only trigger + DB-grant scoping + chain-hash)
   - `AdminContext` Scoped Value, mutually exclusive with `TenantContext`
   - Two-cookie session split + `AdminEndpointFilter` ordered before `AuthorizationFilter`
   - Bootstrap-admin env-var contract; no Liquibase seed for grants
   - Reaffirm `OnlyOneGmailSendCallSiteTest` count = 1; ArchUnit "admin packages do not call AssistantSendExecutor"
   - ArchUnit "first statement is `AdminContext.currentOrThrow()`" enforcer
   - Custom Spring Security `PermissionEvaluator` reading `AdminContext`
   - Persistent admin-chrome layout for `/admin/*` pages

2. **Phase 8B — Master-key management** (depends on 8A)
   - `master_key_credential` schema; no Liquibase seed
   - AES-GCM encrypt/decrypt service (mirrors BYOK pattern, separate key class)
   - PUT/GET/Test endpoints with mask-only contract, edit-session-token, rate limit, `ProviderErrorTranslator`
   - `MASTER_KEY_ROTATED` Spring Modulith event + provider-scoped ChatModel cache eviction
   - Master-key sentinel leak test
   - Two-admin co-sign for rotation (with `--allow-single-admin` bootstrap escape)
   - `application*.yml` no-secret-pattern grep test

3. **Phase 8C — Tenant read-only views** (depends on 8A)
   - `AdminPathBodyBanTest` ArchUnit rule
   - `AdminResponseBodyBanFilter` Spring filter
   - Allow-listed admin tenant endpoints: triage history, metadata, rule list, spend, connection health
   - Admin tenant audit-log entries on every read with reason-required
   - PROJECT.md policy entry: "admins MUST NOT read tenant email content"

4. **Phase 8D — Catalog management + Sync flow** (depends on 8A and 8B for `/models` calls via master keys)
   - `catalog_model` + `catalog_audit_log` + `sync_draft` schema; soft-delete via `deprecated_at`
   - Per-provider JSON Schema validators (`openai-models-v1.schema.json`, `openrouter-models-v1.schema.json`, etc.)
   - Anthropic / manual-entry path (no Sync button)
   - 3-step Sync flow: Fetch → Diff → Apply
   - Model-ID regex allow-list
   - Editorial note server-side sanitizer
   - Per-feature toggle preservation across sync
   - Ping-completion test before model enablement
   - `CATALOG_UPDATED` Spring Modulith event + ETag-versioned response

5. **Phase 8E — Worker queue health view** (depends on 8A)
   - `queue_stats_snapshot` table + 30s scheduled refresh job
   - `payload_summary` view on `processing_job`
   - Admin retry endpoint via outbox enqueue (no direct worker invocation)
   - ArchUnit ban on DELETE-from-queue from admin paths
   - Tenant-ID pseudonymization in queue stats

6. **Phase 8F — Global spend dashboard** (depends on 8A; consume spend_ledger from v1.0)
   - k-anonymity threshold enforced server-side
   - Drill-down ticket-required endpoint
   - `SpendAggregateService` as the only admin-readable spend interface
   - ArchUnit ban on admin direct reads of `spend_ledger`
   - Spend ledger schema audit (no free-text columns)

7. **Phase 9 — Settings UI on curated catalog** (depends on 8B for BYOK contract, 8D for catalog read API)
   - 4 tabs via shadcn `<Tabs>` query-param-driven: Personalization, Behavior, Safety Net, AI Provider/Model
   - AI Provider/Model tab reads admin-curated catalog with ETag-driven refresh
   - BYOK save: idempotency-key + advisory lock + submit-disable + mask-only response
   - Same-transaction catalog freshness validation on settings save → `MODEL_DEPRECATED_DURING_SAVE` 409
   - Coordinated TanStack Query invalidation across `['settings']` and `['catalog']`
   - Editorial note safe-render (React text-only)
   - Carry the 19 deferred v1.1 reqs (SET-AI-01..04, SET-VOICE-01..06, SET-BEHV-01..05, SET-SAFE-01..04)
   - Playwright suites: stale-catalog refresh, BYOK double-submit, hostile editorial note XSS, race admin-disable + user-save

---

## Sources

### Zero Mail source code (read directly)
- `D:/study-materials-summer-2026/EXE202/zero-mail/.planning/PROJECT.md` — privacy carve-out, write-actions policy, decision log, v1.2 milestone scope
- `D:/study-materials-summer-2026/EXE202/zero-mail/CLAUDE.md` — constraints, conventions, hard "do not use" list, master-key + BYOK rules
- `D:/study-materials-summer-2026/EXE202/zero-mail/.planning/research/PITFALLS.md` (v1.1 prior baseline, this file replaces it) — confirmation state machine, BYOK regressions, JSONB schema drift, send-call-site invariant

### Inbox Zero source code (read directly)
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/app/(app)/admin/AdminUserControls.tsx` — admin user-impersonation / role-grant UI pattern (Zero Mail diverges: no impersonation in v1.2)
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/app/(app)/admin/AdminUpgradeUserForm.tsx` — admin self-service upgrade reference
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/app/api/admin/top-spenders/route.ts` — hashed-email top-spenders pattern (Zero Mail extends: k-anonymity threshold + per-session salt + drill-down ticket-required)

### Spring Security / Modulith / Java (verified)
- [Spring Security 7.0 Method Security Reference](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html) — `@PreAuthorize` ordering, custom `PermissionEvaluator`
- [Spring Security Filter Chains](https://docs.spring.io/spring-security/reference/servlet/architecture.html) — `securityMatcher` + multiple chains
- [Spring Modulith `@ApplicationModuleListener`](https://docs.spring.io/spring-modulith/reference/events.html) — after-commit event ordering for cache eviction
- [JEP 506: Scoped Values (Java 25)](https://openjdk.org/jeps/506) — final ScopedValue API, immutable per-thread bindings

### Postgres / Liquibase (verified)
- [Postgres trigger functions for append-only tables](https://www.postgresql.org/docs/current/sql-createtrigger.html) — `BEFORE UPDATE OR DELETE` rejection pattern
- [Postgres `pg_advisory_xact_lock`](https://www.postgresql.org/docs/current/explicit-locking.html#ADVISORY-LOCKS) — serialization without table locks for BYOK save
- [Liquibase YAML changeset reference](https://docs.liquibase.com/concepts/changelogs/yaml-format.html) — append-only changelog discipline; partial unique index via custom SQL

### Security / Privacy guidance (verified)
- [OWASP Top 10 2021 — A04 Insecure Design](https://owasp.org/Top10/A04_2021-Insecure_Design/) — design-time controls for admin authority + auditability
- [OWASP Top 10 2021 — A09 Security Logging & Monitoring Failures](https://owasp.org/Top10/A09_2021-Security_Logging_and_Monitoring_Failures/) — audit log integrity, tamper-evidence
- [CWE-522 — Insufficiently Protected Credentials](https://cwe.mitre.org/data/definitions/522.html) — master-key storage and exposure surface
- [CWE-532 — Insertion of Sensitive Information into Log File](https://cwe.mitre.org/data/definitions/532.html) — sentinel-leak test rationale
- [CWE-798 — Use of Hard-coded Credentials](https://cwe.mitre.org/data/definitions/798.html) — Liquibase seed / `application.yml` prohibitions
- [NIST SP 800-57 Part 1 Rev 5 — Recommendation for Key Management](https://csrc.nist.gov/pubs/sp/800/57/pt1/r5/final) — rotation cadence, distinct key purposes (master vs per-tenant BYOK)
- [PortSwigger — DOM-based XSS](https://portswigger.net/web-security/cross-site-scripting/dom-based) — editorial-note rendering vulnerability class
- [k-anonymity (Wikipedia, with reference to Sweeney 2002)](https://en.wikipedia.org/wiki/K-anonymity) — aggregation threshold rationale for spend dashboard

### Provider supply-chain references
- [OpenAI Models API reference](https://platform.openai.com/docs/api-reference/models) — `/v1/models` shape
- [OpenRouter Models API](https://openrouter.ai/docs/api-reference/list-available-models) — extended fields (pricing, routing metadata)
- [Anthropic API reference — no `/models` endpoint](https://docs.anthropic.com/en/api/getting-started) — manual catalog entry required
- [Google Gemini API — Models list](https://ai.google.dev/api/models) — fast-churning model list

### Inbox Zero "negative" reference (admin features deliberately not ported)
- Inbox Zero admin includes user-impersonation (`AdminUserControls.tsx`), Stripe-sync, and hashed-email top-spenders. Zero Mail v1.2 deliberately omits user-impersonation entirely; reuses the hashed-email approach with k-anonymity + per-session salt + drill-down ticket-required hardening; does not include Stripe sync (Zero Mail uses SePay/VietQR + Postgres ledger).

---

*Pitfalls research for: Zero Mail v1.2 admin console foundation + Settings UI on curated catalog, added on top of v1.0/v1.1 trust-first baseline*
*Researched: 2026-05-19*
