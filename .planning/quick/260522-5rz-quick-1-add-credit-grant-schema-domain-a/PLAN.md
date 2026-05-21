---
status: in_progress
created: 2026-05-21
---

# Quick 1: Credit Grant Schema And Domain

Goal: add the persistent grant model needed for beta/promotional/paid/service/admin credit sources without changing reservation allocation behavior yet.

Steps:
- Add `credit_grant` Liquibase changelog and include it from the master changelog.
- Extend `credit_ledger_entry` with nullable `grant_id` plus new ledger kinds used by future grants.
- Add Java domain enums and JPA/repository classes for grants.
- Keep current top-up/reserve behavior compatible while later quick tasks wire allocation.
- Validate with focused build/tests.
