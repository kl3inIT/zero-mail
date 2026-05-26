---
created: 2026-05-21T00:00:00Z
title: MEDIUM — Landing page (/) HTTP 500 vì WaitlistDialog nhận function prop thiếu "use server"
priority: medium
area: frontend
source:
  phase: pre-Phase-8 (landing page work, exact phase TBD via git blame)
  detected_in: Phase 8 Wave 8 SUMMARY deferred-items.md (2026-05-20) + verify-work 2026-05-21
  status: pre_existing_landing_bug
files:
  - apps/web/app/(public)/page.tsx
  - apps/web/features/landing/components/WaitlistDialog.tsx
---

## Problem

Landing page `GET /` trả HTTP 500 từ Next.js dev server với stack trace:

```
Error: Functions cannot be passed directly to Client Components unless you explicitly
expose it by marking it with "use server". Or maybe you meant to call this function
rather than return it.
  {title: ..., description: ..., emailPlaceholder: ..., button: ..., closeAria: ...,
   closeCta: ..., submitting: ..., privacyNote: ..., successTitle: ...,
   successBody: function successBody}
  digest: '3491488813'
```

**Root cause:**
- `app/(public)/page.tsx` là server component render `<WaitlistDialog>` client component.
- Trong i18n bundle có 1 message dạng **interpolation function** (`successBody({ email })` chẳng
  hạn — function nhận args trả về string với HTML-like fragment).
- Next.js 16.2.6 với App Router strict mode: function props passed từ Server → Client component
  phải được mark `"use server"` (Server Action) — không thể serialize raw function.
- Lỗi happens trong `JSON.stringify` của props payload khi RSC payload được gửi xuống browser.

**Tác động:**
- Landing page **không render được** trong dev mode → UX broken cho mọi user mở trang chủ.
- Marketing flow / waitlist signup / "Đăng ký dùng thử" CTA không reach được.
- Phase 8 cleanup routes (`/cleanup/*`) **không bị ảnh hưởng** (verified: HTTP 200) — bug
  cô lập ở landing.
- Production build có thể có behaviour khác (Next 16 prod build có thể bypass dev warning, hoặc
  cũng có thể fail tương tự — chưa verify).

## Solution

**Hai approach:**

### Approach A — Đổi function prop thành string template (simplest)

Trong `messages.ts` của landing feature:
```ts
// thay vì:
successBody: ({ email }) => `Email của ${email} đã được ghi nhận`,
// dùng:
successBody: "Email {email} đã được ghi nhận",
```

Trong `WaitlistDialog.tsx`:
```tsx
// dùng next-intl interpolation thay vì gọi function trực tiếp:
const t = useTranslations('landing.waitlist');
const body = t('successBody', { email: submittedEmail });
```

Đây là pattern next-intl chuẩn (placeholder interpolation), không dùng function props. Vì
`useTranslations` resolve interpolation ở runtime trong client component, không pass function
qua boundary.

### Approach B — Mark function as Server Action

Thêm `"use server"` ở đầu function declaration, nhưng pattern này không phù hợp cho format
helper (Server Actions là cho mutation submit). Approach A đúng convention next-intl hơn.

**Acceptance:**
- `curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/` → 200.
- Console log Next.js dev server không có "Functions cannot be passed directly to Client
  Components" trong 5 phút quan sát.
- Manual smoke: mở `/` trên browser, dialog "Đăng ký waitlist" mở được, submit email thành công,
  thấy success message render đúng với email user vừa nhập.

## Estimated effort

Small — single file change (`messages.ts` + `WaitlistDialog.tsx` import → swap function với
template string + next-intl interpolation). 1 commit. ~30 phút.

**Trigger phase:** Phase 8.1 (hot-fix nhanh trước hoặc cùng release với Phase 8).
**Phải fix trước khi user-facing.**

---

## Resolution — 2026-05-26

`WaitlistDialog.tsx` now uses inline Vietnamese string literals + a `successHeading(status)` switch helper for success state copy — no more function props passed Server→Client. Bundled into the landing polish work:

- `1740419e feat(landing): i18n remaining WaitlistDialog strings`
- `cc82cc5a fix: address CodeRabbit PR feedback`
- `3f989de8 finish email approve to use beta version`

The seed's Approach A recommendation (next-intl `t('successBody', { email })` interpolation) was the conceptual direction; final implementation uses direct switch + literals rather than next-intl placeholders for the success state copy, because the success heading is a closed enum (`ADDED` / `ALREADY_REGISTERED` / `ALREADY_USER`).
