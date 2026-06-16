package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.zeromail.core.mailbox.MailboxRef;
import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.persistence.TriageAuditRepository;
import com.zeromail.core.triage.persistence.TriageAuditWriter;
import com.zeromail.core.triage.usecases.TriageActionResultJsonValidator;
import com.zeromail.core.triage.usecases.TriageAuditSaga;
import com.zeromail.core.triage.usecases.TriageAuditSaga.GmailWriteResult;
import com.zeromail.core.triage.usecases.TriageAuditSaga.TriageAuditCommand;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import com.zeromail.core.triage.usecases.TriageUndoService;
import com.zeromail.core.triage.usecases.UndoAuditCommand;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class NoActiveTransactionDuringGmailWriteTest extends PostgresContainerTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000105");
    private static final UUID MAILBOX_ID = UUID.fromString("00000000-0000-0000-0000-000000000106");
    private static final MailboxRef MAILBOX_REF = new MailboxRef(TENANT_ID, MAILBOX_ID);
    private static final UUID RULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000205");
    private static final String GMAIL_MESSAGE_ID = "gmail-message-transaction-boundary";
    private static final String GMAIL_THREAD_ID = "gmail-thread-transaction-boundary";

    @Autowired TriageAuditSaga triageAuditSaga;

    @Autowired TriageUndoService triageUndoService;

    @Autowired TriageAuditWriter triageAuditWriter;

    @Autowired TriageAuditRepository triageAuditRepository;

    @Autowired TriageActionResultJsonValidator actionResultJsonValidator;

    @Autowired JdbcTemplate jdbcTemplate;

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
                            return "Label_123";
                        })
                .when(triageGmailWriter)
                .applyLabel(eq(MAILBOX_REF), eq(GMAIL_MESSAGE_ID), eq("Finance"));
        doAnswer(
                        invocation -> {
                            transactionActiveAtWriter.add(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            return null;
                        })
                .when(triageGmailWriter)
                .archiveSkipInbox(eq(MAILBOX_REF), eq(GMAIL_MESSAGE_ID));
        doAnswer(
                        invocation -> {
                            transactionActiveAtWriter.add(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            return "draft-transaction-boundary";
                        })
                .when(triageGmailWriter)
                .saveDraft(
                        eq(MAILBOX_REF),
                        any(ReplyHeaders.class),
                        eq("Draft a safe reply for user review"),
                        eq(GMAIL_THREAD_ID));

        GmailWriteResult labelResult = executeInsideOuterTransaction(labelCommand());
        GmailWriteResult archiveResult = executeInsideOuterTransaction(archiveCommand());
        executeInsideOuterTransaction(saveDraftCommand());

        assertThat(transactionActiveAtWriter).containsExactly(false, false, false);
        assertThat(labelResult.gmailChangeToken())
                .contains("\"addedLabelId\":\"Label_123\"")
                .doesNotContain("\"labelId\"");
        assertThat(archiveResult.gmailChangeToken())
                .contains("\"removedLabelIds\":[\"INBOX\"]")
                .doesNotContain("\"removedLabelId\"");
    }

    @Test
    void undo_suspends_outer_transaction_before_inverse_gmail_write() throws Exception {
        seedTenant();
        UUID auditId = seedAppliedLabelAudit();
        ArrayList<Boolean> transactionActiveAtWriter = new ArrayList<>();
        doAnswer(
                        invocation -> {
                            transactionActiveAtWriter.add(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            return "Label_123";
                        })
                .when(triageGmailWriter)
                .removeLabel(eq(MAILBOX_REF), eq(GMAIL_MESSAGE_ID), eq("Label_123"));

        ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .run(
                        () ->
                                transactionTemplate.executeWithoutResult(
                                        transactionStatus -> {
                                            assertThat(
                                                            TransactionSynchronizationManager
                                                                    .isActualTransactionActive())
                                                    .isTrue();
                                            triageUndoService.undo(
                                                    new UndoAuditCommand(auditId, TENANT_ID));
                                        }));

        assertThat(transactionActiveAtWriter).containsExactly(false);
    }

    private GmailWriteResult executeInsideOuterTransaction(TriageAuditCommand command) {
        return transactionTemplate.execute(
                transactionStatus -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isTrue();
                    try {
                        return triageAuditSaga.gmailWritePhase(command);
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
                MAILBOX_ID,
                MAILBOX_ID,
                GMAIL_MESSAGE_ID,
                GMAIL_THREAD_ID,
                "Transaction boundary",
                "founder@example.com",
                RULE_ID,
                "Transaction boundary rule",
                actionType,
                actionResult,
                replyHeadersFor(actionResult),
                "evidenceIds=transaction-boundary");
    }

    private static ReplyHeaders replyHeadersFor(TriageActionResult actionResult) {
        if (!(actionResult instanceof TriageActionResult.SaveDraft)) {
            return null;
        }
        return ReplyHeaders.of(
                "<transaction-boundary@example.com>",
                null,
                "Transaction boundary",
                "founder@example.com",
                GMAIL_THREAD_ID);
    }

    private UUID seedAppliedLabelAudit() {
        return ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .call(
                        () -> {
                            UUID auditId =
                                    triageAuditWriter
                                            .insertPending(
                                                    TENANT_ID,
                                                    MAILBOX_ID,
                                                    MAILBOX_ID,
                                                    GMAIL_MESSAGE_ID,
                                                    GMAIL_THREAD_ID,
                                                    "Transaction boundary",
                                                    "founder@example.com",
                                                    RULE_ID,
                                                    "Transaction boundary rule",
                                                    RuleActionType.LABEL,
                                                    new TriageActionResult.Label(
                                                            "Finance", "Finance"),
                                                    "evidenceIds=transaction-boundary")
                                            .orElseThrow();
                            triageAuditRepository.markApplied(
                                    auditId,
                                    TENANT_ID,
                                    GMAIL_MESSAGE_ID,
                                    "{\"addedLabelId\":\"Label_123\"}",
                                    actionResultJsonValidator.toJson(
                                            new TriageActionResult.Label("Label_123", "Finance")));
                            return auditId;
                        });
    }

    private void seedTenant() {
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?) on conflict (id) do nothing",
                TENANT_ID,
                "transaction-boundary");
        jdbcTemplate.update(
                "insert into gmail_connections(id, tenant_id, google_email, status, is_primary) values (?, ?, ?, 'CONNECTED', true) on conflict (id) do nothing",
                MAILBOX_ID,
                TENANT_ID,
                "transaction-boundary@example.com");
    }
}
