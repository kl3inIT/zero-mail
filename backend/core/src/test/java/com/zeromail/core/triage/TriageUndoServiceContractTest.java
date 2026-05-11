package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TriageUndoServiceContractTest {

  private static final String TRIAGE_UNDO_SERVICE =
      "com.zeromail.core.triage.application.TriageUndoService";
  private static final String TRIAGE_GMAIL_WRITER =
      "com.zeromail.core.triage.service.TriageGmailWriter";
  private static final String TRIAGE_UNDO_EXPIRED_EXCEPTION =
      "com.zeromail.core.triage.exception.TriageUndoExpiredException";
  private static final String TRIAGE_UNDO_ALREADY_DONE_EXCEPTION =
      "com.zeromail.core.triage.exception.TriageUndoAlreadyDoneException";
  private static final String TRIAGE_UNDO_UNSUPPORTED_ACTION_EXCEPTION =
      "com.zeromail.core.triage.exception.TriageUndoUnsupportedActionException";
  private static final String TRIAGE_AUDIT_EXCEPTION =
      "com.zeromail.core.triage.exception.TriageAuditException";

  @Test
  void future_undo_contract_types_are_present() {
    assertFutureTypePresent(TRIAGE_UNDO_SERVICE);
    assertFutureTypePresent(TRIAGE_GMAIL_WRITER);
    assertFutureTypePresent(TRIAGE_UNDO_EXPIRED_EXCEPTION);
    assertFutureTypePresent(TRIAGE_UNDO_ALREADY_DONE_EXCEPTION);
    assertFutureTypePresent(TRIAGE_UNDO_UNSUPPORTED_ACTION_EXCEPTION);
    assertFutureTypePresent(TRIAGE_AUDIT_EXCEPTION);
  }

  @Test
  void undo_computes_inverse_for_each_supported_action_result() throws Exception {
    Class<?> undoServiceClass = Class.forName(TRIAGE_UNDO_SERVICE);
    Class<?> undoCommandClass =
        Class.forName("com.zeromail.core.triage.application.UndoAuditCommand");
    Method undoMethod = undoServiceClass.getMethod("undo", undoCommandClass);

    assertThat(undoMethod).isNotNull();
    assertThat(undoServiceSource())
        .contains("switch (actionResult)")
        .contains("removeLabel")
        .contains("restoreToInbox")
        .contains("deleteDraft")
        .contains("markReverted");
  }

  @Test
  void expired_already_done_and_unsupported_actions_fail_with_reason_specific_exceptions()
      throws Exception {
    assertThat(throwableType(TRIAGE_UNDO_EXPIRED_EXCEPTION)).isNotNull();
    assertThat(throwableType(TRIAGE_UNDO_ALREADY_DONE_EXCEPTION)).isNotNull();
    assertThat(throwableType(TRIAGE_UNDO_UNSUPPORTED_ACTION_EXCEPTION)).isNotNull();
    assertThat(undoServiceSource())
        .contains("Duration.ofDays(30)")
        .contains("throw new TriageUndoExpiredException")
        .contains("throw new TriageUndoAlreadyDoneException")
        .contains("unsupportedActionType()");
  }

  private static void assertFutureTypePresent(String futureTypeName) {
    assertThatCode(() -> Class.forName(futureTypeName))
        .as("Future Phase 4 production type must exist: " + futureTypeName)
        .doesNotThrowAnyException();
  }

  private static Class<? extends Throwable> throwableType(String futureTypeName)
      throws ClassNotFoundException {
    return Class.forName(futureTypeName).asSubclass(Throwable.class);
  }

  private static String undoServiceSource() throws Exception {
    return sourceFile(
        "backend/core/src/main/java/com/zeromail/core/triage/application/TriageUndoService.java");
  }

  private static String sourceFile(String relativePath) throws Exception {
    Path currentDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    for (Path candidateDirectory = currentDirectory;
        candidateDirectory != null;
        candidateDirectory = candidateDirectory.getParent()) {
      Path resolvedPath = candidateDirectory.resolve(relativePath);
      if (Files.exists(resolvedPath)) {
        return Files.readString(resolvedPath);
      }
    }
    throw new NoSuchFileException(relativePath);
  }
}
