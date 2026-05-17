---
phase: 02C
slug: llm-gateway
status: approved
shadcn_initialized: true
preset: base-nova (code b2fA)
created: 2026-05-07
reviewed_at: 2026-05-07T03:57:24+07:00
---

# Phase 02C - UI Design Contract

> Visual and interaction contract for Phase 02C frontend work. This phase only adds the BYOK LLM provider configuration surface and the user-facing insufficient-credit prompt pattern required by the gateway.

---

## Scope

Phase 02C owns one user-facing surface in `apps/web`: a BYOK configuration card mounted on the existing `/settings` page through `apps/web/features/llm/`.

In scope:

- Provider selector: `Anthropic` and `OpenAI Compatible`.
- Conditional endpoint field for OpenAI-compatible providers.
- Uncontrolled password input for the raw API key.
- Validate-then-save flow with clear success, invalid-key, pending, and saved states.
- Credit-depleted prompt copy for HTTP 402 gateway failures.
- Vietnamese-first i18n keys with English parity.

Out of scope:

- Admin LLM provider UI.
- Model-routing dashboard.
- Usage analytics, credit-balance rendering, or payment-pending polling.
- Any triage, rules, audit-log, draft, or mailbox UI.
- Third-party component registries.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | shadcn/ui |
| Preset | base-nova, preset code `b2fA`, URL `https://ui.shadcn.com/create?preset=b2fA` |
| Component library | Base UI through local shadcn primitives |
| Icon library | lucide-react |
| Font | Existing `font-sans` token from `app/globals.css`; no new font in this phase |
| Existing primitives | `Card`, `Button`, `Input`, `Label`, `RadioGroup`, `Alert`, `Badge`, `Separator`, `Tooltip` |
| New primitives | none unless executor confirms a missing official shadcn primitive is strictly required |

Design-system evidence:

- `apps/web/components.json` exists.
- `shadcn info` resolves Next.js 16.2.4, Tailwind v4, RSC enabled, TypeScript enabled, `base-nova`, `lucide`.
- Installed primitives already cover this phase: `alert`, `badge`, `button`, `card`, `input`, `label`, `radio-group`, `separator`, `tooltip`.

---

## Layout Contract

The BYOK card must join the existing settings stack in `apps/web/app/(protected)/settings/page.tsx`.

| Area | Contract |
|------|----------|
| Parent layout | Keep existing `mx-auto max-w-2xl space-y-4 p-6`; do not widen the settings page for this card. |
| Placement | Insert after the automated-triage card and before the privacy card. |
| Card structure | One shadcn `Card`; use `CardHeader`, `CardTitle`, `CardDescription`, `CardContent`, optional `CardFooter`. |
| Focal point | The form validation state is the primary visual anchor: provider/key fields first, validation `Alert` directly below, actions last. |
| Form grid | Single-column grid at all viewports; no split two-column form because endpoint and key fields are secret-sensitive and need predictable scan order. |
| Action row | `Validate API key` and `Save API key` in a wrapping row; on mobile each button may take full width. |
| Existing-key state | Show provider, masked endpoint host if present, and last saved timestamp. Never render any part of the API key. |
| Loading state | Disable provider, endpoint, key, validate, and save controls during validation/save; show button-local pending labels. |
| Success state | Use a compact success `Alert` with provider/model summary. For OpenAI-compatible, render at most 5 returned model names; collapse overflow to `+N more`. |
| Error state | Use destructive `Alert` below the affected fields, not a toast-only failure. |

Do not introduce nested cards, marketing sections, decorative gradients, or large hero typography inside `/settings`.

---

## Visual Hierarchy

| Element | Priority | Contract |
|---------|----------|----------|
| Card title | 1 | `AI provider key`; compact settings heading, not display type. |
| Privacy helper | 2 | One-sentence description explaining BYOK bypasses platform credits and stores only an encrypted key. |
| Provider selector | 3 | Radio group with two visible labels; no icon-only provider selector. |
| Validation alert | 4 | Appears only after validation attempt; success/destructive color communicates state. |
| Actions | 5 | Primary action is `Validate API key`; `Save API key` is disabled until validation succeeds. |
| Existing-key metadata | 6 | Subordinate `text-xs` metadata in card footer or muted inline row. |

Accessibility contracts:

- Every input has a visible `Label`.
- Validation result uses `role="status"` or an `aria-live="polite"` wrapper.
- Error alert must identify the problem and recovery path.
- Provider radio group is keyboard operable through the local shadcn `RadioGroup`.
- Focus rings use `ring` / `ring-ring`, not custom one-off colors.

---

## Spacing Scale

Declared values (must be multiples of 4):

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4px | Icon gaps, badge padding, tight inline metadata gaps |
| sm | 8px | Label-to-input gap, compact alert internal spacing |
| md | 16px | Default field stack, card content groups |
| lg | 24px | Card header/content rhythm, larger form group separation |
| xl | 32px | Not used inside the card unless the card gets an empty state block |
| 2xl | 48px | Not used in this phase |
| 3xl | 64px | Not used in this phase |

