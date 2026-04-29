---
phase: 02A
review_cycle: 7
reviewers: [codex]
reviewed_at: 2026-04-29T11:37:04.4286945+07:00
follow_up_to_cycle: 6
fix_commit: e10e863
plans_reviewed:
  - 02A-00-PLAN.md
  - 02A-01-PLAN.md
  - 02A-02-PLAN.md
  - 02A-03-PLAN.md
  - 02A-04-PLAN.md
  - 02A-05-PLAN.md
current_high: 2
---

# Cross-AI Plan Review - Phase 02A (Cycle 7)

Only the Codex reviewer was requested and invoked for this follow-up convergence cycle, so this is a single-reviewer synthesis rather than a multi-reviewer consensus.

Manual fix commit `e10e863` fully resolved Cycle 6's HIGH concern at the plan-text and test-contract level. The prior Cycle 5 stale `PROCESSING` reclaim HIGH fixed in `b2ae18b` also remains resolved. This Cycle 7 review found two current HIGH concerns.

## Codex Review

### Summary

The latest `e10e863` update resolves the specific watch-renewal cursor regression at the plan-text and test-contract level: `recordWatchSuccess` now initializes `last_synced_history_id` only when null and adds scheduler coverage for preserving an existing cursor. The plan is much stronger than Cycle 6, but two current HIGH concerns still threaten Phase 2A's core guarantees: the history processor depends on Gmail `history.list` message objects containing labels/internalDate, and the Pub/Sub `SecurityFilterChain` ordering is specified in a way that may not actually order the chain in production.

### Prior HIGH Resolution

- **Cycle 6 HIGH:** Fully resolved. The latest plan preserves a non-null `last_synced_history_id` on watch renewal and adds `renew_existingHistoryPointer_doesNotAdvanceLastSyncedHistoryId()` coverage.
- **Cycle 5 HIGH:** Fully resolved. `claimPendingBatch` still atomically updates eligible `PENDING` and expired `PROCESSING` rows to `PROCESSING`, refreshes `locked_until`, increments attempts, and returns rows in one statement.

### Strengths

- Watch renewal cursor handling is now explicit, tested, and consistent across context, research, patterns, and Plan 02.
- The stale `PROCESSING` reclaim design remains correct for worker crash recovery.
- `GmailHistoryProcessor` delegates transactional delivery processing to a public Spring service method, avoiding the private `@Transactional` trap.
- Pub/Sub persistence is correctly moved out of the controller into `PubSubIngestionService`, with unscoped email lookup before tenant-bound insert.
- Test-profile auth is now much better specified for `/me` and `/tenant/triage-pause`, while Pub/Sub OIDC remains machine-authenticated.
- Frontend reconnect gating is anchored at the real settings-page mount point, not the presentational `ReconnectPrompt`.

### Concerns

- **HIGH - Gmail history processing may skip every real inbox message.** Plan 02 filters `messagesAdded` by `msg.getLabelIds().contains("INBOX")` and stores `msg.getInternalDate()`. Google's `users.history.list` docs state that messages in the response typically only include `id` and `threadId`, and separately provide a `labelId` query parameter to filter history results by label. If `labelIds` is absent, the current loop drops all `messagesAdded`, producing no `mail_message_observed` rows and failing MAIL-01. Source: [Gmail users.history.list](https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.history/list).
- **HIGH - Pub/Sub SecurityFilterChain ordering is specified on the configuration class, not the chain bean.** Plan 03 instructs `@Configuration @Order(1)` on `PubSubSecurityConfig` and `@Order(2)` on `SecurityConfig`. Spring Security's documented multiple-chain examples place `@Order` on the `SecurityFilterChain @Bean` method. If class-level ordering does not order the produced chain beans, the catch-all user OAuth chain can intercept `/internal/pubsub/**` before the Pub/Sub OIDC chain in production. Source: [Spring Security 7 multiple HttpSecurity docs](https://docs.spring.io/spring-security/reference/7.0/servlet/configuration/java).
- **MEDIUM - Watch scheduler selection is still not a durable claim.** `findConnectionsNeedingWatchRenewal` uses `FOR UPDATE SKIP LOCKED`, but the transaction ends before the Gmail API call. With multiple workers, the same row can be renewed concurrently. The cursor skip is now fixed, but duplicate `users.watch` calls and failure-counter races remain.
- **MEDIUM - Pub/Sub OIDC wrong-issuer coverage is inconsistent.** Research calls out issuer verification, but Wave 0/Plan 03 test counts cover valid, wrong audience, wrong email, expired, bad signature, and non-Pub/Sub skip. Add an explicit wrong-issuer test.
- **LOW - Missing or blank Pub/Sub `messageId` is still not explicitly ack-dropped.** The controller validates data/email/historyId but passes `messageId` through to insertion. Google should supply it, but the ack-fast malformed-payload policy should guard it explicitly.
- **LOW - OAuth reconnect cleanup should be scoped precisely.** `clearForReconnect` is correct for explicit reconnect/re-consent. The plan should say not to run it for ordinary login paths that preserve an existing healthy connection.

