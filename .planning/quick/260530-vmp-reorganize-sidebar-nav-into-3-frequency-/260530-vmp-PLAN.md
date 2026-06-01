---
phase: quick-260530-vmp
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - apps/web/components/shell/AppSidebar.tsx
  - apps/web/features/shell/messages.ts
  - apps/web/i18n/messages/vi.json
  - apps/web/i18n/messages/en.json
autonomous: false
requirements: [QUICK-NAV-3GROUP]
must_haves:
  truths:
    - "Sidebar renders three labeled groups in order: Daily, Automation, Tools"
    - "Quy tắc (Rules) and Cấu hình AI (AI configuration) appear together in the Automation group"
    - "All six item links (Inbox, Assistant, Rules, AI config, Unsubscribe, Analytics) resolve to their existing hrefs with correct icons"
    - "Item label text (VN/EN) for inbox/chat/ai/rules/cleanupUnsubscribe/analytics is unchanged"
    - "pnpm --filter web typecheck and lint pass with no references to removed keys"
  artifacts:
    - path: "apps/web/components/shell/AppSidebar.tsx"
      provides: "Three nav arrays + three SidebarGroup blocks"
      contains: "AUTOMATION_NAV"
    - path: "apps/web/features/shell/messages.ts"
      provides: "Three new section label keys"
      contains: "nav.sectionAutomation"
  key_links:
    - from: "apps/web/components/shell/AppSidebar.tsx"
      to: "apps/web/features/shell/messages.ts"
      via: "t('nav.sectionDaily') / t('nav.sectionAutomation') / t('nav.sectionTools')"
      pattern: "nav\\.section(Daily|Automation|Tools)"
---

<objective>
Reorganize the app sidebar navigation from two groups (Mail, Manage) into three frequency/intent groups (Daily, Automation, Tools), so that Quy tắc (Rules) and Cấu hình AI (AI configuration) — both answering "how does the AI behave on my behalf?" — live in the SAME group.

Purpose: Fix the organizational smell where Rules and AI config are split across two unrelated groups.
Output: Updated `AppSidebar.tsx` (three nav arrays + three groups), updated `messages.ts` (three new section keys, old `sectionMail`/`sectionManage` removed), regenerated `vi.json`/`en.json`, browser-verified.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@./CLAUDE.md
@apps/web/AGENTS.md
@apps/web/components/shell/AppSidebar.tsx
@apps/web/features/shell/messages.ts

Project UI constraints (from CLAUDE.md): no hardcoded hex (consume tokens), i18n through `features/shell/messages.ts`, use existing shadcn primitives, DO NOT invoke any global UI/design skill, browser verification required for FE changes.

Grep findings (already verified):
- `sectionMail` / `sectionManage` are consumed ONLY by `AppSidebar.tsx` (being replaced) at runtime; they also appear as source entries in `messages.ts` and as generated entries in `i18n/messages/vi.json` + `en.json`.
- `i18n/messages/vi.json` and `en.json` are GENERATED from all `features/**/messages.ts` by `scripts/merge-feature-i18n.ts` (run via `pnpm --filter web run i18n:build`). They are NOT hand-edited; regenerating after editing `messages.ts` is what updates them. Removing the two old keys from `messages.ts` + regenerating removes them from the JSON safely.
- Item-label keys (`nav.inbox` — note: not present yet, see action — `nav.chat`, `nav.ai`, `nav.rules`, `nav.cleanupUnsubscribe`, `nav.analytics`) already exist except verify `nav.inbox`; their text must not change.
</context>

<tasks>

<task type="auto">
  <name>Task 1: Regroup nav arrays and section labels into three groups</name>
  <files>apps/web/components/shell/AppSidebar.tsx, apps/web/features/shell/messages.ts</files>
  <action>
In `AppSidebar.tsx`: replace the two arrays `MAIL_NAV` and `MANAGE_NAV` with three `NavItem[]` arrays in this exact membership and order — `DAILY_NAV` = [ `{ href: '/inbox', labelKey: 'nav.inbox', icon: Inbox }`, `{ href: '/chat', labelKey: 'nav.chat', icon: Sparkles }` ]; `AUTOMATION_NAV` = [ `{ href: '/rules', labelKey: 'nav.rules', icon: ListChecks }`, `{ href: '/ai', labelKey: 'nav.ai', icon: Bot }` ]; `TOOLS_NAV` = [ `{ href: '/cleanup/unsubscribe-campaign', labelKey: 'nav.cleanupUnsubscribe', icon: MailX }`, `{ href: '/analytics', labelKey: 'nav.analytics', icon: BarChart3 }` ]. Preserve the existing `as Route` casts where the current code used them (e.g. `'/inbox' as Route`, `'/ai' as Route`). Keep the `NavItem.labelKey` union type as-is (all six labelKeys still exist). Do not touch `ACCOUNT_NAV`, `renderNavItem`, `isActivePath`, the icon imports, or collapsed-state handling. In `SidebarContent`, replace the two `<SidebarGroup>` blocks with three identical-structure blocks (same className, same `SidebarGroupLabel`/`SidebarGroupContent`/`SidebarMenu` markup, same collapsed guard) rendering `DAILY_NAV` under `t('nav.sectionDaily')`, `AUTOMATION_NAV` under `t('nav.sectionAutomation')`, and `TOOLS_NAV` under `t('nav.sectionTools')`, in that order.

