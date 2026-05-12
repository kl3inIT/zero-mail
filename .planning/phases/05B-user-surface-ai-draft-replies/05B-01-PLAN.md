---
phase: 05B-user-surface-ai-draft-replies
plan: 01
type: execute
wave: 2
depends_on: ["05B-00"]
files_modified:
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java
  - backend/core/src/main/java/com/zeromail/core/triage/domain/ReplyHeaders.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/ThreadingHeaderValidator.java
  - backend/core/src/main/java/com/zeromail/core/triage/exception/MissingMessageIdException.java
  - backend/core/src/main/java/com/zeromail/core/triage/exception/ThreadingHeaderInvalidException.java
autonomous: true
requirements: [DRFT-01]
must_haves:
  truths:
    - "Every reply draft Zero Mail creates (triage-initiated) carries In-Reply-To = inbound Message-ID and References = prior chain + that id"
    - "The draft MIME is built with jakarta.mail.internet.MimeMessage, not hand-concatenated strings"
    - "A reply to a message with no RFC822 Message-ID fails closed — no mis-threaded draft is saved"
    - "Subject gets exactly one `Re:` prefix (case-insensitive), never `Re: Re:`"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java"
      provides: "widened saveDraft(...) building a threaded reply MIME via MimeMessage"
      contains: "MimeMessage"
    - path: "backend/core/src/main/java/com/zeromail/core/triage/domain/ReplyHeaders.java"
      provides: "value object: inbound Message-ID, prior References, inbound Subject, reply-to address"
    - path: "backend/core/src/main/java/com/zeromail/core/triage/usecases/ThreadingHeaderValidator.java"
      provides: "deterministic pre-create validator that aborts on missing/malformed headers or threadId mismatch"
  key_links:
    - from: "backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java"
      to: "TriageGmailWriter.saveDraft"
      via: "passes ReplyHeaders sourced from the inbound message it already holds"
      pattern: "ReplyHeaders"
    - from: "backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java"
      to: "ThreadingHeaderValidator"
      via: "validate(mime, threadId) before drafts.create"
      pattern: "ThreadingHeaderValidator"
---

<objective>
Retrofit correct RFC-2822 threading into the existing triage `save_draft` path: replace the hand-concatenated MIME in `TriageGmailWriter.draftMessage` with a `jakarta.mail.internet.MimeMessage` build that sets `In-Reply-To`, `References`, a single `Re:`-prefixed `Subject`, and `To`; widen `saveDraft(...)` to accept a `ReplyHeaders` value object supplied by `TriageOrchestratorService` (which already holds the inbound Gmail message); add a deterministic `ThreadingHeaderValidator` that fails closed before `drafts.create`. This is the shared MIME path that the on-demand draft service (Plan 03) also uses.

