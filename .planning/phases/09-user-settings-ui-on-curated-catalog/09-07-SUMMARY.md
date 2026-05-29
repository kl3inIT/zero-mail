---
phase: 09-user-settings-ui-on-curated-catalog
plan: 07
subsystem: validation-closeout
tags: [playwright, archunit, phase-validation, ai-settings, byok]

requires:
  - phase: 09-06
    provides: Frontend AI settings surface and generated API schema
provides:
  - Executable Playwright golden path for `/ai`
  - Aggregate Phase 9 ArchUnit guard
  - Manual checkpoint sign-off
  - Phase 9 validation and roadmap closeout
affects: [phase-09-settings-ui, phase-09-validation, byok, voice-generation]

key-files:
  created:
    - backend/core/src/test/java/com/zeromail/core/architecture/Phase9ArchitectureTest.java
  modified:
    - apps/web/e2e/ai-settings.spec.ts
    - apps/web/features/ai/components/AiProviderSection.tsx
    - apps/web/features/ai/components/WritingStyleDialog.tsx
    - apps/web/features/ai/messages.ts
    - .planning/phases/09-user-settings-ui-on-curated-catalog/09-VALIDATION.md

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
completed: 2026-05-29
---

# Phase 09 Plan 07 Summary

**Phase 9 validation closeout: `/ai` e2e coverage, aggregate architecture guard, and manual UX sign-off.**

## Performance

- **Duration:** multi-session
- **Completed:** 2026-05-29T10:43:44+07:00
- **Tasks:** 3
- **Files modified:** 23 app/test/planning files plus regenerated i18n/schema outputs

## Accomplishments

- Replaced the skipped Phase 9 Playwright stub with an executable `/ai` golden path covering voice persistence, personal instructions, behavior toggles, draft-confidence selection, knowledge CRUD, safety-net EMAIL/DOMAIN round-trip, BYOK save/test gating, DOM plaintext-key guard, audit safety-net badge, and horizontal overflow guard.
- Added `Phase9ArchitectureTest` as the aggregate ArchUnit entry point for Phase 9 invariants: personalization sanitizer call sites, knowledge append callers, knowledge repository write site, provider tester binding, user BYOK package confinement, and Gmail API access through `GmailSentMessagesReader`.
- Fixed live bugs surfaced during manual validation: tenant-bound async reply-status classification, no-tool Spring AI preview generation, writing-style dialog viewport overflow, and BYOK status readability / save-before-test copy.
- Updated generated OpenAPI/frontend schema and i18n bundles after the main merge and UI copy changes.
- Recorded final validation status in `09-VALIDATION.md` with all automated rows green and manual checkpoint approved.

## Manual Checkpoint

- **Visual sweep / dialog fit:** approved by developer in chat on 2026-05-29 after the writing-style dialog was widened and capped to the viewport.
- **Live SET-VOICE-07:** approved by developer in chat on 2026-05-29 after the generate-from-recent-sent flow was fixed and retested.
- **BYOK save/test UX:** approved by developer in chat on 2026-05-29. The UI intentionally saves the key before testing because `POST /api/byok/test-connection` uses the encrypted saved row, not plaintext request payload.
- **BYOK status badge:** approved by developer in chat on 2026-05-29 after changing the muted `OK` chip into a green `Kết nối OK` / `Connection OK` badge with a check icon.

## Verification

- `./gradlew.bat :backend:api:generateOpenApiDocs` — passed.
- `pnpm --filter web generate:api` — passed.
- `pnpm --filter web run typecheck` — passed.
- `pnpm --filter web e2e -- ai-settings.spec.ts` — passed.
- `pnpm --filter web test --run features/ai/AiProviderSection.test.tsx` — passed.
- `pnpm --filter web run i18n:check` — passed.
- `pnpm --filter web run lint` — passed with one pre-existing coverage report warning.
- `./gradlew.bat :backend:core:test --tests SpringAiLlmModelClientTest --tests SpringAiProviderChatExecutorTest --tests VoiceGenerationRateLimitTest --tests GmailSentMessagesReaderAggregateCapTest` — passed.
- JetBrains project build for modified Java files — passed.
- `git diff --check` — passed.
- Browser-level Playwright check confirmed `Kết nối OK` renders with `bg-green-soft text-green` styling.

## Notes

- Full Docker/Testcontainers suites were not rerun after the final UI polish because the local Docker environment was previously unavailable; targeted backend and frontend validation passed.
- Provider keys and Gmail body content were not captured by the agent. Manual live-provider / live-Gmail verification is represented by developer approval in chat, not by persisted secrets or logs.

## Next Step

Phase 9 is complete. The remaining v1.2 execution work is Phase 08.1 Plan 06: runtime outbound execution and fallback-to-draft gates.

