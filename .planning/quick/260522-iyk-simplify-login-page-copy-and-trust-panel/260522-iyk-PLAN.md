---
quick_id: 260522-iyk
slug: simplify-login-page-copy-and-trust-panel
status: in_progress
date: 2026-05-22
---

# Quick Task 260522-iyk: Simplify Login Page Copy And Trust Panel

## Goal

Make `/login` feel closer to Inbox Zero's sparse auth screen: one clear sign-in action, minimal explanatory copy, and compact safety reassurance without a dense permission explainer.

## Tasks

1. Simplify the login form copy and structure in `apps/web/app/(auth)/login/page.tsx`.
2. Add a compact `TrustPanel` variant for login while preserving the full panel for onboarding pages.
3. Update auth/trust i18n copy and CSS spacing, then verify `/login` in desktop and mobile render.
