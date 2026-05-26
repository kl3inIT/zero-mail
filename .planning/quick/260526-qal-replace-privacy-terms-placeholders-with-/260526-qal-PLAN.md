---
phase: 260526-qal
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - apps/web/i18n/messages/en.json
  - apps/web/i18n/messages/vi.json
  - apps/web/app/(public)/privacy/page.tsx
  - apps/web/app/(public)/terms/page.tsx
autonomous: true
requirements:
  - QUICK-260526-qal
must_haves:
  truths:
    - "Visiting /privacy renders a full, launch-ready Privacy Policy in the user's locale (vi or en) with TOC, section anchors, and tokenized readable typography."
    - "Visiting /terms renders a full, launch-ready Terms of Service in the user's locale with TOC, section anchors, and tokenized readable typography."
    - "Privacy Policy explicitly covers: who 'Zero Mail team' is (student/MVP, pre-launch), what data is collected, what is NOT stored (raw email bodies, LLM prompts/completions on email content, embeddings), the sanitize→truncate→in-memory→LLM→discard pipeline, the Google API Services User Data Policy + Limited Use clause, third-party AI providers (OpenRouter default + BYOK; no training on customer data under default policies), retention windows, security (encryption at rest, Redis-backed session, logging policy), user rights (access/delete/export/disconnect), cookies, children's privacy, changes-to-policy, and contact at legal@zeromail.app."
    - "Privacy Policy distinguishes in plain user-facing language between extracted email content (NOT persisted in chat) and user-authored draft bodies (persisted in chat conversation lifetime so the user can review before sending)."
    - "Terms of Service explicitly covers: acceptance, beta/pre-launch academic disclaimer, eligibility + Google OAuth requirement, scope of Gmail authorization (read, modify labels, save drafts, outbound send/reply/forward when enabled), AI-authorized outbound actions (Auto-send rules toggle default ON, safety nets, daily caps, idempotency, fallback to Gmail draft when gates fail, user remains responsible), credits & billing (prepaid pay-as-you-go, BYOK, no subscription), refunds for unused credits, acceptable use, IP, warranties disclaimer, liability limits, termination, changes, governing law (Vietnam, neutral), and contact."
    - "Existing consumers continue to work: LegalFooter (`legal.terms.body`, `legal.googleApiPolicy.body`), Footer.tsx (`footer.privacy`, `footer.terms`), login screen (`auth.login.privacy`, `auth.login.terms`)."
    - "`pnpm --filter web run typecheck` passes (tsc --noEmit clean) and `pnpm --filter web run i18n:check` passes (en/vi leaf-key parity preserved)."
    - "Both pages stay server components (no `'use client'`), use `getTranslations()` from next-intl, render exactly one `<h1>` per page, and do NOT render `<main>`, `<header>`, `<footer>`, or the `zm-proto` wrapper (the public layout owns chrome)."
    - "Zero hardcoded color hex values; only design tokens (`text-foreground`, `text-muted-foreground`, `border-border`, `bg-card`, etc.) and Tailwind utility spacing/typography classes are used. No `prose` utility (project does not ship `@tailwindcss/typography`)."
  artifacts:
    - path: "apps/web/i18n/messages/en.json"
      provides: "English `legal.*` namespace with full Privacy Policy + Terms structured content"
      contains: "legal.privacy.sections, legal.terms.sections, legal.privacy.toc, legal.terms.toc, legal.contact, legal.lastUpdated; preserved: legal.terms.body, legal.googleApiPolicy.body"
    - path: "apps/web/i18n/messages/vi.json"
      provides: "Vietnamese parallel of `legal.*` namespace, same key structure"
      contains: "Identical leaf-key set as en.json (i18n:check parity)"
    - path: "apps/web/app/(public)/privacy/page.tsx"
      provides: "Server-component Privacy Policy page with TOC + sections + tokenized typography"
      min_lines: 60
    - path: "apps/web/app/(public)/terms/page.tsx"
      provides: "Server-component Terms of Service page with TOC + sections + tokenized typography"
      min_lines: 60
  key_links:
    - from: "apps/web/features/auth/components/LegalFooter.tsx"
      to: "legal.terms.body, legal.googleApiPolicy.body"
      via: "t.rich(...)"
      pattern: "legal\\.terms\\.body|legal\\.googleApiPolicy\\.body"
    - from: "apps/web/features/landing/components/Footer.tsx"
      to: "footer.privacy, footer.terms"
      via: "t(...)"
      pattern: "footer\\.privacy|footer\\.terms"
    - from: "apps/web/app/(public)/privacy/page.tsx"
      to: "legal.privacy.title, legal.privacy.toc.*, legal.privacy.sections.*"
      via: "getTranslations() + t(...) + array iteration over section ids"
      pattern: "legal\\.privacy\\."
    - from: "apps/web/app/(public)/terms/page.tsx"
      to: "legal.terms.title, legal.terms.toc.*, legal.terms.sections.*"
      via: "getTranslations() + t(...) + array iteration over section ids"
      pattern: "legal\\.terms\\.(?!body)"
---

