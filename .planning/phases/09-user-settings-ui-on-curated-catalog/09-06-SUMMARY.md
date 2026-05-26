---
phase: 09-user-settings-ui-on-curated-catalog
plan: 06
subsystem: frontend-settings
tags: [nextjs, react, tanstack-query, openapi, byok, safety-net]

requires:
  - phase: 09-02
    provides: Assistant settings endpoints and DTOs
  - phase: 09-03
    provides: Safety-net audit field exposure and sender-safety APIs
  - phase: 09-04
    provides: User BYOK lifecycle endpoints and DTOs
  - phase: 09-05
    provides: Generate voice from Sent endpoint and privacy guarantees
provides:
  - Flat five-section /ai settings UI shell
  - Voice, behavior, knowledge, safety-net, and BYOK frontend features
  - Regenerated OpenAPI schema after legacy /api/llm/byok deletion
  - Audit safety-net badge in table and card audit views
affects: [phase-09-settings-ui, phase-09-07-e2e, byok, audit-log, knowledge-ui]

tech-stack:
  added: []
  patterns:
    - Feature API wrappers derive types from generated schema.d.ts
    - TanStack Query mutation toasts use meta.successMessage and meta.errorMessage
    - BYOK UI enforces Save -> Test -> Pick -> Activate lifecycle

key-files:
  created:
    - apps/web/features/ai/api/ai-settings-api.ts
    - apps/web/features/ai/api/byok-api.ts
    - apps/web/features/knowledge/
    - apps/web/features/triage/components/AuditSafetyNetBadge.tsx
  modified:
    - apps/web/features/ai/components/AiConfigPage.tsx
    - apps/web/features/ai/components/AiProviderSection.tsx
    - apps/web/features/triage/components/AuditRow.tsx
    - apps/web/features/triage/components/AuditCardList.tsx
    - apps/web/lib/api/schema.d.ts
    - apps/web/openapi/openapi.json

key-decisions:
  - "The BYOK Test button only calls POST /api/byok/test-connection after a row is saved; unsaved local edits disable Test."
  - "The model select uses the latest OK test response in local component state; existing saved model remains visible after reload."
  - "The legacy /api/llm/byok e2e spec was deleted with the legacy controller because the Settings BYOK surface no longer exists."

patterns-established:
  - "Dialog setting editors reset draft state on open-change handlers instead of synchronous setState-in-effect."
  - "Short setting mutations update TanStack Query cache optimistically and roll back on error."

requirements-completed:
  - SET-VOICE-01
  - SET-VOICE-02
  - SET-VOICE-03
  - SET-VOICE-04
  - SET-VOICE-05
  - SET-VOICE-06
  - SET-VOICE-07
  - SET-BEHV-01
  - SET-BEHV-02
  - SET-BEHV-03
  - SET-BEHV-04
  - SET-BEHV-05
  - SET-SAFE-01
  - SET-SAFE-04
  - SET-AI-01
  - SET-AI-02
  - SET-AI-03
  - SET-AI-04

duration: multi-session
completed: 2026-05-27
---

# Phase 09 Plan 06 Summary

**Frontend AI settings surface with voice personalization, knowledge CRUD, safety-net controls, BYOK lifecycle, and audit safety-net badges.**

## Performance

- **Duration:** multi-session
- **Completed:** 2026-05-27T06:45:00+07:00
- **Tasks:** 5
- **Files modified:** 71 files across web, generated API schema, and legacy backend controller deletion

## Accomplishments

- Regenerated OpenAPI and frontend types from the running backend, then removed the legacy `/api/llm/byok` schema surface after deleting the shim controller.
- Replaced the old `/ai` implementation with a flat five-section settings page: Your voice, Behavior, Updates, Safety net, AI Provider.
- Added voice and behavior typed API wrappers/hooks, edit dialogs, generate-from-sent wiring, and optimistic behavior toggles.
- Added `apps/web/features/knowledge/` with typed CRUD API, hooks, table, dialog, delete confirmation, and focused Vitest coverage.
- Added BYOK typed API/hooks and a single AI Provider card with provider, base URL, masked key, test, model, active switch, delete, and cost footer.
- Added `AuditSafetyNetBadge` and wired it into both desktop row and mobile card audit views.

## Task Commits

