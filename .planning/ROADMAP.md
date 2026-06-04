# Roadmap: Zero Mail

## Milestones

- ✅ **v1.0 MVP** — Phases 1, 1.1-1.6, 2A-2C, 3, 4, 5A-5C, 6 (shipped 2026-05-15) — see [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)
- ✅ **v1.1 Email assistant chat** — Phase 7 only (shipped 2026-05-19) — see [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)
- ✅ **v1.2 Admin Console + User Settings UI** — Phases 8, 08.1, 9 (+ 08-bulk-unsubscribe) (shipped 2026-06-01) — see [milestones/v1.2-ROADMAP.md](milestones/v1.2-ROADMAP.md)

## Phases

<details>
<summary>✅ v1.0 MVP (shipped 2026-05-15) — 17 phases, 123 plans</summary>

Full details: [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)

</details>

<details>
<summary>✅ v1.1 Email assistant chat (shipped 2026-05-19) — Phase 7 only</summary>

- [x] Phase 7: Chat Email Assistant — 6/6 plans, completed 2026-05-18

Full details: [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)

</details>

<details>
<summary>✅ v1.2 Admin Console + User Settings UI (shipped 2026-06-01) — 4 phases, 28 plans</summary>

- [x] Phase 8: Admin Console & Operator Tooling — 6/6 plans, completed 2026-05-20
- [x] Phase 08.1: Inbox Zero-style Rule Actions & Admin-managed Examples Catalog — 6/6 plans, completed 2026-05-25
- [x] Phase 08-bulk-unsubscribe: Bulk Unsubscribe Campaign (UNS-01..07) — shipped alongside v1.2
- [x] Phase 9: User Settings UI on Curated Catalog — 7/7 plans, completed 2026-05-29

70/73 v1.2 requirements complete; 3 deferred to v1.3 (SET-BEHV-05, SET-SAFE-02, SET-SAFE-03).

Full details: [milestones/v1.2-ROADMAP.md](milestones/v1.2-ROADMAP.md)

</details>

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-6 (collapsed) | v1.0 | 123/123 | Complete | 2026-05-15 |
| 7. Chat Email Assistant | v1.1 | 6/6 | Complete | 2026-05-18 |
| 8. Admin Console & Operator Tooling | v1.2 | 6/6 | Complete | 2026-05-20 |
| 08.1. Inbox Zero-style Rule Actions & Examples Catalog | v1.2 | 6/6 | Complete | 2026-05-25 |
| 08-bulk-unsubscribe. Bulk Unsubscribe Campaign | v1.2 | — | Complete | 2026-05 |
| 9. User Settings UI on Curated Catalog | v1.2 | 7/7 | Complete | 2026-05-29 |

### Phase 10: Telegram Messaging Assistant

**Goal:** Ship a Telegram-side companion that lets a tenant connect a single Gmail-paired tenant to their personal Telegram DM, receive per-rule triage notifications with inline-keyboard actions (reply / archive / open / spam / save draft / forward / send), confirm draft sends via a deterministic preview card, and chat free-text with the streaming AI assistant — all without expanding the body-content ban surface and without adding a second Gmail send call site.
**Requirements**: TG-01 .. TG-19 (19 — see `.planning/phases/10-telegram-messaging-assistant/10-SPEC.md`)
**Depends on:** Phase 9 (User Settings UI), Phase 8 (queue infra), Phase 7 (chat assistant + assistant_pending_action), Phase 02A (Pub/Sub SecurityFilterChain pattern)
**Mode:** sequential — Wave 2 plans (05–08) are linearly dependent; see CONTEXT.md `<deferred>` note "Wave 2 plans … sequential, not parallel"
**Plans:** 11 plans

Plans:

- [ ] 10-00 — foundation (Bucket4j 8.19.0 pin, REQUIREMENTS.md mint TG-01..TG-19, Liquibase 099-103, Modulith package-info.java, 6 ArchUnit skeletons, Playwright + WireMock fixtures scaffolding)  *Wave 0*
- [ ] 10-01 — TriageDecisionRecorded event + ResponseSurface enum  *Wave 1*
- [ ] 10-02 — OutboundActionSource enum + OutboundActionAuditWriter + MailActionService boundary  *Wave 1*
- [ ] 10-03 — TelegramAccount entity + repositories + telegram_notification_log persistence  *Wave 1*
- [ ] 10-04 — TelegramApiClient + TelegramSendRateLimiter (chatPausedUntil ConcurrentMap per RESEARCH override) + TelegramProperties  *Wave 1*
- [ ] 10-05 — TelegramWebhookSecurityConfig (@Order 2) + UpdateRouter DM-only enforcement  *Wave 2*
- [ ] 10-06 — PairingCodeService (HMAC-SHA256 compact code per RESEARCH override, NOT JWT) + REST controllers + SetMyCommandsService bot initializer  *Wave 2*
- [ ] 10-07 — TelegramNotificationListener (AFTER_COMMIT) + CallbackRouter with cross-actor CAS + inline keyboard wiring  *Wave 2*
- [ ] 10-08 — Worker outbox drain for MESSAGING_NOTIFICATION + dedup vacuum (ShedLock) + TelegramOutboxDrainArchTest assertion  *Wave 2*
- [ ] 10-09 — TelegramChatStreamSink (Reactor sample 800ms cadence, D-05..D-08 streaming) + ChatStreamSinkFactory.createTelegramSink + free-text dispatch  *Wave 3*
- [ ] 10-10 — apps/web/features/telegram-integration FE + /settings/connected-apps sub-route + SettingsClient.tsx nav-link (TG-18) + OpenAPI regen + Playwright e2e + docs/integrations/telegram-setup.md  *Wave 3*

**Threat model:** 15 T-10-XX threats across 6 vectors (webhook public endpoint, bot token secret, pairing code, callback cross-actor, Telegram→Gmail mutate, body-ban regression). All plans carry `threat_refs:` frontmatter or `<threat_model>` block.

**Deferred (see CONTEXT.md `<deferred>` block):** snooze un-snooze worker, `vipOnly`/`enabledRuleIds` filter predicates UI editors (backend always-allow until `TriageDecisionRecorded` carries `ruleId`), quiet hours UI, Zalo OA + Slack/Teams integrations, `/digest` `/unread` `/pause` slash commands, bot-token rotation drill.

---

*v1.0 archived 2026-05-15. v1.1 archived 2026-05-19 (Phase 7 only). v1.2 archived 2026-06-01 — Phases 8 + 08.1 + 9 (+ bonus 08-bulk-unsubscribe campaign), 70/73 requirements complete, 3 deferred to v1.3. No GA tag this milestone (visual refresh, hostile-corpus eval, Grafana, CASA refresh, LAUNCH-GO-NOGO deferred to v1.3+).*