<objective>
Replace the placeholder stubs at `/privacy` and `/terms` with launch-ready, CASA-verification-grade content for Google OAuth review. Operator identity is "Zero Mail team" (student / MVP / pre-launch academic project); contact placeholder is `legal@zeromail.app`. Scope is the minimum needed for Google API Services User Data Policy + Limited Use + AI/LLM disclosure — not full GDPR/CCPA/Nghị định 13.

Purpose: Unblock Google OAuth CASA verification. Disclose AI-authorized outbound actions (send_reply / forward_email / send_email rules + chat assistant), the global `Auto-send rules` toggle, safety nets, daily caps, idempotency, and the draft-fallback policy. Make the privacy invariants from PROJECT.md (no email-body storage, no LLM-exchange storage on email content, draft-body carve-out, encryption at rest, push-based Gmail, OpenRouter+BYOK no-training) legible to a non-technical reviewer.

Output: Two server-rendered legal pages with structured TOC + anchored sections, full vi/en parity, surviving all existing i18n consumers, passing tsc + i18n:check.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@CLAUDE.md
@apps/web/AGENTS.md
@apps/web/app/(public)/layout.tsx
@apps/web/app/(public)/privacy/page.tsx
@apps/web/app/(public)/terms/page.tsx
@apps/web/app/(public)/docs/[slug]/page.tsx
@apps/web/features/auth/components/LegalFooter.tsx
@apps/web/features/landing/components/Footer.tsx
@apps/web/i18n/messages/en.json
@apps/web/i18n/messages/vi.json

<interfaces>
Existing i18n consumers that MUST keep working (do NOT remove or rename these keys):

```
legal.terms.body             // LegalFooter.tsx — rich-text login inline "By clicking continue..." with <terms> and <privacy> placeholders
legal.googleApiPolicy.body   // LegalFooter.tsx — rich-text "Google API data is handled under <link>...</link>"
footer.privacy               // Footer.tsx — "Privacy" / "Bảo mật" link label
footer.terms                 // Footer.tsx — "Terms" / "Điều khoản" link label
auth.login.privacy           // login screen — "Privacy Policy" link label (already used)
auth.login.terms             // login screen — "Terms" link label (already used)
```

Keys to DELETE (current placeholders, both en.json + vi.json):
```
legal.privacy.placeholderTitle
legal.privacy.placeholderBody
legal.terms.placeholderTitle
legal.terms.placeholderBody
```

New i18n surface to ADD (under `legal` namespace, identical structure in en.json + vi.json):
```
legal.lastUpdated                              // "Last updated: 26 May 2026" / "Cập nhật lần cuối: 26/05/2026"
legal.contact.email                            // "legal@zeromail.app" (verbatim, both locales)
legal.contact.body                             // "Reach the Zero Mail team at <email>legal@zeromail.app</email>." (rich)
legal.tocHeading                               // "On this page" / "Trên trang này"

legal.privacy.title                            // "Privacy Policy" / "Chính sách bảo mật"
legal.privacy.intro                            // 1–2 sentence framing paragraph
legal.privacy.toc.<id>                         // 11 entries — one per section
legal.privacy.sections.<id>.heading            // 11 section headings
legal.privacy.sections.<id>.body               // section body; for multi-paragraph sections use `\n\n` as paragraph delimiter rendered via `whitespace-pre-line`

legal.terms.title                              // "Terms of Service" / "Điều khoản dịch vụ"
legal.terms.intro                              // 1–2 sentence framing paragraph
legal.terms.toc.<id>                           // 15 entries
legal.terms.sections.<id>.heading              // 15 section headings
legal.terms.sections.<id>.body                 // section body; same `\n\n` paragraph convention
```

Section id lists (stable — page components iterate these in order to render TOC + body):

Privacy (11 sections):
1. `about`                  — About this policy + who we are (Zero Mail team, student/MVP, pre-launch)
2. `dataCollected`          — Google account profile, OAuth tokens, rule config, chat assistant config, billing/credit ledger
3. `notStored`              — Raw email bodies, LLM prompts/completions on email content, embeddings of user mail
4. `processing`             — Sanitize → truncate → in-memory → LLM → discard; draft-body carve-out (user-authored draft data persists for conversation lifetime so user can review before sending)
5. `googleApi`              — Google API Services User Data Policy + Limited Use affirmation block (CASA wording)
6. `aiProviders`            — Default OpenRouter behind Spring AI, BYOK option; default policies = no training on customer data
7. `retention`              — OAuth until disconnect; config until deleted; email bodies 0 days; audit logs bounded; billing ledger legal minimum
8. `security`               — Industry-standard encryption at rest for OAuth tokens, Redis-backed session, logging policy (no email/token/prompt/completion content in logs)
9. `userRights`             — Access, delete, export, disconnect Gmail; how to exercise via Settings → Privacy + contact email
10. `cookies`               — HttpOnly server-issued session cookie, SameSite=Lax, Secure, Redis-backed Spring Session (short)
11. `childrenAndChanges`    — Children's privacy + changes to this policy + contact

