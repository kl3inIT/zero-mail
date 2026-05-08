---
phase: 02C-llm-gateway
plan: 08
subsystem: web-llm
tags: [byok, frontend, nextjs, tanstack-query, i18n, playwright]

requires:
  - phase: 02C-05b
    provides: "POST /api/llm/byok/validate, POST /api/llm/byok, GET /api/llm/byok"
  - phase: 02C-UI-SPEC
    provides: "BYOK settings-card layout, interaction, accessibility, and i18n contracts"
provides:
  - "features/llm API/hooks/component triplet for BYOK validate/save/current"
  - "Validate-then-save BYOK form mounted on /settings after automated triage and before privacy"
  - "Feature-owned messages.ts source of truth with generated vi/en bundles"
  - "Vitest privacy invariants and Playwright BYOK browser flow"
affects: [phase-04-triage, phase-05-ux, byok, billing, i18n]

tech-stack:
  added: []
  patterns:
    - "Feature-owned messages.ts merged into i18n/messages via build-time script"
    - "Uncontrolled password input with formRef-only raw key reads"
    - "Inline byokKeys.current query key with mutation-only validate/save calls"

key-files:
  created:
    - apps/web/features/llm/api/llm-api.ts
    - apps/web/features/llm/hooks/use-byok.ts
    - apps/web/features/llm/components/ByokForm.tsx
    - apps/web/features/llm/messages.ts
    - apps/web/scripts/merge-feature-i18n.ts
    - apps/web/features/llm/components/ByokForm.test.tsx
    - apps/web/__tests__/byok-key-handling.test.ts
    - apps/web/__tests__/i18n-erase-protection.test.ts
    - apps/web/e2e/byok.spec.ts
  modified:
    - apps/web/app/(protected)/settings/page.tsx
    - apps/web/i18n/messages/en.json
    - apps/web/i18n/messages/vi.json
    - apps/web/lib/api/schema.d.ts
    - apps/web/openapi/openapi.json
    - apps/web/package.json
    - apps/web/scripts/check-i18n.ts
    - .planning/ROADMAP.md

requirements-completed: [LLM-03, LLM-04, LLM-10]

duration: 95 min
completed: 2026-05-08
---

# Phase 02C Plan 08: BYOK Frontend Summary

**BYOK settings card with validate-before-save flow, feature-owned i18n, generated API types, and browser coverage.**

## Performance

- **Duration:** 95 min
- **Completed:** 2026-05-08T11:25:00+07:00
- **Tasks:** 2 implementation tasks plus OpenAPI/schema regeneration and browser verification
- **Files modified:** 17 tracked files

## Accomplishments

- Invoked the `frontend-design` skill before writing `ByokForm.tsx`; applied the UI-SPEC as a restrained product settings card rather than a marketing surface.
- Regenerated the backend OpenAPI artifact and `apps/web/lib/api/schema.d.ts` so the frontend has typed `ByokValidateRequest`, `ByokSaveRequest`, `ByokValidateResponse`, `ByokSaveResponse`, and `ByokCurrentResponse` schemas.
- Added `apps/web/features/llm/` with API functions, TanStack hooks, the raw-shadcn `ByokForm`, and co-located `messages.ts`.
- Mounted `ByokForm` on `/settings` between automated triage and privacy; removed the stale visible privacy line that said BYOK was still planned.
- Added `merge-feature-i18n.ts`, wired `pnpm i18n:build` before `pnpm build`, and generated vi/en bundles with an `_generated` marker while preserving legacy non-feature keys.
- Added 11 Vitest checks covering component behavior, raw-key handling invariants, and i18n erase protection.
- Added `e2e/byok.spec.ts` with route-mocked desktop and 375x812 mobile browser coverage.

## Key Decisions

- `messages.ts` is the source of truth for BYOK copy per D-D5. The generated JSON bundles are emitted by `pnpm i18n:build`; they now include 32 feature-sourced keys: 23 under `llm.byok.*` and 9 under `errors.llm.*`.
- Both camelCase plan keys and snake_case backend error-code keys were emitted for LLM errors so `useLocalizedApiError` resolves current backend codes such as `error.llm.safety_violation` and `error.llm.byok.validate_failed`.
- The raw API key is read only from `formRef.current?.elements.namedItem('apiKey')` during validate/save. React state tracks only provider, endpoint, booleans, and non-secret response state.
- The test-only form-state snapshot is gated to `NODE_ENV === 'test'` and `hidden`, so dev/prod browser surfaces do not expose debug JSON to the accessibility tree.
- OpenAPI generation used the hermetic Gradle OpenAPI task plus dummy `SPRING_AI_OPENAI_API_KEY`/`SPRING_AI_OPENAI_BASE_URL` env vars because Spring AI OpenAI auto-configuration now requires the Spring AI key in addition to the project `zero-mail.llm.platform.api-key`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Blocking] Spring AI OpenAI auto-config needed dummy env vars for OpenAPI emit**
- **Issue:** `:backend:api:generateOpenApiDocs` failed because `openAiAudioSpeechModel` required `spring.ai.openai.api-key`.
- **Fix:** Reran the task with dummy `SPRING_AI_OPENAI_API_KEY=openapi-emit` and `SPRING_AI_OPENAI_BASE_URL=https://openrouter.ai/api/v1`; no backend code change was needed.
- **Verification:** `pnpm -C apps/web generate:api` picked up `/api/llm/byok` and `/api/llm/byok/validate`.

**2. [Browser verification] Password fill needed input-event tracking**
- **Issue:** Real Playwright `fill()` updated the uncontrolled password DOM value but did not flip the non-secret `hasApiKey` boolean through the first handler shape.
- **Fix:** Added `onInput` alongside `onChange` for the password field, both updating only key-presence state.
- **Verification:** Playwright desktop and mobile BYOK specs passed.

**3. [Accessibility cleanup] Test-only state snapshot appeared in browser snapshots**
- **Issue:** The `sr-only` debug snapshot was still in the accessibility tree.
- **Fix:** Gated it to `NODE_ENV === 'test'` and rendered it hidden.
- **Verification:** MCP browser snapshot no longer contains the JSON debug state.

## Verification

- `./gradlew.bat :backend:api:generateOpenApiDocs` - passed with dummy Spring AI env vars.
- `pnpm -C apps/web generate:api` - passed.
- `pnpm -C apps/web typecheck` - passed.
- `pnpm -C apps/web i18n:check` - passed; 355 vi/en leaf keys.
- `pnpm -C apps/web exec vitest run features/llm/components/ByokForm.test.tsx __tests__/byok-key-handling.test.ts __tests__/i18n-erase-protection.test.ts` - passed; 11 tests.
- `pnpm -C apps/web lint` - passed.
- `pnpm -C apps/web exec playwright test e2e/byok.spec.ts --reporter=line` - passed; 2 Chromium tests.
- Playwright MCP manual pass on `http://localhost:3000/settings` - passed: BYOK card renders in the correct settings order, validation enables save, save clears the password input, and no console warnings/errors were reported.

## User Setup Required

- The local Next dev server is running at `http://localhost:3000` for review.
- Browser replay command: `pnpm -C apps/web exec playwright test e2e/byok.spec.ts --reporter=line`.

## Next Phase Readiness

- Phase 4/5 can rely on localized `errors.llm.insufficientCredits.*`, safety, sanitization, and BYOK validation copy.
- BYOK list-display and revoke-key flows remain out of scope for Phase 02C and should be handled in Phase 5 UX if needed.

---
*Phase: 02C-llm-gateway*
*Completed: 2026-05-08*