Exceptions: none.

Implementation notes:

- Preserve existing settings card rhythm: `space-y-4` between cards.
- Inside BYOK form use `space-y-4` for field groups and `gap-3` for action rows.
- Buttons use existing shadcn sizes; do not invent one-off heights.

---

## Typography

Use exactly these four sizes and two weights for the BYOK surface.

| Role | Size | Weight | Line Height |
|------|------|--------|-------------|
| Metadata | 12px | 400 | 1.4 |
| Body / field text | 14px | 400 | 1.5 |
| Section helper | 16px | 400 | 1.5 |
| Card heading | 20px | 600 | 1.25 |

Rules:

- Font weights are limited to 400 and 600.
- No display/hero type in this phase.
- No negative letter spacing in the settings card.
- Long endpoint strings must truncate with `overflow-hidden text-ellipsis`, not resize the layout.

---

## Color

| Role | Value | Usage |
|------|-------|-------|
| Dominant (60%) | `#FAFAF7` | Page background and surrounding settings surface |
| Secondary (30%) | `#FFFFFF` and `#F1EFE7` | Card surface, muted rows, footer metadata background if needed |
| Accent (10%) | `#0E5E5A` | Primary validation CTA, active radio indicator, focus ring, saved-key badge outline |
| Destructive | `#B0413E` | Invalid key, failed save, remove/replace confirmation errors |

Accent reserved for:

- `Validate API key` primary button.
- Active radio indicator.
- Keyboard focus ring through `ring-ring`.
- Saved/validated status badge outline only when the key is confirmed valid.

Semantic status colors:

- Success uses existing `--green` / `--green-soft` tokens for validated/saved state.
- Warning uses existing `--warning` for credit-depleted or provider-unreachable-but-retryable prompts.
- Destructive uses `--destructive` only for invalid key, failed validation, failed save, or irreversible account/Gmail actions already present on settings.

Accent must not be used for all buttons or all links.

---

## Copywriting Contract

Primary UI language is Vietnamese; English copy must be semantically equivalent and kept in lock-step in `apps/web/i18n/messages/{vi,en}.json`.

| Element | Vietnamese copy | English copy |
|---------|-----------------|--------------|
| Card title | Khóa API cho nhà cung cấp AI | AI provider key |
| Card description | Dùng khóa riêng của bạn để gọi OpenAI Compatible hoặc Anthropic. Zero Mail chỉ lưu khóa đã mã hóa và không trừ tín dụng nền tảng cho các lượt gọi BYOK. | Use your own key for OpenAI Compatible or Anthropic calls. Zero Mail stores only the encrypted key and does not spend platform credits for BYOK calls. |
| Provider label | Nhà cung cấp | Provider |
| Provider Anthropic | Anthropic | Anthropic |
| Provider OpenAI-compatible | OpenAI Compatible | OpenAI Compatible |
| Endpoint label | Endpoint OpenAI Compatible | OpenAI Compatible endpoint |
| Endpoint placeholder | https://openrouter.ai/api/v1 | https://openrouter.ai/api/v1 |
| Key label | Khóa API | API key |
| Key placeholder | Dán khóa API | Paste API key |
| Primary CTA | Kiểm tra khóa API | Validate API key |
| Save CTA | Lưu khóa API | Save API key |
| Pending validate | Đang kiểm tra khóa... | Validating key... |
| Pending save | Đang lưu khóa... | Saving key... |
| Empty state heading | Chưa có khóa BYOK | No BYOK key connected |
| Empty state body | Chọn nhà cung cấp, dán khóa API, rồi kiểm tra trước khi lưu. | Choose a provider, paste an API key, then validate it before saving. |
| Success state | Khóa đã được kiểm tra. Bạn có thể lưu cấu hình này. | Key validated. You can save this configuration. |
| Invalid-key error | Không thể kiểm tra khóa này. Kiểm tra nhà cung cấp, endpoint và khóa API, rồi thử lại. | We could not validate this key. Check the provider, endpoint, and API key, then try again. |
| Save error | Không thể lưu khóa đã mã hóa. Tải lại trang rồi thử lại. | We could not save the encrypted key. Refresh the page, then try again. |
| Saved state | Đã lưu khóa BYOK đã mã hóa. Các lượt gọi AI sẽ dùng khóa này cho đến khi bạn thay đổi. | Encrypted BYOK key saved. AI calls will use this key until you change it. |
| Credit depleted heading | Tín dụng nền tảng đã hết | Platform credits are depleted |
| Credit depleted body | Nạp thêm tín dụng hoặc lưu khóa BYOK hợp lệ để tiếp tục gọi AI. | Top up credits or save a valid BYOK key to continue AI calls. |

Destructive confirmation:

