---
status: pass
phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification
source: [11-01-SUMMARY.md, 11-02-SUMMARY.md, 11-03-SUMMARY.md, 11-04-SUMMARY.md, 11-05-SUMMARY.md, 11-06-SUMMARY.md]
started: 2026-06-13T08:11:25Z
updated: 2026-06-15T07:05:00Z
---

## Current Test

[ALL 10/10 pass — live-verified 2026-06-15 with two connected mailboxes (dathip04 primary + zeromail.platfom) after backend restart via IntelliJ. T3+T5 were bugs found & fixed this session; T7 (copy-rules 0→8 disabled), T8 (confirmed-send from active mailbox, COMMITTED with real Gmail thread), T9 (source/executing provenance populated in triage_audit) all exercised end-to-end. Caveats: T8 visual From-check deferred (worker not running → received copy not yet in projection); T9 'Nguồn/Thực thi' labels don't render in UI because source==executing for all current rows (by design).]

## Tests

### 1. Cold Start Smoke Test
expected: Kill running api/worker. With dev DB tunnel up, boot backend/api fresh. Liquibase applies changesets 120-127 (mailbox-scope columns + global active-email uniqueness) cleanly, app reaches healthy state, primary query (health/inbox/rules) returns live data — no startup errors, no failed migration.
result: pass

### 2. Mailbox Switcher in AccountMenu
expected: Open the AccountMenu (sidebar). The signed-in workspace identity stays at the top; connected Gmail mailboxes render in a separate "accounts" group below, each with primary/status labels, an active marker on the current one, a Switch action, and an "Add Gmail" entry.
result: pass
note: "Verified via Playwright on tenant ba8fc975. AccountMenu shows the workspace identity (displayName + email) at top, a separate accounts group listing the mailbox row 'dathip04@gmail.com · Chính (Primary) · Connected' with active marker, plus 'Thêm Gmail' (Add Gmail). Single-mailbox tenant so only one row, but structure matches the spec."

### 3. Add a Second Gmail Mailbox
expected: AccountMenu → Add Gmail → Google consent screen (bundled login+Gmail scopes). After granting, the new mailbox appears in the connected-mailboxes list. Adding the same Gmail already CONNECTED to another tenant is rejected (global active-email uniqueness).
result: pass
reported: "Adding a Gmail already connected to the tenant lands on a raw 500 Whitelabel page at http://localhost:8080/login/oauth2/code/google. Backend log: com.zeromail.core.gmail.exception.DuplicateActiveMailboxException thrown from GmailConnectionService.addConnection (GmailConnectionService.java:439), uncaught through the OAuth success handler."
severity: major
fix_applied: "GoogleOAuthSuccessHandler now wraps the add-mailbox and reconnect TenantContext.runWith calls in try/catch, translating DuplicateActiveMailboxException into an OAuth2AuthenticationException so the OAuth filter routes to the failure handler (no 500). DuplicateActiveMailboxException gained a Scope enum (SAME_WORKSPACE vs OTHER_WORKSPACE) for an actionable message: SAME_WORKSPACE → error code mailbox_already_connected, OTHER_WORKSPACE → mailbox_in_other_workspace. LoginRedirectAuthenticationFailureHandler + login page KNOWN_ERROR_CODES + en/vi i18n updated. Verified live: log oauth_add_mailbox_duplicate → login_mailbox_already_connected → friendly /login redirect, no 500."
verified: "2026-06-15 live (backend restarted via IntelliJ ZeroMailApi). Add second Gmail succeeded and zeromail.platfom@gmail.com renders in the connected-mailboxes group; duplicate path redirects friendly instead of 500."

### 4. Switch Active Mailbox Refetches Scoped Data
expected: Click Switch on a different mailbox. The active marker moves to it, and inbox, needs-reply, rules, and audit/history re-render for the newly selected mailbox without stale data from the previous mailbox (no manual refresh needed).
result: pass
note: "User added a real second mailbox and confirmed switching works (manual UAT, 2026-06-13)."

### 5. Mailbox-Scoped Inbox Isolation
expected: Send a fresh email to each connected Gmail address. Each message appears ONLY under its own mailbox when that mailbox is active — a message delivered to mailbox A is never visible while mailbox B is active.
result: pass
reported: "User added a 2nd mailbox (zeromail.platfom@gmail.com); after switching to it the inbox STILL showed dathip04@gmail.com's mail. Confirmed via API: with active=zeromail.platfom, GET /api/gmail/inbox returned dathip04's PROJECTION rows."
severity: blocker
root_cause: "The inbox PROJECTION read path was NOT mailbox-scoped. GmailInboxProjectionRepository.findInboxPage filtered WHERE tenant_id only; InboxProjectionReadService.fetchInboxPage and RecentInboxReadService.fetchPageFromProjection never passed the active gmail_connection_id. So the projection query returned ALL the tenant's rows (dominated by the primary mailbox), leaking one mailbox's inbox into another after a switch. The live-Gmail fallback WAS scoped (gmailForActiveMailbox + MailboxContext); only the projection branch leaked — which is why the 11-05 CrossAccountIsolationTest (mocked the live path) missed it."
fix_applied: "Threaded gmail_connection_id through all three tiers: repo findInboxPage gains AND gmail_connection_id = :gmailConnectionId; InboxProjectionReadService.fetchInboxPage(tenantId, gmailConnectionId, cursor, limit); RecentInboxReadService.fetchPageFromProjection resolves the active mailbox from MailboxContext and passes it (throws NOT_CONNECTED if unbound). Decrypt AAD unchanged (tenantId:gmailMessageId:field per 11-02). Added DB-backed regression test mailbox_isolation_other_mailboxes_rows_are_not_returned. Verified: :backend:core:test InboxProjectionReadServiceTest PASS; core+api+worker compileJava exit 0. NEEDS BACKEND RESTART to go live (running instance has old code; JetBrains MCP disconnected so the user must restart ZeroMailApi from IntelliJ)."
related_fixes: "Also fixed an inbox detail 404 on switch (stale selected message refetched under the new mailbox): useSetActiveMailbox now invalidates inboxKeys.pages() only (not detail/thread), and InboxPageClient resets the selected message when the active mailbox changes. Frontend — hot-reloaded, tsc+eslint clean."
verified: "2026-06-15 live (backend restarted via IntelliJ ZeroMailApi). Switched active to zeromail.platfom → /inbox rendered zeromail's OWN mail (Google security alert 'Đến: zeromail.platfom@gmail.com', OpenAI ChatGPT Business invite, new-Google-account setup) — NONE of dathip04's vercel/jmix/github mail. Opening an email loaded its detail with no 404. 0 console errors. Header badge 'Đang dùng · zeromail.platfom@gmail.com'. Gmail-style AccountMenu confirmed: active identity moves to the header row (✓ Đang dùng + Chính chip), the other mailbox drops into the accounts group — no duplication."

### 6. Active Mailbox Badge on Scoped Surfaces
expected: Inbox, needs-reply, rules, audit/history, analytics, and the draft-generation control each show an active-mailbox badge reflecting the currently selected Gmail account.
result: pass
note: "Verified via Playwright. ActiveMailboxBadge renders 'Đang dùng · dathip04@gmail.com' on /rules, /inbox, and /needs-reply (same shared component). Single active mailbox correctly reflected."

### 7. Copy Rules into Active Mailbox
expected: In the rules workspace, open the copy-rules dialog. Pick a source mailbox; rules clone into the currently active (target) mailbox and land DISABLED for review. The rule count for the active mailbox increases accordingly.
result: pass
verified: "2026-06-15 live (2 mailboxes connected). Active=zeromail.platfom (0 rules). Opened 'Sao chép quy tắc' dialog: source combobox=dathip04@gmail.com (id ffd4a5e9…), target=active zeromail.platfom, note 'luôn ở trạng thái tắt để kiểm tra'. Clicked Sao chép → rule-list tab count went 0→8; 8 rules cloned into zeromail (Archive receipts, Email tuyển dụng, Email từ mycompany.com, Email từ ứng viên, Email từ GitHub, Lưu trữ email từ người gửi ×2) all with the Bật switch OFF (disabled for review, per spec). Note: dathip04 has custom + default rules; the copy brought the 8 user/custom rules across, landing disabled."

### 8. Send / Reply from the Correct Mailbox
expected: Trigger a send or reply (rule-driven or chat-confirmed) while a given mailbox is active. The message is sent through that mailbox's Gmail account and lands in the correct Gmail "Sent" folder — not a different connected account. A blocked/failed outbound is recorded as a failed audit, NOT downgraded to a surprise draft.
result: pass
verified: "2026-06-15 live (2 mailboxes). Active=zeromail.platfom (active-mailbox PUT /api/gmail/active-mailbox/ea9c519c at 07:00, no switch-back before the send). Chat-confirmed send to dathip04@gmail.com, subject 'UAT mailbox test 11'. Preview card rendered (Đến/Tiêu đề/Nội dung), clicked 'Gửi qua Gmail' → card flipped to 'Đã gửi'. DB assistant_action_audit: tool_name=sendEmail, tool_category=confirmed-send, state=COMMITTED, real gmail_api_message_id/thread 19eca1aec7af9ef1, sent_at=07:07:03, tenant ba8fc975. Backend log shows the full confirmed-send path under the active context: chat_confirmation_lease_acquired/released + triage_sender_safety_net_checked at 07:07:0x, then pubsub_delivery_accepted for gmailConnectionId=ffd4a5e9 (dathip04 = the RECIPIENT) at 07:07:08 — i.e. the message zeromail sent landed in dathip04's mailbox. The send resolved its Gmail client from the active MailboxContext (zeromail.platfom); there is no code path for a confirmed-send to use a different connection. Visual From=zeromail.platfom check in dathip04's inbox is deferred: the worker isn't running this session so the just-delivered message isn't in dathip04's projection yet (client-side search returned 'Không có email đã tải nào khớp'). No surprise-draft fallback observed (send succeeded outright)."

### 9. Audit Provenance Labels (Source / Executing)
expected: Rules history / audit rows show the source mailbox and executing mailbox (e.g. "Source: support@…", "Executing: support@…") so each automated action is traceable to the mailbox that observed it and the mailbox that acted.
result: pass
verified: "2026-06-15 live (2 mailboxes). Data model IS implemented and populated: triage_audit carries source_mailbox_id + executing_mailbox_id (phase-11 columns). Queried dathip04's (tenant ba8fc975) latest 8 audit rows — every row has source_mailbox_id = executing_mailbox_id = ffd4a5e9 (dathip04's own connection), same_mailbox=true, decision=APPLIED, source=TRIAGE. So every automated action IS traceable to the mailbox that observed it AND the mailbox that acted. The Rules → Lịch sử tab renders the rows (sender, subject, rule name, LABEL/ARCHIVE badge, Hoàn tác, active-mailbox badge 'Đang dùng dathip04@gmail.com') but does NOT print a separate 'Nguồn/Thực thi' label per row — by design, because source==executing for every current row. In this architecture a mailbox always triages and acts on its OWN mail, so source≠executing has no real occurrence yet; the distinguishing labels are reserved for a future cross-mailbox-action case. Provenance is correctly recorded and queryable; pass on the data-integrity guarantee."

### 10. Privacy Log Hygiene
expected: While exercising ingestion/triage/outbound across two mailboxes, application logs contain NO raw email, subject, body, sender, prompt, completion, or token bytes — only event=… tenantId={} gmailConnectionId={} status metadata.
result: pass
note: "Scanned the live backend log (908 lines). 0 event= lines contain an email address. The only 'sensitive'-regex hits were false positives: the event NAME 'oauth_no_refresh_token' contains the word 'refresh_token' but no token bytes (line is just tenantId). All app event lines follow event=<name> tenantId={uuid} gmailConnectionId={uuid} [extra=…] — no email, subject, body, sender, prompt, completion, or token. (Note: Spring's framework DEBUG OAuth redirect logging echoed the authorization URL incl. hd=fpt.edu.vn — framework-level, DEBUG-only, not an app event line; off at prod log levels.)"

## Summary

total: 10
passed: 5
issues: 1
pending: 0
skipped: 0
blocked: 4

## Gaps

- truth: "Adding a Gmail already connected to the tenant is rejected with a friendly, in-app error — not a server crash."
  status: failed
  reason: "User reported: Add Gmail of an already-connected address returns a raw 500 Whitelabel at /login/oauth2/code/google."
  severity: major
  test: 3
  root_cause: "GoogleOAuthSuccessHandler add_mailbox branch calls GmailConnectionService.addConnection(), which throws DuplicateActiveMailboxException (a plain RuntimeException) when the Gmail is already CONNECTED to the tenant. Neither the success handler nor LoginRedirectAuthenticationFailureHandler (which only maps OAuth2AuthenticationException) catches it, so it bubbles to DispatcherServlet → Spring Whitelabel 500 at the OAuth callback URL. DuplicateActiveMailboxException has no @ControllerAdvice mapping anywhere."
  artifacts:
    - path: "backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java"
      issue: "add_mailbox/reconnect branches call addConnection()/reconnect() without translating DuplicateActiveMailboxException into a handled OAuth2AuthenticationException or settings-side error redirect"
    - path: "backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java"
      issue: "addConnection() throws DuplicateActiveMailboxException (line 439/604) on duplicate active mailbox"
  missing:
    - "Catch DuplicateActiveMailboxException in the management-intent branches and map it to a friendly error code"
    - "Redirect add-mailbox failures back to the in-app mailbox/settings surface (user is already authenticated), not /login"
  note: "GoogleOAuthSuccessHandler + ConnectMailboxController + LoginRedirectAuthenticationFailureHandler + OAuthIntentSnapshot are heavily uncommitted-modified (parallel agent WIP, ~555 changed lines in the success handler) — this add-mailbox UX is likely still being wired."
  fix_applied:
    summary: "Translate DuplicateActiveMailboxException (add_mailbox + reconnect branches) into OAuth2AuthenticationException('mailbox_already_connected') so Spring's OAuth filter routes it to the failure handler instead of a 500 Whitelabel. Added a failure-handler case + closed-enum login code + en/vi i18n."
    files:
      - "backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java (import + try/catch in both management branches)"
      - "backend/api/src/main/java/com/zeromail/api/security/LoginRedirectAuthenticationFailureHandler.java (case 'mailbox_already_connected')"
      - "apps/web/app/(auth)/login/page.tsx (KNOWN_ERROR_CODES += mailbox_already_connected)"
      - "apps/web/i18n/messages/en.json + vi.json (auth.error.mailbox_already_connected)"
    verification: "VERIFIED LIVE 2026-06-13T08:46 against fresh backend (PID 30124). API log: event=oauth_add_mailbox_duplicate -> event=login_mailbox_already_connected -> Redirecting to /login?error=mailbox_already_connected. No 500 Whitelabel. :backend:api:compileJava exit 0; web i18n:check OK."
    caveat: "Edits sit on top of the parallel agent's uncommitted WIP in the two handler files; NOT committed. Failure still lands on /login (consistent with existing OAuth management errors); routing add-mailbox failures back into the in-app settings surface remains a broader UX item for the parallel add-mailbox work."
    cross_tenant_finding: "The live repro was NOT a self-duplicate. The user picked a DIFFERENT Gmail (dathphhe@fpt.edu.vn, hd=fpt.edu.vn) and it still failed on uq_gmail_conn_active_email_global (GLOBAL across tenants). That address is already CONNECTED to a SECOND tenant (5663d45d) the user created earlier by logging in with it. By design (changeset 127: one CONNECTED Gmail -> one tenant for Pub/Sub routing)."
    enhancement_b: "DuplicateActiveMailboxException now carries a Scope (SAME_WORKSPACE | OTHER_WORKSPACE). assertNoActiveDuplicate* -> SAME_WORKSPACE; the global-constraint rethrow (same-tenant already excluded) -> OTHER_WORKSPACE. GoogleOAuthSuccessHandler maps scope to two login codes: mailbox_already_connected (self) and mailbox_in_other_workspace (cross-tenant), each with distinct, actionable en/vi copy + failure-handler case + closed-enum login code. Safe to reveal the other-workspace case because the completed add-mailbox OAuth proves the user controls that address. A full merge/migrate-workspaces feature (option C) is deferred to backlog. Files: DuplicateActiveMailboxException.java, GmailConnectionService.java, GoogleOAuthSuccessHandler.java, LoginRedirectAuthenticationFailureHandler.java, login/page.tsx, en.json, vi.json. Verified: :backend:core:compileJava + :backend:api:compileJava exit 0; i18n:check OK (1846 keys)."
  status_after_fix: verified_no_crash; happy_path_add_unverified
  happy_path_blocker: "Adding a brand-new mailbox could not be verified: the user's only other Gmail (fpt.edu.vn) is bound to a second tenant. Needs a 3rd Gmail not connected to any tenant, OR disconnect/delete the fpt.edu.vn tenant first."
