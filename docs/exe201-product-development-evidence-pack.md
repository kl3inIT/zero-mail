# ZeroMail Product Development Evidence Pack

> Draft for EXE201 Product Development / Product Improvement evidence package.
> Working date: 2026-07-09.

## Collection status

Status: collected from current repo docs and planning artifacts.

This pack completes the "Collect ZeroMail product development evidence" task at document level. It still has two asset gaps: the screenshots `fb-zeromail-after.png` and `integrations-calendar-section.png` were referenced in Northstar but are not present in the current repo checkout.

## What this report will cover

This report is written to support the EXE201 product development section for ZeroMail. The main narrative is:

1. ZeroMail started from a clear user pain: busy professionals and founders need a trustworthy way to reduce Gmail overload without losing control of their inbox.
2. The product developed from basic AI triage into a broader Gmail workflow product: rules, assistant chat, outbound actions, multi-mailbox workspace support, and future Zero Flow workflow automation.
3. The strongest evidence is not only UI screens, but also safety architecture: privacy constraints, tenant isolation, Gmail scope justification, outbound send gates, audit logs, and live UAT.
4. The next report section should position ZeroMail's product improvement as a move from "AI sorts my email" to "AI helps run my email workflow safely."

The pack is structured so it can be copied into a formal submission:

- Product overview and problem.
- Product development timeline.
- Evidence of implemented product capabilities.
- Evidence of privacy, safety, and compliance readiness.
- Product improvement direction: Zero Flow.
- Gaps and next actions for the evidence package.

## Evidence matrix

| Product-development claim | Evidence found | How to use in EXE201 report |
|---|---|---|
| ZeroMail has a clear product thesis: trusted Gmail automation for inbox zero. | Project instructions, `.planning/ROADMAP.md`, `docs/zeromail_email_workflow_builder.md` | Use in introduction/problem framing. |
| Product evolved from AI triage into structured rule automation. | `.planning/MILESTONES.md`, `.planning/ROADMAP.md`, `docs/uat/phase-08.1-rule-actions.md` | Use in Product Improvement and Activity Update. |
| Rule authoring is not only a prompt box; saved rules are structured `When/Then` schemas. | Project rule-authoring policy, `docs/uat/phase-08.1-rule-actions.md` | Use as trust/usability improvement. |
| Outbound actions are high-risk and protected by product gates. | Project write-action policy, `docs/architecture/outbound-gateway-boundary.md`, `docs/uat/phase-08.1-rule-actions.md` | Use as safety/control evidence; note older fallback docs are stale. |
| Chat assistant supports user-initiated email actions. | `.planning/MILESTONES.md` v1.1 section, web route `apps/web/app/(protected)/(app)/chat/page.tsx` | Use as product capability expansion. |
| Multi-Gmail workspace support was shipped and live-verified. | `.planning/MILESTONES.md`, `.planning/milestones/v1.3-ROADMAP.md`, `.planning/milestones/v1.3-REQUIREMENTS.md` | Use as strongest implemented product evidence. |
| Privacy and compliance are product features, not only technical details. | `docs/casa/data-handling-attestation.md`, `docs/casa/privacy-policy-draft.md`, `docs/casa/scopes-justification.md` | Use in trust/safety subsection; update stale scope/no-send wording before external filing. |
| Zero Flow is the next product-improvement direction. | `docs/zeromail_email_workflow_builder.md` | Use as final product/improvement direction. |
| Current app has enough surface area for screenshots. | `apps/web/app/(protected)/(app)/**/page.tsx`, public images under `apps/web/public/images` | Use for screenshot appendix; missing two named screenshots still need capture/recovery. |

## 1. Product overview

ZeroMail is a Gmail-first AI email workflow SaaS for busy professionals, founders, and small teams. Its core promise is helping users reach inbox zero by using AI to triage incoming email, categorize messages, apply Gmail actions, and draft or send replies according to user-defined rules.

The product is not positioned as a general automation platform. It is a focused email productivity product where the main value is trust: the user must feel that the system understands email correctly, does not leak private content, does not take surprising destructive actions, and can be audited when automation runs.

The product thesis can be summarized as:

> ZeroMail helps users turn Gmail overload into safe, reviewable workflows: AI understands incoming email, applies structured rules, performs safe Gmail actions, and escalates sensitive actions through policy, audit, and user control.

