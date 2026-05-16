---
phase: 1
slug: foundation-safety-infrastructure
status: draft
shadcn_initialized: false
preset: pending-implementation-init
created: 2026-04-24
---

# Phase 1 — UI Design Contract

> Visual and interaction contract for the Phase 1 frontend surface: Google OAuth sign-in, Gmail connection, guided onboarding through template-rule selection, revoked-grant recovery, settings revoke/delete data, typed-client integration impact, and trust/safety UX. This contract is Phase 1 only; it must not design the full Phase 5 application.

---

## Scope Boundary

| Area | Contract |
|------|----------|
| Routes | `/login`, `/onboarding`, `/settings` only |
| Primary journey | Sign in with Google → connect Gmail → choose one starter template → complete onboarding |
| Recovery journey | Externally revoked grant appears as `DISCONNECTED` on the next request with a reconnect prompt |
| Settings journey | View Gmail connection status, reconnect, disconnect Gmail, read privacy posture, delete account/data |
| Data surface | Use only Phase 1 OpenAPI endpoints and generated types; no future dashboard, audit log, billing, drafts, analytics, or full rules CRUD |
| Phase 3 handoff | Template cards store an onboarding selection only; they do not compile, preview, evaluate, reorder, edit, or enable real rules in Phase 1 |

Sources: `.planning/phases/01-foundation-safety-infrastructure/01-CONTEXT.md`, `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md`.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | shadcn/ui required by Phase 1 implementation; `components.json` not present at research time |
| Preset | Initialize during `apps/web` scaffold; use default shadcn New York-style neutral preset unless executor intentionally records a different preset |
| Component library | Radix primitives through shadcn/ui |
| Styling | Tailwind CSS 4 tokens, CSS variables, no bespoke component framework |
| Icon library | `lucide-react` |
| Font | `Inter` or Next.js `Geist Sans`; use one sans-serif family across the Phase 1 UI |
| Typed data | `openapi-typescript` 7 + `openapi-fetch` 0.17 generated client is the source of truth for request/response shapes |

### Required shadcn Components

| Component | Usage |
|-----------|-------|
| `Button` | Google sign-in, connect Gmail, reconnect, disconnect, delete account, confirmation actions |
| `Card` | Login panel, onboarding step container, template cards, settings sections |
| `Alert` | Privacy notice, disconnected grant warning, destructive-action warning, typed-client/API errors |
| `Input` | Delete-account confirmation phrase only, if confirmation uses typed text |
| `Badge` | Connection status: `CONNECTED`, `DISCONNECTED`, `NOT_CONNECTED` |
| `Dialog` | Disconnect Gmail and delete account confirmations |
| `Skeleton` | Initial `/me` and `/tenant/status` loading states |
| `Separator` | Settings section breaks |

No third-party shadcn registries or blocks are approved for Phase 1.

---

## Spacing Scale

Declared values; all spacing uses multiples of 4px.

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4px | Icon gaps, badge inner gaps, helper text spacing |
| sm | 8px | Button icon gap, compact row gap, form-control gap |
| md | 16px | Default card padding on mobile, stack gap between related controls |
| lg | 24px | Desktop card padding, route header-to-body spacing, settings section gap |
| xl | 32px | Page content gutters, onboarding card grid gap |
| 2xl | 48px | Major section separation, login panel vertical offset |
| 3xl | 64px | Desktop page top/bottom padding only |

Exceptions: interactive controls must meet a 44px minimum hit target even when visual height is 40px; OAuth provider logo may be 20px inside a 44px button.

### Layout Measurements

| Surface | Measurement |
|---------|-------------|
| Auth shell | `min-height: 100dvh`, centered content, `padding: 24px` mobile and `48px` desktop |
| Login card | `max-width: 432px`, full width on mobile |
| Onboarding card | `max-width: 760px`, centered |
| Settings content | `max-width: 880px`, left-aligned on desktop |
| Border radius | Use shadcn token default; do not introduce custom radii in Phase 1 |
| Elevation | Prefer borders and subtle background contrast; no heavy shadows |

---

## Typography

Use exactly these four sizes and two weights in Phase 1.

