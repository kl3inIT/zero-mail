---
status: complete
phase: 09-user-settings-ui-on-curated-catalog
source:
  - .planning/phases/09-user-settings-ui-on-curated-catalog/09-01-SUMMARY.md
  - .planning/phases/09-user-settings-ui-on-curated-catalog/09-02-SUMMARY.md
  - .planning/phases/09-user-settings-ui-on-curated-catalog/09-03-SUMMARY.md
  - .planning/phases/09-user-settings-ui-on-curated-catalog/09-04-SUMMARY.md
  - .planning/phases/09-user-settings-ui-on-curated-catalog/09-05-SUMMARY.md
  - .planning/phases/09-user-settings-ui-on-curated-catalog/09-06-SUMMARY.md
  - .planning/phases/09-user-settings-ui-on-curated-catalog/09-07-SUMMARY.md
started: 2026-05-30T22:29:51+07:00
updated: 2026-05-31T15:25:00+07:00
---

## Current Test

[testing complete]

## Tests

### 1. Automated AI Settings Golden Path
expected: Existing Playwright coverage should exercise the /ai golden path for voice persistence, behavior persistence, knowledge CRUD, safety-net EMAIL/DOMAIN round-trip, BYOK save/test gating, DOM plaintext-key guard, audit safety-net badge, and horizontal overflow guard.
result: pass
verified_by: pnpm --filter web e2e -- ai-settings.spec.ts

### 2. AI Settings Page Structure
expected: Open /ai as an authenticated user. The page should show a single flat AI settings screen with the five sections: Your voice, Behavior, Updates, Safety net, and AI Provider. It should not use tabs, query-param tab state, or the old /settings BYOK form.
result: pass

### 3. Voice Profile Editing
expected: In Your voice, the user can edit writing style, personal instructions, email signature, and output language through setting-card dialogs. Saving closes the dialog, shows a success toast, and the saved value remains after reload. The UI does not expose a separate global Tone setting; chat-saved memory appears through the same Knowledge store.
result: pass
reported: "The voice dialogs repeated the same header and field label; the global Tone setting overlapped with Writing style; chat Save memory wrote to assistant_memory while the Knowledge section read assistant_knowledge_snippet, so saved memories did not appear in Knowledge."
fixed: "Removed the user-facing Tone setting from UI/API/domain settings, changed dialog field labels so they do not duplicate dialog titles, made saveMemory write assistant_knowledge_snippet, removed addToKnowledgeBase from the active chat tool catalog, migrated legacy assistant_memory rows into Knowledge, and dropped the old assistant_memory store."
verified_by:
  - pnpm --filter web e2e -- ai-settings.spec.ts
  - pnpm --filter web run typecheck
  - pnpm --filter web run i18n:check
  - pnpm --filter web test --run __tests__/chat/tool-catalog-contract.test.ts __tests__/chat/tool-results.test.tsx
  - ./gradlew.bat :backend:core:test --tests "com.zeromail.core.chat.domain.ChatToolNameEnumTest" --tests "com.zeromail.core.chat.settings.SettingsVoiceServiceWordBoundsTest" --tests "com.zeromail.core.chat.settings.AssistantSettingsValidationTest" --tests "com.zeromail.core.chat.usecases.tools.ReadToolsIT" --tests "com.zeromail.core.chat.knowledge.AssistantKnowledgeAppendCallSiteTest" --tests "com.zeromail.core.architecture.Phase9ArchitectureTest" --tests "com.zeromail.core.support.LiquibaseMigrationTest"
  - ./gradlew.bat :backend:api:test --tests "com.zeromail.api.controllers.settings.SettingsVoiceControllerTest"

### 4. Generate Writing Style From Sent Mail
expected: In the writing-style dialog, Generate from recent sent emails enters a loading state, fills the textarea with a generated style preview when Sent samples exist, does not auto-save, and requires the user to click Save before the generated style persists.
result: pass

### 5. Knowledge Snippet Management
expected: In Your voice, the user can add, edit, and delete titled knowledge snippets. New or edited snippets appear in the table, deletion requires confirmation, duplicate titles produce a localized error, and deleted snippets disappear without a page refresh.
result: pass

### 6. Behavior And Updates Settings
expected: In Behavior and Updates, the user can toggle auto-draft replies, sensitive-data protection, daily digest, and pause/shadow behavior, and can choose LOW, MEDIUM, or HIGH draft confidence. Changes show success feedback and remain after reload.
result: pass
reported: "Pausing triage is a high-impact action but the toggle flipped silently with no warning; the only feedback was a persistent PauseBanner forced onto the top of every AppShell page."
fixed: "Replaced the always-on AppShell PauseBanner with a confirmation AlertDialog on the /ai Updates pause toggle (reusing existing shell.pause.confirm.* copy); turning pause off resumes immediately without a prompt. Removed PauseBanner from AppShell, deleted PauseBanner.tsx/.test.tsx, and dropped it from the check-i18n EN scan allowlist."
verified_by:
  - pnpm --filter web run typecheck
  - pnpm --filter web run i18n:check
  - manual browser check at /ai (Updates -> Tạm dừng triage shows confirm dialog)

