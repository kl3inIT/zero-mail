# Feature Landscape

**Domain:** AI Gmail-triage SaaS (Zero Mail, alternative to Inbox Zero)
**Researched:** 2026-04-24
**Overall confidence:** MEDIUM-HIGH (Inbox Zero verified from official docs + GitHub; competitor features from verified product pages + recent reviews)

---

## Executive Summary

The AI email-assistant category in 2026 has split into three archetypes:

1. **Full-replacement clients** (Shortwave, Superhuman, Canary) — own the whole UI, bet on native AI chat + triage inside the mail client.
2. **Sidecar assistants** (Inbox Zero, SaneBox) — leave Gmail's UI alone, run AI in the background that labels/archives/drafts inside the real Gmail.
3. **Narrow utilities** (Cleanfox, Unroll.me) — one-trick unsubscribe/clean tools, not assistants.

Zero Mail is firmly in the **sidecar** archetype (and the same archetype as Inbox Zero). That archetype's table stakes in 2026 are: Google OAuth onboarding, natural-language rules with preview, per-message audit log, pause/kill switch, labels+archive actions, Gmail-draft creation (never auto-send is *a premium safety position*, not a limitation), credit/billing UI, and minimal analytics.

The **competitive frontier** has moved to: per-recipient tone learning (Superhuman Instant Reply / Auto Drafts), conversational rule authoring with live preview (Inbox Zero "plain English" + Shortwave AI chat), and meeting/calendar-aware triage (Superhuman Meeting Briefs, Shortwave). Inbox Zero leads on open-ended rule expression; Superhuman leads on tone quality; Shortwave leads on semantic search and agentic flows.

**For v1 scope:** match Inbox Zero's rule engine, onboarding, audit log, and Reply-Zero-style "needs reply" labeling. Skip Shortwave/Superhuman-grade UI polish (not achievable in v1). Defer bulk unsubscribe, cold-email-as-distinct-feature, and reply-tracker as explicit anti-features — these are all expressible as user rules in the v1 engine.

---

## Table Stakes
Features users expect; missing any of these = churn within a week.

| # | Feature | Why Expected | Complexity | Dependencies | Competitor Reference | Privacy-Fit |
|---|---------|--------------|------------|--------------|----------------------|-------------|
| T1 | Google OAuth + Gmail scope consent (read, modify, compose) with explicit scope list shown to user | Trust floor for any AI-on-inbox product | S | — | Inbox Zero, Shortwave | OK — tokens stored encrypted, no body storage implied |
| T2 | Connect / disconnect / revoke Gmail account; delete account wipes all derived metadata | Privacy regulator and app-store table stakes | S | T1 | All competitors | Full fit — deletion is our privacy story |
| T3 | Rule CRUD (list, create, edit, enable/disable, reorder, delete) | You can't sell "AI rules" and not let users edit them | M | — | Inbox Zero (cornerstone) | Fit — rule text is metadata, persisted |
| T4 | Natural-language rule input with AI → structured matcher + action parsing | Inbox Zero set this bar in 2024; everyone assumes it now | L | T3, LLM gateway | Inbox Zero: "Archive all newsletters but label investment-related ones as important" | Fit — rule compilation is a one-shot LLM call; result is persisted, prompt is not |
| T5 | Rule preview / dry-run against recent mail before enabling | Users will not grant write access without a "show me what this would have done" view | L | T4, Gmail history API | Inbox Zero has a "Test" button per rule | Fit — preview runs in-memory, no body persistence |
| T6 | Per-message audit log: "rule X matched, did Y action, at T, why" | Any autonomous action on real email must be reversible + explainable | M | T3 | Inbox Zero Activity tab | Fit — log stores metadata + decision summary, NOT the body |
| T7 | Global pause / kill switch for all automation | When it misbehaves, user needs one button to stop everything | S | T3 | Inbox Zero, SaneBox | Fit |
| T8 | Undo last action / undo window per triage decision | Mistakes happen; Gmail labels/archive are reversible and users expect undo | M | T6 | Gmail native, Superhuman | Fit — undo is just another Gmail API call |
| T9 | Apply labels (create + assign) as a rule action | Core Gmail organization primitive | S | T1 (gmail.modify scope) | All | Fit |
| T10 | Archive / skip-inbox as a rule action | #1 reason users buy a triage tool | S | T1 | All | Fit |
| T11 | Save Gmail draft as a rule action (never auto-send in v1) | Your hero feature; must land in real Gmail drafts, not in-app | M | T1 (gmail.compose), LLM gateway | Inbox Zero, Superhuman Auto Drafts | Fit — draft body is written to Gmail, not retained by us |
| T12 | "Needs reply" labeling (light Reply Zero) | Users expect the assistant to surface what needs attention | M | T4 | Inbox Zero Reply Zero, Superhuman Auto-Label | Fit — we label in Gmail, store no body |
| T13 | Gmail push via Pub/Sub (near-real-time triage, not polling) | Polling feels broken; competitors are all push-based | L | T1, infra | Inbox Zero architecture reference | Fit — webhook just carries historyId, no content |
| T14 | Basic analytics dashboard: volume triaged, actions taken, top senders, rule hits over time | Users want proof the thing is working | M | T6 | Inbox Zero Stats page | Fit — all from metadata / audit log, never body content |
| T15 | Credit balance UI + per-action cost display + low-balance warnings + block when empty | Prepaid model is dead on arrival without a clear balance surface | M | Billing provider, LLM cost accounting | Novel for this category (most charge subs) — model after OpenRouter balance UX | Fit |
| T16 | Buy credits flow (Stripe/Lemon Squeezy checkout) | — | M | T15 | Standard SaaS | Fit |
| T17 | Settings: model selection (default vs BYOK), provider key management | BYOK is advertised in our constraints | M | LLM gateway | Inbox Zero has BYOK; OpenRouter in general | Fit — keys encrypted at rest |
| T18 | Error surfacing: per-rule failure with reason; token/quota/model errors visible to user | Silent failure = churn | S | T6 | Inbox Zero error banners | Fit |
| T19 | Onboarding: walk user from OAuth → first rule → first preview → first triage → see audit log | First 5 minutes determines retention | L | T1–T6 | Inbox Zero setup wizard | Fit |
| T20 | Email notifications / digest of what the assistant did (opt-in) | Confidence-builder early on | S | T6 | Inbox Zero weekly digest | Fit — content from audit metadata |

