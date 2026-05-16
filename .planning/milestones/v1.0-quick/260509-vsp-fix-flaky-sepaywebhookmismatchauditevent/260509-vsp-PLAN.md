---
quick_id: 260509-vsp
status: planned
date: 2026-05-09
---

# Quick Task 260509-vsp: Fix Flaky SepayWebhookMismatchAuditEventTest Assertion

## Goal

Remove the CI flake where SePay log scrub tests assert that root logs do not contain short numeric substrings that may appear in random UUID tenant IDs.

## Tasks

1. Replace short sensitive payload sentinels (`999`, `0123`) in SePay log scrub tests with deterministic, high-entropy payload values that cannot collide with UUID fragments or unrelated infrastructure logs.
2. Keep the privacy contract assertions: mismatch logs still include intentional VND amounts, and captured logs still must not expose API keys, SePay transaction IDs, account numbers, or bank memo content.
3. Run the targeted SePay tests and backend API tests.
