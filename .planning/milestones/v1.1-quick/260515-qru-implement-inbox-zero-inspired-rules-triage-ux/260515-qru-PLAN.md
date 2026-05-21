# Quick Task 260515-qru: Implement Inbox Zero Inspired Rules/Triage UX

**Date:** 2026-05-15
**Status:** Complete

## Scope

Read the adjacent Inbox Zero source at `../inbox-zero` and implement a focused Zero Mail UI improvement for rules and triage clarity without opening a new phase.

## Plan

1. Inspect Inbox Zero's assistant/rules source, especially rule list, create/edit rule form, condition/action summaries, examples, and action history patterns.
2. Compare against Zero Mail's current `apps/web/features/rules` and `apps/web/features/triage` surfaces.
3. Implement a narrow UI/copy pass that makes the user mental model explicit:
   - rules are `When` + `Then`;
   - natural language is only a compiler input;
   - triage shows what matched, what Zero Mail did, why, and how to undo;
   - unsafe Gmail actions remain blocked by product copy.
4. Verify with Playwright/browser inspection. Do not run unit tests unless explicitly requested.
