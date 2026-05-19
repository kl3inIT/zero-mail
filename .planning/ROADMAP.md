# Roadmap: Zero Mail

## Milestones

- ✅ **v1.0 MVP** — Phases 1, 1.1-1.6, 2A-2C, 3, 4, 5A-5C, 6 (shipped 2026-05-15) — see [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)
- ✅ **v1.1 Email assistant chat** — Phase 7 only (shipped 2026-05-19; Phase 8 deferred to v1.2) — see [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)
- 📋 **v1.2 Admin console + Settings + Visual refresh + GA discipline** — planned (start with `/gsd-new-milestone`)

## Phases

<details>
<summary>✅ v1.0 MVP (shipped 2026-05-15) — 17 phases, 123 plans</summary>

Full details: [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)

</details>

<details>
<summary>✅ v1.1 Email assistant chat (shipped 2026-05-19) — Phase 7 only</summary>

- [x] Phase 7: Chat Email Assistant (Backend + Frontend + Send Executor + ArchUnit flip 0→1) — 6/6 plans, completed 2026-05-18

Phase 8 (Settings + Hardening + Eval + GA) **deferred to v1.2** during spec-phase 2026-05-19. 19 unchecked v1.1 reqs (SET-AI/VOICE/BEHV/SAFE-*) carried forward to v1.2 candidates.

Full details: [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)

</details>

### 📋 v1.2 (planned)

Next milestone scope (intent, finalized during `/gsd-new-milestone`):

1. **Admin console foundation** (SEED-011 / OPS-02 promoted from deferred) — auth/role, `/admin/*` route, RBAC, catalog persistence + UI to curate, admin master keys management
2. **Visual refresh of user pages** — align with PR #40 brand palette shift (teal `#0E5E5A` → purple `#867AEB`); audit Phase 7 chat UI, Settings, Triage, Rules, Analytics
3. **Settings page (full)** — Personalization (SET-VOICE-01..06), Behavior (SET-BEHV-01..05), Safety Net (SET-SAFE-01..04), AI Provider/Model (SET-AI-01..04) with per-feature picker via admin-curated catalog
4. **Hardening + GA discipline** — hostile-corpus eval (15 hostile emails + 10 hostile personal_instructions + VIP refusal + VI/EN fidelity), Grafana dashboards (lease residuals, audit-vs-state mismatch, ordering violations, leak counters), CASA evidence refresh, README/CONTRIBUTING send-call-site doc, LAUNCH-GO-NOGO checklist, v1.2 GA tag

Run `/gsd-new-milestone` to formalize scope, generate REQUIREMENTS.md, and create phase breakdown.

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-6 (collapsed) | v1.0 | 123/123 | Complete | 2026-05-15 |
| 7. Chat Email Assistant | v1.1 | 6/6 | Complete | 2026-05-18 |
| 8. Settings + Hardening + GA | v1.1 → v1.2 | — | Deferred | — |

---

*v1.0 archived 2026-05-15. v1.1 archived 2026-05-19 (Phase 7 only). v1.2 scope drafted 2026-05-19 — run `/gsd-new-milestone` to start.*