This thesis matters because email is a high-trust surface. A weak AI assistant that misclassifies messages or sends unsafe replies can damage business relationships. Therefore, product development evidence should emphasize both user-facing capability and the engineering controls behind it.

## 2. Customer problem

The main customer problem is that important email arrives mixed with newsletters, operations updates, invoices, customer requests, recruiting messages, and low-priority notifications. Users often repeat the same actions:

- Marking newsletters or low-priority updates as archived.
- Labelling customer leads or support requests.
- Drafting similar replies to common requests.
- Following up when a customer does not reply.
- Reviewing invoices, attachments, calendar-related messages, and action items.

Traditional Gmail filters can match sender, subject, or simple keywords, but they do not understand intent well. For example, a founder may want to treat "Can we schedule a demo?", "Could you send pricing?", and "We are evaluating your product" as sales opportunities even if the exact words differ. ZeroMail's product direction is to make these workflows structured and AI-assisted while keeping user control.

## 3. Product development timeline

The repository planning docs show a progression from foundational MVP to richer workflow capability:

| Stage | Product development evidence | Meaning for EXE201 report |
|---|---|---|
| v1.0 MVP | Foundation, safety infrastructure, Gmail connection, onboarding, triage, rule basics, privacy posture | Proves the team did not start from UI mockups only; it built the trust and technical foundation first. |
| v1.1 Email assistant chat | User-initiated chat assistant, streaming backend, tool catalog, confirmed-send preview cards | Moves from automatic triage into an interactive assistant that can help users act on email. |
| v1.2 Admin console + user settings + rule actions | Admin-managed example catalog, structured rule actions, global Auto-send rules setting, outbound gateway, BYOK/settings | Makes rule authoring more operational and configurable. |
| v1.3 Gmail workspace foundation | Multi-Gmail mailbox model, active mailbox switcher, mailbox-owned rules/actions/audit, live UAT with two real Gmail accounts | Turns the product from single inbox automation into workspace-ready Gmail operations. |
| v1.4 planning | Calendar, Drive filing, booking links, meeting briefs | Shows the product roadmap extends from inbox zero into email-adjacent workflows. |
| Zero Flow concept | Gmail-specialized workflow builder with AI classify/extract, action logs, templates, approvals | Best product improvement story for the report: a clear future direction from rule builder to workflow builder. |

This development path supports a strong Product Development / Product Improvement narrative: ZeroMail evolved from an AI triage tool into a workflow product that can handle real operational email tasks.

## 4. Implemented product capabilities and evidence

### 4.1 Gmail-first AI triage and rule automation

The core product capability is AI-assisted Gmail triage. Users define rules and the system applies Gmail actions such as labels, archive, draft creation, read/unread changes, starring, spam marking, digest inclusion, and controlled outbound actions.

The important product decision is that natural language is only the front-end for authoring. The saved rule must become a structured, editable `When/Then` schema. This is important because it prevents the product from becoming an opaque AI prompt box. A user can understand and edit what the system will do.

Product evidence:

- Rules are structured around an "Enabled / Name / When / Then" mental model.
- The action catalog includes safe actions and outbound actions.
- The system distinguishes between rule-triggered automation and user-initiated chat assistant actions.
- All Gmail send execution must go through a shared outbound gateway, which makes the safety boundary testable.

Report wording:

> One of the main product improvements was converting natural-language rule authoring into a structured rule system. Users can describe what they want in plain language, but the authoritative saved artifact is an editable `When/Then` rule. This improves trust because users can review the exact conditions and actions before automation runs.

### 4.2 Expanded rule actions and outbound safety

ZeroMail supports expanded outbound rule actions such as `send_reply`, `forward_email`, and `send_email`. These actions are high-risk because they can communicate externally on behalf of the user, so they are controlled by product and runtime gates.

The current product decision is:

- Rule-triggered outbound actions are controlled by the global `Auto-send rules` setting.
- The default setting is ON.
- Runtime gates still enforce tenant correctness, sender safety-net list, per-tenant outbound rate caps, idempotency, and append-only audit.
- A blocked outbound action or failed send is recorded as a failed audit and does nothing else.
- Blocked/failed send or forward actions do not fall back to creating a Gmail draft.
- Only an explicit `save_draft` rule action writes a draft.

This is important for the report because it shows the team made specific trust and safety decisions instead of treating "AI sends email" as a simple feature.

Evidence note:

