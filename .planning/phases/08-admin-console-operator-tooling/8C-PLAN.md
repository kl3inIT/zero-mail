---
phase: 08-admin-console-operator-tooling
plan: 8C
type: execute
wave: 2
depends_on:
  - 08-8A
files_modified:
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/AdminTenantAccess.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantListRow.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantDetailOverview.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantHealthSnapshot.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantBillingSnapshot.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantSpendSnapshot.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantActivitySnapshot.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantListPage.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantListQuery.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantInspectionService.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantPauseService.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantDisconnectService.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantDeletionService.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantOAuthRevocationGateway.java
  - backend/core/src/main/java/com/zeromail/core/admin/tenant/package-info.java
  - backend/api/src/main/java/com/zeromail/api/security/AdminResponseBodyBanFilter.java
  - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminTenantController.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantListResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantListRowResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantDetailResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantHealthResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantBillingResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantSpendResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantActivityResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantActionRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantDeletionPreviewResponse.java
  - backend/core/src/test/java/com/zeromail/core/admin/arch/AdminTenantOAuthGuardTest.java
  - backend/api/src/test/java/com/zeromail/api/security/AdminResponseBodyBanFilterTest.java
  - apps/admin/src/routes/tenants.tsx
  - apps/admin/src/routes/tenants-detail.tsx
  - apps/admin/src/features/tenants/tenants-api.ts
  - apps/admin/src/features/tenants/query-keys.ts
  - apps/admin/src/features/tenants/use-tenant-list.ts
  - apps/admin/src/features/tenants/use-tenant-detail.ts
  - apps/admin/src/features/tenants/use-tenant-pause.ts
  - apps/admin/src/features/tenants/use-tenant-disconnect.ts
  - apps/admin/src/features/tenants/use-tenant-delete.ts
  - apps/admin/e2e/tenants.spec.ts

autonomous: false
requirements:
  - OPS-TENANT-01
  - OPS-TENANT-02
  - OPS-TENANT-03
  - OPS-TENANT-04
  - OPS-TENANT-05

must_haves:
  truths:
    - "Operator can browse `/tenants` paginated list showing tenantId, creation date, connected Gmail email, status, 7-day k-anonymized spend bucket (no exact figure)."
    - "Operator can open `/tenants/:tenantId` 5-tab detail (Overview, Health, Billing, Spend, Activity) via shadcn `<Tabs>` driven by `?tab=` query param."
    - "Each tab visit writes exactly one `admin_read_event` row (via AdminTenantAccess.readOnly) — 5 max per session."
    - "No tab renders email body, chat content, prompts/completions, or session details — only metadata/counts/timestamps."
    - "Chat-session inspection: shows count, last activity, model selection only; `Show details` button is disabled with tooltip referring to v1.3+ tenant-bound support ticket grant."
    - "Operator can pause / disconnect / delete a tenant with `<ConfirmTwiceDialog>` requiring reason 8-500 chars + step-2 typed token (`pause` literal / tenant email / tenant email respectively)."
    - "Delete action shows preview counts (gmail_connection rows, chat sessions, rules, audit rows) before final confirm."
    - "Disconnect Gmail revokes the OAuth token via a tenant-OAuth-revocation gateway that takes only a tenantId — admin code never reads token bytes."
    - "`AdminResponseBodyBanFilter` rejects any `/api/admin/**` response containing a JSON string field whose key matches `body|bodyHtml|snippet|payload|prompt|completion|content` AND value length >200; returns HTTP 500 + writes `ADMIN_RESPONSE_BODY_BAN_TRIPPED` audit row."
    - "`AdminTenantAccess.readOnly(tenantId, supplier)` is the only legitimate cross-tenant read path — enters TenantContext temporarily after writing `admin_read_event`; ArchUnit guards forbid all other admin code from touching TenantContext."
    - "ArchUnit `AdminTenantOAuthGuardTest` forbids `..controllers.admin..` and `..core.admin..` from injecting `GmailConnectionRepository`, `GmailOAuthTokenService`, or any class exposing decrypted tokens — disconnect flows via TenantOAuthRevocationGateway only."
    - "AdminPathBodyBanTest now tightens — production OPS-TENANT projections shipped here MUST pass field-name + accessor regex."
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/AdminTenantAccess.java"
      provides: "Only legitimate cross-tenant read path: writes admin_read_event then ScopedValue.where(TenantContext.TENANT, tenantId).call(supplier)."
    - path: "backend/api/src/main/java/com/zeromail/api/security/AdminResponseBodyBanFilter.java"
      provides: "OncePerRequestFilter on /api/admin/** scanning JSON output for forbidden key + value-length combo; replaces body + audits on trip."
    - path: "backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantDeletionService.java"
      provides: "Cascade delete across all tenant tables with preview (counts before commit); same-tx audit row TENANT_DELETED."
    - path: "backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantOAuthRevocationGateway.java"
      provides: "Takes tenantId only; calls existing OAuth revocation path without exposing decrypted token bytes to admin caller."
  key_links:
    - from: "backend/api/.../AdminTenantController#getDetail"
      to: "AdminTenantAccess.readOnly"
      via: "supplier closure invoking TenantInspectionService"
      pattern: "AdminTenantAccess\\.readOnly"
    - from: "backend/api/.../AdminResponseBodyBanFilter"
      to: "ContentCachingResponseWrapper + Jackson 3 JsonParser streaming scan"
      via: "doFilterInternal post-chain"
      pattern: "ContentCachingResponseWrapper|JsonFactory"
    - from: "apps/admin/src/routes/tenants-detail.tsx"
      to: "?tab=health|billing|spend|activity"
      via: "useSearchParams + shadcn <Tabs value=...>"
      pattern: "useSearchParams"