Purpose: Closes DRFT-01. Existing triage drafts don't thread reliably today (D-01..D-05).
Output: Widened `TriageGmailWriter`, `ReplyHeaders` record, `ThreadingHeaderValidator`, two new exceptions, `TriageOrchestratorService` wiring.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@CLAUDE.md
@CONVENTIONS.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-PATTERNS.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-RESEARCH.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md
@backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java
@backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: ReplyHeaders value object + ReplyMimeBuilder/threading-header validator</name>
  <files>backend/core/src/main/java/com/zeromail/core/triage/domain/ReplyHeaders.java, backend/core/src/main/java/com/zeromail/core/triage/usecases/ThreadingHeaderValidator.java, backend/core/src/main/java/com/zeromail/core/triage/exception/MissingMessageIdException.java, backend/core/src/main/java/com/zeromail/core/triage/exception/ThreadingHeaderInvalidException.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java (current `draftMessage(...)` and `executeGmailWrite(...)` — lines around the hand-rolled MIME and the error-log pattern)
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java (the METADATA `messages.get` request shape — `setFormat("metadata").setMetadataHeaders(...)`)
    - backend/core/src/main/java/com/zeromail/core/triage/exception/*.java (existing exception shape — no message payload that could carry content; mirror the no-arg / cause-only style used by `SafetyViolationException`)
    - backend/core/src/test/java/com/zeromail/core/draft/ReplyMimeBuildTest.java and ThreadingHeaderValidatorTest.java (the RED tests — make them pass)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-PATTERNS.md §"Pattern 1: Build the reply MIME with jakarta.mail.MimeMessage" and §"Anti-Patterns to Avoid"
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md §6 "Deterministic threading-header validator"
  </read_first>
  <behavior>
    - `ReplyHeaders.of(inboundMessageId, priorReferences, inboundSubject, replyToAddress, gmailThreadId)` validated record (compact ctor with `Objects.requireNonNull` on `gmailThreadId`; `inboundMessageId` may be null/blank → caller decides; `priorReferences` nullable)
    - A static MIME builder (in `TriageGmailWriter` or a small `ReplyMimeBuilder`): `new MimeMessage(Session.getInstance(new Properties()))` → `setSubject(prefixReIfAbsent(subject), "UTF-8")` → `setRecipients(TO, InternetAddress.parse(replyToAddress, false))` → if `inboundMessageId` non-blank: `setHeader("In-Reply-To", inboundMessageId)` + `setHeader("References", buildReferences(priorReferences, inboundMessageId))` → `setText(body, "UTF-8")` → `writeTo(ByteArrayOutputStream)` → `Base64.getUrlEncoder().withoutPadding().encodeToString(...)` → `new Message().setThreadId(gmailThreadId).setRaw(raw)`
    - `prefixReIfAbsent`: returns `"Re:"` if subject null; `subject.trim()` if it already starts (case-insensitive) with `"Re:"` via `regionMatches(true,0,"Re:",0,3)`; else `"Re: " + trimmed`
    - `buildReferences`: `inboundMessageId` alone if no prior; else `priorReferences.trim() + " " + inboundMessageId`
    - `ThreadingHeaderValidator.validate(MimeMessage mime, String expectedThreadId)`: throws `ThreadingHeaderInvalidException` if `In-Reply-To` missing/blank, `References` missing/blank, `Subject` not `Re:`-prefixed, `To` empty, or (for the on-demand path that also passes the constructed `Message`) `threadId` != `expectedThreadId`
    - Missing inbound `Message-ID` at build time → throw `MissingMessageIdException` (fail closed) — never produce a draft with no `In-Reply-To`
  </behavior>
  <action>
    Create `ReplyHeaders` as a validated record in `core.triage.domain`. Create `MissingMessageIdException` and `ThreadingHeaderInvalidException` in `core.triage.exception` following the existing payload-free exception style (no email content in the message). Create `ThreadingHeaderValidator` (`@Component` or plain class) with `validate(...)` as above; it parses headers back via `jakarta.mail` and asserts each is present/well-formed. Implement the MIME builder + `prefixReIfAbsent` + `buildReferences` helpers (decide: static methods on `TriageGmailWriter`, or a package-private `ReplyMimeBuilder` in `core.triage.usecases` — the PATTERNS map shows both as acceptable; pick one and keep it consistent). Confine all `jakarta.mail.*` imports to this package — `DraftPathArchUnitTest` enforces it.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:core:test --tests "*ReplyMimeBuild*" --tests "*ThreadingHeaderValidator*" 2>&1 | tail -10</automated>
  </verify>
  <acceptance_criteria>
    - `ReplyMimeBuildTest` passes: parsed-back MIME has `In-Reply-To` == inbound `Message-ID`, `References` == prior chain + that id, exactly one `Re:` prefix, correct `To`, `threadId` set, raw is base64url without padding
    - Regression cases pass: no prior `References` → `References` == inbound id only; subject already `Re:`-prefixed → not doubled; Vietnamese subject → `setSubject(s,"UTF-8")` encoded-word, no mojibake; missing `Message-ID` → `MissingMessageIdException` thrown, no MIME produced
    - `ThreadingHeaderValidatorTest` passes: malformed/missing-header MIME → `ThreadingHeaderInvalidException`; `threadId` mismatch → exception
    - `MissingMessageIdException` / `ThreadingHeaderInvalidException` carry no email content (no subject, no address, no body) in their messages
    - `mcp__jetbrains__get_file_problems` on the new Java files reports no problems
  </acceptance_criteria>
  <done>ReplyHeaders + threading validator + MIME builder + exceptions land; the two RED MIME tests are GREEN.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Widen TriageGmailWriter.saveDraft + wire TriageOrchestratorService</name>
  <files>backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java, backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java (current `saveDraft(UUID, TriageActionResult.SaveDraft, String)` + `deleteDraft` + `executeGmailWrite` + the `event=triage_gmail_write_failed` log lines)
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java (where the `save_draft` branch currently calls `triageGmailWriter.saveDraft(...)`; what inbound-message metadata it holds — `Message-ID`, `References`, `Subject`, `From`/`Reply-To`)
    - backend/core/src/main/java/com/zeromail/core/triage/domain/TriageActionResult.java (`SaveDraft(instruction, draftId, threadId)` shape)
    - backend/core/src/test/java/.../TriageGmailWriterTest.java, NoGmailSendAllowedTest.java, TriageGmailWriteBoundaryTest.java (extend these for the widened signature + headers)
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java (METADATA fetch shape — fallback when the orchestrator doesn't already hold the headers; add `Message-ID`, `References` to the metadataHeaders list)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md D-03, D-05
  </read_first>
  <behavior>
    - `TriageGmailWriter.saveDraft(UUID tenantId, ReplyHeaders replyHeaders, String body, String gmailThreadId)` (new arity) builds the threaded MIME, runs `ThreadingHeaderValidator.validate(...)` against it, then `gmail.users().drafts().create(...)` inside the existing `executeGmailWrite` wrapper; returns the new `draftId`. The legacy single-purpose triage call site (`TriageActionResult.SaveDraft`) is migrated to this new arity — no second hand-rolled path remains.
    - On `MissingMessageIdException` / `ThreadingHeaderInvalidException`: log `event=draft_threading_invalid tenantId={} gmailThreadId={}` and abort the create (do NOT save a mis-threaded draft); the exception propagates so the caller (orchestrator audit saga / on-demand service) records the failure.
    - `deleteDraft(...)` unchanged (already 404-idempotent).
    - `TriageOrchestratorService`: when it decides `save_draft`, it constructs `ReplyHeaders` from the inbound message it already holds (reusing triage-time metadata per D-03; only call `messages.get(format=METADATA)` if not already in hand), and passes them + the draft body to `saveDraft(...)`. (The draft *body* still comes from wherever Phase 4 sourced it for now; Plan 03 replaces that with `GenerateThreadDraftService` — this plan keeps the existing body source to stay focused on threading.)
  </behavior>
  <action>
    Add the new `saveDraft(...)` arity to `TriageGmailWriter` building+validating+creating the threaded MIME; migrate the existing triage `save_draft` call path to it; keep `executeGmailWrite` and the privacy-log format. Add a small fallback `messages.get(format=METADATA, metadataHeaders=[Message-ID, References, Subject, From, Reply-To])` helper (or reuse `GmailPreviewReadService`'s) for when the orchestrator doesn't hold the headers. Wire `TriageOrchestratorService` to build `ReplyHeaders` and call the new arity. Extend `TriageGmailWriterTest` / `NoGmailSendAllowedTest` / `TriageGmailWriteBoundaryTest` for the new signature and the `event=draft_threading_invalid` abort path. Do NOT add `drafts.send` or `drafts.update` anywhere.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:core:test --tests "*TriageGmailWriter*" --tests "*NoGmailSend*" --tests "*TriageGmailWriteBoundary*" --tests "*TriageOrchestrator*" 2>&1 | tail -12</automated>
  </verify>
  <acceptance_criteria>
    - `TriageGmailWriter` exposes the new `saveDraft(UUID, ReplyHeaders, String, String)` arity; the old hand-rolled `draftMessage(String, String)` MIME path is gone (or delegates to the new builder)
    - A triage-initiated `save_draft` produces a Gmail draft whose MIME (parsed back) carries `In-Reply-To`/`References`/`Re:` subject/`To` consistent with the inbound message — asserted by the extended `TriageGmailWriterTest`
    - On a missing inbound `Message-ID`, the writer logs `event=draft_threading_invalid` and does NOT call `drafts.create`
    - `NoGmailSendAllowedTest` / `TriageGmailWriteBoundaryTest` still green; no `drafts.send` / `drafts.update` reference exists
    - `./gradlew :backend:core:test :backend:api:test` green; `mcp__jetbrains__get_file_problems` on `TriageGmailWriter.java` + `TriageOrchestratorService.java` reports no problems
  </acceptance_criteria>
  <done>Triage `save_draft` now threads correctly; the widened `saveDraft(...)` is the single MIME path Plan 03 will reuse.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| inbound Gmail message → reply MIME | inbound `Message-ID`/`References`/`Subject`/`From` headers are attacker-influenceable and flow into the outgoing draft's headers and recipient |
| `core.triage` → Gmail write API | the only sanctioned Gmail-write site; allow-list is `{label, archive, save_draft}` |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05B-01-01 | Tampering | reply-target / header-chain construction | mitigate | Reply only to the single inbound message triage acted on (D-02) — never walk the thread; `In-Reply-To` = that message's `Message-ID`, `References` = prior chain + that id, deterministic; `ThreadingHeaderValidator` aborts the create on any malformed result |
| T-05B-01-02 | Spoofing | forged / missing `Message-ID` on the inbound message | mitigate | Fail closed — `MissingMessageIdException` aborts the draft; never produce a draft with no `In-Reply-To` that would silently start a new thread or mis-thread; logged as `event=draft_threading_invalid` |
| T-05B-01-03 | Information Disclosure | wrong-recipient / cross-thread bleed in the draft | mitigate | `To` is taken only from the inbound message's `Reply-To`/`From`; `threadId` validated to match the inbound thread; this plan does not assemble any cross-thread context |
| T-05B-01-04 | Elevation of Privilege | new Gmail-write call site (`drafts.send`/`drafts.update`) | mitigate | None added; `NoGmailSendAllowedTest` / `TriageGmailWriteBoundaryTest` / `DraftPathArchUnitTest` enforce the allow-list structurally |
| T-05B-01-05 | Information Disclosure | email content (subject/address/body) in logs or exception messages | mitigate | Privacy logging format (`event=draft_threading_invalid tenantId={} gmailThreadId={}` only); new exceptions are payload-free; `DraftPrivacyLogScrubTest` (Plan 03) and the extended writer tests assert no content bytes |
| T-05B-01-06 | Tampering | `jakarta.mail` API misuse → encoded-word / CRLF / folding bugs | mitigate | Use `MimeMessage.setSubject(s, "UTF-8")` / `setText(body,"UTF-8")` (library handles encoded-word + folding + CRLF) — never hand-build header strings; Vietnamese-subject regression fixture in `ReplyMimeBuildTest` |
</threat_model>

<verification>
- `./gradlew :backend:core:test --tests "*ReplyMimeBuild*" --tests "*ThreadingHeaderValidator*" --tests "*TriageGmailWriter*" --tests "*NoGmailSend*"` all green
- `grep -rn "drafts().send\|drafts().update\|messages().send" backend/core/src/main` returns nothing
- `mcp__jetbrains__get_file_problems` on `TriageGmailWriter.java`, `TriageOrchestratorService.java`, `ThreadingHeaderValidator.java`, `ReplyHeaders.java` — no problems
</verification>

<success_criteria>
DRFT-01 satisfied for the triage path: reply drafts thread correctly via a `jakarta.mail` MIME with `In-Reply-To`/`References`/`Re:` subject/`To`; missing `Message-ID` fails closed; the widened `saveDraft(...)` is the single MIME path reused by Plan 03.
</success_criteria>

<output>
After completion, create `.planning/phases/05B-user-surface-ai-draft-replies/05B-01-SUMMARY.md`
</output>