Some older docs, such as the Phase 08.1 UAT and CASA scope justification drafts, still describe an earlier policy where blocked outbound sends fall back to Gmail drafts or where auto-send was prohibited. For the EXE201 report, use the current project decision as the product truth and treat those older docs as historical evidence that the team iterated on safety policy. The evidence package should include a note that these docs should be refreshed before external compliance submission.

Report wording:

> The outbound-action design demonstrates product iteration. Earlier versions emphasized draft-only behavior, while the current product direction allows rule-triggered outbound actions under strict runtime gates. The important product improvement is not simply enabling send actions; it is placing all send execution behind one audited outbound gateway and recording blocked or failed actions without creating surprising drafts.

### 4.3 Chat assistant for user-initiated email actions

The chat assistant adds a different mode of interaction: the user can ask the assistant to help with email tasks, but sensitive actions are user-initiated and can be confirmed through preview cards.

This expands ZeroMail from passive automation into an active work assistant. The assistant can support send, reply, and forward flows while respecting privacy boundaries:

- User-authored draft bodies can persist in chat messages and pending actions for the life of the conversation.
- Raw Gmail-read email content remains banned from long-term chat persistence.
- Assistant tool outputs must distinguish draft data from extracted Gmail content.

Report wording:

> The assistant chat product surface improves usability because users do not need to configure every action in advance. They can ask the assistant for help, review a preview card, edit the draft, and decide whether to send. This supports the core trust thesis: AI assists, but the user stays in control for high-impact communication.

### 4.4 Multi-Gmail workspace foundation

The v1.3 milestone is strong product evidence because it moves the product beyond a single personal inbox. ZeroMail now has a workspace-owned, multi-Gmail model:

- A workspace can connect multiple Gmail mailboxes.
- Business configuration stays workspace-shared: credits, billing, AI provider/BYOK, global safety settings, templates.
- Gmail operations stay mailbox-isolated: OAuth, watch/history, inbox, rules, actions, outbound execution, audit, and display identity.
- Users can switch active mailbox from app chrome.
- Rules are mailbox-owned by default.
- Cross-mailbox reuse requires explicit copy action, not silent all-mailbox execution.

This directly supports the target segment of founders and small teams because a founder may manage multiple operational mailboxes, or a small business may need shared operational context without full enterprise collaboration yet.

The repository milestone summary states that v1.3 was live-verified with two real Gmail mailboxes, with 10/10 UAT pass. Two real issues were found and fixed:

- Duplicate add-Gmail OAuth error handling.
- Inbox projection cross-mailbox leak after switching mailboxes.

These fixes are valuable evidence because they show real browser/Gmail testing found trust-related bugs and the product improved because of it.

Report wording:

> A major product improvement was the multi-Gmail workspace foundation. This changed ZeroMail from a single-inbox assistant into a workspace-ready product. The design explicitly separates workspace-level business settings from mailbox-level Gmail state, which supports both usability and safety. The live UAT with two real Gmail mailboxes is especially important evidence because it verified actual switching, isolation, rule behavior, outbound audit, and privacy logging.

### 4.5 User-facing web surfaces

The current web application includes routes that map to the product story:

- `/inbox` for mailbox reading and active Gmail context.
- `/needs-reply` for reply workflow management.
- `/rules` for rule authoring and automation.
- `/chat` for assistant interaction.
- `/analytics` for visibility.
- `/ai` and `/settings` for user configuration, provider/BYOK, behavior, safety, and connection settings.
- `/integrations` for external connection surfaces.
- `/cleanup/bulk-unsubscribe` and `/cleanup/suppression` for inbox cleanup workflows.
- `/onboarding/gmail-connect` and `/onboarding/template-select` for first-time activation.

These routes show that the product is not only a backend automation engine. It has a user-facing workflow around onboarding, configuration, inbox work, rules, assistant chat, analytics, and cleanup.

Report wording:

> The product surface is organized around the actual email workflow: connect Gmail, choose templates, process inbox, define rules, use assistant chat, review analytics, and manage settings. This is more defensible than a generic AI dashboard because each screen maps to a real user job.

## 5. Privacy, safety, and compliance evidence

ZeroMail's strongest product evidence is its trust architecture. For a Gmail automation product, privacy and safety are part of the product, not only engineering details.

### 5.1 Gmail scope justification

ZeroMail requests Gmail access because its core function requires modifying Gmail state: labeling, archiving, saving drafts, and controlled outbound actions. A read-only Gmail scope would not allow the product to deliver the core value.

