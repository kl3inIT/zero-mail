## From Plan 04 execution (2026-04-27)

**i18n:check pre-existing failure — out of Plan 04 scope**

`pnpm i18n:check` FAILS with 4 backend-coverage issues:
- `error.gmail.identity.mismatch` missing in vi.json + en.json
- `error.gmail.consent.denied` missing in vi.json + en.json

These constants land in Plan 02 (`ErrorCodes.GMAIL_IDENTITY_MISMATCH/GMAIL_CONSENT_DENIED`).
The matching i18n keys are scheduled for Plan 05 per UI-SPEC §Copywriting Contract:
`gmail.identity.mismatch.{title,body,cta}` + `gmail.consent.denied.{title,body,cta}`.

Plan 04 owns 5 UI primitives only — adding i18n keys without the consuming UI
(error-boundary fallbacks land Plan 05) would be scope drift. Verified the
failure was pre-existing on a clean tree.
