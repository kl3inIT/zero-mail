---
phase: 05B-user-surface-ai-draft-replies
plan: 01
type: execute
wave: 2
depends_on: ["05B-00"]
files_modified:
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageAuditSaga.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageRuleEvaluationInputFactory.java
  - backend/core/src/main/java/com/zeromail/core/triage/domain/ReplyHeaders.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/ThreadingHeaderValidator.java
  - backend/core/src/main/java/com/zeromail/core/triage/exception/MissingMessageIdException.java
  - backend/core/src/main/java/com/zeromail/core/triage/exception/ThreadingHeaderInvalidException.java
  - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java
autonomous: true
requirements: [DRFT-01]
must_haves:
  truths:
    - "Every reply draft Zero Mail creates carries In-Reply-To = inbound Message-ID and References = prior chain + that id"
    - "The draft MIME is built with jakarta.mail.internet.MimeMessage, not hand-concatenated strings"
    - "A reply to a message with no RFC822 Message-ID fails closed — no mis-threaded draft is saved (the saga records GmailWriteResult.failed)"
    - "Subject gets exactly one `Re:` prefix (case-insensitive); a localized reply prefix (AW:/SV:/RV:) is left as-is — documented cosmetic v1 behavior"
    - "The actual current Gmail-write call path for drafts is TriageOrchestratorService → TriageAuditSaga.gmailWritePhase → TriageGmailWriter.saveDraft — the threading retrofit threads ReplyHeaders through TriageAuditCommand into that path; no new direct-call path is introduced"
    - "TriageRuleEvaluationInput / the inbound-message metadata now carries Message-ID, References, Reply-To (not just the headers Phase 4 already collected)"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java"
      provides: "widened saveDraft(UUID, ReplyHeaders, String body, String gmailThreadId) building a threaded reply MIME via MimeMessage; the old hand-rolled draftMessage(String,String) path removed"
      contains: "MimeMessage"
    - path: "backend/core/src/main/java/com/zeromail/core/triage/domain/ReplyHeaders.java"
      provides: "value object: inbound Message-ID, prior References, inbound Subject, reply-to address, gmailThreadId"
    - path: "backend/core/src/main/java/com/zeromail/core/triage/usecases/ThreadingHeaderValidator.java"
      provides: "deterministic pre-create validator that aborts on missing/malformed headers or threadId mismatch"
    - path: "backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageAuditSaga.java"
      provides: "TriageAuditCommand carries ReplyHeaders; gmailWritePhase SaveDraft branch passes them to the widened saveDraft(...)"
      contains: "ReplyHeaders"
  key_links:
    - from: "backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java"
      to: "TriageAuditSaga.TriageAuditCommand"
      via: "builds ReplyHeaders from the inbound message it already holds and puts them on the command"
      pattern: "ReplyHeaders"
    - from: "backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageAuditSaga.java"
      to: "TriageGmailWriter.saveDraft"
      via: "gmailWritePhase SaveDraft branch passes command.replyHeaders() + the draft body"
      pattern: "triageGmailWriter\\.saveDraft"
    - from: "backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java"
      to: "ThreadingHeaderValidator"
      via: "validate(mime, threadId) before drafts.create"
      pattern: "ThreadingHeaderValidator"
---

<objective>
Retrofit correct RFC-2822 threading into the **existing** triage `save_draft` write path — which today is `TriageOrchestratorService → TriageAuditSaga.gmailWritePhase → TriageGmailWriter.saveDraft(UUID, TriageActionResult.SaveDraft, String)` — by: (1) replacing the hand-concatenated MIME in `TriageGmailWriter`'s `draftMessage(...)` with a `jakarta.mail.internet.MimeMessage` build that sets `In-Reply-To`, `References`, a single `Re:`-prefixed `Subject`, and `To`; (2) widening `TriageGmailWriter.saveDraft(...)` to accept a `ReplyHeaders` value object; (3) carrying `ReplyHeaders` on `TriageAuditSaga.TriageAuditCommand` so `gmailWritePhase`'s `SaveDraft` branch passes them through (the saga, not the orchestrator, is the Gmail-write site — Plan 03's on-demand service will call the widened `saveDraft(...)` directly, but the triage path stays routed through the saga); (4) extending the inbound-message metadata (`TriageRuleEvaluationInputFactory` + `GmailPreviewReadService`'s METADATA header list) to capture `Message-ID`, `References`, `Reply-To` — Phase 4 collects neither; (5) `TriageOrchestratorService` builds `ReplyHeaders` from the inbound message it already holds and puts them on the `TriageAuditCommand`; (6) a deterministic `ThreadingHeaderValidator` that fails closed before `drafts.create`.

