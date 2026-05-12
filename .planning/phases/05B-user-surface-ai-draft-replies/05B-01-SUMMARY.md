---
phase: 05B-user-surface-ai-draft-replies
plan: 01
subsystem: backend
tags: [gmail-api, jakarta-mail, triage, threading, drafts]

requires:
  - phase: 05B-00
    provides: jakarta.mail dependency, thread-reply-status changelog, and RED draft/threading contracts
provides:
  - RFC 2822 reply MIME builder using jakarta.mail MimeMessage
  - ReplyHeaders metadata value object for Gmail reply threading
  - ThreadingHeaderValidator fail-closed checks before Gmail drafts.create
  - Triage save_draft path wired through ReplyHeaders on TriageAuditCommand
  - Gmail triage metadata capture for Message-ID, References, In-Reply-To, and Reply-To
affects: [triage, gmail, draft, thread]

tech-stack:
  added: []
  patterns:
    - MimeMessage-based Gmail draft raw construction with base64url no padding
    - ReplyHeaders carried through audit saga instead of direct writer calls
    - Fixed-code draft threading failures with privacy-safe exceptions

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/triage/domain/ReplyHeaders.java
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/ReplyMimeBuilder.java
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/ThreadingHeaderValidator.java
    - backend/core/src/main/java/com/zeromail/core/triage/exception/MissingMessageIdException.java
    - backend/core/src/main/java/com/zeromail/core/triage/exception/ThreadingHeaderInvalidException.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageRuleEvaluationInputFactory.java
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageAuditSaga.java
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java

key-decisions:
  - "Reply MIME construction lives in core.triage.usecases for Plan 01 so the existing triage Gmail-write boundary stays intact."
  - "Localized reply prefixes such as AW: remain a documented v1 cosmetic double-prefix behavior."
  - "Missing inbound Message-ID maps to fixed failure reason draft_threading_invalid and creates no Gmail draft."

patterns-established:
  - "Build then parse the raw MIME before drafts.create so deterministic validators inspect the actual bytes Gmail receives."
  - "Threading exceptions carry no email/header content; logs use tenantId and gmailThreadId only."
  - "TriageRuleEvaluationInput carries non-LLM threading metadata separately from RuleEvaluationInput."

requirements-completed: [DRFT-01]

duration: 46min
completed: 2026-05-12
---

# Phase 05B Plan 01: Threaded Triage Draft Path Summary

**Triage save-draft now builds Gmail reply drafts with MimeMessage threading headers and fails closed on missing Message-ID.**

## Performance

- **Duration:** 46 min
- **Started:** 2026-05-12T20:42:00Z
- **Completed:** 2026-05-12T21:28:55Z
- **Tasks:** 2
- **Files modified:** 15

## Accomplishments

- Added `ReplyHeaders`, `ReplyMimeBuilder`, `ThreadingHeaderValidator`, and payload-free threading exceptions.
- Replaced the hand-built draft MIME path with `jakarta.mail.internet.MimeMessage` and base64url raw construction.
- Extended Gmail metadata capture and triage input to carry RFC `Message-ID`, `References`, `In-Reply-To`, and `Reply-To`.
- Wired `ReplyHeaders` through `TriageOrchestratorService -> TriageAuditSaga -> TriageGmailWriter.saveDraft`.
- Converted the Plan 00 MIME/threading/saga RED contracts into passing assertions.

## Task Commits

1. **Task 1: ReplyHeaders + MIME builder + validator** - `0d9dd69`
2. **Task 2: Thread ReplyHeaders through triage draft path** - `79fdf92`

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/triage/domain/ReplyHeaders.java` - Carries inbound threading metadata and reply target.
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/ReplyMimeBuilder.java` - Builds/parses RFC 2822 MIME as Gmail base64url raw.
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/ThreadingHeaderValidator.java` - Rejects malformed threading headers and thread-id mismatch.
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java` - Uses the threaded MIME builder before `drafts.create`.
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageAuditSaga.java` - Requires/passes `ReplyHeaders` for `SaveDraft` and records `draft_threading_invalid`.
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java` - Builds `ReplyHeaders` from triage-time metadata.
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java` - Fetches and exposes threading metadata headers.

## Decisions Made

Followed the plan-specified triage path. No new Gmail write call site was introduced; the saga remains the triage write boundary.

## Deviations from Plan

None - plan executed as specified.

## Issues Encountered

- The broad `:backend:core:test :backend:api:test` gate is still blocked by intentional future-plan RED suites from Plan 00 (`core.thread`, `core.draft`, audit/threads API contracts, eval/privacy contracts). The focused 05B-01 suite is green.
- JetBrains inspections still report pre-existing warnings in `TriageAuditSaga` and `TriageOrchestratorService`; the edited files compile, and the JetBrains rebuild for touched production files passed.

## Verification

- `./gradlew.bat :backend:core:test --tests "*ReplyMimeBuild*" --tests "*ThreadingHeaderValidator*" --tests "*TriageGmailWriter*" --tests "*TriageAuditSaga*" --tests "*NoGmailSend*" --tests "*TriageGmailWriteBoundary*" --tests "*TriageOrchestrator*" --tests "*GmailPreview*"` - PASS
- `rg -n "drafts\\(\\)\\.send|drafts\\(\\)\\.update|messages\\(\\)\\.send" backend/core/src/main` - no matches
- `rg -n "draftMessage\\(" backend/core/src/main` - no matches
- JetBrains `build_project` on touched production files - PASS

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 05B-02 can build the `core.thread` reply-status domain on top of the now-threaded draft metadata path. Plan 05B-03 can reuse `TriageGmailWriter.saveDraft(UUID, ReplyHeaders, String, String)` for on-demand generated drafts.

---
*Phase: 05B-user-surface-ai-draft-replies*
*Completed: 2026-05-12*