---

## Differentiators
Features that set the product apart. Not all need to ship in v1 — pick 1–2 to lead with.

| # | Feature | Value Proposition | Complexity | Dependencies | Competitor Reference | Privacy-Fit |
|---|---------|-------------------|------------|--------------|----------------------|-------------|
| D1 | Conversational rule builder with inline preview ("show me what this rule would have done on the last 50 mails") | This is the 2026 UX shape — hybrid chat + structured preview panel | L | T4, T5 | Inbox Zero AI Chat, Shortwave AI Assistant | Fit — preview in-memory, no retention |
| D2 | Tone-matched drafts learned from user's sent mail | Killer feature post-Superhuman Instant Reply | XL | T11, sent-mail sampling, style fingerprint | Superhuman Instant Reply, Shortwave Ghostwriter | **Tension** — need a persisted "style profile" (short stylometric signal, NOT raw bodies). Design a derived fingerprint (avg sentence length, greeting/signoff patterns, formality score) that is privacy-safe. Avoid storing embeddings of sent mail. |
| D3 | Per-recipient tone adaptation (formal with execs, casual with teammates) | Superhuman's 2026 flagship | XL | D2, per-contact metadata | Superhuman Auto Drafts | **Tension** — requires per-contact style notes. Store only derived features, not historical threads. |
| D4 | Thread summarization on demand | Users expect a "summarize this thread" button in 2026 | M | LLM gateway | Shortwave (native), Superhuman | Fit — summary is ephemeral; do not persist summary text beyond a short cache |
| D5 | Semantic search over inbox (RAG) | Shortwave's hero feature | XL | Embeddings, vector store, re-indexing on new mail | Shortwave | **POOR FIT for v1** — requires persistent embeddings of email bodies. Directly violates our privacy constraint. Skip. |
| D6 | Smart "follow-up" / reply tracker (nudge when waiting on a response) | Superhuman Auto Reminders, Inbox Zero Reply Zero's second half | L | T6, needs thread-level state | Superhuman, Inbox Zero | Fit — tracks message-ids + timestamps, no body |
| D7 | VIP / priority sender detection | Superhuman Split Inbox, SaneBox VIP | M | T6, sender frequency analysis | Superhuman, SaneBox | Fit — derived from metadata only |
| D8 | Snooze-until-signal (snooze until sender replies, or until Monday, etc.) | Expected by prosumer mail-power-users | M | Scheduler | Shortwave, Superhuman | Fit |
| D9 | Calendar-aware triage (meeting briefs, "I'm in meetings today — triage differently") | Superhuman Meeting Briefs is hot in 2026 | L | Calendar API scope, meeting extraction | Superhuman, Shortwave | Fit — calendar event metadata only |
| D10 | Cold-email detection (as a shipped rule template, not a distinct feature) | Inbox Zero markets this; we can ship the template, not a button | M | T4 | Inbox Zero Cold Email Blocker | Fit — classifier prompt, no stored training data |
| D11 | Rule template gallery ("Archive Stripe receipts", "Triage investor intros") | Lowers cold-start — biggest onboarding drop-off is "I don't know what rules to write" | S | T3 | Inbox Zero | Fit |
| D12 | Audit log exportable to CSV (transparency lever) | Privacy-conscious users love this | S | T6 | Rare in category | Fit |
| D13 | Explicit "what we store" page in-product | Privacy-forward positioning; Inbox Zero does this poorly | S | — | None as a first-class in-product surface | Fit — it *is* the privacy story |
| D14 | Chat-with-assistant command surface ("label all emails from Amazon as shopping") | 2026 mainstream, but not required in v1 | L | T4 | Inbox Zero AI Chat, Shortwave | Fit — per-message; no history persistence |
| D15 | BYOK model selection per rule (e.g. use GPT-5 for draft, mini model for classify) | Differentiates on cost control for prosumers | M | T17 | Rare; OpenRouter-style | Fit |
| D16 | Prepaid credits as the billing primitive | Most competitors are flat-subscription; credits align cost ↔ LLM spend honestly | M | T15, T16 | Novel for category | Fit |
| D17 | Never-auto-send as a marketing position | Anti-feature turned differentiator: "We don't send email on your behalf. Ever." | S | T11 | Opposite of Superhuman Auto Drafts (which can send) | Fit |