---

<objective>
Deliver read-only tenant inspection: paginated `/tenants` list, 5-tab `/tenants/:id` detail (Overview/Health/Billing/Spend/Activity), all metadata-only; destructive actions (pause/disconnect/delete) with `<ConfirmTwiceDialog>` + reason + preview; `AdminTenantAccess.readOnly` enforcing audit-before-read; `AdminResponseBodyBanFilter` failsafe; `TenantOAuthRevocationGateway` so admin never holds token bytes.

Purpose: This is the most privacy-sensitive admin path. The body-ban filter, ArchUnit field scan, and AdminTenantAccess audit gate together make leak vectors syntactically impossible at multiple layers.

Output: Operator can inspect tenant health + pause/disconnect/delete a tenant with confirm-twice + reason; every read writes an admin_read_event; no email body / chat content / prompt-completion ever touches admin DOM.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@CONVENTIONS.md
@TESTING.md
@.planning/phases/08-admin-console-operator-tooling/08-SPEC.md
@.planning/phases/08-admin-console-operator-tooling/08-RESEARCH.md
@.planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md
@.planning/phases/08-admin-console-operator-tooling/08-UI-SPEC.md
@.planning/phases/08-admin-console-operator-tooling/08-PROTOTYPE.html
@.planning/phases/08-admin-console-operator-tooling/08-8A-SUMMARY.md
@backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java
@backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java
@backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java
@backend/core/src/main/java/com/zeromail/core/gmail/
@backend/core/src/test/java/com/zeromail/core/draft/DraftPathArchUnitTest.java
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 8C-01: AdminTenantAccess + tenant projection records + TenantInspectionService + AdminPathBodyBan tightening + AdminTenantOAuthGuardTest</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/AdminTenantAccess.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantListRow.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantListPage.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantListQuery.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantDetailOverview.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantHealthSnapshot.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantBillingSnapshot.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantSpendSnapshot.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantActivitySnapshot.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantInspectionService.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/package-info.java,
    backend/core/src/test/java/com/zeromail/core/admin/arch/AdminPathBodyBanTest.java,
    backend/core/src/test/java/com/zeromail/core/admin/arch/AdminTenantOAuthGuardTest.java,
    backend/core/src/test/java/com/zeromail/core/admin/arch/AdminContextMutexTest.java
  </files>
  <read_first>
    backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java (lines 97-134 — ScopedValue.where(TenantContext.TENANT).run pattern),
    backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogRow.java (record + Objects.requireNonNull constructor),
    backend/core/src/test/java/com/zeromail/core/draft/DraftPathArchUnitTest.java (lines 77-89 — dependOnClassesThat package ban),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C11, §C17,
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §OPS-TENANT-01/02/05 + §ARCH-08/09,
    .planning/phases/08-admin-console-operator-tooling/08-CONTEXT.md §D-20 (chat-session metadata only)
  </read_first>
  <behavior>
    - `AdminTenantAccess.readOnly(UUID tenantId, Supplier<T> supplier) -> T`:
      1. AdminContext.currentOrThrow() — assert admin scope active (throws if not).
      2. adminAuditWriter.writeReadEvent(admin, "TENANT_INSPECTION", tenantId) — writes admin_read_event row.
      3. `ScopedValue.where(TenantContext.TENANT, tenantId.toString()).call(supplier::get)` — temporarily enters TenantContext.
      4. After return, ScopedValue.where scope exits and AdminContext is re-bound by the surrounding admin chain filter.
    - `AdminTenantAccess` class is the ONLY whitelisted caller of `ScopedValue.where(TenantContext.TENANT, ...)` from `core.admin.*` — ArchUnit AdminContextMutexTest updated to whitelist this class explicitly.
    - Projection records (all `record` per CONVENTIONS §3):
      - `TenantListRow(UUID tenantId, Instant createdAt, String gmailAccountEmail, String status, String spendBucket7d)` — `spendBucket7d` is enum label `LOW|MEDIUM|HIGH` (k-anonymized; not exact figure).
      - `TenantDetailOverview(UUID tenantId, Instant createdAt, String gmailAccountEmail, String status, Instant lastActivityAt, int rulesCount)`.
      - `TenantHealthSnapshot(String tokenRefreshStatus, Instant lastTokenRefreshAt, String watchStatus, Instant lastPubSubPushAt, int pubsubBacklogCount)`.
      - `TenantBillingSnapshot(int creditsBalance, String plan, Instant lastTopUpAt)`.
      - `TenantSpendSnapshot(int last7dCallCount, int last30dCallCount, String spendBucket7d, String spendBucket30d, Map<String,Integer> perFeatureCallCount)`.
      - `TenantActivitySnapshot(int last30dRuleFireCount, int chatSessionCount, Instant lastChatSessionAt, String lastChatModelSelection)` — explicitly NO message content fields.
    - All projection record fields are scalars/enums/counts/timestamps. NO String field carries email body, chat content, prompt, completion, snippet, payload, or any field name matching the body-ban regex.
    - `TenantInspectionService` methods (callable only inside `AdminTenantAccess.readOnly` closure):
      - `listTenants(TenantListQuery) -> TenantListPage` — joins `tenants`, `gmail_connection`, aggregated `llm_call_audit` (sums bucketed); paginated.
      - `getOverview(tenantId) -> TenantDetailOverview`.
      - `getHealth(tenantId) -> TenantHealthSnapshot`.
      - `getBilling(tenantId) -> TenantBillingSnapshot`.
      - `getSpend(tenantId) -> TenantSpendSnapshot`.
      - `getActivity(tenantId) -> TenantActivitySnapshot`.
    - AdminPathBodyBanTest tightens — now scans the actual projection record + DTO files shipped here and asserts ZERO field/getter matches forbidden regex.
    - `AdminTenantOAuthGuardTest` ArchUnit: classes in `..controllers.admin..` and `..core.admin..` (except TenantOAuthRevocationGateway in 8C-02) cannot `@Autowired` or have constructor param typed to `GmailConnectionRepository`, `GmailOAuthTokenService`, or any class exposing decrypted OAuth token bytes (look up exact class names via `mcp__jetbrains__search_symbol` + read source under `core.gmail.persistence.crypto`).
    - `AdminContextMutexTest` updated: whitelist class `core.admin.tenant.usecases.AdminTenantAccess` from the rule "admin packages cannot reference TenantContext".
  </behavior>
  <action>
    Implement AdminTenantAccess per PATTERNS §C11 excerpt. The readOnly closure pattern lets the mutex hold OUTSIDE the closure while TenantContext is bound INSIDE — ArchUnit must allow exactly this single call site; whitelist by full class FQN. Projection records: all bucket strings use enum-shaped values (`LOW`/`MEDIUM`/`HIGH`) NOT raw amount fields — k-anonymity (per OPS-TENANT-01 acceptance). `lastChatModelSelection` is the model ID string (e.g. `anthropic/claude-4.7-opus`); it is a model identifier not content (allowed). `TenantActivitySnapshot.chatSessionCount: int` — NEVER `chatSessions: List<...>`. Per CONVENTIONS §3 record validation: `Objects.requireNonNull(...)` in compact constructors for non-null fields. AdminTenantOAuthGuardTest uses `noClasses().that().resideInAnyPackage("..controllers.admin..","..core.admin..").and().areNotAssignableTo(TenantOAuthRevocationGateway.class).should().dependOnClassesThat().haveSimpleNameEndingWith("OAuthTokenService").orHaveSimpleName("GmailConnectionRepository").allowEmptyShould(true)`. AdminPathBodyBanTest excerpt from 8A-01 now exercises the real OPS-TENANT projections — must be green; if any field name accidentally matches (e.g. snake_case JSON serialization), rename or remap with @JsonProperty.
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "com.zeromail.core.admin.tenant.*" --tests "com.zeromail.core.admin.arch.AdminPathBodyBanTest" --tests "com.zeromail.core.admin.arch.AdminTenantOAuthGuardTest" --tests "com.zeromail.core.admin.arch.AdminContextMutexTest"</automated>
  </verify>
  <done>
    AdminTenantAccess writes admin_read_event before entering tenant scope; projection records contain only metadata; ArchUnit gates all green; mutex whitelist confirmed; TenantInspectionService methods return correctly typed records with no body fields.
  </done>
  <acceptance_criteria>
    - Unit test: AdminTenantAccess.readOnly(tenantId, () -> TenantContext.currentTenantUuid()) returns the passed tenantId; one admin_read_event row inserted.
    - AdminTenantAccess.readOnly throws if AdminContext not bound (no admin scope).
    - All 6 projection record classes have zero fields whose name matches `(?i).*(body|bodyHtml|snippet|payload|prompt|completion|content).*` (grep verification: `grep -rE 'String\s+(body|bodyHtml|snippet|payload|prompt|completion|content)' backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/ | wc -l` == 0).
    - AdminPathBodyBanTest green over production OPS-TENANT projections (allowEmptyShould now meaningful).
    - AdminTenantOAuthGuardTest: fixture admin service injecting `GmailConnectionRepository` makes test red; removing the inject makes it green.
    - AdminContextMutexTest: ArchUnit whitelist allows AdminTenantAccess to call ScopedValue.where(TenantContext.TENANT) while still rejecting other admin classes from doing so.
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 8C-02: AdminResponseBodyBanFilter + TenantOAuthRevocationGateway + TenantPauseService + TenantDisconnectService + TenantDeletionService (preview + cascade)</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/security/AdminResponseBodyBanFilter.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantOAuthRevocationGateway.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantPauseService.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantDisconnectService.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantDeletionService.java,
    backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantDeletionPreview.java,
    backend/api/src/test/java/com/zeromail/api/security/AdminResponseBodyBanFilterTest.java
  </files>
  <read_first>
    backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java (lines 1-60 — OncePerRequestFilter shape),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C12,
    .planning/phases/08-admin-console-operator-tooling/08-RESEARCH.md §Pattern 5 (Jackson 3 streaming scan) + §Pitfall 5 (NPM Host forwarding),
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §OPS-TENANT-03/04/05,
    backend/core/src/main/java/com/zeromail/core/gmail/ (existing OAuth revocation paths — find tokenRevocation service)
  </read_first>
  <behavior>
    - `AdminResponseBodyBanFilter extends OncePerRequestFilter`:
      1. Match only `/api/admin/**` URI; pass through otherwise.
      2. Wrap response in `ContentCachingResponseWrapper`.
      3. After `chain.doFilter`, read cached bytes; if Content-Type is JSON: use Jackson 3 `JsonFactory.createParser(byte[])` streaming walk (NOT `ObjectMapper.readTree` — that would buffer & may transform).
      4. For each `JsonToken.FIELD_NAME`, if key matches regex `(?i)(body|bodyHtml|snippet|payload|prompt|completion|content)` AND next token is VALUE_STRING with length >200 → tripped.
      5. On trip: resetBuffer, setStatus(500), write `{"code":"error.admin.body_ban","auditId":"{id}"}`, call AdminAuditWriter.append(ADMIN_RESPONSE_BODY_BAN_TRIPPED, ...) — but writer requires AdminContext; if scope already exited (filter runs after chain), open a dedicated AdminContext bind via a `ServiceAccountAdmin` system principal for this audit insert OR use a dedicated `AdminAuditWriter.appendAsSystem(...)` overload that does not require AdminContext for the system-failsafe path.
      6. Always call `wrapper.copyBodyToResponse()` so client sees either the original (clean) or replaced (tripped) body.
    - `TenantOAuthRevocationGateway.revoke(UUID tenantId)`: takes ONLY tenantId; internally enters tenant scope; calls existing Gmail OAuth revocation service (look up via mcp__jetbrains__search_symbol "GmailOAuthTokenService" or similar in `core.gmail`); admin caller never touches decrypted tokens. This is the only `..core.admin..` class allowed to depend on Gmail OAuth services (whitelisted in AdminTenantOAuthGuardTest).
    - `TenantPauseService.pause(UUID tenantId, String reason)`:
      1. AdminContext.currentOrThrow().
      2. AdminTenantAccess.readOnly is NOT used here — pause is a WRITE action, executed under @Transactional via direct tenant-scoped invocation (TenantPauseService internally enters TenantContext for the duration of the write).
      3. UPDATE `tenants SET status='PAUSED'`; suspend Pub/Sub consumption (stop watch refresh).
      4. Same-tx audit row TENANT_PAUSED with before/after status JSON + reason.
    - `TenantDisconnectService.disconnect(UUID tenantId, String reason)`:
      1. AdminContext.currentOrThrow().
      2. Call TenantOAuthRevocationGateway.revoke(tenantId).
      3. UPDATE `tenants SET status='DISCONNECTED'` + delete Gmail watch row.
      4. Same-tx audit row TENANT_DISCONNECTED.
    - `TenantDeletionService.preview(UUID tenantId) -> TenantDeletionPreview(int gmailConnections, int chatSessions, int rules, int triageAudits, int chatMessages, int byokCredentials)`: counts only, no content.
    - `TenantDeletionService.delete(UUID tenantId, String reason)`:
      1. AdminContext.currentOrThrow().
      2. Cascade delete in transactional order: byok_credential → chat_message → chat → triage_audit → assistant_send_audit → rules → assistant_settings → outbox/processing_job (for this tenant) → gmail_connection → tenants row.
      3. Call TenantOAuthRevocationGateway.revoke(tenantId) before final tenant row delete.
      4. Same-tx audit row TENANT_DELETED with `before_state_json={tenant_id, gmail_account_email, status, created_at}` + reason. After tenant row deletes, only audit row remains (preserves forensic trail).
    - AdminResponseBodyBanFilterTest: integration test using MockMvc; fixture controller returns response with `{"content":"<201-char string>"}` → expect HTTP 500 + body `{"code":"error.admin.body_ban"}` + 1 audit row ADMIN_RESPONSE_BODY_BAN_TRIPPED. Legitimate short metadata (`{"content":"OK"}`) passes through unchanged.
  </behavior>
  <action>
    Implement filter per PATTERNS §C12. Use Jackson 3 streaming API; if Jackson 3 namespace `tools.jackson.core.JsonFactory` is correct (verify via Context7 `/FasterXML/jackson` for Spring Boot 4 Jackson 3.x — Boot 4 ships Jackson 3 per CLAUDE.md but annotations remain `com.fasterxml.jackson.annotation.*`; core classes may be in `tools.jackson.*` per CLAUDE.md note). Filter ordering: register `addFilterAfter(adminResponseBodyBanFilter, AuthorizationFilter.class)` on admin chain in SecurityConfig (extend 8A-04's adminChain bean). `appendAsSystem` overload in AdminAuditWriter (added here as backward-compatible extension): inserts row with `actor_user_id=ZERO_UUID`, `actor_email="<system>"`, used only by the body-ban filter — document and constrain via private package-protected method. TenantOAuthRevocationGateway is the only class in `core.admin.tenant.usecases` that can import a Gmail OAuth service; class-level `@SuppressWarnings("admin-gmail-oauth-whitelist")` paired with ArchUnit whitelist by FQN. TenantDeletionService cascade order matches FK direction (children first; OAuth revoke before deleting `gmail_connection` since OAuth call needs the row). Wrap entire delete in `@Transactional(propagation=REQUIRED, isolation=READ_COMMITTED)`; if OAuth revoke fails, abort entire deletion (per OPS-TENANT-03 acceptance "disconnect revokes Gmail token verified by next Pub/Sub push fails with 401").
  </action>
  <verify>
    <automated>./gradlew :backend:api:test --tests "com.zeromail.api.security.AdminResponseBodyBanFilterTest" && ./gradlew :backend:core:test --tests "com.zeromail.core.admin.tenant.usecases.*"</automated>
  </verify>
  <done>
    Filter trips on >200-char forbidden field; legitimate metadata passes; OAuth revoke runs without admin holding token bytes; pause/disconnect/delete write audit + flip status + cascade; preview counts accurate.
  </done>
  <acceptance_criteria>
    - Fixture controller `@GetMapping("/api/admin/test-leak") String leak() { return "{\\"content\\":\\""+"x".repeat(250)+"\\"}"; }` → MockMvc returns 500 + `error.admin.body_ban` + 1 ADMIN_RESPONSE_BODY_BAN_TRIPPED row inserted.
    - Fixture controller returning `{"summary":"short"}` (200-char limit not exceeded) → passes through unchanged with 200.
    - `TenantPauseService.pause(tenantId, "user requested")` writes 1 TENANT_PAUSED row + flips `tenants.status` to PAUSED; subsequent Pub/Sub push for this tenant is dropped (verified by counter).
    - `TenantDisconnectService.disconnect(tenantId, "compromised")` writes TENANT_DISCONNECTED + calls TenantOAuthRevocationGateway exactly once + admin code never reads token bytes (verified by code review + AdminTenantOAuthGuardTest staying green).
    - `TenantDeletionService.preview(tenantId)` returns accurate counts against fixture: 1 gmail_connection, 3 chat sessions, 5 rules.
    - `TenantDeletionService.delete(tenantId, "user requested deletion")` removes all rows except 1 admin_audit_event row that survives with before_state_json containing tenant metadata.
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 8C-03: AdminTenantController + DTOs + apps/admin /tenants list + /tenants/:id 5-tab detail page</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminTenantController.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantListResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantListRowResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantDetailResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantHealthResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantBillingResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantSpendResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantActivityResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantActionRequest.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/TenantDeletionPreviewResponse.java,
    apps/admin/src/routes/tenants.tsx,
    apps/admin/src/routes/tenants-detail.tsx,
    apps/admin/src/features/tenants/tenants-api.ts,
    apps/admin/src/features/tenants/query-keys.ts,
    apps/admin/src/features/tenants/use-tenant-list.ts,
    apps/admin/src/features/tenants/use-tenant-detail.ts,
    apps/admin/src/features/tenants/use-tenant-pause.ts,
    apps/admin/src/features/tenants/use-tenant-disconnect.ts,
    apps/admin/src/features/tenants/use-tenant-delete.ts,
    apps/admin/e2e/tenants.spec.ts
  </files>
  <read_first>
    backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java (page DTO + filter request shape),
    apps/web/components/ui/tabs.tsx + table.tsx + badge.tsx + alert-dialog.tsx + tooltip.tsx (primitives copied in 8A),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C14, §C16,
    .planning/phases/08-admin-console-operator-tooling/08-UI-SPEC.md §`/tenants` + §`/tenants/:tenantId` + §Destructive action confirmations,
    .planning/phases/08-admin-console-operator-tooling/08-PROTOTYPE.html (visual reference for tenant pages),
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §OPS-TENANT-01/02/03/04,
    .planning/phases/08-admin-console-operator-tooling/08-CONTEXT.md §D-11 (tab routing via single React Router route + shadcn Tabs + ?tab=)
  </read_first>
  <behavior>
    - `AdminTenantController @PreAuthorize("hasRole('ADMIN')") @RequestMapping("/api/admin/tenants")`:
      - GET `/?status=&from=&to=&cursor=&limit=` → TenantListResponse. Wraps body in AdminTenantAccess.readOnly per-tenant for cross-tenant aggregate is NOT applicable here (list is cross-tenant by design); use a dedicated `AdminTenantAccess.crossTenantList(supplier)` that writes a single admin_read_event with target_kind="TENANT_LIST" and does NOT bind TenantContext (read-only aggregate via service-account JDBC).
      - GET `/{tenantId}/overview` → TenantDetailResponse via AdminTenantAccess.readOnly.
      - GET `/{tenantId}/health` → TenantHealthResponse via AdminTenantAccess.readOnly.
      - GET `/{tenantId}/billing` → TenantBillingResponse via AdminTenantAccess.readOnly.
      - GET `/{tenantId}/spend` → TenantSpendResponse via AdminTenantAccess.readOnly.
      - GET `/{tenantId}/activity` → TenantActivityResponse via AdminTenantAccess.readOnly.
      - POST `/{tenantId}/pause` body `TenantActionRequest{reason}` → 204; calls TenantPauseService.pause.
      - POST `/{tenantId}/disconnect` body `TenantActionRequest{reason}` → 204; calls TenantDisconnectService.disconnect.
      - GET `/{tenantId}/deletion-preview` → TenantDeletionPreviewResponse; writes admin_read_event with target_kind="TENANT_DELETION_PREVIEW".
      - POST `/{tenantId}/delete` body `TenantActionRequest{reason, confirmEmail}` → 204; verify confirmEmail matches tenant's gmail_account_email; call TenantDeletionService.delete.
    - All DTOs are records with @Schema annotations per CONVENTIONS §3; no field name matches body-ban regex (verified by AdminPathBodyBanTest).
    - `TenantActionRequest`: `@NotBlank @Size(min=8,max=500) @NoSentinelLeak String reason; @Email String confirmEmail` (confirmEmail optional except for delete).
    - apps/admin `/tenants` route: paginated table (id, created date, gmail email, status badge, 7d spend bucket badge); row click → `/tenants/{id}?tab=overview`. Filter bar: status dropdown + date range popover.
    - `/tenants/:tenantId` route: shadcn `<Tabs>` value driven by useSearchParams `?tab=`; default `overview`. Tabs: Overview, Health, Billing, Spend, Activity. Each tab has its own `useQuery` keyed by `["admin","tenants",id,"overview"|"health"|...]` — lazy fetch on tab change.
    - Overview tab shows tenant metadata + 3 destructive action buttons in a card footer: `Pause`, `Disconnect Gmail`, `Delete tenant` — each opens `<ConfirmTwiceDialog>` with step-2 token (`pause` literal / tenant email / tenant email).
    - Activity tab shows chat session count + last activity + last model selection + a disabled `Show details` button with tooltip `Session detail inspection is deferred to v1.3+ via tenant-bound support ticket grant.` (per D-20).
    - Delete flow first calls `GET /deletion-preview` → renders preview counts in dialog body, then proceeds to confirm-twice + reason + typed email.
    - Playwright `tenants.spec.ts`: login → /tenants renders table → click row → /tenants/abc?tab=overview → click Health tab → URL becomes ?tab=health → admin_read_event count increments (verified via mock backend); click Pause → ConfirmTwiceDialog with typed-token step `pause` → submit → toast with audit-row link.
  </behavior>
  <action>
    Implement per PATTERNS §C14/§C16 excerpts. Controllers wrap every cross-tenant read in AdminTenantAccess.readOnly closure. List endpoint uses dedicated `crossTenantList` helper that writes a single admin_read_event with target_kind=TENANT_LIST (target_id null) and queries via direct JDBC bypassing tenant binding — this is acceptable since the list aggregates platform-scope metadata only (no per-tenant secrets). Frontend uses TanStack Query stale time 30s for tabs to avoid redundant reads on rapid tab toggling, but each "deliberate" tab change still produces a fresh fetch (use `refetchOnMount: 'always'` per query key). `<Tabs value={tab} onValueChange={setTab}>` where `setTab` updates URL via `setSearchParams({tab})`. URL is shareable; navigating back/forward preserves tab state. ConfirmTwiceDialog usage per UI-SPEC §Destructive action confirmations table line 196-202: pause=`pause` literal, disconnect=tenant email, delete=tenant email + show preview counts as the consequence list. Activity tab `Show details` button: `<Button disabled><Tooltip>...</Tooltip></Button>` with copy from UI-SPEC line 268. Per CONVENTIONS §8 frontend: feature folder owns `tenants-api.ts` deriving types from admin-schema.d.ts paths; no hand-written mirror DTOs; use typed `api.GET("/api/admin/tenants/{tenantId}/health",{params:{path:{tenantId}}})`.
  </action>
  <verify>
    <automated>./gradlew :backend:api:test --tests "com.zeromail.api.controllers.admin.AdminTenantController*" && pnpm --filter @zeromail/admin test:unit && pnpm --filter @zeromail/admin e2e -- --grep "tenants"</automated>
  </verify>
  <done>
    All endpoints respond per contract under mocked admin session; 5 tabs render in apps/admin with ?tab= URL; each tab visit writes admin_read_event; destructive actions use ConfirmTwiceDialog with correct step-2 tokens; deletion preview displays cascade counts; AdminResponseBodyBanFilter never trips on production responses; Playwright e2e green.
  </done>
  <acceptance_criteria>
    - `GET /api/admin/tenants/` returns paginated list with TenantListRowResponse rows; 1 admin_read_event row inserted (target_kind=TENANT_LIST).
    - `GET /api/admin/tenants/{id}/health` returns TenantHealthResponse + writes 1 admin_read_event row (target_kind=TENANT_INSPECTION, target_id=tenantId).
    - Same `GET /api/admin/tenants/{id}/health` repeated within 60s within same session writes 0 additional rows (debounced per filter combo) — note: SPEC OPS-TENANT-02 says "opening any tab writes one admin_read_event row" so debounce ONLY applies to identical tab; switching tabs writes new row.
    - `POST /api/admin/tenants/{id}/pause` body `{reason:"short"}` (7 chars) returns 400 `error.admin.reason_too_short`; with `{reason:"compromised account"}` returns 204.
    - `POST /api/admin/tenants/{id}/delete` body `{reason:"...", confirmEmail:"wrong@example.com"}` returns 400 `error.admin.confirm_email_mismatch`.
    - Playwright `tenants.spec.ts`: tab navigation Overview→Health→Billing→Spend→Activity writes 5 admin_read_event rows; URL `?tab=` updates each time; clicking Pause opens dialog with `Type "pause" to confirm`.
    - Activity tab `Show details` button is `disabled` and tooltip text `Session detail inspection is deferred to v1.3+ via tenant-bound support ticket grant.` is visible on hover.
    - AdminResponseBodyBanFilter NOT tripped on any production OPS-TENANT response (verified by zero ADMIN_RESPONSE_BODY_BAN_TRIPPED rows after running full e2e).
  </acceptance_criteria>
</task>

</tasks>

<threat_model>

## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Admin browser → /api/admin/tenants/** | Read-only metadata; destructive actions require confirm-twice + reason |
| backend/api → AdminTenantAccess.readOnly | Audit-before-bind invariant; ScopedValue.where bounds tenant scope to closure |
| backend/api → TenantOAuthRevocationGateway | Only admin code allowed to touch Gmail OAuth services; ArchUnit gated |
| backend/api response → admin browser | AdminResponseBodyBanFilter post-serialization scan; replaces leaked body |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-08-25 | Information Disclosure | Email body / chat content leaks via projection | mitigate | All projection records have only scalars/enums/counts/timestamps; AdminPathBodyBanTest ArchUnit scans field names + accessor calls; AdminResponseBodyBanFilter is post-serialization failsafe |
| T-08-26 | Information Disclosure | Chat session details (messages, prompts) viewable | mitigate | TenantActivitySnapshot exposes count/lastActivityAt/modelSelection only; "Show details" disabled with v1.3+ tooltip (D-20) |
| T-08-27 | Repudiation | Admin views tenant data without audit trail | mitigate | AdminTenantAccess.readOnly writes admin_read_event row BEFORE binding TenantContext; rollback if scope entry fails |
| T-08-28 | Elevation of Privilege | Admin gains tenant authority via TenantContext leak | mitigate | AdminContext/TenantContext mutex; ArchUnit whitelist only AdminTenantAccess to bridge scopes; mutex re-bound after readOnly closure exits |
| T-08-29 | Information Disclosure | Decrypted OAuth tokens visible to admin code | mitigate | TenantOAuthRevocationGateway takes tenantId only; admin code cannot inject GmailConnectionRepository / GmailOAuthTokenService (AdminTenantOAuthGuardTest ArchUnit) |
| T-08-30 | Tampering | Tenant pause/disconnect/delete without reason | mitigate | TenantActionRequest validates reason 8-500 chars + sentinel-leak guard; @PreAuthorize at controller; same-tx audit row |
| T-08-31 | Information Disclosure | Body-ban filter bypass via streaming chunked response | mitigate | ContentCachingResponseWrapper buffers entire response before scan; Jackson 3 streaming parser handles large JSON without buffering full tree |
| T-08-32 | Repudiation | Body-ban filter trips without audit | mitigate | On trip, AdminAuditWriter.appendAsSystem writes ADMIN_RESPONSE_BODY_BAN_TRIPPED with offending controller path + audit-row link returned to client |
| T-08-33 | Elevation of Privilege | Tenant deletion without OAuth revocation leaves dangling Pub/Sub | mitigate | TenantDeletionService cascades OAuth revoke BEFORE deleting gmail_connection row; transaction rollback if revoke fails |
| T-08-34 | Information Disclosure | Deletion preview enumerates per-tenant counts to non-target admins | accept | Preview is gated by @PreAuthorize ADMIN; counts are aggregate (not content); each preview write admin_read_event row for trail |
| T-08-SC | Tampering | No new npm/pip/cargo installs in 8C | accept | 8C is backend Java + frontend feature work on existing apps/admin scaffold; zero new package additions |

</threat_model>

<verification>

```bash
./gradlew :backend:core:test :backend:api:test --tests "*AdminTenant*" --tests "*AdminResponseBodyBan*" --tests "*AdminPathBodyBan*"
pnpm --filter @zeromail/admin test:unit
pnpm --filter @zeromail/admin e2e -- --grep "tenants"

# Verify body-ban scan post-test (no production leak)
mcp__postgres__execute_sql "SELECT count(*) FROM admin_audit_event WHERE action='ADMIN_RESPONSE_BODY_BAN_TRIPPED'"  # expect 0 (fixture trips cleaned up)

# Verify projection records have no banned field names
grep -rE 'String\s+(body|bodyHtml|snippet|payload|prompt|completion|content)' backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/  # expect 0 matches
grep -rE 'String\s+(body|bodyHtml|snippet|payload|prompt|completion|content)' backend/api/src/main/java/com/zeromail/api/dto/admin/tenant/  # expect 0 matches
```

</verification>

<success_criteria>
- [ ] AdminTenantAccess.readOnly writes admin_read_event before binding TenantContext
- [ ] All 6 tenant projection records contain only scalars/enums/counts/timestamps
- [ ] AdminPathBodyBanTest green over production OPS-TENANT projections + DTOs
- [ ] AdminTenantOAuthGuardTest green (admin code cannot inject Gmail OAuth services)
- [ ] AdminResponseBodyBanFilter trips on fixture >200-char forbidden field; passes legitimate metadata
- [ ] TenantOAuthRevocationGateway is sole admin path to OAuth revocation
- [ ] TenantPauseService/DisconnectService/DeletionService write audit rows + flip status / cascade
- [ ] AdminTenantController endpoints respond per OPS-TENANT-01..05
- [ ] 5-tab detail page in apps/admin syncs ?tab= URL + lazy data fetch + one admin_read_event per tab visit
- [ ] Destructive actions use ConfirmTwiceDialog with correct step-2 tokens (pause / email / email)
- [ ] Activity tab "Show details" disabled with v1.3+ tooltip
- [ ] Playwright tenants spec green
</success_criteria>

<output>
Create `.planning/phases/08-admin-console-operator-tooling/08-8C-SUMMARY.md` when done.
</output>
