---
status: complete
quick_id: 260427-8sw
date: 2026-04-27
commit: 576f671
---

# Quick Task 260427-8sw Summary

## Completed

- Moved `apps/web/content/docs/*.mdx` to `apps/web/docs/*.mdx`.
- Moved locale bundles from `apps/web/messages/{vi,en}.json` to `apps/web/i18n/messages/{vi,en}.json`.
- Moved `LanguageSwitcher` from `apps/web/features/i18n/components` to `apps/web/i18n/components`.
- Removed the `apps/web/features/i18n` feature root entirely, including placeholder `.gitkeep` files.
- Updated next-intl request config, root layout dynamic imports, global message typing, docs loader paths, i18n scanner paths, and tests.

## Verification

- `pnpm --dir apps/web run i18n:check`
- `pnpm --dir apps/web exec vitest run __tests__/i18n/messages.contract.test.ts __tests__/i18n/error-render.test.tsx __tests__/docs/mdx-pipeline.test.ts __tests__/architecture/feature-folders.test.ts __tests__/components/LanguageSwitcher.test.tsx`
- `pnpm --dir apps/web exec tsc --noEmit`

## Notes

- `features/i18n` is now absent. Root `apps/web/i18n` owns routing/request config, messages, and language-switching UI.
