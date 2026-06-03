package com.zeromail.core.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.thread.domain.ThreadReplyBucket;
import com.zeromail.core.thread.domain.ThreadReplyStatus;
import com.zeromail.core.thread.persistence.ThreadReplyStatusEntity;
import com.zeromail.core.thread.persistence.ThreadReplyStatusRepository;
import com.zeromail.core.thread.usecases.ClassifyThreadReplyStatusService;
import com.zeromail.core.thread.usecases.ThreadReplyClassificationInput;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClassifyThreadReplyStatusServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000502");
    private static final String GMAIL_THREAD_ID = "gmail-thread-reply-status";
    private static final Instant CLASSIFIED_AT = Instant.parse("2026-05-12T12:00:00Z");

    private ThreadReplyStatusRepository threadReplyStatusRepository;
    private ClassifyThreadReplyStatusService classifyThreadReplyStatusService;

    @BeforeEach
    void setUp() {
        threadReplyStatusRepository = mock(ThreadReplyStatusRepository.class);
        classifyThreadReplyStatusService =
                new ClassifyThreadReplyStatusService(
                        threadReplyStatusRepository, Clock.fixed(CLASSIFIED_AT, ZoneOffset.UTC));
        when(threadReplyStatusRepository.save(any(ThreadReplyStatusEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void last_self_message_with_sent_label_classifies_as_awaiting_their_reply() {
        when(threadReplyStatusRepository.findByGmailThreadId(GMAIL_THREAD_ID))
                .thenReturn(Optional.empty());

        ThreadReplyStatus result =
                classifyThreadReplyStatusService.classify(
                        input("gmail-message-self", true, true, false, null, false));

        assertThat(result.bucket()).isEqualTo(ThreadReplyBucket.AWAITING_THEIR_REPLY);
        assertThat(result.hasDraft()).isFalse();
        assertThat(result.lastClassifiedAt()).isEqualTo(CLASSIFIED_AT);
    }

    @Test
    void counterparty_last_message_without_draft_classifies_as_to_reply() {
        when(threadReplyStatusRepository.findByGmailThreadId(GMAIL_THREAD_ID))
                .thenReturn(Optional.empty());

        ThreadReplyStatus result =
                classifyThreadReplyStatusService.classify(
                        input("gmail-message-counterparty", false, false, false, null, false));

        assertThat(result.bucket()).isEqualTo(ThreadReplyBucket.TO_REPLY);
        assertThat(result.hasDraft()).isFalse();
    }

    @Test
    void zero_mail_draft_keeps_thread_to_reply_with_has_draft_true() {
        when(threadReplyStatusRepository.findByGmailThreadId(GMAIL_THREAD_ID))
                .thenReturn(Optional.empty());

        ThreadReplyStatus result =
                classifyThreadReplyStatusService.classify(
                        input("draft-123", false, false, true, "draft-123", false));

        assertThat(result.bucket()).isEqualTo(ThreadReplyBucket.TO_REPLY);
        assertThat(result.hasDraft()).isTrue();
        assertThat(result.draftId()).isEqualTo("draft-123");
    }

    @Test
    void auto_reply_or_bulk_last_message_stays_to_reply() {
        when(threadReplyStatusRepository.findByGmailThreadId(GMAIL_THREAD_ID))
                .thenReturn(Optional.empty());

        ThreadReplyStatus result =
                classifyThreadReplyStatusService.classify(
                        input("gmail-message-auto", true, true, false, null, true));

        assertThat(result.bucket()).isEqualTo(ThreadReplyBucket.TO_REPLY);
    }

    @Test
    void unchanged_last_message_is_idempotent_and_new_inbound_reopens_resolved_row() {
        ThreadReplyStatusEntity existingResolvedStatus =
                statusEntity(
                        "gmail-message-old",
                        ThreadReplyBucket.AWAITING_THEIR_REPLY,
                        false,
                        null,
                        true);
        when(threadReplyStatusRepository.findByGmailThreadId(GMAIL_THREAD_ID))
                .thenReturn(Optional.of(existingResolvedStatus));

        ThreadReplyStatus unchangedResult =
                classifyThreadReplyStatusService.classify(
                        input("gmail-message-old", true, true, false, null, false));

        assertThat(unchangedResult.bucket()).isEqualTo(ThreadReplyBucket.AWAITING_THEIR_REPLY);
        verify(threadReplyStatusRepository, never()).save(any(ThreadReplyStatusEntity.class));

        when(threadReplyStatusRepository.findByGmailThreadId(GMAIL_THREAD_ID))
                .thenReturn(Optional.of(existingResolvedStatus));
        ThreadReplyStatus reopenedResult =
                classifyThreadReplyStatusService.classify(
                        input("gmail-message-new", false, false, false, null, false));
        ArgumentCaptor<ThreadReplyStatusEntity> statusCaptor =
                ArgumentCaptor.forClass(ThreadReplyStatusEntity.class);

        verify(threadReplyStatusRepository).save(statusCaptor.capture());
        assertThat(reopenedResult.bucket()).isEqualTo(ThreadReplyBucket.TO_REPLY);
        assertThat(statusCaptor.getValue().isResolved()).isFalse();
        assertThat(statusCaptor.getValue().getLastClassifiedMessageId())
                .isEqualTo("gmail-message-new");
    }

    @Test
    void inbound_message_not_needing_reply_classifies_as_fyi() {
        when(threadReplyStatusRepository.findByGmailThreadId(GMAIL_THREAD_ID))
                .thenReturn(Optional.empty());

        ThreadReplyStatus result =
                classifyThreadReplyStatusService.classify(
                        new ThreadReplyClassificationInput(
                                TENANT_ID,
                                GMAIL_THREAD_ID,
                                "gmail-message-fyi",
                                false,
                                false,
                                false,
                                null,
                                false,
                                false));

        assertThat(result.bucket()).isEqualTo(ThreadReplyBucket.FYI);
        assertThat(result.hasDraft()).isFalse();
    }

    private static ThreadReplyClassificationInput input(
            String lastMessageId,
            boolean lastMessageFromIsTenant,
            boolean threadHasSentLabel,
            boolean hasZeroMailDraft,
            String zeroMailDraftId,
            boolean lastMessageIsAutoReply) {
        return new ThreadReplyClassificationInput(
                TENANT_ID,
                GMAIL_THREAD_ID,
                lastMessageId,
                lastMessageFromIsTenant,
                threadHasSentLabel,
                hasZeroMailDraft,
                zeroMailDraftId,
                lastMessageIsAutoReply,
                // These cases assert direction/draft buckets; an inbound message needs a reply
                // unless explicitly told otherwise (see the dedicated FYI test).
                true);
    }

    private static ThreadReplyStatusEntity statusEntity(
            String lastMessageId,
            ThreadReplyBucket bucket,
            boolean hasDraft,
            String draftId,
            boolean resolved) {
        return new ThreadReplyStatusEntity(
                UUID.randomUUID(),
                TENANT_ID,
                GMAIL_THREAD_ID,
                bucket,
                lastMessageId,
                CLASSIFIED_AT.minusSeconds(60),
                hasDraft,
                draftId,
                resolved);
    }
}