1. **Task 1: Schema regen and i18n/error keys** - `4b3010a5` (`feat(09-06): regenerate api schema and settings messages`)
2. **Task 2: AI settings shell and Settings link** - `80bd3b37` (`feat(09-06): add AI settings shell`)
3. **Task 3: Voice, behavior, knowledge, safety-net sections** - `73f5242d` (`feat(09-06): wire AI personalization sections`)
4. **Task 4: BYOK provider state machine** - `34c26727` (`feat(09-06): add BYOK provider settings card`)
5. **Task 5: Audit badge and legacy BYOK deletion** - `0aca3dac` (`feat(09-06): add audit safety net badge`)

## Files Created/Modified

- `apps/web/features/ai/components/AiConfigPage.tsx` - Renders the five-section `/ai` settings layout.
- `apps/web/features/ai/components/YourVoiceSection.tsx` - Voice setting cards and knowledge table composition.
- `apps/web/features/ai/components/BehaviorSection.tsx` - Auto-draft, draft confidence, and sensitive-data controls.
- `apps/web/features/ai/components/UpdatesSection.tsx` - Daily digest and pause triage controls.
- `apps/web/features/ai/components/SafetyNetSection.tsx` - Safety-net sender list plus auto-send rules card.
- `apps/web/features/ai/components/AiProviderSection.tsx` - Single BYOK card and AI cost footer.
- `apps/web/features/ai/api/ai-settings-api.ts` - Voice/behavior/generate-from-sent typed API wrappers.
- `apps/web/features/ai/api/byok-api.ts` - BYOK and AI cost typed API wrappers.
- `apps/web/features/ai/hooks/*` - TanStack Query hooks for AI settings and BYOK lifecycle.
- `apps/web/features/knowledge/` - Knowledge snippet API, hooks, table, row, dialog, query keys, and test.
- `apps/web/features/triage/components/SenderSafetyNetList.tsx` and `SenderRow.tsx` - Domain/user badges and protected delete behavior.
- `apps/web/features/triage/components/AuditSafetyNetBadge.tsx` - Localized badge for `blockedBySafetyNetPattern`.
- `apps/web/features/triage/api/triage-api.ts` - Maps `blockedBySafetyNetPattern` into the UI audit model.
- `apps/web/lib/api/errors.ts` and `apps/web/features/ai/messages.ts` - Phase 9 error/success/localized UI strings.
- `apps/web/lib/api/schema.d.ts` and `apps/web/openapi/openapi.json` - Regenerated from backend OpenAPI.
- `backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java` - Deleted legacy 410 shim.
- `apps/web/e2e/byok.spec.ts` - Deleted obsolete Settings BYOK e2e spec.

## Meta Message Keys In Use

- Voice/behavior: `ai.toast.voiceSaved`, `ai.toast.voiceGenerated`, `ai.toast.behaviorSaved`, `errors.voice.generate.failed`, `errors.voice.generate.rate_limited`.
- Knowledge: `ai.toast.snippetAdded`, `ai.toast.snippetUpdated`, `ai.toast.snippetDeleted`, `ai.toast.genericFailure`, `errors.knowledge.title.duplicate`, `errors.knowledge.not_found`.
- Safety net: `ai.toast.safetyNetAdded`, `ai.toast.safetyNetRemoved`, `errors.safety_net.pattern_invalid`, `errors.safety_net.observation_not_deletable`, `errors.safety_net.not_found`.
- BYOK: `ai.toast.byokKeySaved`, `ai.toast.byokDeleted`, `ai.toast.aiPreferenceSaved`, `errors.ai.byok.no_row`, `errors.ai.byok.no_model_picked`, `errors.ai.byok.model_not_in_last_test`, `errors.ai.byok.provider_not_allowed`, `errors.ai.byok.base_url_not_https`, `errors.ai.byok.base_url_host_private`, `errors.ai.byok.base_url_host_unresolvable`, `errors.ai.byok.base_url_port_not_allowed`, `errors.ai.byok.base_url_not_supported_for_provider`, `errors.ai.byok.test_connection.rate_limited`, `errors.ai.byok.rate_limited`, `errors.ai.byok.rate_limit_unavailable`.
- Audit badge: `audit.badge.blockedBySafetyNet`.

## Decisions Made