Terms (15 sections):
1. `acceptance`             — Accepting these terms
2. `description`            — Service description + beta / pre-launch academic project disclaimer
3. `eligibility`            — Eligibility + Google account required + OAuth grant
4. `gmailAuthorization`     — Scope: read messages, modify labels, save drafts, outbound send/reply/forward when enabled (mirror CLAUDE.md action list verbatim conceptually)
5. `autoSendRules`          — Auto-send rules toggle (default ON), safety nets, low-trust sender guards, rate/daily caps, idempotency, append-only audit, fallback to Gmail draft if any gate fails or toggle is OFF, user responsible for outcomes
6. `creditsAndBilling`      — Prepaid pay-as-you-go credits, no subscription, BYOK option, credits do not expire under beta
7. `refunds`                — Refunds for unused credits (short, no over-promising)
8. `acceptableUse`          — No spam/scam/mass-send, no violating Gmail ToS, no abuse of automation
9. `intellectualProperty`   — Zero Mail owns the service; user owns their mail + rule configurations
10. `warrantiesDisclaimer`  — "AS IS" / no warranties
11. `liability`             — Limitation of liability
12. `termination`           — Termination (by user any time; by Zero Mail for cause)
13. `changes`               — Changes to terms
14. `governingLaw`          — Under the laws of Vietnam; disputes via good-faith negotiation, otherwise applicable Vietnamese law (neutral — no specific court / statute / registration)
15. `contact`               — Contact at legal@zeromail.app
```

Page component pseudo-shape (both pages share this structure — implement twice, no shared helper module since this is a one-off quick task):

```tsx
import { getTranslations } from 'next-intl/server';

const PRIVACY_SECTION_IDS = [
  'about','dataCollected','notStored','processing','googleApi','aiProviders',
  'retention','security','userRights','cookies','childrenAndChanges',
] as const;