**Lead differentiators for v1:** D1 (conversational rule builder + preview), D13 (privacy-forward posture in-product), D16 + D17 (credits + never-auto-send as trust story), D11 (rule templates to fix cold start). Defer D2/D3 (tone learning) to v1.5 after evaluating privacy-safe stylometry.

---

## Anti-Features
Things to explicitly NOT build in v1.

| # | Anti-Feature | Why Avoid | What to Do Instead |
|----|--------------|-----------|--------------------|
| A1 | Auto-send replies without human review | One bad auto-send kills the brand. Superhuman has spent 18 months building the review loop for this and still defaults to draft. | Save Gmail draft only. Market "never auto-send" as D17. |
| A2 | Outlook / Microsoft 365 support | Different OAuth, push model (Graph webhooks), label/category model. Doubles integration surface. | Gmail-only in v1; re-evaluate after PMF. |
| A3 | Generic IMAP/SMTP | No push, no labels, no threads API; different auth. | Defer indefinitely. |
| A4 | Self-hosted / OSS distribution | Inbox Zero's OSS story is a support tax; multi-tenant SaaS is our model. | Cloud-only SaaS. |
| A5 | Team / seats / workspaces | Prosumer v1; team billing, role model, shared rules = 3+ months of work. | Individuals only; each seat is a separate account. |
| A6 | Reply-tracker / follow-up nudges | Complex thread-state machine; not essential to hero. | Defer to v1.5; expressible as a "if no reply in 3 days, label Waiting" rule. |
| A7 | Bulk unsubscribe as a first-class UI | Inbox Zero and Cleanfox own this. Expressible as a rule. | Ship as a rule template. |
| A8 | Cold-email blocker as a first-class UI | Same as A7. | Ship as a rule template (D10). |
| A9 | Enterprise compliance (SSO, SCIM, audit exports for compliance, DPA) | Not the buyer. | Prosumer target. |
| A10 | Semantic search / RAG over mail | Requires persistent embeddings of email bodies → violates privacy constraint (D5). | Skip. Position privacy-first as the anti-Shortwave stance. |
| A11 | Full mail client UI (inbox browse, thread view, compose) | Inbox Zero *and* Shortwave both have this; it's a 6-month effort. | Deep-link to Gmail. We are a sidecar. |
| A12 | Mobile apps (native iOS/Android) | 3-month effort per platform. Desktop web first. | Responsive web; native mobile is v2+. |
| A13 | Slack / Telegram / Teams chat surface | Inbox Zero has this; it's polish, not foundation. | Defer to v1.5. |
| A14 | Attachment auto-filing to Drive/OneDrive | Inbox Zero ships this; adds another OAuth scope + provider. | Defer. |
| A15 | Meeting briefs | Superhuman flagship; requires calendar depth. | Defer to v1.5 (D9). |
| A16 | Long-term storage of email bodies, LLM prompts/completions, embeddings | Privacy constraint — this is the product's foundation. | Never store. Sanitize + truncate + discard. |