### 7. Safety Net Sender Controls
expected: In Safety net, the user can add both an email pattern and a domain pattern, see Email/Domain and You/System badges, remove user-created entries with confirmation, and cannot remove system-observed entries.
result: pass

### 8. Audit Safety Net Badge
expected: When an audit item was blocked by a matched safety-net pattern, both the desktop table row and mobile audit card show a visible "Blocked by safety net: {pattern}" badge. Audit items without a matched pattern do not show the badge.
result: pass
notes: |
  Verified by code inspection of the shared AuditLog surface. Desktop: AuditTable -> AuditRow (data-testid="audit-table-row", AuditSafetyNetBadge pattern={entry.blockedBySafetyNetPattern}). Mobile: AuditCardList renders the same badge. AuditSafetyNetBadge shows "audit.badge.blockedBySafetyNet" only when pattern is non-empty, else returns null — matches present/absent expectation. Previously also covered by an e2e block in ai-settings.spec.ts.
  ARCHITECTURE CHANGE (user-directed this session): the /dashboard route + features/dashboard were deleted as redundant (no sidebar link, not a login/onboarding redirect target; audit log duplicated at /rules?tab=history, stats duplicated in shell). The audit log + safety-net badge now live only at /rules?tab=history (same shared AuditLog component). Per user instruction, the dashboard-coupled e2e was removed: the /dashboard audit-badge assertions in ai-settings.spec.ts were deleted and launch-golden-path.spec.ts step 1 was repointed from /dashboard to /rules. Net effect: Test 8 behavior still correct, but its automated e2e coverage was removed — re-add on /rules?tab=history if automated coverage is wanted.

### 9. BYOK Lifecycle
expected: In AI Provider, the user can save a supported provider, base URL, and API key; the key is never echoed in plaintext; Test connection is only available for the saved row; an OK test loads models; selecting a model enables the Active switch; toggling Active persists after reload; the 7-day cost footer displays a dollar amount.
result: pass

### 10. Runtime Settings Effects
expected: Saved settings affect observable runtime behavior: auto-draft off prevents background draft creation, signatures are appended to generated drafts, sensitive-data protection controls redaction, and an active tested BYOK model is used for chat, triage, draft, and voice-generation calls while falling back to platform defaults when inactive.
result: pass
verified_by:
  - ./gradlew.bat :backend:core:test --tests "*SensitiveDataRedactionToggleTest" --tests "*LlmGatewayByokRoutingTest" --tests "*ByokResolutionIntegrationTest" --tests "*RuleAutomationSettingsServiceTest"
  - ./gradlew.bat :backend:worker:test --tests "*DraftAutoToggleIntegrationTest" --tests "*DraftSignatureIntegrationTest"

### 11. Responsive And Dialog Fit Sweep
expected: On desktop and narrow mobile widths, the /ai sections, tables, BYOK card, writing-style dialog, and confirmation dialogs fit the viewport without horizontal overflow, clipped controls, unreadable status badges, or overlapping text.
result: pass
verified_by:
  - "Playwright sweep on /ai: mobile 375px page horizontalOverflow=0 (Knowledge table inside scroll-wrapper, no page overflow); new pause confirm dialog fits (320px, left 28 -> right 348, within viewport, readable); desktop 1440px horizontalOverflow=0, 0 overflowing elements."

## Summary

total: 11
passed: 11
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

- truth: "Voice profile editing should have clear, non-duplicative setting dialogs, avoid redundant global tone configuration, and use one user-visible knowledge store for chat-saved memory and AI settings Knowledge."
  status: resolved
  reason: "Tone is no longer a user-facing setting, dialog field labels are distinct from dialog titles, and chat Save memory now persists to the same Knowledge store shown in AI settings."
  severity: major
  test: 3
  artifacts:
    - apps/web/e2e/ai-settings.spec.ts
    - backend/core/src/main/resources/db/changelog/changes/106-merge-assistant-memory-into-knowledge.yaml
  missing: []

- truth: "Pausing automatic triage — a high-impact action that stops all auto-processing — must warn the user at the point of toggling, not rely on a banner forced onto every page."
  status: resolved
  reason: "User reported the pause toggle flipped silently and the only warning was a persistent PauseBanner on top of every AppShell page; requested a confirmation dialog instead."
  severity: major
  test: 6
  artifacts:
    - apps/web/features/ai/components/UpdatesSection.tsx
    - apps/web/components/shell/AppShell.tsx
    - apps/web/scripts/check-i18n.ts
  missing: []
  note: "PauseBanner.tsx/.test.tsx deleted. Orphan i18n keys settings.triage.pause.banner.* left in place (generated bundle, DO NOT EDIT marker; merge script overlay-only, no unused-key gate)."
