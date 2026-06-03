---
status: in-progress
created: 2026-05-24
---

# Inbox i18n, Composer UX, and Split Scroll

## Goal

Make the Inbox detail pane behave closer to Gmail: the message list keeps its own scroll, the right detail pane gets its own scroll, and reply/reply-all/forward composer overlays the bottom of the message area instead of pushing the page down.

## Scope

- Translate remaining Inbox strings through feature i18n.
- Change Inbox AI generation so it writes generated body text into the composer textarea instead of creating a Gmail draft.
- Add English/Vietnamese generation options.
- Make selected attachments reviewable in the composer.
- Give the composer overflow menu useful actions.
- Remove or de-emphasize template saving because it is not wired to backend behavior.
- Update focused Inbox e2e coverage and run frontend checks.