---

## Feature Dependencies

```
T1 (OAuth) ──┬──> T9 (labels)
             ├──> T10 (archive)
             ├──> T11 (draft)       ──> D2 (tone) ──> D3 (per-recipient tone)
             ├──> T13 (Pub/Sub push)
             └──> T17 (BYOK)

T3 (rule CRUD) ──> T4 (NL rule compile) ──> T5 (preview) ──> D1 (conversational builder)
                                        └─> D10 (cold-email rule template)
                                        └─> D11 (rule template gallery)

T6 (audit log) ──┬──> T8 (undo)
                 ├──> T14 (analytics)
                 ├──> D6 (follow-up tracker)
                 ├──> D7 (VIP detection)
                 └──> D12 (audit export)

T15 (credit balance) ──> T16 (buy credits) ──> D16 (credits as primitive)

LLM gateway ──> T4, T11, T12, D1, D2, D4, D10, D14
```

**Critical path for a shippable v1:** T1 → T13 → T3 → T4 → T5 → T11 → T12 → T6 → T14 → T15/T16 → T19 (onboarding stitches it all). Everything else is polish or differentiator.

---

## UX Shape for Natural-Language Rules (2026 State of the Art)

The 2026 consensus is **hybrid: structured rule list + conversational assistant + live preview panel**, *not* pure chat and not pure forms.

**Evidence:**

- **Inbox Zero (sidecar archetype, closest to us):** Users write rules in plain English in a text box. AI compiles to a structured matcher + action pairs that are then visible as editable fields. A "Test" affordance runs the rule against recent mail and shows what would have happened. Rules live in a list you can reorder and toggle. This is the pattern to match.

- **Shortwave (full-client archetype):** AI Assistant is a chat sidebar that can create filters/automations on command ("auto-archive promotions from Amazon"). But filters, once created, appear in a structured settings list — not as a chat log. Chat is the *entry point*, structure is the *persistence*.

- **Superhuman (full-client archetype):** Auto-Labels are created via natural-language descriptions ("job applications", "contract renewals") inside a form field. No chat UI for rule authoring. The AI is invisible; the form is the surface.

- **General UX research (2026):** Conversational forms get ~40% higher completion than traditional forms, but work best for *short, open-ended* input. Long complex flows need structured UI. The winning 2026 pattern (Linear command palette, Notion AI sidebar, GitHub Copilot inline) is *structured interface with a conversational layer bolted on*.

**Recommendation for Zero Mail v1:**

1. **Primary surface: a rule list** (table of rules with enable/disable, drag-reorder, edit, delete). This is where rules *live* and where they're understandable at a glance.
2. **Rule creation: a "describe your rule" text box** backed by AI compilation. After compilation, show the parsed structure (matcher + actions) in editable form — user can tweak without re-prompting.
3. **Preview panel next to the editor:** as the user types / compiles, show "this rule would have matched 12 of your last 100 emails" with expandable hits. This is the single biggest trust lever. Inbox Zero has it; we must match it.
4. **Do NOT** do a pure chat interface for rule authoring in v1. Chat is good for one-off actions ("archive everything from Amazon right now") but bad for *durable rules users edit later*. Chat for actions is a v1.5 addition (D14).
5. **Rule template gallery** as the cold-start fallback (D11). Most users don't know what rules to write. Give them 10 templates ("Archive Stripe receipts", "Label investor intros", "Draft a polite decline for cold sales pitches") they can clone and edit.

---

## MVP Recommendation

**Ship in v1 (in order):**

