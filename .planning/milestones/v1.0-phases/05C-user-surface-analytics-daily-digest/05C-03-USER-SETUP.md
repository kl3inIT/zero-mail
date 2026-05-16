# Phase 05C Plan 03: User Setup Required

**Generated:** 2026-05-13
**Phase:** 05C-user-surface-analytics-daily-digest
**Status:** Incomplete

Complete these items for daily digest email delivery to function. The code, templates, configuration binding, and tests are in place; these items require access to Resend and DNS/provider dashboards.

## Environment Variables

| Status | Variable | Source | Add to |
|--------|----------|--------|--------|
| [ ] | `RESEND_API_KEY` | Resend Dashboard -> API Keys | Worker runtime environment |
| [ ] | `APP_BASE_URL` | Public Zero Mail web URL | Worker runtime environment |

## Account Setup

- [ ] **Create or use a Resend account**
  - URL: `https://resend.com`
  - Skip if: A Resend account already exists for Zero Mail.

## Dashboard Configuration

- [ ] **Verify a sending domain**
  - Location: Resend Dashboard -> Domains
  - Domain: the domain used by `notifications@zero-mail.app`, or update `zero-mail.notification.email.from-address` to a verified sender.
  - Notes: Add the DNS records Resend provides and wait until verification is complete.

- [ ] **Create a send-capable API key**
  - Location: Resend Dashboard -> API Keys
  - Copy the key into `RESEND_API_KEY`.
  - Notes: The key is secret material; do not commit it to the repo.

## Verification

After completing setup:

```powershell
.\gradlew.bat :backend:worker:check
```

Expected results:

- Worker configuration resolves `RESEND_API_KEY`.
- Digest email channel tests still pass.
- A real runtime send uses the verified `from` address and Resend accepts the request.

---

**Once all items complete:** Mark status as "Complete" at top of this file.
