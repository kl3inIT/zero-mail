---
created: 2026-05-25
status: complete
---

# Remove Inbox Composer Lines

Task: remove visible border lines from the Inbox reply/reply-all/forward composer.

Scope:
- Only update the Inbox composer surface in `apps/web/features/inbox/components/InboxPageClient.tsx`.
- Remove horizontal separators and small inner borders that make the composer look lined.
- Keep spacing, background contrast, controls, and current send confirmation behavior unchanged.

Verification:
- Run targeted frontend checks after the class changes.
