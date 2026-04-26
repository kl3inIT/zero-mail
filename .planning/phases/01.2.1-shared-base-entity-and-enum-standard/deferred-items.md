# Deferred Items — Phase 01.2.1

Out-of-scope discoveries during plan execution. Each entry is observed but NOT
fixed in 01.2.1; ownership stays with the originating phase.

## Logged during 01.2.1-04 (DTO group-by-domain + GmailConnectionStatusResponse rename)

| Discovered | Item | Origin | Status | Owner |
|------------|------|--------|--------|-------|
| 2026-04-26 | 9 failing Wave-0 RED tests in `apps/web/__tests__/docs/mdx-pipeline.test.ts` (asserting `content/docs/`, `lib/docs/loader.ts`, `app/docs/[slug]/page.tsx`, `app/docs/[slug]/loading.tsx`) — these are intentionally-RED scaffolding tests added in commit `cdd9804` (test(01.3-01)) for the future MDX docs pipeline plan. | Phase 01.3 Wave 0 | Deferred — pre-existing, not caused by 01.2.1-04 changes. | Phase 01.3 (whichever plan implements MDX docs) |
