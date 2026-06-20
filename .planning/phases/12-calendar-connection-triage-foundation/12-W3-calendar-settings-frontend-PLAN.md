---
phase: 12-calendar-connection-triage-foundation
plan: 04
type: execute
wave: 3
depends_on:
  - 12-03
files_modified:
  - apps/web/lib/api/schema.d.ts
  - apps/web/lib/api/openapi.json
  - apps/web/features/calendar/api/calendar-api.ts
  - apps/web/features/calendar/query-keys.ts
  - apps/web/features/calendar/messages.ts
  - apps/web/features/calendar/hooks/use-calendar-connections.ts
  - apps/web/features/calendar/hooks/use-disconnect-calendar-connection.ts
  - apps/web/features/calendar/hooks/use-toggle-calendar.ts
  - apps/web/features/calendar/hooks/use-update-calendar-preference.ts
  - apps/web/features/calendar/hooks/use-connect-calendar-intent.ts
  - apps/web/features/calendar/components/CalendarConnectionsPanel.tsx
  - apps/web/features/calendar/components/CalendarConnectionCard.tsx
  - apps/web/features/calendar/components/CalendarList.tsx
  - apps/web/features/calendar/components/RoleAssignmentSection.tsx
  - apps/web/features/calendar/components/ConnectCalendarButton.tsx
  - apps/web/features/calendar/components/EmptyState.tsx
  - apps/web/app/(protected)/(app)/settings/mailboxes/[mailboxId]/calendar/page.tsx
  - apps/web/app/(protected)/(app)/settings/mailboxes/[mailboxId]/calendar/CalendarSettingsClient.tsx
  - backend/api/src/main/java/com/zeromail/api/controllers/calendar/CalendarConnectIntentController.java
  - backend/api/src/main/java/com/zeromail/api/dto/calendar/CalendarConnectIntentResponse.java
  - apps/web/e2e/calendar-settings.spec.ts
  - apps/web/__tests__/calendar/use-calendar-connections.test.tsx
autonomous: true
requirements:
  - CAL-CONN-01
  - CAL-CONN-04
  - CAL-CONN-05
  - CAL-CONN-07
must_haves:
  truths:
    - "User navigates to /settings/mailboxes/[mailboxId]/calendar and sees either an empty-state card with a Connect Google Calendar CTA, or a stack of connection cards mirroring Inbox Zero's layout (per D-07)"
    - "Clicking Connect Google Calendar redirects to /oauth2/authorization/google-calendar after the backend stamps the active mailboxId on the OAuth attributes"
    - "Each connection card lists its sub-calendars with per-calendar enable Switch + a Calendly-style role-assignment section below (multi-select for free/busy, single-select for event_write and brief_source)"
    - "Disconnect button on a card transitions the card to a Disconnected badge state immediately after the DELETE call returns 204"
    - "All TanStack mutations carry meta.successMessage / meta.errorMessage; no local toast.success / toast.error calls per CLAUDE.md §12"
    - "All form primitives are raw shadcn (Card, Collapsible, Switch, Badge, Select, DropdownMenu); no wrapper components per memory feedback_raw_shadcn_first.md"
  artifacts:
    - path: "apps/web/lib/api/schema.d.ts"
      provides: "Regenerated OpenAPI types; components['schemas']['CalendarConnectionResponse'] + MailboxCalendarPreferenceResponse + UpdateMailboxCalendarPreferenceRequest + UpdateCalendarEnabledRequest etc"
    - path: "apps/web/features/calendar/components/CalendarConnectionsPanel.tsx"
      provides: "Top-level panel; empty state OR stacked CalendarConnectionCard list with the RoleAssignmentSection below"
    - path: "apps/web/features/calendar/components/RoleAssignmentSection.tsx"
      provides: "Calendly-style overlay (per D-07): multi-select for FREEBUSY, single-select for EVENT_WRITE + BRIEF_SOURCE"
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/calendar/CalendarConnectIntentController.java"
      provides: "POST /api/calendar/connect-intent — stamps the active mailboxId onto the calendar OAuth authz request attributes via the Phase 10 IntentCarryingAuthorizationRequestRepository pattern, returns the canonical /oauth2/authorization/google-calendar URL"
    - path: "apps/web/e2e/calendar-settings.spec.ts"
      provides: "Playwright e2e against /settings/mailboxes/[id]/calendar"
  key_links:
    - from: "CalendarConnectionsPanel"
      to: "useCalendarConnections(mailboxId)"
      via: "TanStack Query reads /api/calendar/mailboxes/{mailboxId}/connections"
      pattern: "useCalendarConnections"
    - from: "ConnectCalendarButton"
      to: "useConnectCalendarIntent(mailboxId)"
      via: "POST /api/calendar/connect-intent → window.location = oauth2 url"
      pattern: "connect-intent"
    - from: "RoleAssignmentSection"
      to: "useUpdateCalendarPreference"
      via: "PATCH /api/calendar/mailboxes/{mailboxId}/preferences"
      pattern: "useUpdateCalendarPreference"
---

<objective>
Ship the user-visible `/settings/mailboxes/[mailboxId]/calendar` page that lets users connect Google Calendar accounts, view sub-calendars, toggle them, and assign roles per mailbox — entirely via the W2 REST surface.

Layout per D-07: Inbox Zero shell on top (empty-state Card with value props → stacked Card grid per connection with collapsible sub-calendar list with per-calendar Switch toggles → dropdown disconnect) PLUS the Calendly-style role-assignment section beneath ("Check for conflicts" multi-select for FREEBUSY, "Add events to" single-select for EVENT_WRITE, "Use for meeting briefs" single-select for BRIEF_SOURCE).

