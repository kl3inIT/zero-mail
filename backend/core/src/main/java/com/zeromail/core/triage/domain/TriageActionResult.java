package com.zeromail.core.triage.domain;

/**
 * Persisted result of a triage action after it has been resolved for Gmail execution.
 *
 * <p>This is intentionally separate from rules-domain action intent. Triage audit rows must
 * round-trip Gmail response state for undo, including the resolved label id and the draft id
 * returned after a Gmail draft is created.
 *
 * <p>The idempotency {@code args_hash} must be computed from the pre-write intent. For
 * {@link SaveDraft}, that means {@code draftId == null}; retry code that derives the hash before
 * calling Gmail must still match the existing PENDING row after Gmail later returns a draft id.
 */
public sealed interface TriageActionResult
    permits TriageActionResult.Label, TriageActionResult.Archive, TriageActionResult.SaveDraft {

  record Label(String labelId, String labelName) implements TriageActionResult {
    public Label {
      requireText(labelId, "labelId");
      requireText(labelName, "labelName");
    }
  }

  record Archive() implements TriageActionResult {}

  record SaveDraft(String instruction, String draftId, String threadId) implements TriageActionResult {
    public SaveDraft {
      requireText(instruction, "instruction");
      if (draftId != null && draftId.isBlank()) {
        throw new IllegalArgumentException("draftId must not be blank");
      }
      requireText(threadId, "threadId");
    }
  }

  private static void requireText(String text, String fieldName) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }
}