The scope evidence should explain:

- Why Gmail modify access is needed.
- What the product does with that access.
- What it does not store long term.
- How users can disconnect or delete their account.

Important correction for current report:

Older CASA docs say "No sending" and "gmail.modify only." The current product direction includes outbound send/reply/forward actions behind the shared outbound gateway and runtime gates. Before formal external filing, CASA scope docs should be updated to match current product behavior and OAuth scopes.

For EXE201, the report can frame this as product learning:

> The team identified that trustworthy outbound automation is a core user value, but also a high-risk capability. The product therefore evolved from draft-only assumptions to an audited outbound gateway model with explicit gates, rate caps, idempotency, and failed-audit behavior.

### 5.2 No long-term raw email body storage

The privacy posture is:

- No long-term storage of raw email bodies from Gmail.
- No long-term storage of LLM prompts/completions for email-content processing.
- No embeddings of user mail.
- Gmail-read content is sanitized, truncated, prompt-injection-hardened, and handled in short-lived processing contexts.
- User-authored draft data in chat send/reply/forward preview cards can persist for the lifetime of the conversation because it is user-authored draft data, not extracted Gmail content.

This distinction is important and should be written clearly in the report. It shows the product can support useful assistant workflows without treating all content as equivalent.

Report wording:

> ZeroMail separates extracted email content from user-authored draft data. Email bodies received from Gmail are not stored long term, while drafts the user reviews and chooses to send can be stored as part of the assistant conversation. This source distinction allows the product to be useful without weakening its privacy promise.

### 5.3 Tenant and mailbox isolation

The product is multi-tenant and multi-mailbox, so data isolation is a core product requirement.

Evidence from docs:

- Tenant context is bound after authentication.
- Multi-tenant safety is tested with concurrent requests.
- Mailbox context is used for Gmail-specific operations.
- Cross-account isolation tests prove one mailbox cannot act as another mailbox through crafted IDs.
- Architecture rules ban dangerous tenant-only Gmail lookups in mailbox-scoped flows.

Report wording:

> ZeroMail's product trust depends on isolation. A user must not see or act on another tenant's data, and one Gmail mailbox must not accidentally send, archive, or read as another mailbox. The product evidence includes both architectural boundaries and live UAT that specifically tested mailbox switching and isolation.

### 5.4 Logging and audit safety

The docs describe a privacy logging posture:

- Logs use technical IDs, hashes, enum reasons, and tenant/mailbox identifiers.
- Logs must not include raw recipients, Gmail bodies, prompts, completions, draft body text, refresh tokens, or sensitive content.
- `Sensitive<T>` wrapper and log-scrubbing tests are used as evidence.
- Audit rows record what happened without storing forbidden email content.

For report purposes, this can be positioned as "explainability without leakage." Users and operators need to know what automation did, but logs must not become a second copy of private email.

Report wording:

> ZeroMail treats auditability and privacy as a pair. The product records which rule ran, which action was attempted, whether it succeeded or failed, and why, but does not use logs as a hidden archive of email bodies or LLM conversations.

## 6. Product improvement direction: Zero Flow

The `zeromail_email_workflow_builder.md` document provides the clearest future-facing product improvement: Zero Flow.

Zero Flow is a Gmail-specialized workflow builder. It is not meant to become a general n8n or Zapier clone. The product direction is:

> A workflow builder for Gmail where AI understands email content and runs user-configured workflows safely.

### 6.1 Why Zero Flow is the right next product improvement

Current rules can automate email actions, but users eventually need more than a single condition-action rule. Real workflows may require:

- AI classification.
- Structured data extraction.
- Branching by confidence, deadline, amount, or message type.
- Multiple Gmail actions.
- Reminders.
- Notifications.
- Dry-run testing.
- Execution history.
- Approval for sensitive actions.

Zero Flow packages these needs into a product feature that is easier to explain and demonstrate.

### 6.2 Recommended MVP shape

The best MVP is a step builder, not a complex drag-and-drop canvas.

Recommended workflow:

```text
WHEN: A new email arrives
IF: AI classifies it as a quote/demo request
THEN:
  1. Add label "Lead"
  2. Generate a reply draft
  3. Create a follow-up reminder
  4. Notify the user
```

This is easier for users to understand and easier for the team to validate before introducing a full visual canvas.

