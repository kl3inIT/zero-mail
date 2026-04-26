# tools/i18n-key-coverage

Standalone wrapper for the Zero Mail vi/en translation parity gate.

```bash
node tools/i18n-key-coverage/index.mjs
```

Delegates to `pnpm --filter web i18n:check`, which runs
`apps/web/scripts/check-i18n.ts` and exits non-zero on:

1. **Parity drift** — any leaf key present in `apps/web/messages/vi.json` but
   not `apps/web/messages/en.json` (or vice versa).
2. **Backend ErrorCodes coverage gap** — any dotted constant in
   `backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java` not
   reachable as a non-empty string under `errors.*` in BOTH bundles.
3. **Locked-key violation** — `errors.validation.generic` missing, OR a
   rejected `errors.validation_` shape present (CONTEXT.md ISS-002).
4. **English-prose regression** — hard-coded English string literals in
   Phase 1 in-scope files (login / onboarding / settings pages and the four
   modified components).

## CI integration

CI runs `./gradlew check` (backend tests + ArchUnit) and
`node tools/i18n-key-coverage/index.mjs` as **independent** steps. We do NOT
plug this into Gradle's task graph — per CLAUDE.md "Running Gradle's Node
plugin" anti-pattern (slow + brittle on Windows + fights Turborepo cache).

The GitHub Actions workflow lives at `.github/workflows/i18n-check.yml`.
