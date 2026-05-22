---
status: complete
date: 2026-05-21
---

# Summary

- Translated Vietnamese cleanup unsubscribe, suppression, and cleanup navigation copy so the visible UI is Vietnamese-first.
- Regenerated `apps/web/i18n/messages/en.json` and `apps/web/i18n/messages/vi.json`.
- Updated cleanup Playwright assertions to match the new Vietnamese copy.

# Verification

- `pnpm --filter web i18n:build`
- `pnpm --filter web i18n:check`
- `pnpm --filter web exec eslint features/cleanup/unsubscribe-campaign/messages.ts features/cleanup/suppression/messages.ts features/shell/messages.ts e2e/cleanup-unsubscribe-campaign.spec.ts e2e/cleanup-suppression.spec.ts`
- `pnpm exec playwright test --config=playwright.cleanup.config.ts --reporter=list` from `apps/web` passed 4/4.
