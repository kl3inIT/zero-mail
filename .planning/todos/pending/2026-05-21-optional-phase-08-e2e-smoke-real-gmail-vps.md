---
created: 2026-05-21T00:00:00Z
title: OPTIONAL — Phase 8 end-to-end smoke test với Gmail thật trên dev VPS
priority: optional
area: smoke-test / pre-launch-gate
source:
  phase: 08-bulk-unsubscribe-campaign
  detected_in: Phase 8 verify-work — Playwright mock không cover real network
  status: post_ship_validation
files: []
---

## Problem

Phase 8 verify-work (2026-05-21) confirm tất cả unit + integration + Playwright e2e PASS trên
local. Tuy nhiên:

- Playwright mocks `installChromeApiMock(...)` → tất cả `/api/unsubscribe/*` response đều fake.
- Vitest hooks mock `fetchCampaignStatus` qua `vi.mock(...)` → polling logic chưa đụng real HTTP.
- `UnsubscribeHttpClient` real-network call qua WireMock fixture → đúng RFC 8058 status mapping
  nhưng KHÔNG verify behavior với **real provider endpoint thực tế** (Substack, Mailchimp,
  ConvertKit, Beehiiv...).
- `UnsubscribeMailtoSender` qua Gmail send-as-self → unit test mock GmailApiClientFactory, chưa
  test với real Gmail OAuth token + real mailto recipient bounce / accept.
- Pub/Sub push → worker pickup → throttle → archive history mail: unit + E2E test cover, nhưng
  chưa run với real Gmail Pub/Sub topic + real mail volume.

→ Khoảng cách: **production confidence chưa đầy đủ** vì test environment không reach real
upstream.

## Solution

**Manual smoke test trên dev VPS** với:
1. Deploy current `gsd/phase-08-bulk-unsubscribe-campaign` branch lên VPS.
2. Tenant fixture: account Gmail dev có ≥ 3 sender newsletter thực tế (subscribed Substack +
   Mailchimp + 1 sender mailto-only).
3. Subscribe Pub/Sub push endpoint cho tenant → wait 1-2 ngày để `mail_message_observed` có
   data với List-Unsubscribe header.
4. Open `/cleanup/unsubscribe-campaign` → candidate list xuất hiện đủ 3 sender với badge đúng
   (ONE_CLICK / MAILTO).
5. Chọn 2 sender → preview → execute. Quan sát:
   - HTTPS POST tới real unsubscribe URL: status code thực 200/202/204 hay 4xx/5xx?
   - Mailto send: Gmail "Sent" folder xuất hiện mail unsubscribe?
   - `triage_audit` row source='CLEANUP_CAMPAIGN' insert đúng.
   - Mail lịch sử của 2 sender đã archive (removeLabel INBOX) thực sự?
   - Throttle 1/domain/60s hoạt động (chọn 12 sender cùng domain → check time spread).
6. Click "Undo campaign" → tất cả mail trở lại Inbox? Label `Zero Mail/Unsubscribed` removed?
7. Check log `event=cleanup_unsubscribe_http_post tenantId={} senderDomain={} statusCode={}`
   không lộ raw URL / senderEmail.

**Smoke pass criteria (manual checklist):**
- [ ] ≥ 2/3 sender unsubscribe thành công (real provider 200/202/204)
- [ ] Lịch sử archive đúng
- [ ] Undo restore Inbox thành công
- [ ] Throttle visible (12-sender same-domain → 12 attempt cách nhau ≥ 60s)
- [ ] Log không leak PII
- [ ] Real provider không bounce hard sau 24h (kiểm tra Gmail "Sent" folder cho bounce notification)

## Estimated effort

Medium — 1-2 ngày calendar (cần wait Pub/Sub data accumulate trước khi chạy test).
Effort thực tế thao tác: 2-3 giờ.

**Trigger:** Sau khi Phase 8 merge lên main, **trước khi cleanup feature flag mở public**.

**Optional flag:** Có thể bỏ qua nếu acceptance: "Phase 8 ship behind feature flag, ramp internal
testing trước, smoke test = real internal usage data trong 1-2 tuần."
