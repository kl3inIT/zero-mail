---
status: in-progress
created: 2026-05-25
---

# Inbox Send Confirm Dialog

## Goal

Replace the verbose inline assistant preview text in the Inbox composer send flow with a short confirmation dialog, then auto-confirm the generated send action without displaying the full preview card.

## Scope

- Show a popup asking whether the user has reviewed the email and wants to send.
- Hide assistant free-text and full preview card in the Inbox send path.
- Keep the backend safety contract: send/reply/forward still executes only after explicit user confirmation.
- Add margin between the composer overlay and email content.
- Update focused Inbox e2e coverage and run frontend checks.
