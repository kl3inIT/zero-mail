---
status: complete
quick_id: 260531-lpx
slug: flatten-settings-page
date: 2026-05-31
---

# Quick Task 260531-lpx — Summary

Flattened `/settings` to a `/ai`-style section layout, removed duplicated/redundant
controls, consolidated the daily digest into `/ai`, and deleted the redundant
`/settings/privacy` route. Executed inline on the main tree (per user preference;
the refactor builds on uncommitted `/ai` changes that a worktree would not see).

## What changed

**1. Digest consolidated into /ai Updates**
- `features/ai/components/UpdatesSection.tsx`: the daily-digest card now also renders the
  send-hour `Select` (+ helper / downtime note / timezone line), reusing
  `settings.notifications.*` and `useNotificationPreferences` / `useUpdateNotificationPreferences`.
  Shown only when digest is enabled.
- Deleted `features/notifications/components/NotificationsSection.tsx` and
  `features/notifications/__tests__/` (orphan once removed from /settings). Notification
  hooks/api/messages kept — still used by /ai.

**2. /settings flattened to 4 sections (no tabs)**
- `app/(protected)/(app)/settings/SettingsClient.tsx`: rewritten using `/ai`'s
  `SectionHeader` + `SettingCard`. Sections: Tài khoản (email), Hiển thị (language + theme
  as INLINE segmented toggles — `LanguageSwitcher` compact + `ThemeSwitcher`, dialog removed),
  Kết nối Gmail (status + reconnect), Vùng nguy hiểm (disconnect + delete account).
- Removed: pseudo-tab nav strip (dup of AppSidebar), credit balance card (dup of /credits +
  header pill), triage Pause card (pause lives in /ai Updates with confirm dialog), Provider
  pointer card, Privacy card, NotificationsSection.
- `features/ai/components/SettingCard.tsx`: `title` made optional (CardHeader rendered only
  when title/description/rightSlot present) so single-content sections don't repeat the
  SectionHeader label. Backward-compatible for /ai's existing usages.

**3. /settings/privacy removed**
- Deleted route `app/(protected)/(app)/settings/privacy/` and orphaned `features/privacy/`
  (`PrivacySections` was only used there). Public `/privacy` landing page untouched.
- `components/shell/AppSidebar.tsx`: removed the privacy nav item + unused `ShieldCheck`
  import + the `'nav.privacy'` member of the AccountNavItem label union.
- Deleted `e2e/privacy-page.spec.ts` (tested the removed page).
- `scripts/check-i18n.ts`: removed the 3 deleted-file entries from EN_SCAN_FILES
  (settings/privacy/page.tsx, NotificationsSection.tsx, PrivacySections.tsx).

## Verification
- `pnpm --filter web run typecheck` → clean
- `pnpm --filter web run i18n:check` → OK (vi/en parity, 1783 keys, 74 EN-scan files)
- Playwright sweep: `/settings` 375px & 1440px → page horizontal overflow 0, 0 offenders;
  `/ai` 375px & 1440px → page overflow 0 (Knowledge table in its own scroll-wrapper, pre-existing);
  `/ai` digest card shows toggle + send-hour Select (20:00); `/settings` renders 4 flat sections
  with inline language/theme segmented controls and no tabs.

## Notes
- Orphan i18n keys (`settings.navigation.*`, `settings.privacy.*`, `nav.privacy`, old
  `settings.triage.pause.*`) left in the generated bundle (DO NOT EDIT marker; merge is
  overlay-only; no unused-key gate). Harmless; parity preserved.
- Executed inline (no gsd-executor subagent) per user preference and because the change
  depends on uncommitted /ai state on the main tree.
