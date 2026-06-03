---
status: in_progress
created: 2026-05-22
---

# Quick Fix: Login Page Copy Cleanup

Goal: reduce login-page text noise, improve contrast on white surfaces, and keep legal/policy links discoverable without adding an extra consent popup.

Steps:
- Inspect the current login shell, trust panel, legal footer, i18n copy, and auth CSS.
- Shorten the login card copy and remove low-value secondary login noise.
- Remove the trust panel from the login concept; keep only immediately verifiable OAuth/beta information near the CTA.
- Increase muted-text contrast on the auth surface.
- Verify typecheck, i18n, login e2e, and browser rendering on desktop/mobile.