### 6.3 Recommended templates

The report should mention these high-value templates:

| Template | User value |
|---|---|
| Quote/demo request | Helps founders and sales users respond to leads faster. |
| Invoice processing | Helps business users label invoices and create reminders. |
| Complaint handling | Prevents urgent customer issues from being archived or ignored. |
| Recruiting/CV | Helps summarize candidates and create interview drafts. |
| Newsletter digest | Reduces noise while preserving content for later review. |

### 6.4 Why Zero Flow strengthens the business story

Zero Flow makes ZeroMail easier to pitch:

- It turns AI email triage into visible workflows.
- It gives users a clear mental model: trigger, condition, action, log.
- It supports repeatable business use cases.
- It gives the team a demo-friendly product surface.
- It creates future monetization paths around workflow volume, premium actions, and workspace/team use.

Report wording:

> Zero Flow is the clearest product improvement direction because it turns ZeroMail from an assistant that reacts to email into a workflow product that users can configure, test, and trust. It keeps the scope focused on Gmail while giving enough flexibility for founder, sales, accounting, recruiting, and personal productivity use cases.

## 7. Evidence inventory for submission

| Evidence | File / surface | How to use in report |
|---|---|---|
| Product workflow concept | `docs/zeromail_email_workflow_builder.md` | Use as the main Zero Flow / product improvement evidence. |
| Rule actions UAT | `docs/uat/phase-08.1-rule-actions.md` | Use for rule examples, action catalog, outbound gateway, UAT checklist; note outdated fallback assumptions. |
| CASA data handling | `docs/casa/data-handling-attestation.md` | Use for privacy, tenant isolation, log safety, token encryption, deletion/revocation evidence. |
| Gmail scope justification | `docs/casa/scopes-justification.md` | Use for explaining why Gmail access is necessary; flag current-send-policy mismatch before external filing. |
| Privacy policy draft | `docs/casa/privacy-policy-draft.md` | Use for user-facing privacy narrative; update if scopes/send behavior changed. |
| CASA submission log | `docs/casa/submission-log.md` | Use as compliance-readiness evidence, not as completed verification. |
| Project roadmap | `.planning/ROADMAP.md` | Use for shipped milestones v1.0-v1.3 and v1.4 direction. |
| v1.3 roadmap | `.planning/milestones/v1.3-ROADMAP.md` | Use for multi-Gmail workspace evidence and live UAT summary. |
| v1.3 requirements | `.planning/milestones/v1.3-REQUIREMENTS.md` | Use for exact completed requirements and traceability. |
| Web app routes | `apps/web/app/**/page.tsx` | Use as product surface evidence: inbox, rules, chat, analytics, integrations, cleanup, settings. |

Missing evidence from Northstar task:

- `fb-zeromail-after.png` was referenced in the task, but it was not found in the current repo.
- `integrations-calendar-section.png` was referenced in the task, but it was not found in the current repo.

These should be captured or recovered before final submission if screenshots are required.

## 8. Gaps and risks to mention honestly

The report should not overclaim. Current evidence has several gaps:

1. Some compliance docs are drafts and contain outdated assumptions around no-send or fallback-to-draft behavior.
2. Screenshot evidence referenced by the task is missing from the current repo.
3. Zero Flow is currently a product specification/future direction, not necessarily a fully shipped production feature.
4. CASA submission log is still marked draft with TBD fields.
5. v1.4 Calendar/Drive features are in planning, not shipped.

Recommended report language:

> The current evidence package is strong for product development and technical readiness, but the team should refresh older CASA/UAT documents before using them as external compliance artifacts. For EXE201, these documents still provide useful evidence of product thinking, safety requirements, and iteration history.

## 9. Draft report section for EXE201

### Product Development / Product Improvement

ZeroMail was developed as an AI email workflow product for Gmail users who need to reduce inbox overload without giving up control over important communication. The product's starting point is a clear customer problem: busy professionals and founders receive many repetitive emails, including customer leads, invoices, newsletters, recruiting messages, support issues, and operational updates. Traditional Gmail filters can only handle simple sender, subject, or keyword matching. ZeroMail improves this by using AI to understand email intent and convert user instructions into structured, editable rules.

The main product improvement is the shift from simple AI triage to a trusted workflow system. In the rule builder, natural language is only the authoring interface. The saved rule is a structured `When/Then` schema that users can review and edit. This design is important because email automation is high trust: users must know exactly when the system will label, archive, draft, send, or forward messages.

