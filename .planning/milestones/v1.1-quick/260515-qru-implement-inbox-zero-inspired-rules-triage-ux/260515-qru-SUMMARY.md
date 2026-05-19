---
status: complete
quick_id: 260515-qru
date: 2026-05-15
commit: pending
---

# Quick Task 260515-qru Summary

Implemented a focused Inbox Zero inspired rules UX pass. The first pass only changed copy and chips; this follow-up replaced the rules surface with the discussed Inbox Zero/prototype structure.

## Inbox Zero Source Read

- `../inbox-zero/apps/web/app/(app)/[emailAccountId]/assistant/Rules.tsx`
- `../inbox-zero/apps/web/app/(app)/[emailAccountId]/assistant/RuleForm.tsx`
- `../inbox-zero/apps/web/app/(app)/[emailAccountId]/assistant/ConditionSteps.tsx`
- `../inbox-zero/apps/web/app/(app)/[emailAccountId]/assistant/ActionSteps.tsx`
- `../inbox-zero/apps/web/app/(app)/[emailAccountId]/assistant/RulesPromptNew.tsx`

## Changed

- Rules page now leads with `Automation rules` and explains the `When` -> `Then` mental model.
- Rule list is now a table on desktop with `Enabled`, `Name`, `When`, `Then`, `Status`, and row action menu columns, with a mobile card fallback preserving the same scan model.
- Rule list summaries now parse saved matcher/action JSON and show readable `When` / `Then` chips instead of only opaque natural language.
- Rule composer now has `Describe` and `Manual` tabs. Describe keeps natural language as a compiler input; Manual lets users directly edit structured condition/action rows.
- Manual rules compile locally into the existing backend `matcherAst` / `actionIntents` JSON strings, so no new backend API contract was needed.
- Unsafe v1 actions (`auto-send`, `forward`, `delete/spam`, `webhook`) are visibly disabled in the Manual tab.
- Triage page now uses user-facing `AI email actions`, `Activity`, `Test mode`, and `Protected senders` copy.
- Triage audit rows/cards now explain each decision as `When`, `Then`, and `Why`.
- Feature/e2e copy contracts were updated to the new language.

## Verification

- `pnpm --filter web i18n:build` passed.
- `pnpm --filter web typecheck` passed.
- `pnpm --filter web i18n:check` passed.
- Targeted ESLint on touched UI/message files passed.
- Playwright MCP browser check passed against `http://localhost:3000/rules`:
  - Desktop rendered the table with `Enabled`, `Name`, `When`, `Then`, and `Status`.
  - Rule action menu rendered `Move rule up/down`, `Edit rule`, and `Delete rule`.
  - Create dialog rendered `Describe` and `Manual` tabs.
  - Manual tab enabled Save after `Rule name`, `Sender domain`, and `Label` were filled and showed the structured preview.
  - Desktop and 320px mobile checks reported no horizontal overflow.

## Not Run

- Unit tests were intentionally not run per user direction.
