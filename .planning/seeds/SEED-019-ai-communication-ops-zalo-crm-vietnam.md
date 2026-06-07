---
id: SEED-019
status: dormant
planted: 2026-06-07
planted_during: v1.3 scoping discussion
trigger_when: "when planning Zalo OA, omnichannel inbox, lightweight CRM, communication ops, or post-Gmail channel expansion for Vietnam SMBs"
scope: large
---

# SEED-019: AI Communication Ops for Vietnam SMBs - Gmail, Zalo OA, and Lightweight CRM

## Why This Matters

Zero Mail's first wedge is Gmail trust-first triage, but the larger Vietnam SMB opportunity is not just "manage Gmail better." Busy founders and small teams also handle customer conversations across Zalo OA, Facebook/Instagram pages, website chat, and sometimes Microsoft/Google mailboxes. The business pain is fragmented communication: missed follow-ups, repeated answers, no shared customer context, unclear ownership, and inconsistent replies.

The promising direction is an AI-first communication command center for founder-led SMBs in Vietnam: Gmail remains the initial high-trust inbox, then future milestones can add Zalo OA and a lightweight CRM layer so AI can classify, prioritize, draft responses, remind next steps, and preserve an auditable customer timeline.

This should not become a generic all-in-one CRM clone. The differentiated bet is trust-first AI communication operations: fewer missed conversations, safer outbound automation, better follow-up discipline, and enough customer context to help small teams respond well without adopting a heavy CRM.

## When to Surface

**Trigger:** when planning Zalo OA, omnichannel inbox, lightweight CRM, communication ops, or post-Gmail channel expansion for Vietnam SMBs.

Also surface when a milestone proposes any of:

- Multi-channel conversation inbox beyond Gmail.
- Zalo OA integration, Zalo webhook handling, or Zalo customer messaging.
- CRM/contact timeline, customer profile, lead status, owner/assignee, or follow-up workflow.
- Repositioning Zero Mail from a Gmail automation tool toward founder/SMB communication operations.
- Provider/channel abstraction work that could accidentally optimize for Microsoft before Vietnam-specific channels.

## Scope Estimate

**Large.** This is likely a full future milestone or multiple milestones after the multi-Gmail foundation. It touches product positioning, channel strategy, privacy posture, retention policy, team/workspace permissions, and user-facing workflows.

Suggested sequencing:

1. **Multi-Gmail foundation first** - multiple connected Gmail accounts, account selector, account-scoped rules/audit/settings.
2. **Channel-ready product spec** - define `connected channel` / `conversation` concepts without shipping non-Gmail production code.
3. **Zalo OA discovery/prototype** - validate official OA flow, webhook shape, message permissions, approval/compliance, and customer-service use cases.
4. **Lightweight CRM** - contact profile, timeline, tags, status, owner, notes, next-step reminders, and AI summaries.
5. **Omnichannel automation** - rules triggered by conversation events, with audited AI draft/send/escalate behavior.

## Candidate Product Shape

- Founder/SMB dashboard focused on "what needs attention today" across Gmail first, then Zalo OA.
- Contact/customer profile that can link email, phone, Zalo identity, company, tags, owner, and status.
- Conversation timeline for Gmail + future Zalo OA messages.
- AI-generated customer summary and next-step reminder.
- Lightweight pipeline/status only: new lead, waiting for reply, needs quote, follow-up due, resolved.
- Rule automation that works across communication events, not only Gmail messages.
- Audit and safety controls for every AI-assisted outbound action.

## Explicit Early Out of Scope

- Full CRM suite competing directly with HubSpot/Salesforce/NextX.
- POS, inventory, order management, loyalty, accounting, or ERP.
- Mass marketing campaign/broadcast engine before compliance and abuse controls are mature.
- Zalo personal-account automation. Zalo OA is the appropriate business channel.
- Generic omnichannel parity with every support platform at once.
- Replacing Gmail/Zalo as the native client in the first version; start as an AI operations layer.

## Market Notes

- Crisp is useful as a reference for shared inbox + support CRM positioning, but Zero Mail should not copy a generic support-platform surface too early.
- Vietnam-specific differentiation likely favors Zalo OA before Microsoft/Outlook if the target segment is local SMB/founder-led businesses.
- Existing Vietnam tools such as NextX, OMyDesk, Subiz, and Oviro validate demand for CRM/omnichannel support, but also show that "all-in-one CRM" is crowded.
- Zero Mail's better wedge is trust-first AI triage and response automation for high-value conversations, not broad module count.

## Privacy and Trust Notes

Current Zero Mail privacy rules forbid long-term storage of raw Gmail email bodies, email-content prompts/completions, and embeddings. A CRM/shared-inbox direction changes the business model because support channels often need persisted conversation history.

Future planning must make the source distinction explicit:

- Personal Gmail triage should keep the no-long-term-body posture unless deliberately revisited.
- Business support channels such as Zalo OA may persist conversation history only with clear user/admin expectations, retention controls, export/delete behavior, role-based access, and audit.
- AI prompts/completions should still avoid raw logging and unnecessary persistence; store structured outcomes, summaries, scores, and audit metadata instead.

## Breadcrumbs

- Crisp shared inbox / CRM reference: https://crisp.chat/en/shared-inbox/ and https://crisp.chat/en/crm/
- Zalo OA OpenAPI / ecosystem reference: https://oa.zalo.me/home/function/extension
- `.planning/PROJECT.md` - current v1 direction is Gmail-only, trust-first AI auto-triage.
- `.planning/seeds/SEED-005-team-collaboration-shared-email-workspace.md` - related but broader team/shared-workspace seed; use as caution, not as this seed's scope.
- `.planning/seeds/SEED-007-messaging-assistant-slack-telegram-zalo.md` - related messaging-channel seed; this seed is business/product positioning for Vietnam SMB communication ops.

## Notes

For v1.3, use this seed as directional context only: ship multi-Gmail production and keep the architecture channel-ready. Do not pull Zalo OA, CRM, or Microsoft provider production work into v1.3 unless the milestone is explicitly resized.
