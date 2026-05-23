---
status: in_progress
created: 2026-05-22
---

# Quick Task: Hide Beta Onboarding

Goal: temporarily hide the onboarding flow during beta without deleting the implementation.

Plan:
- Add a frontend onboarding beta flag that defaults to disabled.
- Route all onboarding entry points to the main app while disabled.
- Hide "continue setup" links and the sidebar onboarding item while disabled.
- Update route tests to lock the bypass behavior.
- Verify typecheck, i18n, targeted Vitest, targeted Playwright, and browser smoke.