| Role | Size | Weight | Line Height | Usage |
|------|------|--------|-------------|-------|
| Label | 14px | 600 | 1.4 | Field labels, status metadata, helper copy, badge labels |
| Body | 16px | 400 | 1.5 | Default paragraphs, privacy explanation, card descriptions |
| Heading | 20px | 600 | 1.2 | Card titles, route section headings, dialog titles |
| Display | 28px | 600 | 1.15 | Login and onboarding page headline only |

Rules:
- Do not use more than two font weights: regular `400` and semibold `600`; labels, headings, display text, and badge labels all use `600`.
- Long safety copy must stay body-sized; do not use tiny legal text for trust-critical disclosures.
- Error messages use body or label size with semantic color, not larger typography.

---

## Color

Use Tailwind/shadcn CSS variables where available; these hex values define the intended palette if tokens are not yet initialized.

| Role | Value | Usage |
|------|-------|-------|
| Dominant (60%) | `#FAFAF9` | Page background, auth shell, onboarding shell |
| Secondary (30%) | `#FFFFFF` / `#F5F5F4` | Cards, settings panels, template cards, subtle section backgrounds |
| Accent (10%) | `#2563EB` | Primary CTA, selected template card border/check, focus ring, reconnect prompt CTA |
| Success | `#16A34A` | Connected status badge and completed onboarding step only |
| Warning | `#D97706` | `DISCONNECTED` grant warning and reconnect-needed state |
| Destructive | `#DC2626` | Disconnect Gmail, delete account, destructive confirmation alerts only |
| Text primary | `#1C1917` | Headings and core body text |
| Text secondary | `#57534E` | Descriptions, helper text, metadata |
| Border | `#E7E5E4` | Card outlines, separators, form borders |

Accent reserved for: Google sign-in/continue buttons when they initiate the main journey, Connect Gmail, Continue with selected template, selected template card affordance, keyboard focus ring, and Reconnect Gmail in the `DISCONNECTED` state. Accent must not be used for all links or decorative illustrations.

Destructive color reserved for: Disconnect Gmail, Delete account and data, and their confirmation dialog primary buttons.

---

## Interaction Contract

### `/login`

Primary focal point: the login headline and `Sign in with Google` CTA inside the centered card. Secondary safety bullets must support that action without competing visually.

| State | UI Contract |
|-------|-------------|
| Default | Centered card with product name, one-sentence value prop, safety reassurance, and a single primary CTA: `Sign in with Google` |
| CTA behavior | Button navigates to backend OAuth kickoff; do not collect email/password |
| Returning authenticated user | Redirect to `/onboarding` if onboarding is incomplete; redirect to `/settings` if complete |
| Error | Show `Alert` above CTA: `Google sign-in did not finish. Try again or choose the same Google account you started with.` |

Required copy:
- Headline: `Reach inbox zero without giving up control.`
- Body: `Zero Mail connects to Gmail so you can set safe, reviewable automation rules. Phase 1 only stores your account, connection, and onboarding choices.`
- Safety bullets: `No auto-send`, `No long-term email body storage`, `You can revoke access anytime`.

### `/onboarding`

| Step | UI Contract |
|------|-------------|
| Connect Gmail | Explain the second OAuth leg and requested Gmail access before the CTA |
| Select Templates | Show exactly three starter template cards from Phase 1 context; selecting one enables continue |
| Done | Confirm setup is ready and route to `/settings`; do not show dashboard placeholders |

Template cards:
1. `Archive receipts automatically` — `Start with a rule idea for receipts from services like Stripe, stores, and vendors.`
2. `Label newsletters as Newsletters and skip inbox` — `Keep reading material grouped without letting it interrupt your inbox.`
3. `Keep calendar invites and meeting notes on top` — `Prioritize scheduling and meeting context while later phases add real rule execution.`

Stepper rules:
- Use a simple text step indicator: `Step 1 of 2` and `Step 2 of 2`.
- Do not imply automation is active after selection; copy must say `We'll save this as your starter preference for the rules phase.`
- Continue button label after selecting a template: `Save starter template`.

### `/settings`

| Section | UI Contract |
|---------|-------------|
| Account | Show signed-in Google identity if available from `/me`; otherwise show `Signed in` without guessing profile fields |
| Gmail connection | Show status badge, connected account, connect/reconnect/disconnect actions |
| Privacy & safety | Explain no long-term raw body storage, no auto-send in v1, Gmail grant can be revoked, BYOK is planned but not active |
| Danger zone | Separate card with Disconnect Gmail and Delete account/data actions |