### Suggestions

- In Plan 02, change history handling to either call `history.list(...).setLabelId("INBOX")` and emit observed rows from returned `messagesAdded`, or fetch message metadata per candidate with fields limited to `id,threadId,labelIds,internalDate`. Add a test where `messagesAdded.message` has only `id/threadId`.
- Move `@Order(1)` and `@Order(2)` onto the actual `SecurityFilterChain @Bean` methods. Add a production-profile integration test with both chains loaded: missing Pub/Sub auth must return 401, not OAuth redirect.
- Add `wrongIssuer_returns401()` to `PubSubOidcAuthFilterTest`.
- Add a controller guard for null/blank `messageId`: log an opaque event and return `200 OK` without inserting.
- Clarify `clearForReconnect` is called only in the existing-user reconnect path after a new refresh token is obtained.

### Risk Assessment

Overall risk: **HIGH**. The prior cursor-skip HIGH is fixed, but the current history fan-out plan can fail the main Phase 2A success criterion by dropping new inbox messages, and the security-chain ordering ambiguity can break Pub/Sub delivery in production even if tests pass under the test profile.

CURRENT_HIGH_COUNT: 2

### Current HIGH Concerns

- Gmail `history.list` response messages may not contain `labelIds` or `internalDate`, so the planned INBOX filter can skip all real `messagesAdded` and produce no `MessageObserved` rows.
- Pub/Sub `SecurityFilterChain` ordering is specified at configuration-class level instead of on the `SecurityFilterChain @Bean`, risking the user OAuth chain intercepting `/internal/pubsub/**` before OIDC verification in production.

---

## Consensus Summary

Only Codex was invoked in Cycle 7, so the consensus summary reflects a single external review.

### Agreed Strengths

- Cycle 6's watch-renewal cursor skip gap is fully resolved by preserving a non-null `last_synced_history_id` during renewal and adding scheduler coverage.
- Cycle 5's stale `PROCESSING` reclaim gap remains resolved by the atomic claim-and-return query.
- Pub/Sub persistence boundaries, transactional delivery processing, test-profile auth scoping, and frontend reconnect placement remain materially strong.

### Agreed Concerns

- HIGH: Gmail `history.list` message payload assumptions can cause all new inbox messages to be skipped if `labelIds` and `internalDate` are absent from returned `messagesAdded` entries.
- HIGH: Pub/Sub `SecurityFilterChain` ordering should be attached to the chain beans, not only to configuration classes, to avoid the catch-all OAuth chain intercepting Pub/Sub pushes.
- MEDIUM: Watch renewal selection still lacks a durable claim across the external Gmail API call.
- MEDIUM: Pub/Sub OIDC tests should explicitly cover wrong issuer.
- LOW: Missing Pub/Sub `messageId` should be explicitly ack-dropped.
- LOW: Reconnect cleanup should be scoped to explicit reconnect/re-consent only.

### Divergent Views

- None observed. Only one reviewer was invoked.

## Cycle Summary

- Prior Cycle 6 HIGH concerns: 1
- Fully resolved prior Cycle 6 HIGH concerns: 1
- Prior Cycle 5 HIGH concerns still resolved: 1
- Partially resolved prior HIGH concerns: 0
- Previously raised HIGH concerns still unresolved: 0
- New Cycle 7 HIGH concerns: 2
- Current unresolved HIGH concerns: 2
