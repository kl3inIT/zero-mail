package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.service.TriageGmailWriter;
import com.zeromail.core.triage.usecases.TriageAuditSaga;
import com.zeromail.core.triage.usecases.TriageAuditSaga.TriageAuditCommand;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class NoActiveTransactionDuringGmailWriteTest extends PostgresContainerTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000105");
    private static final UUID RULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000205");
    private static final String GMAIL_MESSAGE_ID = "gmail-message-transaction-boundary";
    private static final String GMAIL_THREAD_ID = "gmail-thread-transaction-boundary";

    @Autowired TriageAuditSaga triageAuditSaga;

    @Autowired TransactionTemplate transactionTemplate;

    @MockitoBean TriageGmailWriter triageGmailWriter;

    @Test
    void gmail_write_phase_suspends_outer_transaction_for_every_gmail_write_type()
            throws Exception {
        ArrayList<Boolean> transactionActiveAtWriter = new ArrayList<>();
        doAnswer(
                        invocation -> {
                            transactionActiveAtWriter.add(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            return null;
                        })
                .when(triageGmailWriter)
                .applyLabel(eq(TENANT_ID), eq(GMAIL_MESSAGE_ID), eq("Label_123"));
        doAnswer(
                        invocation -> {
                            transactionActiveAtWriter.add(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            return null;
                        })
                .when(triageGmailWriter)
                .archiveSkipInbox(eq(TENANT_ID), eq(GMAIL_MESSAGE_ID));
        doAnswer(
                        invocation -> {
                            transactionActiveAtWriter.add(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            return "draft-transaction-boundary";
                        })
                .when(triageGmailWriter)
                .saveDraft(
                        eq(TENANT_ID),
                        any(TriageActionResult.SaveDraft.class),
                        eq(GMAIL_THREAD_ID));

        executeInsideOuterTransaction(labelCommand());
        executeInsideOuterTransaction(archiveCommand());
        executeInsideOuterTransaction(saveDraftCommand());

        assertThat(transactionActiveAtWriter).containsExactly(false, false, false);
    }

    private void executeInsideOuterTransaction(TriageAuditCommand command) {
        transactionTemplate.executeWithoutResult(
                transactionStatus -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isTrue();
                    try {
                        triageAuditSaga.gmailWritePhase(command);
                    } catch (IOException ioException) {
                        throw new AssertionError(ioException);
                    }
                });
    }

    private static TriageAuditCommand labelCommand() {
        return command(RuleActionType.LABEL, new TriageActionResult.Label("Label_123", "Finance"));
    }

    private static TriageAuditCommand archiveCommand() {
        return command(RuleActionType.ARCHIVE, new TriageActionResult.Archive());
    }

    private static TriageAuditCommand saveDraftCommand() {
        return command(
                RuleActionType.SAVE_DRAFT,
                new TriageActionResult.SaveDraft(
                        "Draft a safe reply for user review", null, GMAIL_THREAD_ID));
    }

    private static TriageAuditCommand command(
            RuleActionType actionType, TriageActionResult actionResult) {
        return new TriageAuditCommand(
                TENANT_ID,
                GMAIL_MESSAGE_ID,
                GMAIL_THREAD_ID,
                RULE_ID,
                "Transaction boundary rule",
                actionType,
                actionResult,
                "evidenceIds=transaction-boundary");
    }
}