Per CLAUDE.md project rule 13 + memory `feedback_frontend_design_skill.md`: do NOT invoke the global frontend-design skill; follow repo conventions, existing screens, shadcn primitives, locked tokens, i18n bundling, and Playwright verification.

Per CLAUDE.md memory `feedback_raw_shadcn_first.md`: use raw shadcn primitives `Card`, `Collapsible`, `Switch`, `Badge`, `Select`, `DropdownMenu` directly from `@/components/ui/*`. No wrapper components like `StatusCard`, `RoleSelect`, etc.

Per CLAUDE.md memory `feedback_bundled_oauth_scopes.md`: the Connect Calendar flow is its OWN OAuth round-trip (incremental — CAL-CONN-01 requires explicit user action). NOT bundled with login.

The Connect Calendar button does NOT directly `window.location` to `/oauth2/authorization/google-calendar` — it first POSTs to a new `/api/calendar/connect-intent` endpoint that stamps the active `mailboxId` on the OAuth authz request attributes (per Phase 10 D-01 attributes-based intent pattern). The backend then returns the canonical OAuth URL the frontend redirects to. This makes `activeMailboxId` available to `CalendarOAuthSuccessHandler` from W2 so D-06's "auto-tag primary calendar for active mailbox only" works.

Purpose: User-visible Phase 12 surface lands; W4 + W5 are pure backend so the user-facing capability is feature-complete after this plan.
Output: 1 new backend endpoint + DTO (the connect-intent stamper) + 8 frontend feature files + 1 route + 1 Playwright spec + 1 Vitest test.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md
@.planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md
@.planning/phases/12-calendar-connection-triage-foundation/12-PATTERNS.md
@.planning/phases/12-calendar-connection-triage-foundation/12-03-SUMMARY.md
</context>

<artifacts_this_phase_produces>
- `POST /api/calendar/connect-intent` endpoint accepting `{mailboxId: UUID}`; returns `{authorizationUrl: "/oauth2/authorization/google-calendar"}` after stamping the `activeMailboxId` attribute on a pending intent stored in Spring Session (mirror Phase 10 `IntentCarryingAuthorizationRequestRepository`).
- TypeScript types regenerated in `apps/web/lib/api/schema.d.ts` — never hand-edited per CLAUDE.md §11.
- `features/calendar/api/calendar-api.ts` — typed `api.GET / DELETE / PATCH / POST` wrappers.
- `features/calendar/query-keys.ts` — TanStack key factory.
- `features/calendar/hooks/*` — 5 hooks (query + 4 mutations).
- `features/calendar/components/*` — 6 component files (Panel, Card, List, RoleAssignmentSection, ConnectButton, EmptyState).
- Route at `app/(protected)/(app)/settings/mailboxes/[mailboxId]/calendar/page.tsx` + client orchestrator.
- `features/calendar/messages.ts` — per-feature i18n bundle (per CONVENTIONS §10), VN + EN.
- Playwright spec `apps/web/e2e/calendar-settings.spec.ts`.
- Vitest component test `apps/web/__tests__/calendar/use-calendar-connections.test.tsx` (hook tests live beside features per CONVENTIONS §8 — but contract tests live in `__tests__/` — pick by scope; this one is hook+http contract so `__tests__/` is fine).

NOT in this plan:
- ical4j classifier + inbox-projection ORDER BY + Inbox Badge UI for INVITE/CANCEL — W4.
- PRESET_CALENDAR matcher and rule wiring — W5.
</artifacts_this_phase_produces>

<tasks>

