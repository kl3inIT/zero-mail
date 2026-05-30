---
phase: quick-260530-vmp
plan: 01
subsystem: frontend-shell
tags: [navigation, sidebar, i18n, ui]
requires: []
provides:
  - "Three-group sidebar navigation (Daily / Automation / Tools)"
  - "Rules + AI config co-located in the Automation group"
affects:
  - apps/web/components/shell/AppSidebar.tsx
  - apps/web/features/shell/messages.ts
  - apps/web/i18n/messages/vi.json
  - apps/web/i18n/messages/en.json
tech-stack:
  added: []
  patterns:
    - "merge-feature-i18n.ts is additive-only: removing source keys does NOT prune orphaned generated keys"
key-files:
  created: []
  modified:
    - apps/web/components/shell/AppSidebar.tsx
    - apps/web/features/shell/messages.ts
    - apps/web/i18n/messages/vi.json
    - apps/web/i18n/messages/en.json
decisions:
  - "Orphaned nav.sectionMail/sectionManage removed from generated bundles by hand because the additive i18n generator cannot prune keys with no source; re-running i18n:build confirmed they stay removed."
metrics:
  duration: ~15min
  completed: 2026-05-30
---

# Quick 260530-vmp: Reorganize Sidebar Nav into 3 Frequency Groups Summary

Reorganized the app sidebar from two groups (Mail / Manage) into three frequency-and-intent groups (Hằng ngày / Tự động hóa / Công cụ — Daily / Automation / Tools), placing Quy tắc (Rules) and Cấu hình AI (AI configuration) together in the Automation group.

## What Was Built

- **Task 1 (`c20d08fc`)** — Replaced `MAIL_NAV` / `MANAGE_NAV` with `DAILY_NAV` / `AUTOMATION_NAV` / `TOOLS_NAV` in `AppSidebar.tsx`, and the two `<SidebarGroup>` blocks with three identical-structure blocks rendering the new section labels in order. Swapped the `nav.sectionMail` / `nav.sectionManage` source keys for `nav.sectionDaily` / `nav.sectionAutomation` / `nav.sectionTools` in `features/shell/messages.ts`.
  - Group membership (final):
    - **Daily:** `/inbox` (Inbox), `/chat` (Sparkles)
    - **Automation:** `/rules` (ListChecks), `/ai` (Bot) — Rules + AI config in the SAME group
    - **Tools:** `/cleanup/unsubscribe-campaign` (MailX), `/analytics` (BarChart3)
  - `NavItem` union type, `as Route` casts, `ACCOUNT_NAV`, `renderNavItem`, `isActivePath`, icon imports, and collapsed-state handling all left untouched.
  - `nav.inbox` confirmed already present in `features/inbox/messages.ts` — no new label key needed.

- **Task 2 (`aa0b7f05`)** — Regenerated `vi.json` / `en.json` via `pnpm --filter web run i18n:build`, then removed the orphaned `nav.sectionMail` / `nav.sectionManage` entries that the additive generator left behind (see Deviations). `typecheck`, `lint`, and `i18n:check` all green.

## Verification Results

- Task 1 grep: `nav.sectionDaily/Automation/Tools` present in both `AppSidebar.tsx` and `messages.ts`; zero `sectionMail/Manage` matches in source files.
- `pnpm --filter web run typecheck` — clean (`tsc --noEmit`, no output).
- `pnpm --filter web run lint` — 0 errors. (One pre-existing warning in `coverage/lcov-report/block-navigation.js`, a generated coverage artifact, out of scope.)
- `pnpm --filter web run i18n:check` — OK: vi/en parity, 1782 leaf keys, backend ErrorCodes coverage, no mojibake.
- Generated bundles: `sectionDaily/Automation/Tools` present, zero `sectionMail/Manage`. Re-running `i18n:build` after the hand-prune did NOT re-add the orphans (confirms durability).

## Browser Verification (checkpoint:human-verify) — DEFERRED

The plan's Task 3 is a `checkpoint:human-verify` (gate=blocking, plan `autonomous: false`) requiring a Playwright-driven sidebar snapshot. This executor agent runs with a restricted tool set that does **not** include `mcp__playwright__*` tools, so the browser snapshot could not be performed here.

Environment prepared for the verifier:
- Backend is running (http://localhost:8080/actuator/health → 200).
- The `apps/web` dev server was started and is serving (http://localhost:3000/login → 200). NOTE: the background launch wrapper reported a non-zero exit, but the detached `next dev` child survived and continues to respond 200; if it has since stopped, restart with `pnpm --filter web dev`.

Verification steps to run (per plan Task 3):
1. Navigate to the app; sign in via one click on "Tiếp tục với Google" on `/login` (bundled OAuth, single round-trip).
2. Snapshot the sidebar — confirm THREE labels in order: "Hằng ngày", "Tự động hóa", "Công cụ".
3. Confirm membership, especially that **Quy tắc (/rules) and Cấu hình AI (/ai) are in the SAME Automation group**.
4. Click each of the six items; confirm correct href + active state.
5. Collapse the sidebar; confirm icon-only mode renders all six with tooltips and no group labels.
6. Switch locale to English; confirm Daily / Automation / Tools.
7. Check `browser_console_messages` for no new errors (esp. missing-i18n-key warnings).

Static membership/href/icon mapping has been verified to match the plan exactly (see What Was Built), so the remaining risk surface is purely visual/runtime rendering.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] i18n generator does not prune orphaned keys**
- **Found during:** Task 2
- **Issue:** The plan assumed that removing `nav.sectionMail` / `nav.sectionManage` from `messages.ts` and re-running `i18n:build` would remove them from `vi.json` / `en.json`. Inspection of `scripts/merge-feature-i18n.ts` showed the generator reads the existing bundle as a base (`readJson(localePath)`) and only **adds/overwrites** keys from feature `messages.ts` — it never deletes keys that no longer have a source. So both orphan keys persisted in the generated bundles, failing the plan's verify (`rg -c sectionMail|sectionManage ... | rg :0$`).
- **Fix:** Removed the two orphan lines from each generated bundle by hand, then re-ran `i18n:build` to confirm the additive generator does NOT re-add them (no source = stays removed). This is the durable correct state — the "never hand-edit generated files" rule is motivated by regen overwriting edits, which cannot happen here since no source defines these keys.
- **Files modified:** `apps/web/i18n/messages/vi.json`, `apps/web/i18n/messages/en.json`
- **Commit:** `aa0b7f05`

## Known Stubs

None.

## Commits

- `c20d08fc` refactor(quick-260530-vmp): regroup sidebar nav into Daily/Automation/Tools
- `aa0b7f05` chore(quick-260530-vmp): regenerate i18n bundles for 3-group sidebar nav

## Self-Check: PASSED

- FOUND: apps/web/components/shell/AppSidebar.tsx (DAILY_NAV/AUTOMATION_NAV/TOOLS_NAV present)
- FOUND: apps/web/features/shell/messages.ts (three new section keys, no Mail/Manage)
- FOUND: apps/web/i18n/messages/vi.json + en.json (new keys, no orphans)
- FOUND commit: c20d08fc
- FOUND commit: aa0b7f05
