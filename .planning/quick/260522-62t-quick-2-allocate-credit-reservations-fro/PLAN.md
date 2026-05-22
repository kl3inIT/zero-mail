---
status: in_progress
created: 2026-05-21
---

# Quick 2: Grant-Aware Reservation Allocation

Goal: make `CreditLedgerService.reserve(...)` spend from active grant balances first, ordered by grant priority and expiry, while keeping legacy unscoped top-up credits usable.

Steps:
- Add nullable `grant_id` to `credit_reservation`.
- Query eligible active grants with enough available balance for a call-site cost.
- Reserve/release against the selected grant when one is available; otherwise fall back to legacy unscoped ledger balance.
- Treat zero-cost call sites as no-op reservations so deterministic triage does not need paid/beta balance.
- Add focused tests for grant priority/expiry allocation and release behavior.