<task type="auto">
  <name>Task 1: Backend POST /api/calendar/connect-intent + DTO + IntentCarryingAuthorizationRequestRepository extension</name>
  <files>backend/api/src/main/java/com/zeromail/api/controllers/calendar/CalendarConnectIntentController.java, backend/api/src/main/java/com/zeromail/api/dto/calendar/CalendarConnectIntentResponse.java, backend/api/src/test/java/com/zeromail/api/controllers/calendar/CalendarConnectIntentControllerTest.java</files>
  <read_first>
    - backend/api/src/main/java/com/zeromail/api/security/IntentCarryingAuthorizationRequestRepository.java (Phase 10 D-01 attributes-based intent — `OAuthIntentSnapshot` + the session-snapshot constant `ZEROMAIL_OAUTH_PENDING_INTENT`)
    - backend/api/src/main/java/com/zeromail/api/security/GoogleAuthorizationRequestResolver.java (the resolver that reads the pending intent snapshot and stamps it on `OAuth2AuthorizationRequest.attributes(...)`)
    - backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java (W1/W2 — the consumer side reading `activeMailboxId` from the request attributes)
    - backend/api/src/main/java/com/zeromail/api/controllers/calendar/CalendarConnectionController.java (W2 — the existing controller this endpoint sits next to)
    - backend/api/src/main/java/com/zeromail/api/security/MailboxBindingFilter.java (the active-mailbox binding for `/api/calendar/...` routes; this endpoint takes the mailboxId from the request body NOT a path param, so MailboxBindingFilter does not bind — explicit validation via CalendarConnectionService.resolveOwnedMailboxOrThrow is done in the controller)
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java (the `resolveOwnedConnectionOrThrow(MailboxRef)` method — the controller asks Gmail-service to validate the mailbox before stamping intent)
    - .planning/milestones/v1.3-phases/10-gmail-mailbox-foundation-and-account-management/10-05-PLAN.md (find the intent-carrying repository implementation; this plan extends it to carry an `activeMailboxId` attribute alongside the existing OAuth intent fields)
  </read_first>
  <action>
    Read `IntentCarryingAuthorizationRequestRepository` carefully. The Phase 10 intent shape probably has fields like `(intent, targetMailboxId, initiatingTenantId)`. For Calendar, the relevant field is `targetMailboxId` (already present per the Phase 10 design) — verify and reuse, OR add a `calendarActiveMailboxId` field if the existing slot is Gmail-specific and would collide. Per memory `feedback_config_record_no_new_package.md`, prefer reusing the existing `targetMailboxId` semantics if they match (the field name "targetMailboxId" maps naturally to "active mailbox at calendar-connect time").

    Create `CalendarConnectIntentResponse.java` as a record `(String authorizationUrl)`. Annotate `@Schema(requiredProperties = {"authorizationUrl"})`. The URL is the literal `"/oauth2/authorization/google-calendar"` returned to the client.

    Create `CalendarConnectIntentController.java`:
    - `@RestController @RequestMapping("/api/calendar")`. Inject `GmailConnectionService` (to validate the mailbox is owned), `IntentCarryingAuthorizationRequestRepository` (to stamp the intent in session).
    - `@PostMapping("/connect-intent") public CalendarConnectIntentResponse prepareConnect(@Valid @RequestBody CalendarConnectIntentRequest request)`. Define the request record `CalendarConnectIntentRequest(@NotNull UUID mailboxId)` in `api/dto/calendar/`.
    - Steps: (1) Resolve `tenantId = TenantContext.currentOrThrow();` (2) Verify mailbox ownership: `gmailConnectionService.resolveOwnedConnectionOrThrow(new MailboxRef(tenantId, request.mailboxId()));` — throws `MailboxNotOwnedException` → 404. (3) Stamp the intent in Spring Session via `IntentCarryingAuthorizationRequestRepository.savePendingIntent(...)` with the calendar-specific intent (e.g. `OAuthIntent.CALENDAR_CONNECT`) + `targetMailboxId = request.mailboxId()`. If the existing Phase 10 `OAuthIntent` enum does not have `CALENDAR_CONNECT`, add it — this is a one-value enum addition. (4) Return `new CalendarConnectIntentResponse("/oauth2/authorization/google-calendar")`.

    Per Phase 10's resolver, after the user is redirected to Google and comes back to `/login/oauth2/code/google-calendar`, the resolver reads the pending intent (one-shot remove pattern per Phase 10 codex HIGH-A/HIGH-B) and stamps `activeMailboxId` on `OAuth2AuthorizationRequest.attributes(...)`. The `CalendarOAuthSuccessHandler` reads it via `authenticationToken.getAuthorizedClientRegistrationId() + the saved-request shim` to recover the `activeMailboxId`. The W2 success handler's `resolveActiveMailboxIdFromOAuthAttributes(...)` method (left as a TODO in W2 Task 2) is finalized HERE — wire the actual attribute read.

    Edit `GoogleAuthorizationRequestResolver` (extending W1's modification): in the `customizeAuthorizationRequest` method, when `calendarFlow == true`, also read the pending intent snapshot from the session-keyed repository (`IntentCarryingAuthorizationRequestRepository.consumePendingIntent(servletRequest)` returning the `OAuthIntentSnapshot`) and stamp `authorizationRequest.attributes(attrs -> attrs.put(CALENDAR_ACTIVE_MAILBOX_ID_ATTR, intent.targetMailboxId()))`. Define the attribute key constant `CALENDAR_ACTIVE_MAILBOX_ID_ATTR = "zeromail.calendar.activeMailboxId"`. The one-shot remove is handled by the repository's `consumePendingIntent` method per Phase 10.

    Edit `CalendarOAuthSuccessHandler` (W2 — finalize the TODO from W2 Task 2): replace the placeholder `resolveActiveMailboxIdFromOAuthAttributes(...)` with a real read. The `OAuth2AuthenticationToken` carries the `OAuth2AuthorizationRequest` indirectly via Spring's authorized-client store — or more reliably, retrieve it from the request session via the `IntentCarryingAuthorizationRequestRepository.consumePendingIntent(...)` if the resolver did not remove it during the redirect (mirror Phase 10's success-handler shim pattern). Choose the approach that matches Phase 10's existing wiring exactly.

    Create `CalendarConnectIntentControllerTest.java`:
    - `@WebMvcTest(CalendarConnectIntentController.class)`. `@MockitoBean GmailConnectionService`; `@MockitoBean IntentCarryingAuthorizationRequestRepository`.
    - Mint a session cookie via `TestSessionSupport.TestSessionMinter`.
    - Cases:
      - `POST /api/calendar/connect-intent` body `{"mailboxId": "<valid-uuid>"}` — assert 200 + `{"authorizationUrl": "/oauth2/authorization/google-calendar"}`; assert `intentRepository.savePendingIntent(...)` called with `OAuthIntent.CALENDAR_CONNECT` and the right `targetMailboxId`.
      - Mailbox not owned → `gmailConnectionService.resolveOwnedConnectionOrThrow` throws `MailboxNotOwnedException`; assert 404 ProblemDetail.
      - Missing `mailboxId` in body → 400 ProblemDetail (Bean Validation).

    Privacy: no `event=` log statement carries the mailboxId beyond the standard `event=calendar_connect_intent_stamped tenantId={} mailboxId={}`.

    JetBrains MCP: run `get_file_problems` on the modified `GoogleAuthorizationRequestResolver.java` (small surface change) and `CalendarOAuthSuccessHandler.java` to confirm the finalization didn't break compilation.
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:api:test --tests "com.zeromail.api.controllers.calendar.CalendarConnectIntentControllerTest" --tests "com.zeromail.api.security.CalendarOAuthSuccessHandlerTest"</automated>
  </verify>
  <acceptance_criteria>
    - `CalendarConnectIntentController.java` exists; `grep -c '/connect-intent' backend/api/src/main/java/com/zeromail/api/controllers/calendar/CalendarConnectIntentController.java` returns at least 1.
    - `grep -c 'OAuthIntent.CALENDAR_CONNECT' backend/api/src/main/java/com/zeromail/api/security/CalendarConnectIntent*.java backend/api/src/main/java/com/zeromail/api/controllers/calendar/CalendarConnectIntentController.java 2>/dev/null | grep -v ':0$' | head -1` returns at least one line — the new enum value or the controller call site references it.
    - `CalendarOAuthSuccessHandler` no longer has the W2 Task 2 TODO marker — `grep -c 'TODO.*activeMailboxId' backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java | grep -v '^#'` returns 0.
    - `CalendarConnectIntentControllerTest` + `CalendarOAuthSuccessHandlerTest` (W2 test) both green.
    - JetBrains `get_file_problems` returns no errors on modified files.
  </acceptance_criteria>
  <done>The `/api/calendar/connect-intent` endpoint stamps the active mailboxId in session; the OAuth round-trip carries it through to `CalendarOAuthSuccessHandler` so D-06 default-role assignment lands on the right mailbox.</done>