| Action | Confirmation approach |
|--------|-----------------------|
| Replace saved BYOK key | Not a destructive delete, but the existing-key row must show `Saving a new validated key will replace the current encrypted key.` before `Save API key` is enabled. |
| Remove BYOK key | Out of scope for Phase 02C. If added later, require a dialog with explicit CTA `Remove API key` and body `Zero Mail will return to platform credits for future AI calls.` |

Avoid generic labels:

- Do not use `Submit`, `OK`, `Save`, or `Cancel` as standalone BYOK action labels.
- Use `Validate API key`, `Save API key`, `Try validation again`, and `Top up credits`.

---

## Interaction Contract

Validation state machine:

1. Initial state: `Save API key` disabled; `Validate API key` enabled only when provider and key are present, plus endpoint for OpenAI-compatible.
2. Validation request: read raw key from the uncontrolled form field into a local variable; do not place it in React state, URL params, query keys, logs, or persistent storage.
3. Validation success: render success `Alert`; enable `Save API key`; retain provider/endpoint/key field values until saved or changed.
4. Field change after validation: clear validation success and disable `Save API key`.
5. Validation failure: render destructive `Alert`; keep field values for correction; keep `Save API key` disabled.
6. Save success: reset the form, clear raw key field, invalidate current BYOK config query if one exists, and render saved metadata.
7. Save failure: keep fields, render destructive `Alert`, and allow retry.

Privacy contracts:

- `apiKey` must be an uncontrolled `<Input type="password" name="apiKey" autoComplete="off">`.
- Do not store the raw key in React state, TanStack Query cache, localStorage, sessionStorage, cookies, or URL search params.
- TanStack Query usage is mutation-only for validate/save; do not create query keys containing provider endpoint or secret values.
- Existing configuration responses may include provider, endpoint host, and saved timestamp only; never return or render decrypted key material.

---

## Component Inventory

| Need | Component contract |
|------|--------------------|
| Provider choice | shadcn `RadioGroup` with visible labels. |
| Endpoint field | shadcn `Input` type `url` or `text`; visible only for OpenAI Compatible. |
| Secret field | shadcn `Input` type `password`; uncontrolled through form ref. |
| Validate result | shadcn `Alert`; success and destructive variants only. |
| Current config | muted metadata row or `Badge`; no nested card. |
| Actions | shadcn `Button`; primary for validation, secondary for save when enabled if visual hierarchy needs it. |
| Help affordance | shadcn `Tooltip` only for short clarifications such as "OpenAI Compatible includes OpenRouter". Visible label remains required. |

Do not create wrapper components unless the executor finds three or more repeated BYOK sub-blocks with identical behavior.

---

## i18n Keys

Add keys under a new `llm.byok.*` namespace and `errors.llm.*`.

Required keys:

- `llm.byok.title`
- `llm.byok.description`
- `llm.byok.provider.label`
- `llm.byok.provider.anthropic`
- `llm.byok.provider.openaiCompatible`
- `llm.byok.endpoint.label`
- `llm.byok.endpoint.placeholder`
- `llm.byok.apiKey.label`
- `llm.byok.apiKey.placeholder`
- `llm.byok.validateCta`
- `llm.byok.saveCta`
- `llm.byok.validating`
- `llm.byok.saving`
- `llm.byok.empty.heading`
- `llm.byok.empty.body`
- `llm.byok.validation.success`
- `llm.byok.validation.invalid`
- `llm.byok.save.success`
- `llm.byok.save.error`
- `llm.byok.existing.replaceNotice`
- `errors.llm.insufficientCredits.title`
- `errors.llm.insufficientCredits.body`
- `errors.llm.safetyViolation`
- `errors.llm.sanitizationFailed`
- `errors.llm.byokValidateFailed`

`pnpm i18n:check` must pass after implementation.

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | `card`, `button`, `input`, `label`, `radio-group`, `alert`, `badge`, `separator`, `tooltip` | not required |
| third-party registries | none | not applicable |

No third-party registry blocks are approved for Phase 02C.

---

## Source Decisions Used

| Source | Decisions used |
|--------|----------------|
| `02C-SPEC.md` | BYOK provider selector, validate-before-save, encrypted-at-rest key, HTTP 402 top-up prompt, no admin UI. |
| `02C-CONTEXT.md` | `features/llm/` folder, uncontrolled password input, raw shadcn primitives, Vietnamese-first copy, no wrapper components. |
| `apps/web/components.json` | shadcn initialized, base-nova style, lucide icons, no third-party registries. |
| `apps/web/app/(protected)/settings/page.tsx` | Existing compact settings layout and card rhythm. |
| `apps/web/app/globals.css` | Teal/paper color tokens, semantic warning/destructive/green tokens, system font token. |

---

## Checker Sign-Off

- [x] Dimension 1 Copywriting: PASS
- [x] Dimension 2 Visuals: PASS
- [x] Dimension 3 Color: PASS
- [x] Dimension 4 Typography: PASS
- [x] Dimension 5 Spacing: PASS
- [x] Dimension 6 Registry Safety: PASS

**Approval:** approved 2026-05-07
