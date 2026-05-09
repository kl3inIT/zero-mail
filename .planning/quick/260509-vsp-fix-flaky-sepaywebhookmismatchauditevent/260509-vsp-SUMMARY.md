---
quick_id: 260509-vsp
status: complete
date: 2026-05-09
commit: fc6f234
---

# Quick Task 260509-vsp: Fix Flaky SepayWebhookMismatchAuditEventTest Assertion

## Summary

Replaced short SePay log scrub sentinels (`999`, `0123`) with deterministic high-entropy payload values in:

- `BillingPrivacyLogScrubTest`
- `SepayWebhookMismatchAuditEventTest`

The tests still verify that captured logs do not expose SePay transaction IDs, account numbers, API key material, or bank memo content. The previous short numeric assertions could collide with random UUID tenant IDs captured in root logs.

## Validation

- `./gradlew --no-daemon :backend:api:test --tests "*.SepayWebhookMismatchAuditEventTest" --tests "*.BillingPrivacyLogScrubTest"` — passed
- `./gradlew --no-daemon :backend:api:test` — passed
- JetBrains build for changed test files — passed
- `./gradlew --no-daemon check` — passed