</task>

<task type="auto">
  <name>Task 2: OpenAPI codegen + feature folder (api, query-keys, hooks, messages)</name>
  <files>apps/web/lib/api/schema.d.ts, apps/web/lib/api/openapi.json, apps/web/features/calendar/api/calendar-api.ts, apps/web/features/calendar/query-keys.ts, apps/web/features/calendar/messages.ts, apps/web/features/calendar/hooks/use-calendar-connections.ts, apps/web/features/calendar/hooks/use-disconnect-calendar-connection.ts, apps/web/features/calendar/hooks/use-toggle-calendar.ts, apps/web/features/calendar/hooks/use-update-calendar-preference.ts, apps/web/features/calendar/hooks/use-connect-calendar-intent.ts, apps/web/__tests__/calendar/use-calendar-connections.test.tsx</files>
  <read_first>
    - apps/web/features/mailbox/api/mailbox-api.ts (typed api.GET / api.PATCH wrappers using openapi-fetch + components types — the EXACT pattern this plan mirrors)
    - apps/web/features/mailbox/query-keys.ts (key factory shape — calendarQueryKeys mirrors mailboxQueryKeys)
    - apps/web/features/mailbox/hooks/use-*.ts (TanStack Query hook pattern — invalidation on mutation success, meta.successMessage/errorMessage instead of local toast.* per CLAUDE.md §12)
    - apps/web/lib/api/client.ts (the typed openapi-fetch client + 401 redirect middleware)
    - apps/web/lib/query-client.tsx (MutationCache.onError/onSuccess reading meta.successMessage/errorMessage; QueryCache.onError TkDodo pattern)
    - apps/web/scripts/generate-api.ts (the OpenAPI codegen script — `pnpm --filter web run generate:api`)
    - CLAUDE.md §11 — schema.d.ts is GENERATED, never hand-edited; regen via `pnpm --filter web run generate:api`
    - CLAUDE.md §12 — TanStack Query meta toasts; type the meta surface via `declare module '@tanstack/react-query'`
    - CONVENTIONS.md §8 — feature folder layout: api/, query-keys.ts (no `query-keys/` folder), hooks/use-X.ts one per use case, components/, NO barrel index.ts
    - CONVENTIONS.md §10 — per-feature messages.ts merged by `apps/web/scripts/merge-feature-i18n.ts`
    - apps/web/features/account/hooks/useUpdateLanguage.ts (mutation with meta toast — the exact CLAUDE.md §12 pattern this plan mirrors)
    - .planning/phases/12-calendar-connection-triage-foundation/12-PATTERNS.md (§§ "apps/web/features/calendar/api/calendar-api.ts" + "query-keys.ts" + "use-toggle-calendar.ts" — concrete sketches at lines 493-557)
  </read_first>
  <action>
    Boot backend at localhost:8080 (via SSH tunnel to dev Postgres per memory `reference_dev_db_ssh_tunnel.md`) — confirm `:backend:api:bootRun` is up. Then run `pnpm --filter web run generate:api`. This emits `apps/web/lib/api/schema.d.ts` + the cached `openapi.json` from the backend's `/v3/api-docs`. Verify the regenerated `schema.d.ts` carries: `components['schemas']['CalendarConnectionResponse']`, `MailboxCalendarPreferenceResponse`, `CalendarSubResponse`, `UpdateMailboxCalendarPreferenceRequest`, `UpdateCalendarEnabledRequest`, `CalendarConnectIntentRequest`, `CalendarConnectIntentResponse`, `paths['/api/calendar/mailboxes/{mailboxId}/connections']`, etc. If the regen fails (backend not reachable), STOP and surface the error — never hand-edit `schema.d.ts` per CLAUDE.md §11.

    Create `apps/web/features/calendar/query-keys.ts`:
    ```ts
    export const calendarQueryKeys = {
      all: ['calendar'] as const,
      connections: (mailboxId: string) =>
        [...calendarQueryKeys.all, 'mailbox', mailboxId, 'connections'] as const,
    } as const;
    ```
    Per CONVENTIONS §8: no key for mutation-only paths; mutations invalidate `connections(mailboxId)`.

    Create `apps/web/features/calendar/api/calendar-api.ts` mirroring `features/mailbox/api/mailbox-api.ts`:
    - `import { adaptFetchForOpenApi, api } from '@/lib/api/client';`
    - `import type { components } from '@/lib/api/schema';`
    - Export type aliases derived from generated schemas: `export type CalendarConnection = components['schemas']['CalendarConnectionResponse']; export type CalendarSub = components['schemas']['CalendarSubResponse']; export type MailboxCalendarPreference = components['schemas']['MailboxCalendarPreferenceResponse'];` etc.
    - Functions: `listCalendarConnections(mailboxId)`, `disconnectCalendarConnection(connectionId)`, `toggleCalendar(calendarId, enabled)`, `updateMailboxCalendarPreference(mailboxId, request)`, `prepareCalendarConnect(mailboxId)` — each calls the typed `api.GET / DELETE / PATCH / POST` per CONVENTIONS §8. NO hand-written mirror DTOs (the generated types are the only source).
    - Privacy: NEVER log request/response bodies (the response carries `googleEmail` — log only HTTP status + timing if instrumented).

    Create the 5 hook files under `features/calendar/hooks/`:
    - `use-calendar-connections.ts`: `export function useCalendarConnections(mailboxId: string) { return useQuery({ queryKey: calendarQueryKeys.connections(mailboxId), queryFn: () => listCalendarConnections(mailboxId), staleTime: 5_000, }); }` — short staleTime so post-OAuth refresh sees the new row.
    - `use-disconnect-calendar-connection.ts`: `useMutation` with `meta.successMessage` + `meta.errorMessage` from the i18n messages bundle (NO local `toast.*` call); on `onSuccess` invalidate `calendarQueryKeys.connections(mailboxId)`.
    - `use-toggle-calendar.ts`: similar mutation; the response (preference count removed) is shown via the meta success message.
    - `use-update-calendar-preference.ts`: similar mutation; PATCH the preferences endpoint.
    - `use-connect-calendar-intent.ts`: `useMutation` that POSTs to `/api/calendar/connect-intent`; on success, `window.location.assign(response.authorizationUrl)`. Per CLAUDE.md §12 use meta success/error message; on error a TanStack global handler surfaces toast.

    Per CLAUDE.md §12 — register the meta surface types if not already in this codebase:
    ```ts
    declare module '@tanstack/react-query' {
      interface Register {
        mutationMeta: { successMessage?: string; errorMessage?: string; silent?: boolean };
      }
    }
    ```
    If already registered project-wide, do NOT duplicate.

    Create `apps/web/features/calendar/messages.ts` per CONVENTIONS §10. Shape: `export default { vi: {...}, en: {...} }` with at least these keys:
    - `calendar.title` — VN "Lịch", EN "Calendar"
    - `calendar.subtitle` — VN "Kết nối Google Calendar để AI gợi ý khung giờ rảnh và đặt lịch giúp bạn.", EN "Connect Google Calendar to let AI suggest free slots and schedule meetings for you."
    - `calendar.empty.heading` / `calendar.empty.body` / `calendar.empty.connectCta` ("Kết nối Google Calendar" / "Connect Google Calendar")
    - `calendar.card.disconnect`, `calendar.card.disconnected`, `calendar.card.connectedSince`
    - `calendar.list.heading` ("Sub-calendars"), `calendar.list.enabledLabel`, `calendar.list.disabledLabel`
    - `calendar.roles.heading` ("Per-role assignments"), `calendar.roles.freebusyLabel` ("Check for conflicts using"), `calendar.roles.eventWriteLabel` ("Add events to"), `calendar.roles.briefSourceLabel` ("Use for meeting briefs")
    - `calendar.actions.disconnect.success` ("Đã ngắt kết nối lịch" / "Calendar disconnected"), `calendar.actions.disconnect.error` ("Không ngắt được — thử lại" / "Could not disconnect — try again")
    - `calendar.actions.toggle.success` ("Đã cập nhật" / "Updated"), `calendar.actions.toggle.error` ("Không cập nhật được" / "Could not update")
    - `calendar.actions.preference.success`, `.error`
    - `calendar.connectIntent.error` ("Không bắt đầu được — thử lại" / "Could not start connect flow")
    Default seeded rule label "Calendar" stays in English EN — match `materializeDefaultRulesEnabled` convention per memory `feedback_explain_before_options.md` AND per W5 plan; do NOT add a Vietnamese label override here.

    Run `pnpm --filter web run i18n:build` to regenerate the bundles; verify the new keys appear in `apps/web/i18n/messages/{vi,en}.json` (these JSON files are generated artifacts per CONVENTIONS §10).

    Create `apps/web/__tests__/calendar/use-calendar-connections.test.tsx`:
    - Vitest 4 + `@testing-library/react` + a mock `openapi-fetch` (use the existing pattern from `apps/web/features/mailbox/__tests__/` if such tests live there, or `apps/web/__tests__/` per CONVENTIONS §8).
    - Wrap in `QueryClientProvider`.
    - Case: hook returns a stub list when the mocked API responds 200; case: hook surfaces a toast when the mocked API responds 500 (verify the global `MutationCache.onError` fires by checking the toast registry — or simply assert the hook's `error` state is populated; the toast pathway is covered by lib/query-client tests).
    Per TESTING.md §2, test the observable outcome (data flowing into a component), not the implementation detail (`mutationFn` called X times).
  </action>
  <verify>
    <automated>cd apps/web && pnpm --filter web run typecheck && pnpm --filter web test -- use-calendar-connections</automated>
  </verify>
  <acceptance_criteria>
    - `apps/web/lib/api/schema.d.ts` carries `CalendarConnectionResponse`, `MailboxCalendarPreferenceResponse`, `UpdateMailboxCalendarPreferenceRequest`, `UpdateCalendarEnabledRequest`, `CalendarConnectIntentRequest`, `CalendarConnectIntentResponse` (grep finds each name in the file).
    - `grep -c 'declare module' apps/web/features/calendar/api/calendar-api.ts | grep -v '^#'` returns 0 (no hand-written mirror DTOs; types are derived from `components`).
    - All 5 hook files exist; `grep -c 'meta:' apps/web/features/calendar/hooks/use-disconnect-calendar-connection.ts` returns at least 1, AND `grep -c 'toast\.' apps/web/features/calendar/hooks/*.ts | grep -v ':0$'` returns empty (no local `toast.*` calls per CLAUDE.md §12).
    - `messages.ts` carries `vi` + `en` keys; `pnpm --filter web run i18n:check` passes.
    - `use-calendar-connections.test.tsx` passes.
    - `pnpm --filter web run typecheck` is green.
  </acceptance_criteria>
  <done>OpenAPI types regenerated; feature folder skeleton compiled + typechecked + first hook test green. The feature has no UI yet (Task 3 lands components + route).</done>
</task>

<task type="auto">
  <name>Task 3: UI components + route + Playwright e2e</name>
  <files>apps/web/features/calendar/components/CalendarConnectionsPanel.tsx, apps/web/features/calendar/components/CalendarConnectionCard.tsx, apps/web/features/calendar/components/CalendarList.tsx, apps/web/features/calendar/components/RoleAssignmentSection.tsx, apps/web/features/calendar/components/ConnectCalendarButton.tsx, apps/web/features/calendar/components/EmptyState.tsx, apps/web/app/(protected)/(app)/settings/mailboxes/[mailboxId]/calendar/page.tsx, apps/web/app/(protected)/(app)/settings/mailboxes/[mailboxId]/calendar/CalendarSettingsClient.tsx, apps/web/e2e/calendar-settings.spec.ts</files>
  <read_first>
    - apps/web/app/(protected)/(app)/settings/page.tsx (the parent settings shell — its header/title pattern is mirrored here)
    - apps/web/components/ui/card.tsx (raw shadcn Card primitive — used directly with token classes)
    - apps/web/components/ui/collapsible.tsx (raw shadcn Collapsible — used for sub-calendar list collapse/expand)
    - apps/web/components/ui/switch.tsx (raw shadcn Switch — used for is_enabled per-calendar toggle and per-role boolean states)
    - apps/web/components/ui/badge.tsx (raw shadcn Badge — used for "Connected" / "Disconnected" status badge)
    - apps/web/components/ui/select.tsx (raw shadcn Select — used for EVENT_WRITE + BRIEF_SOURCE single-select dropdowns)
    - apps/web/components/ui/dropdown-menu.tsx (raw shadcn DropdownMenu — used for the per-card "..." overflow menu containing Disconnect)
    - apps/web/components/ui/button.tsx + buttonVariants (per memory `feedback_raw_shadcn_first.md` — use `<a className={buttonVariants()}>` for navigation, `<Button>` for actions)
    - apps/web/lib/i18n/server.ts (or wherever the project gets the typed `t` for next-intl — confirm before authoring server-component i18n)
    - ../inbox-zero/apps/web/app/(app)/[emailAccountId]/calendars/CalendarConnectionCard.tsx (visual reference ONLY — do NOT port TypeScript code; the React shape + the Card+Collapsible+DropdownMenu composition is the inspiration)
    - ../inbox-zero/apps/web/app/(app)/[emailAccountId]/calendars/CalendarConnections.tsx (the top-level shell visual reference)
    - ../inbox-zero/apps/web/app/(app)/[emailAccountId]/calendars/CalendarList.tsx (the collapsible sub-calendar Switch list — visual reference)
    - .planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md (D-07 layout instructions — IZ shell on top + Calendly-style RoleAssignmentSection beneath; raw shadcn only)
    - CLAUDE.md §13 (do NOT invoke global UI/design skills; use repo conventions, existing screens, shadcn primitives, locked tokens, i18n rules, Playwright verification)
    - apps/web/AGENTS.md (find via Glob — frontend guard file; verify shadcn + Tailwind 4 + token classes only, no hex colors)
  </read_first>
  <action>
    Create `EmptyState.tsx`: a `Card` carrying `text-center` content. CardTitle uses `t('calendar.empty.heading')`, CardDescription uses `t('calendar.empty.body')`. CardContent renders a `ConnectCalendarButton` for the page's `mailboxId`. Use token classes: `bg-card text-foreground border-border` per AGENTS.md (NO hex colors).

    Create `ConnectCalendarButton.tsx`: a client component (`'use client'`). Uses `useConnectCalendarIntent(mailboxId)` hook. On click, calls `mutate(undefined)` (the hook POSTs and redirects). Renders as a raw shadcn `Button` with text from `t('calendar.empty.connectCta')`. Disabled state while pending.

    Create `CalendarList.tsx`: a client component receiving `connection: CalendarConnection`. Renders a `Collapsible` with the connection's `calendars[]`. Each item: `<div className="flex items-center justify-between py-2"><div><p>{calendar.name}</p><p className="text-sm text-muted-foreground">{calendar.timezone ?? ''}</p></div><Switch checked={calendar.isEnabled} onCheckedChange={(next) => toggle.mutate({ calendarId: calendar.id, enabled: next })} /></div>`. Uses `useToggleCalendar(mailboxId)`. The Collapsible header shows "Sub-calendars (N)" with a chevron icon (use the existing `lucide-react` ChevronDown if the repo uses it, else inline SVG per memory `feedback_raw_shadcn_first.md` and previous lessons about lucide-react+vitest dedupe).

    Create `CalendarConnectionCard.tsx`: a client component receiving `connection: CalendarConnection` and `mailboxId`. Renders a `Card`. CardHeader: shows `googleProfileName` (or `googleEmail` as fallback) + a `Badge variant={status === 'CONNECTED' ? 'default' : 'destructive'} outline` showing `t('calendar.card.disconnected')` when not CONNECTED. CardHeader also carries a `DropdownMenu` (the `...` overflow) whose only item is "Disconnect" using `useDisconnectCalendarConnection(mailboxId)`. CardContent: renders `<CalendarList />`. Per privacy lesson, do NOT use `googleEmail` in any sentry-style log; render in UI only.

    Create `RoleAssignmentSection.tsx`: a client component receiving `connections: CalendarConnection[]`, `preferences: MailboxCalendarPreference[]`, and `mailboxId`. Renders a `Card` titled `t('calendar.roles.heading')`. Three rows:
    - FREEBUSY: a custom multi-select. Per memory `feedback_raw_shadcn_first.md`, do NOT install a wrapper `MultiSelect` primitive — compose from raw shadcn. Use a horizontal list of `Badge`-styled chips with `Switch`-toggle behavior, OR use a list of `Checkbox` rows under a `Collapsible` panel. Decide by reading apps/web for any existing multi-select pattern; if none, use the Checkbox-list shape (simpler, accessible). Filter to only `is_enabled=true` calendars across all `connections` (D-13 picker filter).
    - EVENT_WRITE: a raw shadcn `Select` with a single value (the calendar id) or "None". Options filtered to `is_enabled=true` calendars.
    - BRIEF_SOURCE: same shape as EVENT_WRITE.
    Below the three rows render a `Button` "Save changes" — disabled until dirty. On click, call `useUpdateCalendarPreference(mailboxId).mutate({ freebusyCalendarIds, eventWriteCalendarId, briefSourceCalendarId })`. The mutation invalidates `calendarQueryKeys.connections(mailboxId)`, which re-renders this section with the new preferences.

    Create `CalendarConnectionsPanel.tsx`: a client component receiving `mailboxId`. Calls `useCalendarConnections(mailboxId)`. On loading, render a `Skeleton` (raw shadcn). On error, render an `Alert` with `t('common.error')`. On success: if `data.length === 0` render `<EmptyState mailboxId={mailboxId} />`. Otherwise render: (a) a top-of-section `ConnectCalendarButton` (so users can add a second account from the populated state), (b) a `<div className="space-y-4">` of `CalendarConnectionCard`s, (c) a `RoleAssignmentSection` with the flattened `connections + preferences` from `data`.

    Create the route: `apps/web/app/(protected)/(app)/settings/mailboxes/[mailboxId]/calendar/page.tsx`. Server Component shell: renders the page title `t('calendar.title')` + subtitle `t('calendar.subtitle')`, then `<CalendarSettingsClient mailboxId={params.mailboxId} />`. The `mailboxId` is the path param.

    Create `CalendarSettingsClient.tsx` — a client component: `'use client'`. Wraps `<CalendarConnectionsPanel mailboxId={mailboxId} />` inside the existing Suspense/error-boundary scaffolding the settings group uses.

    Create the Playwright spec `apps/web/e2e/calendar-settings.spec.ts`:
    - Use the existing `apps/web/e2e/chrome-test-utils.ts` (or whichever fixture file the project uses to mint a signed-in browser context) per memory `reference_ai_page_e2e_and_hydration.md`.
    - Mock `/api/calendar/mailboxes/{mailboxId}/connections` to return an empty list; navigate to the route; assert the empty-state heading is visible + a button with text "Connect Google Calendar" (or VN equivalent if locale is VN) is visible.
    - Mock the same route to return 1 connection with 2 sub-calendars; navigate again; assert the card renders + the Collapsible opens to reveal both sub-calendars + the role-assignment section is present.
    - Click Disconnect via the dropdown menu; assert the DELETE request fires; mock 204 response; assert the card transitions to a "Disconnected" badge state (the read query refetches and the second mocked response carries `status='DISCONNECTED'`).
    - Per memory `reference_ai_page_e2e_and_hydration.md`, mock ALL endpoints the page calls (the role-assignment section may also call additional endpoints — verify and mock).
    - Per memory `reference_baseui_tabs_url_controlled.md`, avoid 320px viewport tab-bar flake; the calendar settings page has no tab UI so this guidance is informational.

    Run `pnpm --filter web exec playwright test e2e/calendar-settings.spec.ts` against the running dev server. If dev server cannot run in this sandbox (per memory `reference_dev_db_ssh_tunnel.md` + `reference_ai_page_e2e_and_hydration.md`), commit the spec as a durable gate and document the manual-run instruction in the SUMMARY.

    Per CLAUDE.md "UX Philosophy" + project rule 13 + memory `feedback_frontend_design_skill.md` — DO NOT use the global frontend-design skill. Visual verification is via Playwright + browser screenshots through Playwright MCP if needed.

    JetBrains MCP doesn't apply to TS/TSX files; use ESLint (`pnpm --filter web run lint`) and TypeScript (`pnpm --filter web run typecheck`) as the diagnostic gate after each file is written.
  </action>
  <verify>
    <automated>cd apps/web && pnpm --filter web run typecheck && pnpm --filter web run lint && pnpm --filter web exec playwright test e2e/calendar-settings.spec.ts --reporter=line</automated>
  </verify>
  <acceptance_criteria>
    - All 9 listed files exist.
    - `grep -c '#[a-fA-F0-9]\{3,8\}' apps/web/features/calendar/components/*.tsx | grep -v ':0$' | head -1` returns empty (no hex color literals — token classes only per AGENTS.md).
    - `grep -c 'StatusCard\|CalendarCardWrapper\|RoleSelect' apps/web/features/calendar/components/*.tsx | grep -v ':0$' | head -1` returns empty (no wrapper components per memory `feedback_raw_shadcn_first.md`).
    - `grep -c 'toast\.' apps/web/features/calendar/components/*.tsx | grep -v ':0$'` returns empty (no local toast calls — meta-based per CLAUDE.md §12).
    - `pnpm --filter web run typecheck` green; `pnpm --filter web run lint` green.
    - Playwright spec passes the empty-state + populated-card + disconnect-cascade flows.
  </acceptance_criteria>
  <done>The `/settings/mailboxes/[mailboxId]/calendar` page renders, connects, lists, toggles, and disconnects against the W2 backend with raw shadcn primitives and meta-based toast notifications.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Browser → POST /api/calendar/connect-intent | Session-bound; mailboxId in body validated by GmailConnectionService.resolveOwnedConnectionOrThrow. |
| OAuth attribute pendingIntent → Spring Session → CalendarOAuthSuccessHandler | Active mailboxId rides the session-stored intent snapshot per Phase 10 D-01; never on a URL query param. |
| Frontend mutation → backend | CSRF token echoed for mutating methods per CLAUDE.md §12. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-12-08 | Tampering | Untrusted activeMailboxId via URL param | mitigate | Per Phase 10 D-01 attributes-based intent pattern, the active mailboxId rides Spring Session via `IntentCarryingAuthorizationRequestRepository.savePendingIntent(...)` — never a URL query param. `CalendarConnectIntentController` validates mailbox ownership BEFORE stamping intent. Test case asserts MailboxNotOwnedException → 404. |
| T-12-V13-1 | API and Web Service | CSRF on PATCH/DELETE | mitigate | Spring Security `csrf().spa()` (existing); openapi-fetch `onRequest` middleware echoes XSRF-TOKEN cookie back as X-XSRF-TOKEN header per CLAUDE.md §12 contract. Tested by existing project-wide CSRF gates; no new gate added. |
| T-12-V5-1 | Input Validation | Frontend sends UUID list with cross-tenant calendar id | mitigate | Backend `MailboxCalendarPreferenceService.updateForMailbox` validates ownership; frontend `RoleAssignmentSection` filters options to `is_enabled=true` calendars from the workspace's connections — but UI is UX only; the server is the truth (W2 mitigation). |
| T-12-V3-1 | Session Management | Pending intent stored in Spring Session leaks across users on shared session | mitigate | Spring Session is Redis-backed (existing); each session is bound to a single user. Per Phase 10 one-shot remove pattern, `consumePendingIntent` removes the snapshot on first read. |
| T-12-09 (open-redirect) | Tampering | `prepareConnect` returns a URL string the browser navigates to | mitigate | The returned URL is a hardcoded `"/oauth2/authorization/google-calendar"` literal — same-origin path only; never accepts user input as a redirect target. |
</threat_model>

<verification>
- `cd backend && ./gradlew :backend:api:test --tests "com.zeromail.api.controllers.calendar.*" --tests "com.zeromail.api.security.Calendar*"` — backend regression green.
- `cd apps/web && pnpm --filter web run typecheck && pnpm --filter web run lint` — frontend typecheck + lint green.
- `cd apps/web && pnpm --filter web run i18n:check` — vi.json + en.json bundles in sync with feature messages.ts.
- `cd apps/web && pnpm --filter web test -- calendar` — Vitest feature tests green.
- `pnpm --filter web exec playwright test e2e/calendar-settings.spec.ts` — Playwright e2e green (manual run if sandbox dev server unavailable).
- Manual: click "Connect Google Calendar" from the empty state, complete OAuth, confirm the connection card appears with the primary calendar auto-tagged for 3 roles for the active mailbox (D-06 verification).
</verification>

<success_criteria>
- `/settings/mailboxes/{mailboxId}/calendar` route renders empty state when no connections, stacked cards when present, sub-calendar Collapsible list, Calendly-style role-assignment section.
- "Connect Google Calendar" CTA reaches Google's consent screen via POST → stamp intent → redirect (D-01 attribute path).
- Disconnect reaches DISCONNECTED state in <1s with cascade-removed role rows.
- Per-calendar Switch toggle calls PATCH `/calendars/{id}/enabled`; the response's `preferencesRemoved` count is surfaced in the success toast.
- Role-assignment Save calls PATCH `/mailboxes/{id}/preferences` with the right shape; single-select for EVENT_WRITE/BRIEF_SOURCE; multi-select for FREEBUSY.
- All toasts driven by `meta.successMessage` / `meta.errorMessage` — no local `toast.*`.
- All UI built from raw shadcn primitives + token classes — no hex colors, no wrapper components.
- Playwright e2e green for empty state + populated card + disconnect.
</success_criteria>

<output>
Create `.planning/phases/12-calendar-connection-triage-foundation/12-04-SUMMARY.md` listing: (a) the regenerated `schema.d.ts` symbols (`grep -c CalendarConnection apps/web/lib/api/schema.d.ts`), (b) the i18n bundle deltas (new key count), (c) screenshots from Playwright run showing empty state + populated card + role-assignment section (committed under `apps/web/e2e/screenshots/calendar-settings-*.png`), (d) confirmation that the OAuth round-trip lands the user on a CONNECTED card with the primary calendar already tagged for FREEBUSY/EVENT_WRITE/BRIEF_SOURCE.
</output>
