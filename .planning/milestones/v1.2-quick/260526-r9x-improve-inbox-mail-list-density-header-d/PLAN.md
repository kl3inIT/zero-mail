---
status: complete
created: 2026-05-26
---

# Improve inbox list density and search

## Scope

- Simplify inbox rows so the first line shows only the sender.
- Put the subject/header on the second line with snippet and labels below.
- Format list dates like Gmail/Inbox Zero: today shows time only; older mail shows date only.
- Add loaded-message search by sender/email, subject, and labels.
- Reduce sidebar width and widen the inbox list area.

## Verification

- Typecheck and lint web.
- Run the inbox Playwright spec and add focused assertions for search/date/list layout.