Purpose: Closes DRFT-01. Existing triage drafts don't thread reliably today (D-01..D-05), and the threading data isn't even flowing through to the Gmail-write site. This is the shared MIME path that the on-demand draft service (Plan 03) also uses.
Output: Widened `TriageGmailWriter`; `ReplyHeaders` record; `ThreadingHeaderValidator`; two new exceptions; `TriageAuditCommand` + `gmailWritePhase` threading; `TriageRuleEvaluationInputFactory` + `GmailPreviewReadService` metadata extension; `TriageOrchestratorService` wiring.
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
@backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageAuditSaga.java
@backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java
@backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageRuleEvaluationInputFactory.java
@backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: ReplyHeaders value object + reply-MIME builder + ThreadingHeaderValidator + exceptions</name>
  <files>backend/core/src/main/java/com/zeromail/core/triage/domain/ReplyHeaders.java, backend/core/src/main/java/com/zeromail/core/triage/usecases/ThreadingHeaderValidator.java, backend/core/src/main/java/com/zeromail/core/triage/exception/MissingMessageIdException.java, backend/core/src/main/java/com/zeromail/core/triage/exception/ThreadingHeaderInvalidException.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java (current `draftMessage(String instruction, String gmailThreadId)` — the hand-rolled `From:`/`To:`/`Subject:` + base64url + `setThreadId`/`setRaw` block around lines 234-245 — and `executeGmailWrite(...)` + the `event=triage_gmail_write_failed` log pattern)
    - backend/core/src/main/java/com/zeromail/core/triage/exception/*.java (existing exception shape — payload-free, cause-only; mirror `SafetyViolationException`)
    - backend/core/src/test/java/com/zeromail/core/draft/ReplyMimeBuildTest.java and ThreadingHeaderValidatorTest.java (the RED scaffolds from Plan 00 — make them real assertions)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-PATTERNS.md §"Pattern 1: Build the reply MIME with jakarta.mail.MimeMessage" and §"Anti-Patterns to Avoid"
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md §6 "Deterministic threading-header validator"
  </read_first>
  <behavior>
    - `ReplyHeaders.of(String inboundMessageId, String priorReferences, String inboundSubject, String replyToAddress, String gmailThreadId)` — validated record. Compact ctor: `Objects.requireNonNull` on `gmailThreadId` and `replyToAddress` (a draft must have a `To`); `inboundMessageId` may be null/blank, but THAT IS THE CALLER'S EXPLICIT FAIL-CLOSED DECISION POINT — `ReplyHeaders` carries a `boolean hasMessageId()` and the MIME builder throws `MissingMessageIdException` rather than silently producing a draft with no `In-Reply-To`; `priorReferences` nullable. Carries no body, no participant list beyond the single reply-to address.
    - A `ReplyMimeBuilder` (package-private class in `core.triage.usecases`, or static methods on `TriageGmailWriter` — pick one, keep consistent): `String buildBase64UrlMime(ReplyHeaders headers, String body)` → if `!headers.hasMessageId()` throw `MissingMessageIdException` → `MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()))` → `mime.setSubject(prefixReIfAbsent(headers.inboundSubject()), "UTF-8")` → `mime.setRecipients(Message.RecipientType.TO, InternetAddress.parse(headers.replyToAddress(), true))` (strict parse) → `mime.setHeader("In-Reply-To", headers.inboundMessageId())` → `mime.setHeader("References", buildReferences(headers.priorReferences(), headers.inboundMessageId()))` → `mime.setText(body, "UTF-8")` → `writeTo(ByteArrayOutputStream)` → `Base64.getUrlEncoder().withoutPadding().encodeToString(...)`. The caller wraps it in `new com.google.api.services.gmail.model.Message().setThreadId(headers.gmailThreadId()).setRaw(raw)`.
    - `prefixReIfAbsent`: `"Re:"` if subject null/blank; `subject.trim()` if it already starts (case-insensitive) with `"Re:"` via `regionMatches(true, 0, "Re:", 0, 3)`; else `"Re: " + subject.trim()`. **Known v1 cosmetic limitation (documented):** localized reply prefixes (`AW:` German, `SV:` Swedish, `RV:` Spanish, `RE:`/`AW:` etc.) are NOT recognized — a subject `AW: Notes` becomes `Re: AW: Notes`. Gmail's UI normalizes this on display; left as a documented TODO, not fixed in v1 (no i18n prefix table). The `ReplyMimeBuildTest` asserts THIS behavior, not stripping.
    - `buildReferences`: `inboundMessageId` alone if `priorReferences` null/blank; else `priorReferences.trim() + " " + inboundMessageId`.
    - `ThreadingHeaderValidator.validate(jakarta.mail.internet.MimeMessage mime, String expectedThreadId)`: re-parse headers off the built MIME — throw `ThreadingHeaderInvalidException` if `In-Reply-To` missing/blank, `References` missing/blank, `Subject` not `Re:`-prefixed, or `getRecipients(TO)` empty; for the on-demand path that also passes the constructed Gmail `Message`, throw if its `threadId` != `expectedThreadId`. Payload-free message.
  </behavior>
  <action>
    Create `ReplyHeaders` (validated record, `core.triage.domain`). Create `MissingMessageIdException` + `ThreadingHeaderInvalidException` (`core.triage.exception`, payload-free). Create `ThreadingHeaderValidator` (`@Component` or plain class). Implement the reply-MIME builder + `prefixReIfAbsent` + `buildReferences`. Confine all `jakarta.mail.*` imports to this package — `DraftPathArchUnitTest` enforces it. Turn `ReplyMimeBuildTest` + `ThreadingHeaderValidatorTest` into real, passing assertions.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:core:test --tests "*ReplyMimeBuild*" --tests "*ThreadingHeaderValidator*" 2>&1 | tail -10</automated>
  </verify>
  <acceptance_criteria>
    - `ReplyMimeBuildTest` passes: parsed-back MIME has `In-Reply-To` == inbound `Message-ID`, `References` == prior chain + that id, exactly one `Re:` prefix, correct `To`, raw is base64url without padding; no prior `References` → `References` == inbound id only; subject already `Re:`-prefixed → not doubled; Vietnamese subject → `setSubject(s,"UTF-8")` encoded-word, no mojibake; subject `AW: …` → becomes `Re: AW: …` (documented cosmetic behavior); missing `Message-ID` → `MissingMessageIdException`, no MIME produced; a malformed reply-to address → `AddressException` surfaces (strict parse), no MIME
    - `ThreadingHeaderValidatorTest` passes: malformed/missing-header MIME → `ThreadingHeaderInvalidException`; `threadId` mismatch → exception
    - `ReplyHeaders` requires `gmailThreadId` and `replyToAddress` non-null; `hasMessageId()` reflects whether the inbound id was supplied; the record carries no body / no participant list
    - `MissingMessageIdException` / `ThreadingHeaderInvalidException` carry no email content in their messages
    - `mcp__jetbrains__get_file_problems` on the new Java files reports no problems
  </acceptance_criteria>
  <done>ReplyHeaders + reply-MIME builder + threading validator + exceptions land; the two RED MIME tests are GREEN.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Capture Message-ID/References/Reply-To on the inbound message + thread ReplyHeaders through TriageAuditCommand + widen TriageGmailWriter.saveDraft</name>
  <files>backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java, backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageRuleEvaluationInputFactory.java, backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java, backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageAuditSaga.java, backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java (the `messages.get(...).setFormat("metadata").setMetadataHeaders(List.of(...))` request — add `Message-ID`, `References`, `In-Reply-To`, `Reply-To` to that list; the `GmailPreviewMessage` validated-record shape — add the new header fields)
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageRuleEvaluationInputFactory.java (how it assembles the per-message input from the fetched Gmail message — extend it to carry `messageId` (the RFC `Message-ID` header, distinct from the Gmail message id), `references`, `replyTo`/`from`)
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java (`saveDraft(UUID, TriageActionResult.SaveDraft, String)` at line 81, `draftMessage(...)` at 234, `deleteDraft` at 140, `executeGmailWrite`)
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageAuditSaga.java (`TriageAuditCommand` record at line 200 — add a `ReplyHeaders replyHeaders` component, nullable for non-`save_draft` actions; `gmailWritePhase`'s `case TriageActionResult.SaveDraft` branch at line 119 — pass `command.replyHeaders()` + the draft body to the widened `saveDraft(...)`; on `MissingMessageIdException`/`ThreadingHeaderInvalidException` return `GmailWriteResult.failed("draft_threading_invalid")`)
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java (where it builds the `TriageAuditCommand` for a `save_draft` decision — add the `ReplyHeaders` built from the inbound message it already holds: `Message-ID`, `References`, `Subject`, `Reply-To`/`From`)
    - backend/core/src/main/java/com/zeromail/core/triage/domain/TriageActionResult.java (`SaveDraft(instruction, draftId, threadId)` — the body for the triage path still comes from `instruction` IN THIS PLAN; Plan 03 replaces it with `GenerateThreadDraftService`)
    - backend/core/src/test/java/.../TriageGmailWriterTest.java, NoGmailSendAllowedTest.java, TriageGmailWriteBoundaryTest.java, and the RED `TriageAuditSagaDraftThreadingTest.java` (Plan 00)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md D-03, D-05
  </read_first>
  <behavior>
    - `GmailPreviewReadService`: add `Message-ID`, `References`, `In-Reply-To`, `Reply-To` to the METADATA `metadataHeaders` list; add the corresponding fields to `GmailPreviewMessage` (validated record, compact ctor unchanged style). NOTE: the RFC `Message-ID` header is distinct from the Gmail-internal `message.getId()` — keep both, never conflate.
    - `TriageRuleEvaluationInputFactory`: carry `rfcMessageId`, `references`, `replyToAddress` (fall back to `From` when `Reply-To` absent) onto the per-message evaluation input so the orchestrator has them when it decides `save_draft`. (These are evaluation-time metadata, not LLM inputs.)
    - `TriageGmailWriter.saveDraft(UUID tenantId, ReplyHeaders replyHeaders, String body, String gmailThreadId)` — NEW arity: `String raw = ReplyMimeBuilder.buildBase64UrlMime(replyHeaders, body)` → parse it back into a `MimeMessage` for `ThreadingHeaderValidator.validate(mime, gmailThreadId)` → `gmail.users().drafts().create(...)` inside `executeGmailWrite(...)` → return `draftId`. On `MissingMessageIdException`/`ThreadingHeaderInvalidException`: log `event=draft_threading_invalid tenantId={} gmailThreadId={}` and let the exception propagate (caller decides). The old `draftMessage(String instruction, String gmailThreadId)` hand-rolled MIME path is REMOVED — the legacy `saveDraft(UUID, TriageActionResult.SaveDraft, String)` arity either delegates to the new one (building `ReplyHeaders` is now done by the saga, so this arity may be dropped entirely if no caller remains) or is deleted.
    - `TriageAuditSaga.TriageAuditCommand` gains a `ReplyHeaders replyHeaders` component — REQUIRED non-null when `preWriteIntent` is a `SaveDraft`, may be null otherwise (validate in the compact ctor: `if (preWriteIntent instanceof TriageActionResult.SaveDraft) Objects.requireNonNull(replyHeaders, ...)`). `gmailWritePhase`'s `case TriageActionResult.SaveDraft saveDraft -> { String draftId = triageGmailWriter.saveDraft(command.tenantId(), command.replyHeaders(), saveDraft.instruction(), command.gmailThreadId()); ... }`; wrap so `MissingMessageIdException`/`ThreadingHeaderInvalidException` → `GmailWriteResult.failed("draft_threading_invalid")` (no content). `reservePhase`/`recordTerminal` use the same command but ignore `replyHeaders`.
    - `TriageOrchestratorService`: when it builds the `TriageAuditCommand` for a `save_draft` decision, construct `ReplyHeaders.of(inbound.rfcMessageId(), inbound.references(), inbound.subject(), inbound.replyToAddress(), gmailThreadId)` from the inbound message's already-held metadata (D-03 — reuse triage-time metadata; only call `messages.get(format=METADATA)` again if not in hand) and pass it on the command. The draft *body* is still `instruction` in this plan; Plan 03 replaces it.
    - `deleteDraft(...)` unchanged (404-idempotent).
  </behavior>
  <action>
    Extend `GmailPreviewReadService`'s METADATA headers + `GmailPreviewMessage` with `Message-ID`/`References`/`In-Reply-To`/`Reply-To`. Extend `TriageRuleEvaluationInputFactory`/the eval input with `rfcMessageId`/`references`/`replyToAddress`. Add the new `saveDraft(UUID, ReplyHeaders, String, String)` arity to `TriageGmailWriter`; remove the hand-rolled `draftMessage(...)` path; drop or delegate the legacy `saveDraft(...,SaveDraft,...)` arity. Add `ReplyHeaders replyHeaders` to `TriageAuditCommand` (required for `SaveDraft`) and rewire `gmailWritePhase`'s `SaveDraft` branch + its failure handling. Wire `TriageOrchestratorService` to build `ReplyHeaders` and put it on the command. Extend `TriageGmailWriterTest`/`NoGmailSendAllowedTest`/`TriageGmailWriteBoundaryTest` and turn `TriageAuditSagaDraftThreadingTest` into real assertions. Do NOT add `drafts.send`/`drafts.update` anywhere.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:core:test --tests "*TriageGmailWriter*" --tests "*TriageAuditSaga*" --tests "*NoGmailSend*" --tests "*TriageGmailWriteBoundary*" --tests "*TriageOrchestrator*" --tests "*GmailPreview*" 2>&1 | tail -14</automated>
  </verify>
  <acceptance_criteria>
    - `TriageGmailWriter` exposes `saveDraft(UUID, ReplyHeaders, String, String)`; the hand-rolled `draftMessage(String,String)` MIME path is gone
    - `TriageAuditCommand` carries `ReplyHeaders` (required when `preWriteIntent` is `SaveDraft`); `gmailWritePhase`'s `SaveDraft` branch passes it to `saveDraft(...)` and the produced draft MIME (parsed back) carries `In-Reply-To`/`References`/`Re:` subject/`To` consistent with the inbound message — asserted by `TriageAuditSagaDraftThreadingTest`
    - On a missing inbound `Message-ID`, the saga records `GmailWriteResult.failed("draft_threading_invalid")` and calls no `drafts.create`; `event=draft_threading_invalid` logged with ids only
    - `GmailPreviewMessage` + the triage eval input carry the RFC `Message-ID`, `References`, `Reply-To` (distinct from the Gmail-internal message id); `GmailPreview*` tests still green
    - `NoGmailSendAllowedTest` / `TriageGmailWriteBoundaryTest` still green; `grep -rn "drafts().send\|drafts().update\|messages().send" backend/core/src/main` returns nothing
    - `./gradlew :backend:core:test :backend:api:test` green; `mcp__jetbrains__get_file_problems` on `TriageGmailWriter.java`, `TriageAuditSaga.java`, `TriageOrchestratorService.java`, `TriageRuleEvaluationInputFactory.java`, `GmailPreviewReadService.java`, `ThreadingHeaderValidator.java`, `ReplyHeaders.java` — no problems
  </acceptance_criteria>
  <done>Triage `save_draft` now threads correctly through the real saga call path with ReplyHeaders on TriageAuditCommand; the widened `saveDraft(...)` is the single MIME path Plan 03 will reuse.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| inbound Gmail message → reply MIME | inbound `Message-ID`/`References`/`Subject`/`Reply-To`/`From` headers are attacker-influenceable and flow into the outgoing draft's headers and recipient |
| `core.triage` → Gmail write API (via `TriageAuditSaga.gmailWritePhase`) | the only sanctioned Gmail-write site; allow-list is `{label, archive, save_draft}` |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05B-01-01 | Tampering | reply-target / header-chain construction | mitigate | Reply only to the single inbound message triage acted on (D-02) — never walk the thread; `In-Reply-To` = that message's `Message-ID`, `References` = prior chain + that id, deterministic; `ThreadingHeaderValidator` aborts the create on any malformed result; the saga records the abort as `GmailWriteResult.failed` |
| T-05B-01-02 | Spoofing | forged / missing `Message-ID` on the inbound message | mitigate | Fail closed — `MissingMessageIdException` aborts the draft (`GmailWriteResult.failed`); never produce a draft with no `In-Reply-To`; the caller's null-`inboundMessageId` is an explicit decision point in `ReplyHeaders`, not a silent fallthrough; logged as `event=draft_threading_invalid` |
| T-05B-01-03 | Information Disclosure | wrong-recipient / cross-thread bleed in the draft | mitigate | `To` is taken only from the inbound message's `Reply-To` (fallback `From`), strict-parsed; `threadId` validated to match the inbound thread; this plan assembles no cross-thread context |
| T-05B-01-04 | Elevation of Privilege | new Gmail-write call site (`drafts.send`/`drafts.update`) | mitigate | None added; the write path stays `gmailWritePhase` → `saveDraft`/`deleteDraft` only; `NoGmailSendAllowedTest` / `TriageGmailWriteBoundaryTest` / `DraftPathArchUnitTest` enforce the allow-list structurally |
| T-05B-01-05 | Information Disclosure | email content (subject/address/body/RFC `Message-ID`) in logs or exception messages | mitigate | Privacy logging format (`event=draft_threading_invalid tenantId={} gmailThreadId={}` only); new exceptions are payload-free; `GmailWriteResult.failed` reason is a fixed code, never the header text; `DraftPrivacyLogScrubTest` (Plan 03) and the extended writer/saga tests assert no content bytes |
| T-05B-01-06 | Tampering | `jakarta.mail` API misuse → encoded-word / CRLF / folding bugs | mitigate | `MimeMessage.setSubject(s,"UTF-8")` / `setText(body,"UTF-8")` (library handles encoded-word + folding + CRLF); strict `InternetAddress.parse(addr, true)`; Vietnamese-subject + localized-prefix regression fixtures in `ReplyMimeBuildTest` |
</threat_model>

<verification>
- `./gradlew :backend:core:test --tests "*ReplyMimeBuild*" --tests "*ThreadingHeaderValidator*" --tests "*TriageGmailWriter*" --tests "*TriageAuditSaga*" --tests "*NoGmailSend*" --tests "*GmailPreview*" --tests "*TriageOrchestrator*"` all green
- `grep -rn "drafts().send\|drafts().update\|messages().send" backend/core/src/main` returns nothing
- `grep -rn "draftMessage(" backend/core/src/main` returns nothing (the hand-rolled MIME path is gone)
- `mcp__jetbrains__get_file_problems` on all touched Java files — no problems
</verification>

<success_criteria>
DRFT-01 satisfied for the triage path: reply drafts thread correctly via a `jakarta.mail` MIME with `In-Reply-To`/`References`/`Re:` subject/`To`, threaded through the real `TriageOrchestratorService → TriageAuditSaga.gmailWritePhase → TriageGmailWriter.saveDraft` path via `ReplyHeaders` on `TriageAuditCommand`; the inbound message now carries `Message-ID`/`References`/`Reply-To`; missing `Message-ID` fails closed; the widened `saveDraft(...)` is the single MIME path reused by Plan 03.
</success_criteria>

<output>
After completion, create `.planning/phases/05B-user-surface-ai-draft-replies/05B-01-SUMMARY.md`
</output>