export default async function PrivacyPage() {
  const t = await getTranslations();
  return (
    <section className="mx-auto max-w-3xl px-4 py-8 sm:py-12">
      <header className="mb-8 border-b border-border pb-6">
        <h1 className="text-foreground mb-3 text-3xl font-semibold tracking-tight">
          {t('legal.privacy.title')}
        </h1>
        <p className="text-muted-foreground text-sm">{t('legal.lastUpdated')}</p>
        <p className="text-foreground mt-4 leading-relaxed">{t('legal.privacy.intro')}</p>
      </header>

      <nav aria-label={t('legal.tocHeading')} className="mb-10 rounded-md border border-border bg-card p-5">
        <h2 className="text-foreground mb-3 text-sm font-semibold uppercase tracking-wide">
          {t('legal.tocHeading')}
        </h2>
        <ol className="text-muted-foreground space-y-1.5 text-sm list-decimal pl-5">
          {PRIVACY_SECTION_IDS.map((id) => (
            <li key={id}>
              <a href={`#${id}`} className="hover:text-foreground underline-offset-4 hover:underline">
                {t(`legal.privacy.toc.${id}` as never)}
              </a>
            </li>
          ))}
        </ol>
      </nav>

      <div className="space-y-10">
        {PRIVACY_SECTION_IDS.map((id) => (
          <article key={id} id={id} className="scroll-mt-20">
            <h2 className="text-foreground mb-3 text-xl font-semibold tracking-tight">
              {t(`legal.privacy.sections.${id}.heading` as never)}
            </h2>
            <div className="text-foreground/90 whitespace-pre-line leading-relaxed">
              {t(`legal.privacy.sections.${id}.body` as never)}
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
```

Notes for executor:
- `as never` casts are intentional — next-intl 4 typed-namespace check rejects template-literal keys; pattern is locked precedent (Phase 1.3 Plan 05, see CLAUDE.md / STATE.md decisions).
- Multi-paragraph bodies in i18n use literal `\n\n` separators; `whitespace-pre-line` collapses single newlines but renders paragraph breaks. This avoids per-paragraph keys exploding the i18n surface.
- Rich-text constructs (links, emphasis) inside section bodies are NOT supported by this pattern — keep bodies as plain text. The only rich keys in this plan are `legal.terms.body` (preserved, untouched) and `legal.googleApiPolicy.body` (preserved, untouched).
- No `<main>` / `<header role="banner">` / `<footer>` / `zm-proto` — the `(public)/layout.tsx` owns chrome.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Replace placeholder legal i18n with full Privacy + Terms content (en + vi)</name>
  <files>apps/web/i18n/messages/en.json, apps/web/i18n/messages/vi.json</files>
  <action>
Replace the `legal` namespace placeholders in BOTH `apps/web/i18n/messages/en.json` and `apps/web/i18n/messages/vi.json` with launch-ready structured content. This is the largest task — it produces ~1500–2500 English words and a parallel Vietnamese version per page (Privacy + Terms).

Operations (per file, en.json first then vi.json):

1. **Preserve untouched** (do NOT delete or rename — existing consumers depend on these):
   - `legal.googleApiPolicy.body` — keep verbatim.
   - `legal.terms.body` — keep verbatim. This is the inline "By clicking continue, you agree to..." line for LegalFooter, NOT the Terms-of-Service page body.

2. **Delete** these four placeholder keys from both files:
   - `legal.privacy.placeholderTitle`
   - `legal.privacy.placeholderBody`
   - `legal.terms.placeholderTitle`
   - `legal.terms.placeholderBody`

3. **Add** the following keys (identical structure in both locales — i18n:check enforces leaf-key parity):

   Top-level under `legal`:
   - `legal.lastUpdated` → en: `"Last updated: 26 May 2026"` · vi: `"Cập nhật lần cuối: 26/05/2026"`
   - `legal.tocHeading` → en: `"On this page"` · vi: `"Trên trang này"`
   - `legal.contact.email` → both locales: `"legal@zeromail.app"`
   - `legal.contact.body` → en: `"Reach the Zero Mail team at legal@zeromail.app."` · vi: `"Liên hệ Zero Mail team qua legal@zeromail.app."`

   Add an inline JSON comment is not possible (JSON has no comments). Instead, add the TODO marker as a sibling key that the i18n parity check will still allow — use:
   - `legal.contact.TODO_real_email` → both locales: `"TODO: replace legal@zeromail.app with the real support inbox once provisioned."`
   This is the single project-wide pointer to the placeholder address.

   Under `legal.privacy`:
   - `legal.privacy.title` → en: `"Privacy Policy"` · vi: `"Chính sách bảo mật"`
   - `legal.privacy.intro` → 1–2 sentence framing paragraph; in en, explicitly call out: "operated by the Zero Mail team, a pre-launch student / MVP academic project". In vi parallel.
   - `legal.privacy.toc.<id>` for each id in:
     `about, dataCollected, notStored, processing, googleApi, aiProviders, retention, security, userRights, cookies, childrenAndChanges` (11 entries). Short labels (2–6 words). These appear in the TOC.
   - `legal.privacy.sections.<id>.heading` for each of the 11 ids — full section headings (4–10 words).
   - `legal.privacy.sections.<id>.body` for each of the 11 ids — full prose. Multi-paragraph allowed using literal `\n\n` (two `\n` characters in JSON, i.e. `"para 1\n\npara 2"`). Combined English body word count across all 11 sections MUST land between 1500 and 2500 words. Vietnamese parallel similar length.

   Under `legal.terms`:
   - `legal.terms.title` → en: `"Terms of Service"` · vi: `"Điều khoản dịch vụ"`
   - `legal.terms.intro` → 1–2 sentence framing paragraph; explicitly states "beta / pre-launch academic project operated by the Zero Mail team".
   - `legal.terms.toc.<id>` for each id in:
     `acceptance, description, eligibility, gmailAuthorization, autoSendRules, creditsAndBilling, refunds, acceptableUse, intellectualProperty, warrantiesDisclaimer, liability, termination, changes, governingLaw, contact` (15 entries).
   - `legal.terms.sections.<id>.heading` for each of the 15 ids.
   - `legal.terms.sections.<id>.body` for each of the 15 ids — full prose with `\n\n` paragraph delimiters. Combined English body word count across all 15 sections MUST land between 1500 and 2500 words. Vietnamese parallel similar length.

4. **Content invariants** (these specific facts MUST appear in the indicated English sections; Vietnamese must convey the same facts in human-style register matching the existing vi.json prose tone — NOT a literal machine translation):

   Privacy → `dataCollected.body`: Google account profile (name, email, profile picture, Google subject id), encrypted OAuth refresh/access tokens, rule configuration (user-authored `When/Then` rules + source text), chat assistant configuration (user messages + structured tool outputs persisted to the conversation), billing / credit ledger entries (top-ups, holds, settlements).

   Privacy → `notStored.body`: We do NOT keep long-term copies of (a) the raw bodies of emails received from your Gmail, (b) the LLM prompts or completions generated while processing those email bodies, or (c) embeddings (vector representations) of your mail. This is enforced architecturally, not by policy alone.

   Privacy → `processing.body`: MUST distinguish two sources of "draft body" in plain language:
   - Extracted email content (from emails Gmail delivers to you): sanitized → truncated → kept in memory only → sent to the LLM → discarded immediately after the response is processed. Never persisted.
   - User-authored draft bodies (drafts the AI assistant prepares for YOUR send / reply / forward action and shows you on a preview card before you click Send): these ARE persisted for the lifetime of the chat conversation so you can review, edit, or re-open them. They are your draft data — you own them, you review them, you decide whether to send.

   Privacy → `googleApi.body`: Verbatim CASA-style affirmation that Zero Mail's use of information received from Google APIs adheres to the Google API Services User Data Policy, including the Limited Use requirements. Include a brief explanation that Zero Mail does not transfer the data to others except as necessary to provide or improve user-facing features, to comply with applicable law, or as part of a merger, acquisition, or sale of assets with notice to users; does not use the data for serving ads; and does not allow humans to read it except (a) with user explicit consent, (b) for security purposes, (c) to comply with law, or (d) when the data has been aggregated and anonymized.

   Privacy → `aiProviders.body`: Default model routing goes through OpenRouter (behind a Spring AI abstraction). Users may bring their own API key (BYOK) for supported providers. Under the providers' default policies, customer data submitted via the API is not used to train provider models. We do not log full prompt / completion payloads on email-content processing; only request metadata (tenant id, model, token counts, latency, truncation) is retained.

   Privacy → `retention.body`: OAuth tokens persist until the user disconnects Gmail or deletes their account. Rule configurations persist until the user deletes them or their account. Email bodies received from Gmail: retention is zero — held only in transient memory during processing. Audit logs (rule-action audit, outbound-send audit) are bounded and retained for a limited operational window. Billing / credit ledger entries are retained for the legal minimum applicable to financial records.

   Privacy → `security.body`: OAuth refresh tokens are encrypted at rest with industry-standard encryption. User sessions are signed, HttpOnly, SameSite=Lax cookies backed by Redis-backed Spring Session — not stateless JWTs. Application logs never contain email addresses, Google subject ids, token bytes, message bodies, or LLM prompts / completions; only structured event metadata.

   Privacy → `userRights.body`: Users can (a) access their stored configuration via the Settings UI, (b) export their rule configuration, (c) delete their account (which cascades through OAuth, Gmail connections, rules, and chat history), (d) disconnect Gmail at any time (which revokes OAuth and stops processing). Contact `legal@zeromail.app` to exercise any right that is not already self-service.

   Privacy → `cookies.body`: We use one server-issued, signed, HttpOnly, SameSite=Lax, Secure session cookie. Session state is stored server-side in Redis. We do not use third-party analytics cookies. We do not use tracking pixels in marketing pages.

   Privacy → `childrenAndChanges.body`: Zero Mail is not directed at children under 13; we do not knowingly collect data from them. We will update this policy as the product evolves; material changes will be announced in-product and via the user's account email. Continued use after an update constitutes acceptance.

   Terms → `description.body`: MUST state clearly: "Zero Mail is a beta / pre-launch academic project operated by the Zero Mail team. The service is provided for evaluation and feedback purposes. Features, pricing, and availability may change without notice during this period."

   Terms → `gmailAuthorization.body`: Zero Mail requests the minimum Gmail OAuth scopes required to operate. With the user's consent, Zero Mail may: read incoming messages to triage them; modify Gmail labels (apply, remove); archive messages (skip inbox); save Gmail drafts; mark messages as read / unread; star / unstar; flag spam; add messages to the daily digest. With explicit user opt-in (the Auto-send rules toggle), Zero Mail may also send replies, forward messages, and send new emails on the user's behalf. Destructive actions (permanent delete, arbitrary webhooks) are NOT in scope.

   Terms → `autoSendRules.body`: The Auto-send rules toggle in Settings defaults to ON. When ON, AI rules MAY take outbound actions (send_reply, forward_email, send_email) once they pass runtime safety gates: low-trust sender guards, per-tenant rate caps, per-tenant daily caps, idempotency (a single decision cannot send twice), and append-only audit logging. If any gate fails, or if the toggle is OFF, the rule downgrades automatically to saving a Gmail draft instead of sending. The chat assistant additionally requires explicit user confirmation through a preview card before any outbound send. The user remains responsible for all outcomes of outbound actions taken under their account.

   Terms → `creditsAndBilling.body`: Zero Mail uses a prepaid, pay-as-you-go credit model. Users top up credits and credits are consumed per AI action. There is no recurring subscription in beta. Users may bring their own API key (BYOK) for supported providers — actions billed against a BYOK key bypass Zero Mail's credit ledger.

   Terms → `refunds.body`: During beta, unused credits remain available indefinitely. Refunds for unused credits will be considered on a case-by-case basis at the Zero Mail team's discretion; contact `legal@zeromail.app`.

   Terms → `acceptableUse.body`: Users must not (a) use Zero Mail to send spam, scams, phishing, unsolicited bulk mail, or mass marketing, (b) violate Gmail's Program Policies or applicable email-sending laws (including but not limited to CAN-SPAM and equivalent regional regimes), (c) abuse the automation to harass, deceive, or impersonate, or (d) attempt to circumvent the safety gates or audit logs.

   Terms → `intellectualProperty.body`: The Zero Mail service, source code, branding, and documentation are owned by the Zero Mail team. The user owns their email content, their rule configurations, and their chat-assistant inputs.

   Terms → `warrantiesDisclaimer.body`: The service is provided "AS IS" without warranties of any kind, express or implied. The Zero Mail team does not warrant that the service will be uninterrupted, error-free, or that AI triage / draft output will be accurate in every case. The user is responsible for reviewing outbound actions and AI-generated drafts before relying on them.

   Terms → `liability.body`: To the maximum extent permitted by applicable law, the Zero Mail team's aggregate liability for any claim arising from or related to the service is limited to the amount the user paid for credits in the three months preceding the claim, or 0 if the user did not pay anything. The Zero Mail team is not liable for indirect, incidental, special, consequential, or punitive damages.

   Terms → `termination.body`: The user may terminate use at any time by disconnecting Gmail and deleting their account from Settings. The Zero Mail team may suspend or terminate accounts for breach of these terms, for security reasons, or if continued operation would be unlawful. Termination does not extinguish accrued rights and obligations.

   Terms → `changes.body`: The Zero Mail team may update these terms as the product evolves. Material changes will be announced in-product and via the user's account email. Continued use after an update constitutes acceptance.

   Terms → `governingLaw.body`: These terms are governed by and construed under the laws of Vietnam. Disputes will be resolved through good-faith negotiation between the parties in the first instance; if not resolved, by the applicable Vietnamese law. Do NOT name a specific court, a specific statute citation, or a registration number — none exist yet.

   Terms → `contact.body`: For any question about these terms, contact the Zero Mail team at `legal@zeromail.app`.

5. **JSON syntax discipline**:
   - Keys must be alphabetically sorted within each object (i18n:check / Prettier convention — match existing en.json ordering).
   - Use `\n\n` (two characters: backslash-n backslash-n) inside strings for paragraph breaks. Do not use real newlines inside JSON string values.
   - Do not introduce unescaped quotes — escape with `\"`.
   - Vietnamese MUST be human-style register matching the existing vi.json `privacy.*` and `landing.*` tone (you saw examples like "Bạn có thể dùng khóa mô hình riêng trong Cài đặt. Khóa của bạn, nhà cung cấp của bạn, hóa đơn của bạn.") — NOT a stiff machine translation. Use Vietnamese tech vocabulary already in repo where applicable: "khóa OAuth", "mô hình AI", "credit", "Gmail", "nhãn", "lưu trữ", "bản nháp", "preview card".
   - No emojis anywhere.
   - The `legal.contact.email` value is literally `legal@zeromail.app` in both locales — no localization.
   - The `legal.contact.TODO_real_email` value is the placeholder marker per the constraint.

6. After editing both files, do NOT run any other commands in this task. The next task runs typecheck and i18n:check together.
  </action>
  <verify>
    <automated>node -e "const e=JSON.parse(require('fs').readFileSync('apps/web/i18n/messages/en.json','utf8')); const v=JSON.parse(require('fs').readFileSync('apps/web/i18n/messages/vi.json','utf8')); const flat=(o,p='')=>Object.entries(o).flatMap(([k,val])=>typeof val==='object'&&val!==null&&!Array.isArray(val)?flat(val,p+k+'.'):[p+k]); const ek=new Set(flat(e)); const vk=new Set(flat(v)); const reqd=['legal.privacy.title','legal.privacy.intro','legal.privacy.toc.about','legal.privacy.toc.notStored','legal.privacy.toc.googleApi','legal.privacy.sections.about.heading','legal.privacy.sections.about.body','legal.privacy.sections.notStored.body','legal.privacy.sections.processing.body','legal.privacy.sections.googleApi.body','legal.privacy.sections.aiProviders.body','legal.privacy.sections.retention.body','legal.privacy.sections.security.body','legal.privacy.sections.userRights.body','legal.privacy.sections.cookies.body','legal.privacy.sections.childrenAndChanges.body','legal.terms.title','legal.terms.intro','legal.terms.toc.acceptance','legal.terms.toc.governingLaw','legal.terms.sections.gmailAuthorization.body','legal.terms.sections.autoSendRules.body','legal.terms.sections.creditsAndBilling.body','legal.terms.sections.governingLaw.body','legal.terms.sections.contact.body','legal.terms.body','legal.googleApiPolicy.body','legal.contact.email','legal.contact.TODO_real_email','legal.lastUpdated','legal.tocHeading']; const missingEn=reqd.filter(k=>!ek.has(k)); const missingVi=reqd.filter(k=>!vk.has(k)); const onlyEn=[...ek].filter(k=>!vk.has(k)&&k.startsWith('legal.')); const onlyVi=[...vk].filter(k=>!ek.has(k)&&k.startsWith('legal.')); const placeholders=[...ek,...vk].filter(k=>k.includes('placeholder')); const errors=[]; if(missingEn.length)errors.push('missing en: '+missingEn.join(',')); if(missingVi.length)errors.push('missing vi: '+missingVi.join(',')); if(onlyEn.length)errors.push('en-only legal keys: '+onlyEn.join(',')); if(onlyVi.length)errors.push('vi-only legal keys: '+onlyVi.join(',')); if(placeholders.length)errors.push('placeholders still present: '+placeholders.join(',')); if(errors.length){console.error(errors.join('\\n'));process.exit(1)} console.log('i18n legal namespace OK — en keys:',[...ek].filter(k=>k.startsWith('legal.')).length,'vi keys:',[...vk].filter(k=>k.startsWith('legal.')).length)"</automated>
  </verify>
  <done>
- en.json and vi.json both parse as valid JSON.
- `legal.privacy.placeholderTitle/Body` and `legal.terms.placeholderTitle/Body` are DELETED from both files.
- `legal.terms.body` and `legal.googleApiPolicy.body` are preserved byte-identical to before this task.
- All required new keys exist in BOTH locales (no en-only or vi-only `legal.*` leaves).
- Privacy body content total English word count is 1500–2500; Terms body content total English word count is 1500–2500; Vietnamese parallel length similar.
- `legal.contact.email` is `legal@zeromail.app` in both locales.
- `legal.contact.TODO_real_email` exists in both locales as the placeholder marker.
- No emojis appear anywhere in the modified keys.
- Verification node script above prints `i18n legal namespace OK` and exits 0.
  </done>
</task>

<task type="auto">
  <name>Task 2: Rewrite privacy + terms page components with structured TOC + sections + tokenized typography</name>
  <files>apps/web/app/(public)/privacy/page.tsx, apps/web/app/(public)/terms/page.tsx</files>
  <action>
Replace the body of both page files with a server-component implementation that renders:
- One `<section>` wrapper using `mx-auto max-w-3xl px-4 py-8 sm:py-12`.
- A `<header>` containing exactly one `<h1>` (the page title), the `legal.lastUpdated` line, and the page-level intro paragraph.
- A `<nav aria-label={t('legal.tocHeading')}>` with an ordered list of anchor links to each section id.
- A `<div className="space-y-10">` containing one `<article id={sectionId} className="scroll-mt-20">` per section, each with an `<h2>` heading and a body `<div className="whitespace-pre-line ...">` rendering the section body translation (which contains `\n\n` paragraph delimiters).
- A trailing `<footer className="mt-12 border-t border-border pt-6 text-muted-foreground text-sm">` (note: this is a semantic element inside the section, NOT the page footer — the public layout owns the page footer) containing the `legal.contact.body` text. Use a styled `<span>` for the email to avoid a real `mailto:` link (the email is a placeholder per the TODO). Render the email as plain text inside the contact line: `t('legal.contact.body')` already includes "Reach the Zero Mail team at legal@zeromail.app." — render as a single paragraph, no `mailto:`.

CRITICAL constraints (re-read CLAUDE.md rule 11 + project UI rule before editing):
- Server component only. NO `'use client'`.
- Use `getTranslations()` from `next-intl/server` (matches existing privacy/page.tsx + docs/[slug]/page.tsx pattern).
- Exactly ONE `<h1>` per page (the title). All section headings are `<h2>`.
- Do NOT render a `<main>`, page-level `<header role="banner">`, page-level `<footer>` (semantic article footer is fine — see above), or apply the `zm-proto` class. `(public)/layout.tsx` already wraps with these.
- Do NOT install `@tailwindcss/typography` and do NOT use the `prose` utility — it is not configured in this project. Hand-roll typography with tokens.
- Design tokens ONLY: `text-foreground`, `text-foreground/90`, `text-muted-foreground`, `border-border`, `bg-card`, `hover:text-foreground`. Zero hardcoded color hex (`bg-[#...]`, `text-[#...]`) anywhere.
- No emojis.
- Do NOT invoke any global UI/design skill (CLAUDE.md rule 12). Follow the existing repo precedent (docs/[slug]/page.tsx is the closest reference).
- Section iteration: define `PRIVACY_SECTION_IDS` (11 entries, exact order from the interface block above) and `TERMS_SECTION_IDS` (15 entries, exact order from the interface block above) as a `const` tuple inline in each page file. No shared helper module — keep each page self-contained.
- Use `as never` cast on dynamic translation keys (e.g. `t(\`legal.privacy.toc.${id}\` as never)`) — this is the established repo pattern for next-intl typed-namespace bypass on dynamic keys (Phase 1.3 Plan 05 decision; STATE.md confirms). The static literal-key calls (`t('legal.privacy.title')`, `t('legal.lastUpdated')`, `t('legal.tocHeading')`) do not need the cast.
- `<a>` anchor links for in-page TOC navigation (NOT `next/link`) — these are pure same-page hash anchors, not route navigation.
- Add `scroll-mt-20` to each section article so the sticky-ish top of the layout does not occlude anchored sections when navigating from the TOC.

Reference shape (privacy — terms follows the exact same shape with `TERMS_SECTION_IDS` and the `legal.terms.*` keys):

```tsx
import { getTranslations } from 'next-intl/server';

const PRIVACY_SECTION_IDS = [
  'about',
  'dataCollected',
  'notStored',
  'processing',
  'googleApi',
  'aiProviders',
  'retention',
  'security',
  'userRights',
  'cookies',
  'childrenAndChanges',
] as const;

export default async function PrivacyPage() {
  const t = await getTranslations();

  return (
    <section className="mx-auto max-w-3xl px-4 py-8 sm:py-12">
      <header className="border-border mb-8 border-b pb-6">
        <h1 className="text-foreground mb-3 text-3xl font-semibold tracking-tight">
          {t('legal.privacy.title')}
        </h1>
        <p className="text-muted-foreground text-sm">{t('legal.lastUpdated')}</p>
        <p className="text-foreground mt-4 leading-relaxed">{t('legal.privacy.intro')}</p>
      </header>

      <nav
        aria-label={t('legal.tocHeading')}
        className="border-border bg-card mb-10 rounded-md border p-5"
      >
        <h2 className="text-foreground mb-3 text-sm font-semibold tracking-wide uppercase">
          {t('legal.tocHeading')}
        </h2>
        <ol className="text-muted-foreground list-decimal space-y-1.5 pl-5 text-sm">
          {PRIVACY_SECTION_IDS.map((id) => (
            <li key={id}>
              <a
                href={`#${id}`}
                className="hover:text-foreground underline-offset-4 hover:underline"
              >
                {t(`legal.privacy.toc.${id}` as never)}
              </a>
            </li>
          ))}
        </ol>
      </nav>

      <div className="space-y-10">
        {PRIVACY_SECTION_IDS.map((id) => (
          <article key={id} id={id} className="scroll-mt-20">
            <h2 className="text-foreground mb-3 text-xl font-semibold tracking-tight">
              {t(`legal.privacy.sections.${id}.heading` as never)}
            </h2>
            <div className="text-foreground/90 leading-relaxed whitespace-pre-line">
              {t(`legal.privacy.sections.${id}.body` as never)}
            </div>
          </article>
        ))}
      </div>

      <footer className="border-border text-muted-foreground mt-12 border-t pt-6 text-sm">
        <p>{t('legal.contact.body')}</p>
      </footer>
    </section>
  );
}
```

Both files are ~60–80 lines once formatted. Each file fully self-contained; do NOT extract a shared helper component.

After both files are written, run typecheck + i18n:check:

```
pnpm --filter web run typecheck
pnpm --filter web run i18n:check
```

If typecheck fails with next-intl typed-key errors on dynamic keys, confirm the `as never` cast is present on the template-literal `t(...)` calls. If i18n:check fails, return to Task 1 and fix the missing leaf-key parity — do NOT patch around it from the page files.

Do NOT start the dev server. Do NOT run Playwright. Do NOT regenerate the OpenAPI schema.
  </action>
  <verify>
    <automated>cd apps/web && pnpm run typecheck && pnpm run i18n:check</automated>
  </verify>
  <done>
- `apps/web/app/(public)/privacy/page.tsx` is a server component (no `'use client'`) using `getTranslations()`.
- `apps/web/app/(public)/terms/page.tsx` is a server component (no `'use client'`) using `getTranslations()`.
- Each page renders exactly one `<h1>` (the title), a TOC `<nav>` with anchor links matching the section ids, and one `<article id="...">` per section with a single `<h2>` heading and a body div using `whitespace-pre-line` to render paragraph breaks.
- Privacy page iterates 11 sections in the canonical order; Terms page iterates 15 sections in the canonical order.
- No `<main>`, `<header role="banner">`, page-level `<footer>` (semantic article footer is OK), or `zm-proto` class anywhere in either file.
- Zero hardcoded color hex values (`bg-[#...]`, `text-[#...]`, etc.) in either file. Only tokens: `text-foreground`, `text-foreground/90`, `text-muted-foreground`, `border-border`, `bg-card`, `hover:text-foreground`.
- No emojis anywhere.
- `pnpm --filter web run typecheck` passes (tsc --noEmit exits 0).
- `pnpm --filter web run i18n:check` passes (en/vi leaf-key parity intact across the entire bundle).
- Existing consumers untouched and still resolve: LegalFooter renders `legal.terms.body` + `legal.googleApiPolicy.body` (unchanged); Footer.tsx renders `footer.privacy` + `footer.terms` (untouched).
  </done>
</task>

</tasks>

<verification>
After both tasks complete:

1. **i18n parity + content scope** — Task 1 verify script ensures the `legal.*` namespace has matching en/vi leaf keys and all required section ids are present.

2. **Type + i18n project-wide check** — Task 2 verify runs `pnpm --filter web run typecheck` and `pnpm --filter web run i18n:check`. The latter is the canonical guard for the existing consumers (`auth.login.privacy`, `auth.login.terms`, `footer.privacy`, `footer.terms`, `legal.terms.body`, `legal.googleApiPolicy.body`) because i18n:check validates the full key surface still resolves.

3. **No-hex grep on the two page files** (manual spot-check command for the executor):
   `grep -nE 'bg-\[#|text-\[#|border-\[#' apps/web/app/\(public\)/privacy/page.tsx apps/web/app/\(public\)/terms/page.tsx` — must return zero matches.

4. **Existing consumers spot-check** — after Task 1 lands, `apps/web/features/auth/components/LegalFooter.tsx` and `apps/web/features/landing/components/Footer.tsx` are unchanged; their translation keys still exist in en.json and vi.json byte-equivalent to before this work for `legal.terms.body` and `legal.googleApiPolicy.body`.

Do NOT start the dev server. Do NOT run Playwright. Do NOT regenerate the OpenAPI schema. Do NOT modify any backend code.
</verification>

<success_criteria>
- `/privacy` and `/terms` render full, structured, launch-ready content (1500–2500 words each in English; parallel Vietnamese) covering every section listed in `must_haves.truths`.
- Privacy Policy explicitly contains: Zero Mail team operator identity, no-storage invariants for email bodies + LLM exchanges + embeddings, the draft-body carve-out, the Google API Services User Data Policy + Limited Use affirmation block, OpenRouter + BYOK + no-training disclosure, retention windows, encryption-at-rest mention without naming the algorithm, push-not-poll Gmail, contact `legal@zeromail.app`.
- Terms of Service explicitly contains: beta / pre-launch academic disclaimer, Gmail authorization scope (mirroring CLAUDE.md), AI-authorized outbound actions section covering Auto-send rules toggle (default ON), safety nets, daily caps, idempotency, draft fallback, and user responsibility; prepaid credits + BYOK; refunds policy (short); acceptable use; IP; warranties disclaimer; liability cap; termination; changes; governing law (Vietnam, neutral); contact.
- All existing i18n consumers (`auth.login.privacy`, `auth.login.terms`, `footer.privacy`, `footer.terms`, `legal.terms.body`, `legal.googleApiPolicy.body`) continue to resolve.
- `pnpm --filter web run typecheck` exits 0.
- `pnpm --filter web run i18n:check` exits 0.
- Zero hardcoded color hex anywhere in the two page files.
- Two atomic commits (one per task) on the branch.
- Single TODO marker at `legal.contact.TODO_real_email` flags the placeholder email for future replacement.
</success_criteria>

<output>
This is a `/gsd-quick` plan — no phase SUMMARY required. After Task 2 verifies clean, the implementer commits and reports back with the two commit SHAs.
</output>
