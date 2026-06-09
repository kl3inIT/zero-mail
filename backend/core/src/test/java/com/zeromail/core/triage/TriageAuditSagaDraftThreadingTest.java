package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.gmail.gateway.MailboxRef;
import com.zeromail.core.outbound.usecases.OutboundSendGateway;
import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.exception.MissingMessageIdException;
import com.zeromail.core.triage.persistence.TriageAuditRepository;
import com.zeromail.core.triage.persistence.TriageAuditWriter;
import com.zeromail.core.triage.usecases.OutboundRuleMessageBuilder;
import com.zeromail.core.triage.usecases.TriageAuditSaga;
import com.zeromail.core.triage.usecases.TriageAuditSaga.GmailWriteResult;
import com.zeromail.core.triage.usecases.TriageAuditSaga.TriageAuditCommand;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TriageAuditSagaDraftThreadingTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID MAILBOX_ID = UUID.fromString("00000000-0000-0000-0000-000000000502");
    private static final MailboxRef MAILBOX_REF = new MailboxRef(TENANT_ID, MAILBOX_ID);
    private static final UUID RULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");
    private static final String GMAIL_MESSAGE_ID = "gmail-message-draft-threading";
    private static final String GMAIL_THREAD_ID = "gmail-thread-draft-threading";
    private static final String DRAFT_BODY = "Draft a safe reply for user review";

    @Test
    void save_draft_branch_passes_reply_headers_to_gmail_writer() throws Exception {
        TriageGmailWriter triageGmailWriter = mock(TriageGmailWriter.class);
        TriageAuditSaga triageAuditSaga = sagaWith(triageGmailWriter);
        ReplyHeaders replyHeaders = completeReplyHeaders();
        when(triageGmailWriter.saveDraft(
                        eq(MAILBOX_REF), same(replyHeaders), eq(DRAFT_BODY), eq(GMAIL_THREAD_ID)))
                .thenReturn("draft-123");

        GmailWriteResult result = triageAuditSaga.gmailWritePhase(saveDraftCommand(replyHeaders));

        assertThat(result.applied()).isTrue();
        assertThat(result.externalRef()).isEqualTo("draft-123");
        assertThat(result.resolvedActionArgsJson()).contains("\"draftId\":\"draft-123\"");
        verify(triageGmailWriter)
                .saveDraft(
                        eq(MAILBOX_REF), same(replyHeaders), eq(DRAFT_BODY), eq(GMAIL_THREAD_ID));
    }

    @Test
    void missing_message_id_records_failed_write_and_creates_no_draft() throws Exception {
        TriageGmailWriter triageGmailWriter = mock(TriageGmailWriter.class);
        TriageAuditSaga triageAuditSaga = sagaWith(triageGmailWriter);
        ReplyHeaders replyHeadersWithoutMessageId =
                ReplyHeaders.of(null, null, "Planning", "founder@example.com", GMAIL_THREAD_ID);
        when(triageGmailWriter.saveDraft(
                        eq(MAILBOX_REF),
                        same(replyHeadersWithoutMessageId),
                        eq(DRAFT_BODY),
                        eq(GMAIL_THREAD_ID)))
                .thenThrow(new MissingMessageIdException());

        GmailWriteResult result =
                triageAuditSaga.gmailWritePhase(saveDraftCommand(replyHeadersWithoutMessageId));

        assertThat(result.applied()).isFalse();
        assertThat(result.failureReason()).isEqualTo("draft_threading_invalid");
        verify(triageGmailWriter)
                .saveDraft(
                        eq(MAILBOX_REF),
                        same(replyHeadersWithoutMessageId),
                        eq(DRAFT_BODY),
                        eq(GMAIL_THREAD_ID));
    }

    @Test
    void save_draft_command_requires_reply_headers() {
        assertThatThrownBy(() -> saveDraftCommand(null)).isInstanceOf(NullPointerException.class);
    }

    private static TriageAuditSaga sagaWith(TriageGmailWriter triageGmailWriter) {
        return new TriageAuditSaga(
                mock(TriageAuditWriter.class),
                mock(TriageAuditRepository.class),
                triageGmailWriter,
                mock(OutboundSendGateway.class),
                mock(OutboundRuleMessageBuilder.class));
    }

    private static TriageAuditCommand saveDraftCommand(ReplyHeaders replyHeaders) {
        return new TriageAuditCommand(
                TENANT_ID,
                MAILBOX_ID,
                MAILBOX_ID,
                GMAIL_MESSAGE_ID,
                GMAIL_THREAD_ID,
                "Planning",
                "founder@example.com",
                RULE_ID,
                "Draft threading rule",
                RuleActionType.SAVE_DRAFT,
                new TriageActionResult.SaveDraft(DRAFT_BODY, null, GMAIL_THREAD_ID),
                replyHeaders,
                "evidenceIds=draft-threading");
    }

    private static ReplyHeaders completeReplyHeaders() {
        return ReplyHeaders.of(
                "<draft-threading@example.com>",
                "<root@example.com>",
                "Planning",
                "founder@example.com",
                GMAIL_THREAD_ID);
    }
}
