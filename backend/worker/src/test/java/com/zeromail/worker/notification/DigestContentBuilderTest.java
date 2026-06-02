package com.zeromail.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.gmail.usecases.RecentInboxReadService;
import com.zeromail.core.llm.usecases.DigestSummaryLine;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.notification.domain.DigestContentSection;
import com.zeromail.core.triage.projection.DigestSourceItem;
import com.zeromail.core.triage.usecases.AuditLogQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DigestContentBuilderTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000060d1");
    private static final Instant WINDOW_START = Instant.parse("2026-05-10T00:00:00Z");
    private static final Instant SEND_MOMENT = Instant.parse("2026-05-17T00:00:00Z");

    private final AuditLogQueryService auditLogQueryService = mock(AuditLogQueryService.class);
    private final RecentInboxReadService recentInboxReadService =
            mock(RecentInboxReadService.class);
    private final LlmGateway llmGateway = mock(LlmGateway.class);

    private final DigestContentBuilder builder =
            new DigestContentBuilder(auditLogQueryService, recentInboxReadService, llmGateway);

    @Test
    void buildSections_groups_by_rule_and_attaches_summaries_preserving_audit_order() {
        DigestSourceItem newsletterRecent =
                item("msg-news-1", "Newsletters", "news@example.test", "This week in tech");
        DigestSourceItem receipt =
                item("msg-receipt", "Receipts", "billing@example.test", "Invoice 4021");
        DigestSourceItem newsletterOlder =
                item("msg-news-2", "Newsletters", "weekly@example.test", "Product updates");
        when(auditLogQueryService.findDigestSourceItems(eq(TENANT_ID), any(), any(), anyInt()))
                .thenReturn(List.of(newsletterRecent, receipt, newsletterOlder));
        bodyFor("msg-news-1", "Big launch announced this week.");
        bodyFor("msg-receipt", "Your invoice is attached.");
        bodyFor("msg-news-2", "We shipped three features.");
        when(llmGateway.summarizeDigestItems(any()))
                .thenReturn(
                        List.of(
                                new DigestSummaryLine("msg-news-1", "Big launch this week."),
                                new DigestSummaryLine("msg-receipt", "Invoice 4021 attached."),
                                new DigestSummaryLine("msg-news-2", "Three new features.")));

        List<DigestContentSection> sections =
                builder.buildSections(TENANT_ID, WINDOW_START, SEND_MOMENT);

        // Two rule groups, ordered by first appearance in the (most-recent-first) audit list.
        assertThat(sections)
                .extracting(DigestContentSection::ruleName)
                .containsExactly("Newsletters", "Receipts");
        DigestContentSection newsletters = sections.getFirst();
        assertThat(newsletters.entries())
                .extracting(entry -> entry.subject())
                .containsExactly("This week in tech", "Product updates");
        assertThat(newsletters.entries().getFirst().summary()).isEqualTo("Big launch this week.");
        assertThat(newsletters.entries().getFirst().senderEmail()).isEqualTo("news@example.test");
    }

    @Test
    void buildSections_degrades_to_subject_and_sender_when_summarization_throws() {
        when(auditLogQueryService.findDigestSourceItems(eq(TENANT_ID), any(), any(), anyInt()))
                .thenReturn(List.of(item("msg-1", "Newsletters", "news@example.test", "Weekly")));
        bodyFor("msg-1", "Some body.");
        when(llmGateway.summarizeDigestItems(any()))
                .thenThrow(new RuntimeException("model unavailable"));

        List<DigestContentSection> sections =
                builder.buildSections(TENANT_ID, WINDOW_START, SEND_MOMENT);

        assertThat(sections).hasSize(1);
        assertThat(sections.getFirst().entries().getFirst().subject()).isEqualTo("Weekly");
        assertThat(sections.getFirst().entries().getFirst().hasSummary()).isFalse();
    }

    @Test
    void buildSections_keeps_unfetchable_messages_in_sections_without_summary() {
        when(auditLogQueryService.findDigestSourceItems(eq(TENANT_ID), any(), any(), anyInt()))
                .thenReturn(
                        List.of(
                                item("msg-ok", "Newsletters", "a@example.test", "Has body"),
                                item("msg-gone", "Newsletters", "b@example.test", "Deleted")));
        bodyFor("msg-ok", "Readable body.");
        when(recentInboxReadService.fetchPlainTextForDigest(TENANT_ID, "msg-gone"))
                .thenReturn(Optional.empty());
        when(llmGateway.summarizeDigestItems(any()))
                .thenReturn(List.of(new DigestSummaryLine("msg-ok", "A summary.")));

        List<DigestContentSection> sections =
                builder.buildSections(TENANT_ID, WINDOW_START, SEND_MOMENT);

        assertThat(sections).hasSize(1);
        assertThat(sections.getFirst().entries()).hasSize(2);
        assertThat(sections.getFirst().entries().getFirst().summary()).isEqualTo("A summary.");
        assertThat(sections.getFirst().entries().getLast().hasSummary()).isFalse();
    }

    @Test
    void buildSections_returns_empty_when_no_digest_items() {
        when(auditLogQueryService.findDigestSourceItems(eq(TENANT_ID), any(), any(), anyInt()))
                .thenReturn(List.of());

        assertThat(builder.buildSections(TENANT_ID, WINDOW_START, SEND_MOMENT)).isEmpty();
    }

    @Test
    void buildSections_returns_empty_when_audit_read_fails() {
        when(auditLogQueryService.findDigestSourceItems(eq(TENANT_ID), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        assertThat(builder.buildSections(TENANT_ID, WINDOW_START, SEND_MOMENT)).isEmpty();
    }

    private void bodyFor(String gmailMessageId, String body) {
        when(recentInboxReadService.fetchPlainTextForDigest(TENANT_ID, gmailMessageId))
                .thenReturn(Optional.of(body));
    }

    private static DigestSourceItem item(
            String gmailMessageId, String ruleName, String senderEmail, String subject) {
        return new DigestSourceItem(
                gmailMessageId,
                "thread-" + gmailMessageId,
                subject,
                senderEmail,
                ruleName,
                SEND_MOMENT);
    }
}