1. **T1, T2** Google OAuth + account lifecycle
2. **T13** Pub/Sub push pipeline (infra prerequisite; hardest non-UI piece)
3. **T3, T4, T5** Rule CRUD + NL compilation + preview (the core loop)
4. **T9, T10, T11** Label, archive, save-draft actions (the only three actions in v1)
5. **T6** Audit log (required from day one — safety)
6. **T7, T8** Pause + undo (required from day one — safety)
7. **T12** Needs-reply labeling (light Reply Zero)
8. **T14** Basic analytics (metadata-only)
9. **T15, T16, T17** Credits UI + checkout + BYOK
10. **T18, T19, T20** Error surfacing, onboarding, digest
11. **D1** Conversational-ish rule builder with preview panel
12. **D11** Rule template gallery (cold-start fix)
13. **D13** In-product "what we store" page (privacy story)
14. **D16, D17** Credits + never-auto-send as explicit marketing positions

**Defer to v1.5 (after PMF signal):**

- D2/D3 Tone-matched drafts (need privacy-safe stylometry design)
- D4 Thread summarization on demand
- D6 Follow-up tracker
- D7 VIP detection
- D8 Snooze-until-signal
- D9 Calendar-aware triage
- D14 Chat command surface
- D15 Per-rule model selection

**Never in this product (anti-features):** A1–A16.

---

## Privacy-Fit Summary

Our "no long-term storage of bodies, prompts, completions, embeddings" constraint is compatible with the **entire v1 scope** above. The only features it excludes are:

- **D5 Semantic search / RAG** — requires persistent embeddings. Skip. Position as a competitive anti-stance.
- **D2/D3 Tone learning** — has tension; solvable via derived stylometric features stored instead of raw sent mail or embeddings. Design work required before shipping.

Everything else — rules, preview, triage, audit log, analytics, credits — runs on metadata (senders, timestamps, subjects, decisions, rule-ids, message-ids, labels applied) and ephemeral in-memory body handling during a single LLM call. This is defensible in a trust-page.

---

## Sources

- [Inbox Zero GitHub README (elie222/inbox-zero)](https://github.com/elie222/inbox-zero) — HIGH confidence
- [Inbox Zero official docs](https://docs.getinboxzero.com/) — HIGH
- [Inbox Zero llms.txt feature summary](https://docs.getinboxzero.com/llms.txt) — HIGH
- [Inbox Zero Cold Email Blocker page](https://www.getinboxzero.com/block-cold-emails) — HIGH
- [Shortwave — Automate your email with AI](https://www.shortwave.com/) — HIGH
- [Shortwave AI Assistant docs](https://www.shortwave.com/docs/guides/ai-assistant/) — HIGH
- [Shortwave Review 2025 (max-productive.ai)](https://max-productive.ai/ai-tools/shortwave/) — MEDIUM
- [Is Shortwave Worth It? (2026 review)](https://get-alfred.ai/blog/is-shortwave-worth-it) — MEDIUM
- [Superhuman — Introducing Instant Reply](https://blog.superhuman.com/superhuman-ai-instant-reply/) — HIGH
- [Superhuman — The next evolution of Superhuman AI](https://blog.superhuman.com/the-next-superhuman-ai/) — HIGH
- [Superhuman — Auto Reminders & Auto Drafts](https://help.superhuman.com/hc/en-us/articles/40144492186515-Auto-Reminders-Auto-Drafts) — HIGH
- [Superhuman AI Review 2026 (Gmelius)](https://gmelius.com/blog/superhuman-ai-review) — MEDIUM
- [Superhuman AI Auto-Tagging launch](https://www.aibase.com/news/15544) — MEDIUM
- [SaneBox Review 2026 (toolchamber.com)](https://toolchamber.com/sanebox-review/) — MEDIUM
- [SaneBox vs Shortwave (fahimai.com)](https://www.fahimai.com/sanebox-vs-shortwave) — MEDIUM
- [Conversational UI: when chat helps (marcfriedmanportfolio.com)](https://www.marcfriedmanportfolio.com/blog/conversational-ui-chat-interfaces/) — MEDIUM
- [Unboxd vs Inbox Zero 2026](https://unboxd.ai/blog/unboxd-vs-inbox-zero.html) — LOW (competitor marketing)
- [Jotform Conversational Form Design Guide](https://www.jotform.com/ai/agents/chatbot-design/) — MEDIUM

**Confidence assessment overall:** HIGH for Inbox Zero capability mapping (official sources); HIGH for Superhuman and Shortwave flagship features (official blog posts); MEDIUM for exact 2026 feature-parity comparisons (relies on review sites that can lag or be promotional).
