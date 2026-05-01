---
status: partial
phase: 02A-mail-ingestion
source: [02A-VERIFICATION.md]
started: 2026-04-29T08:04:45Z
updated: 2026-04-30T15:55:48+07:00
---

## Current Test

number: 1
name: End-to-end Pub/Sub delivery on staging VPS
expected: |
  Real Gmail message creates one tenant-attributed mail_message_observed row, and replaying the same Pub/Sub message creates no duplicate.
awaiting: user response

## Tests

### 1. End-to-end Pub/Sub delivery on staging VPS
expected: Real Gmail message creates one tenant-attributed mail_message_observed row, and replaying the same Pub/Sub message creates no duplicate.
result: [pending]

### 2. users.watch 7-day expiry renewal
expected: Backdated watch_expires_at is renewed, watch_renewed_at advances, and last_synced_history_id is not corrupted.
result: [pending]

### 3. Reconnect prompt UX after actual history-404
expected: A history-404 sets ingestion_health=HISTORY_LOST, the reconnect prompt is visible, and clicking reconnect starts the Gmail OAuth flow.
result: [pending]

### 4. Pause toggle visual hierarchy and persistent banner
expected: Settings pause toggle, persistent banner, and inline unpause control are visually correct and clear at target viewports.
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