In `features/shell/messages.ts`: add three section keys — `'nav.sectionDaily'` (vi: "Hằng ngày", en: "Daily"), `'nav.sectionAutomation'` (vi: "Tự động hóa", en: "Automation"), `'nav.sectionTools'` (vi: "Công cụ", en: "Tools"). Remove the now-unused `'nav.sectionMail'` and `'nav.sectionManage'` entries (verified: only `AppSidebar.tsx` consumed them at runtime, and that consumer is being replaced in this same task). Leave every other key — especially `nav.chat`, `nav.ai`, `nav.rules`, `nav.cleanupUnsubscribe`, `nav.analytics` — byte-for-byte unchanged. Verify `nav.inbox` exists in the shell or a loaded feature dictionary; if it is missing, add `'nav.inbox'` (vi: "Hộp thư", en: "Inbox") so the Daily group label resolves.
  </action>
  <verify>
    <automated>cd apps/web && rg -n "section(Daily|Automation|Tools)" components/shell/AppSidebar.tsx features/shell/messages.ts && rg -c "section(Mail|Manage)" components/shell/AppSidebar.tsx features/shell/messages.ts | rg ":0$"</automated>
  </verify>
  <done>AppSidebar.tsx defines DAILY_NAV/AUTOMATION_NAV/TOOLS_NAV and renders three SidebarGroup blocks referencing the three new section keys; messages.ts has the three new keys and no longer has sectionMail/sectionManage; Rules and AI config are both in AUTOMATION_NAV.</done>
</task>

<task type="auto">
  <name>Task 2: Regenerate i18n bundles and pass typecheck + lint</name>
  <files>apps/web/i18n/messages/vi.json, apps/web/i18n/messages/en.json</files>
  <action>
Regenerate the merged i18n JSON from the edited feature dictionaries by running `pnpm --filter web run i18n:build` (script: `scripts/merge-feature-i18n.ts`). Do NOT hand-edit `vi.json`/`en.json` — they are generated. Then run `pnpm --filter web run typecheck` and `pnpm --filter web run lint`. If `i18n:check` exists as part of CI hygiene, run `pnpm --filter web run i18n:check` to confirm no missing/orphaned keys. Fix any failures by correcting the source (`messages.ts`/`AppSidebar.tsx`) and re-running the generator — never by editing generated JSON. Confirm the generated bundles now contain `nav.sectionDaily/sectionAutomation/sectionTools` and no longer contain `nav.sectionMail/sectionManage`.
  </action>
  <verify>
    <automated>cd apps/web && pnpm run typecheck && pnpm run lint && rg -n "sectionDaily|sectionAutomation|sectionTools" i18n/messages/vi.json i18n/messages/en.json && (rg -c "sectionMail|sectionManage" i18n/messages/vi.json i18n/messages/en.json | rg ":0$")</automated>
  </verify>
  <done>typecheck and lint pass; generated vi.json/en.json contain the three new section keys and no longer contain sectionMail/sectionManage.</done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <what-built>Sidebar reorganized into three groups (Daily / Automation / Tools) with Rules + AI config now in the same Automation group; i18n bundles regenerated; typecheck + lint green.</what-built>
  <how-to-verify>
Start the apps/web dev server (`pnpm --filter web dev`) and drive it with Playwright MCP per the project UX rule:
1. Navigate to the app, sign in if needed (one click "Tiếp tục với Google" on /login — bundled OAuth, single round-trip).
2. Take a sidebar snapshot. Confirm THREE group labels render in order: "Hằng ngày" (Daily), "Tự động hóa" (Automation), "Công cụ" (Tools).
3. Confirm membership:
   - Daily: Hộp thư (/inbox, Inbox icon), Trợ lý (/chat, Sparkles icon)
   - Automation: Quy tắc (/rules, ListChecks icon), Cấu hình AI (/ai, Bot icon) — both in the SAME group
   - Tools: Hủy đăng ký (/cleanup/unsubscribe-campaign, MailX icon), Phân tích (/analytics, BarChart3 icon)
4. Click each item; confirm it navigates to the listed href and the item shows the active state.
5. Collapse the sidebar (toggle) and confirm icon-only mode still renders all six items with tooltips and no group labels (collapsed guard intact).
6. Switch locale to English and confirm labels read Daily / Automation / Tools.
7. Check `browser_console_messages` for no new errors (especially missing-i18n-key warnings).
  </how-to-verify>
  <resume-signal>Type "approved" or describe what renders incorrectly.</resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| (none new) | Pure client-side nav reorg; no untrusted input, no new data flow. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-quick-01 | Tampering | i18n generated bundles | accept | Generated JSON is rebuilt from `messages.ts`; verify step confirms regen produced expected keys. No hand-edit. |
| T-quick-SC | Tampering | npm/pip/cargo installs | accept | No new dependencies installed in this plan. |
</threat_model>

<verification>
- `rg "section(Daily|Automation|Tools)"` matches in both AppSidebar.tsx and messages.ts; `rg -c "section(Mail|Manage)"` returns 0 in both source files and both generated bundles.
- `pnpm --filter web run typecheck` and `pnpm --filter web run lint` pass.
- Browser snapshot shows three correctly-ordered, correctly-populated groups with Rules + AI config together.
</verification>

<success_criteria>
- Three sidebar groups render in order Daily / Automation / Tools.
- Quy tắc (Rules) and Cấu hình AI (AI config) are in the same (Automation) group.
- All six links resolve to existing hrefs with correct icons; item label text unchanged.
- No dead `sectionMail`/`sectionManage` references; typecheck + lint green; no console i18n warnings.
</success_criteria>

<output>
Create `.planning/quick/260530-vmp-reorganize-sidebar-nav-into-3-frequency-/260530-vmp-SUMMARY.md` when done.
</output>
