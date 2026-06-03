---
quick_id: 260531-lpx
slug: flatten-settings-page
description: Flatten /settings to /ai-style layout and remove duplicated settings
date: 2026-05-31
mode: quick (executed inline on main tree — dirty tree depends on uncommitted /ai changes)
---

# Quick Task 260531-lpx: Flatten /settings + remove duplicates

## Goal

Refactor `/settings` from a card-grid + pseudo-tab layout into a flat, section-based
layout matching `/ai` (SectionHeader + SettingCard, no tabs). Remove settings that are
duplicated elsewhere, consolidate the daily digest into `/ai`, and delete the redundant
`/settings/privacy` route. User-approved scope (discussed interactively).

## Tasks

### 1. Consolidate daily digest into /ai Updates
- files: apps/web/features/ai/components/UpdatesSection.tsx, apps/web/features/notifications/**
- action: Port the digest send-hour control (Select) from NotificationsSection into the
  /ai Updates daily-digest card (reuse settings.notifications.* + useNotificationPreferences /
  useUpdateNotificationPreferences). Delete features/notifications/components/NotificationsSection.tsx
  and features/notifications/__tests__/NotificationsSection.test.tsx (orphan after removal from /settings).
- verify: /ai Updates shows digest toggle + send-hour; typecheck clean.
- done: digest fully owned by /ai; /settings no longer renders NotificationsSection.

### 2. Flatten SettingsClient to 4 sections + inline display controls
- files: apps/web/app/(protected)/(app)/settings/SettingsClient.tsx
- action: Remove SettingsNavigationStrip + SETTINGS_NAVIGATION_ITEMS (dup of AppSidebar),
  CreditCardBlock, the triage Pause card, the Provider pointer card, the Privacy card, and
  NotificationsSection. Rebuild as flat sections using /ai's SectionHeader + SettingCard:
  Tài khoản (email), Hiển thị (language + theme as INLINE segmented toggles — LanguageSwitcher
  compact + ThemeSwitcher, no dialog), Kết nối Gmail (status + reconnect), Vùng nguy hiểm
  (disconnect + DeleteAccountDialog).
- verify: /settings renders 4 flat sections, no tabs, language/theme switch inline; typecheck clean.
- done: layout matches /ai pattern; no duplicated controls remain.

### 3. Delete /settings/privacy + ripple
- files: apps/web/app/(protected)/(app)/settings/privacy/, apps/web/features/privacy/,
  apps/web/components/shell/AppSidebar.tsx, apps/web/e2e/privacy-page.spec.ts,
  apps/web/scripts/check-i18n.ts
- action: Delete the /settings/privacy route + orphaned features/privacy (PrivacySections only
  used there). Remove the privacy nav item from AppSidebar. Delete privacy-page.spec.ts (tests the
  removed page). Remove deleted-file entries from check-i18n EN_SCAN_FILES (settings/privacy/page.tsx,
  NotificationsSection.tsx, PrivacySections.tsx). Keep public /privacy untouched.
- verify: no references to /settings/privacy or PrivacySections remain; typecheck + i18n:check clean.
- done: /settings/privacy gone, sidebar item removed, no broken refs.

## Verification
- pnpm --filter web run typecheck
- pnpm --filter web run i18n:check
- Playwright sweep /settings + /ai at 375px and 1440px → no horizontal overflow

## Notes
- Orphan i18n keys (settings.navigation.*, settings.privacy.*, old pause) left in vi.json/en.json
  (generated bundle, DO NOT EDIT marker, no unused-key gate).
- Do NOT invoke any global UI/design skill (project rule #13).
