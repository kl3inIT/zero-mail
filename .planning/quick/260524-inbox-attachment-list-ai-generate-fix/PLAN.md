---
status: in-progress
created: 2026-05-24
---

# Inbox Attachment List and AI Generate Fix

## Goal

Make the Inbox composer attachment count and selected-file visibility match the requested compact UI, and fix AI generation so it can call the real backend chat endpoint.

## Scope

- Move the attachment count badge to the right side of the `Đính kèm` label.
- Show selected attachment file names in a compact inline list without image-quality options or blob preview.
- Use valid UUID chat IDs for Inbox composer preview and AI body generation.
- Add focused e2e assertions for UUID chat IDs and generated body insertion.
- Run frontend i18n, lint, typecheck, and Inbox e2e checks.
