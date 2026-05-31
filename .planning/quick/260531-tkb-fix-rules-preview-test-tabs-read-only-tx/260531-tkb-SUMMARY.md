---
quick_id: 260531-tkb
status: complete
---

# Summary — 260531-tkb

## What changed

### Bug fixes
- **Read-only transaction crash** (`1e0396c3`): `previewAllEnabled` / `previewDraft` /
  `previewCustomMail` were `@Transactional(readOnly = true)`, but the LLM gateway's
  `creditLedger.settle()/release()` run with `Propagation.REQUIRED` and join that
  transaction → INSERT in a read-only tx threw. `reserve()` is `REQUIRES_NEW` so it
  succeeded first, masking the cause. Now read-write, consistent with `previewSavedRule`.
- **LLM parse failure** (`602a2417`): routed models returned `{"results":...}` instead of
  `{"nodeMatches":...}`. The semantic-intent system prompt now pins the exact JSON shape
  and field names (output contract, not a keyword patch).

### Behavior / UX
- Custom-email tester now resolves semantic intents via one LLM call (`492f44b8`) — no
  more permanently "deferred" rules. Body is user-authored test input, sent to the
  sanitizing/non-logging gateway.
- Collapsed the two-step preview into a single always-semantic run; removed the
  "Run LLM" CTA, the "Trì hoãn" stat, and deferred chips; preview errors now branch
  Gmail-unavailable / insufficient-credit (402) / generic server error (no longer
  blames credits for a 5xx) (`894b4f9b`). Dropped the duplicate credit badges (`e0abfbeb`).

### Inbox-Zero-style Gmail tester
- Backend (`376a4a42`): `GET /api/rules/test/messages` lists recent emails (free, no eval)
  and `POST /api/rules/test/message` evaluates one email by id (`fetchTriageInput`) against
  all enabled rules + LLM (1 credit, read-write). New `RuleTestMessageList` /
  `RuleTestMessagesResponse` / `RuleTestMessageRequest`; per-message reuses `RulePreviewResponse`.
- Frontend (`b3357a1f`): `GmailRuleTester` mirrors IZ `ProcessRules` — load 10/20 recent
  emails as a list, "Test tất cả" (sequential, with Stop), per-row "Test"/"Test lại" with
  inline matched/not-matched verdict + action chips. Replaced + deleted `RulePreviewPanel`.
  Regenerated `schema.d.ts` / `openapi.json` for the new endpoints.

## Verification
- Backend: JetBrains problem-check clean on all touched files; `:backend:core` +
  `:backend:api` compile (exit 0).
- Frontend: `typecheck` (exit 0), eslint clean, i18n `vi/en` parity, 14/14 rules
  vitest pass. Schema regenerated; both new paths present.
- Earlier (pre-rework) browser pass confirmed: single run button, no LLM-confirm CTA,
  enabled-count in header, credit badges removed.

## Not verified live (manual step required)
The new `/api/rules/test/*` endpoints are NOT in the currently-running backend (only the
throwaway doc-gen instance had them), and the web dev server went down mid-session.
**To exercise the live flow: rebuild + restart `backend/api`, restart the web dev server,
then open Rules → Kiểm tra quy tắc → Email Gmail thật.** The read-only-tx happy path
(credits present → settle writes) is verified by compilation + reasoning, not a live run
(the account was out of credits).

## Follow-up worth noting
Per-message eval holds a DB transaction open across the LLM network call (pre-existing
pattern from `previewSavedRule`). Fine functionally; a future cleanup could move the LLM
call outside the tx to avoid pinning a connection.