Status presentation:
- `CONNECTED`: green badge, copy `Gmail is connected. Zero Mail can use the granted scopes for future triage features.`
- `NOT_CONNECTED`: neutral badge, copy `Gmail is not connected yet. Connect Gmail to finish setup.`
- `DISCONNECTED`: amber warning alert, copy `Google access was revoked or expired. Reconnect Gmail to continue setup.`

---

## Copywriting Contract

Tone: calm, transparent, safety-first, concise. Avoid hype such as `magic`, `autopilot`, `set and forget`, or any copy implying Phase 1 triages mail.

| Element | Copy |
|---------|------|
| Primary CTA | `Sign in with Google` |
| Gmail CTA | `Connect Gmail` |
| Reconnect CTA | `Reconnect Gmail` |
| Template CTA | `Save starter template` |
| Settings disconnect CTA | `Disconnect Gmail` |
| Account deletion CTA | `Delete account and data` |
| Empty state heading | `Connect Gmail to finish setup` |
| Empty state body | `Zero Mail needs a Gmail grant before later phases can watch, label, archive, or draft. You can revoke access anytime from Settings or your Google Account.` |
| Error state | `Something did not sync. Refresh the page, then try the action again. If this continues, disconnect and reconnect Gmail.` |
| Disconnected state | `Google access was revoked or expired. Reconnect Gmail to continue setup.` |
| Destructive confirmation — disconnect | `Disconnect Gmail? Zero Mail will stop using your Gmail grant. Your account remains, and you can reconnect later.` |
| Destructive confirmation — delete | `Delete your account and data? This removes your tenant, Gmail connection, onboarding selections, sessions, and encrypted tokens. This cannot be undone.` |

Delete confirmation approach: require either a confirmation dialog plus typed phrase `delete my data`, or a two-step dialog with a disabled destructive button for 3 seconds. Prefer typed phrase if `Input` is already installed.

---

## Loading, Empty, Error, and Skeleton States

| State | Contract |
|-------|----------|
| Initial app load | Show `Skeleton` card matching final layout while `/me` and `/tenant/status` resolve |
| OAuth redirect pending | Disable CTA after click, show spinner text `Redirecting to Google…` |
| Connect Gmail pending | Disable button, show `Opening Google consent…` |
| Template save pending | Disable template cards and button, show `Saving starter template…` |
| Account delete pending | Disable dialog controls, show `Deleting account and data…` |
| Empty connection | Show neutral `NOT_CONNECTED` badge and the empty state copy above |
| API schema/client mismatch | Show blocking `Alert`: `This page needs a newer API contract. Regenerate the typed client before continuing.` |
| Network failure | Keep user on current route; show retryable `Alert` with one retry button labeled `Try again` |
| `DISCONNECTED` | Never show as generic error; show amber recovery state with `Reconnect Gmail` CTA |

Skeleton typed-client impact:
- All route loaders/hooks must type against generated OpenAPI responses; components must branch on typed discriminants/status strings rather than stringly-typed ad hoc shapes.
- If the generated client cannot compile, Phase 1 UI is not considered shippable even if the static screens render.
- Do not mock future endpoints; only use Phase 1 endpoints from `info.version = 0.1.0`.

---

## Responsive Behavior

| Breakpoint | Contract |
|------------|----------|
| Mobile `<640px` | Single-column layout, cards full width, actions stacked, template cards stacked, page padding `24px` |
| Tablet `640–1023px` | Center onboarding/login cards, template cards may use single column unless width comfortably supports two columns |
| Desktop `≥1024px` | Login centered; onboarding max-width `760px`; settings max-width `880px`; danger zone remains visually separated |

Rules:
- No persistent sidebar in Phase 1.
- No top-level app shell beyond a minimal route header/product mark if needed.
- OAuth and destructive actions must remain reachable without horizontal scrolling at 320px width.

---

## Accessibility Contract