- The old `ByokForm` and `/settings` BYOK e2e were removed rather than adapted because the canonical BYOK surface is now `/ai` and the new backend contract is `/api/byok`.
- Test connection is disabled while the row is absent or local form state is dirty. This prevents the UI from testing a stale saved row while unsaved provider/base URL/key edits are visible.
- BYOK test-result failures render in the status pill rather than using a success toast, avoiding false positive toasts for enum responses like `INVALID_KEY`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Reworked dialog state reset to satisfy React lint**

- **Found during:** Task 3 verification
- **Issue:** The new setting dialogs used synchronous `setState` inside `useEffect`, tripping `react-hooks/set-state-in-effect`.
- **Fix:** Moved draft resets into `onOpenChange` handlers; mounted `KnowledgeDialog` only while open so initial state comes from the selected snippet.
- **Files modified:** AI dialog components and `KnowledgeTable.tsx` / `KnowledgeDialog.tsx`.
- **Verification:** `pnpm --filter web run lint` passes with only pre-existing warnings.
- **Committed in:** `73f5242d`

**2. [Rule 2 - Missing Critical] Added focused knowledge and BYOK tests to satisfy plan filters**

- **Found during:** Task 3 and Task 4 verification
- **Issue:** `pnpm --filter web test --run features/ai features/knowledge` had no new knowledge/AI provider coverage initially.
- **Fix:** Added `KnowledgeTable.test.tsx` and `AiProviderSection.test.tsx`.
- **Verification:** Final targeted Vitest run passed 4 files / 11 tests.
- **Committed in:** `73f5242d`, `34c26727`

**3. [Rule 2 - Missing Critical] Removed stale legacy BYOK e2e/spec paths after deleting controller**

- **Found during:** Task 5 grep gate
- **Issue:** `/api/llm/byok` still appeared in generated schema and obsolete e2e mocks/spec after the new `/api/byok` UI was wired.
- **Fix:** Deleted legacy shim and obsolete e2e spec, updated remaining mocks to `/api/byok`, regenerated OpenAPI/schema.
- **Verification:** `rg -n "/api/llm/byok" apps/web backend` returns no matches.
- **Committed in:** `0aca3dac`

---

**Total deviations:** 3 auto-fixed issues.
**Impact on plan:** All fixes tightened the locked plan behavior; no external dependencies or unrelated refactors were introduced.

## Verification

- `./gradlew.bat :backend:api:generateOpenApiDocs`
- `pnpm --filter web run generate:api`
- `pnpm --filter web run typecheck`
- `pnpm --filter web run lint` - passes with two pre-existing warnings in `app/(public)/page.tsx` and `coverage/lcov-report/block-navigation.js`.
- `pnpm --filter web run i18n:check`
- `pnpm --filter web test --run features/ai features/knowledge features/triage/components/SenderSafetyNetList.test.tsx features/triage/__tests__/AuditSafetyNetBadge`
- `./gradlew.bat :backend:api:compileJava`
- `git diff --check`
- `rg -n "ByokForm" apps/web backend` - no matches.
- `rg -n "/api/llm/byok" apps/web backend -g "*.ts" -g "*.tsx" -g "*.java" -g "*.json"` - no matches.
- `rg -n "toast\.(success|error)" apps/web/features/ai apps/web/features/knowledge apps/web/features/triage/components/AuditSafetyNetBadge.tsx` - no matches.
- `rg -n "bg-\[#|text-\[#|#[0-9A-Fa-f]{3,8}" apps/web/features/ai apps/web/features/knowledge apps/web/features/triage/components/AuditSafetyNetBadge.tsx apps/web/features/triage/components/AuditRow.tsx apps/web/features/triage/components/AuditCardList.tsx apps/web/features/triage/components/SenderSafetyNetList.tsx apps/web/features/triage/components/SenderRow.tsx` - no matches.

## Issues Encountered

- JetBrains MCP stayed unavailable after IntelliJ restart; `get_all_open_file_paths` timed out after 120s. Shell/apply_patch fallback was used for text-level work, while Gradle, TypeScript, ESLint, i18n, and Vitest provided verification.
- The worktree already had unrelated dirty backend security tests. They were left untouched and unstaged.

## User Setup Required

None.

## Next Phase Readiness

Plan 09-07 can perform browser/e2e validation against the new `/ai` page, BYOK state machine, knowledge CRUD, safety-net list, and audit badge. The backend legacy `/api/llm/byok` shim is gone and the generated frontend schema no longer exposes the old paths.

---
*Phase: 09-user-settings-ui-on-curated-catalog*
*Completed: 2026-05-27*
