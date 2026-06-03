---
status: complete
date: 2026-05-21
---

# Summary

- Fixed the cleanup unsubscribe filter and sort selects so their closed trigger labels render localized text instead of raw enum values like `ALL` and `SENDER_ASC`.
- Added cleanup e2e assertions for the default Vietnamese filter and sort labels.

# Verification

- `pnpm --filter web exec eslint features/cleanup/unsubscribe-campaign/components/CandidateListPage.tsx e2e/cleanup-unsubscribe-campaign.spec.ts`
- `pnpm --filter web i18n:check`
- `pnpm exec playwright test --config=playwright.cleanup.config.ts --reporter=list` from `apps/web` passed 4/4.
- Manual screenshot on `http://localhost:3000/cleanup/unsubscribe-campaign` shows `Tất cả người gửi` and `Nhiều email nhất` in the select triggers.