A second improvement is the expansion of rule actions. ZeroMail supports Gmail actions such as labeling, archiving, marking read/unread, starring, adding to digest, saving drafts, and outbound actions such as send reply, forward email, and send email. Because outbound actions are sensitive, they run through a shared outbound gateway and runtime gates such as the global Auto-send rules setting, tenant correctness checks, sender safety-net list, per-tenant rate caps, idempotency, and append-only audit. If an outbound action is blocked or fails, the system records a failed audit and does not create a surprise draft. This shows that the product improvement is not only adding more automation, but adding automation that can be trusted.

ZeroMail also added a chat assistant for user-initiated email workflows. The assistant can help draft replies or prepare actions while the user reviews preview cards before confirming sensitive actions. This gives the user a faster way to work with email while preserving control. The product also separates user-authored draft data from Gmail-read email content: draft data that the user reviews may persist during the conversation, but raw email bodies, prompts, completions, and embeddings from Gmail processing are not stored long term.

Another major development was the multi-Gmail workspace foundation. Earlier versions focused on a single Gmail account, while the current product supports a workspace model with multiple connected Gmail mailboxes. Workspace-level settings such as billing, credits, AI provider configuration, BYOK, templates, and global safety controls are shared, while Gmail-specific state such as OAuth, inbox, rules, actions, outbound execution, and audit logs remain isolated per mailbox. This is important for founders and small teams because they may manage multiple business mailboxes but still need clear boundaries for which mailbox is reading, acting, or sending.

The repository evidence shows this was live-verified with two real Gmail mailboxes. The test covered account connection, mailbox switching, mailbox-owned rules, send-from behavior, audit provenance, and privacy-safe logs. During UAT, the team found and fixed a duplicate add-Gmail OAuth error and an inbox projection cross-mailbox leak. These fixes are strong evidence of real product validation because they directly relate to the user's trust in multi-mailbox isolation.

The next major product improvement direction is Zero Flow, a Gmail-specialized workflow builder. Instead of becoming a general automation tool like Zapier or n8n, Zero Flow focuses on email workflows where AI understands incoming messages and runs user-configured steps. A typical workflow can be: when a new email arrives, AI classifies whether it is a quote request, then ZeroMail adds a Lead label, creates a reply draft, creates a follow-up reminder, and notifies the user. This makes the product easier to demonstrate and easier for users to understand because every automation has a trigger, condition, action, and execution log.

Zero Flow should begin with a step builder rather than a complex drag-and-drop canvas. The first templates should target high-value email workflows: quote/demo requests, invoice processing, complaint handling, recruiting/CV review, and newsletter digest. This direction is aligned with the core value of ZeroMail because it helps users convert repetitive email handling into repeatable workflows while keeping logs, approval, and safety controls.

Privacy and compliance readiness are also part of the product development evidence. The CASA documents describe token encryption, tenant isolation, account deletion, external revocation handling, log scrubbing, and OpenAPI/codegen verification. ZeroMail's privacy posture is that Gmail-read email bodies are not stored long term, LLM prompts/completions for email-content processing are not persisted, and embeddings of user mail are not used. Audit logs record technical events and decisions without becoming a hidden archive of private email content.

Overall, ZeroMail's product development shows a clear evolution: from AI-assisted inbox triage, to structured rule automation, to assistant-driven actions, to multi-mailbox workspace support, and finally toward Zero Flow as a workflow builder for Gmail. The strongest product evidence is the combination of user-facing capability and safety architecture. This makes the product more credible for real inbox usage, where users need both automation speed and confidence that the system will not leak data or act unexpectedly.

## 10. Recommended next actions

1. Capture or recover the missing screenshots referenced by the Northstar task:
   - `fb-zeromail-after.png`
   - `integrations-calendar-section.png`
2. Add a short appendix with screenshots and captions:
   - Gmail connect/onboarding.
   - Rules page / rule examples.
   - Chat assistant preview card.
   - Active mailbox switcher.
   - Integrations/settings page.
   - Any Zero Flow prototype if available.
3. Refresh outdated docs before external submission:
   - `docs/uat/phase-08.1-rule-actions.md`
   - `docs/casa/scopes-justification.md`
   - `docs/casa/privacy-policy-draft.md`
4. Use this report as the source for the EXE201 Product Development / Product Improvement section.