| Area | Requirement |
|------|-------------|
| Keyboard | All controls reachable and operable by keyboard; template cards selectable by button/radio semantics, not click-only divs |
| Focus | Visible focus ring uses accent token; dialog focus is trapped and returns to trigger on close |
| Labels | Buttons use explicit action labels; icon-only buttons are not allowed in Phase 1 |
| Status | Connection status changes announced through visible text and `aria-live="polite"` where state changes after request completion |
| Contrast | Text and control contrast must meet WCAG 2.2 AA; destructive and warning colors require text/icon plus color, not color alone |
| Motion | No required animation; respect `prefers-reduced-motion` for any optional spinner/transition |
| OAuth | External redirect copy must be clear before navigation; do not surprise users with consent screen |
| Errors | Alerts must be programmatically associated with the failed section/action |

---

## Trust & Safety UX

| Safety Principle | UI Contract |
|------------------|-------------|
| Consent clarity | `/onboarding` explains why Gmail access is requested before `Connect Gmail` |
| Reversibility | Settings exposes Disconnect Gmail and Delete account/data without dark patterns |
| No overclaiming | Phase 1 copy must not claim triage, labels, archive, drafts, analytics, or AI rules are active |
| Restricted scopes | Privacy section states Gmail grant is used only for connected-account setup until later features ship |
| No auto-send | Repeat this in login safety bullets and settings privacy section |
| No body retention | State `No long-term storage of raw email bodies, prompts, completions, or embeddings` in settings |
| Revocation | `DISCONNECTED` state explains Google access was revoked or expired and gives one obvious reconnect path |
| CASA support | Settings privacy section must be credible enough to reference in CASA submission content |

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | `button`, `card`, `alert`, `input`, `badge`, `dialog`, `skeleton`, `separator` | Official registry; no third-party gate required |
| third-party | none | No third-party registries approved — 2026-04-24 |

Implementation note: because `components.json` was absent during UI-spec research, the executor must run shadcn initialization inside `apps/web` during scaffold. If any non-official registry is proposed later, it must pass `npx shadcn view` source inspection before inclusion.

---

## Acceptance Checks

| Check | Pass Criteria |
|-------|---------------|
| Auth route | `/login` has one Google sign-in CTA and no password/email form |
| Onboarding flow | User can complete Connect Gmail → Select Template → Done with only Phase 1 endpoints |
| Template scope | The three template cards match this spec and do not expose full rules CRUD or preview |
| Revoked grant | `DISCONNECTED` appears as an amber recovery state with `Reconnect Gmail`, not as a crash or generic error |
| Settings revoke | Disconnect Gmail confirmation is explicit and non-destructive to the account |
| Settings delete | Delete account/data confirmation communicates irreversibility and removes all Phase 1 account data on success |
| Privacy copy | Settings includes no auto-send, no long-term raw body/prompt/completion/embedding storage, and revoke-anytime language |
| Typed client | UI compiles against generated OpenAPI types and fails CI on stale generated types |
| Responsive | `/login`, `/onboarding`, and `/settings` are usable at 320px width with no horizontal scrolling |
| Accessibility | Keyboard-only flow completes sign-in CTA focus, template selection, dialogs, and settings actions |
| Visual scope | No Phase 5 dashboard, analytics, drafts, audit log, billing UI, or full application navigation appears |

---

## Pre-Populated From

| Source | Decisions Used |
|--------|----------------|
| `.planning/PROJECT.md` | Product trust principle, privacy constraints, Gmail-only v1, no auto-send |
| `.planning/REQUIREMENTS.md` | Auth/onboarding/revoke/delete account requirements and web UI direction |
| `.planning/ROADMAP.md` | Phase 1 success criteria and UI hint boundary |
| `01-CONTEXT.md` | Routes, endpoint surface, shadcn/Tailwind/client stack, onboarding template cards, settings privacy page |
| `01-RESEARCH.md` | Next.js/React/Tailwind/TanStack/OpenAPI client architecture and DISCONNECTED validation scenario |
| `01-VALIDATION.md` | Trust/safety and validation emphasis for Phase 1 handoff |
| `ui-brand.md` | GSD clarity patterns adapted as concise, explicit product copy |
| Codebase scan | No existing `components.json`, Tailwind config, UI components, or styles found |

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS
- [ ] Dimension 2 Visuals: PASS
- [ ] Dimension 3 Color: PASS
- [ ] Dimension 4 Typography: PASS
- [ ] Dimension 5 Spacing: PASS
- [ ] Dimension 6 Registry Safety: PASS

**Approval:** pending
