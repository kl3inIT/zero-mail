package com.zeromail.core.cleanup.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.cleanup.domain.UnsubscribeMethod;
import com.zeromail.core.cleanup.persistence.SenderSuppressionRepository;
import com.zeromail.core.cleanup.usecases.CampaignPreviewService.CampaignPreviewResult;
import com.zeromail.core.cleanup.usecases.CampaignPreviewService.PerSenderPreview;
import com.zeromail.core.cleanup.usecases.CleanupRecentInboxWorkingSetService.SenderWorkingSet;
import com.zeromail.core.cleanup.usecases.CleanupRecentInboxWorkingSetService.WorkingSet;
import com.zeromail.core.support.PostgresContainerTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class CampaignPreviewServiceTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired SenderSuppressionRepository senderSuppressionRepository;

    @Test
    void previewUsesRecentInboxWorkingSetWhenAvailable() {
        UUID tenantId = seedTenant();
        CleanupRecentInboxWorkingSetService cleanupRecentInboxWorkingSetService =
                mock(CleanupRecentInboxWorkingSetService.class);
        when(cleanupRecentInboxWorkingSetService.findRecentInboxWorkingSet(
                        eq(tenantId), eq(Duration.ofDays(30))))
                .thenReturn(
                        Optional.of(
                                new WorkingSet(
                                        List.of(
                                                new SenderWorkingSet(
                                                        "newsletter@example.test",
                                                        "example.test",
                                                        2,
                                                        Instant.parse("2026-05-23T09:00:00Z"),
                                                        UnsubscribeMethod.ONE_CLICK,
                                                        List.of("gmail-1", "gmail-2"),
                                                        "https://example.test/unsubscribe",
                                                        null)))));
        CampaignPreviewService campaignPreviewService =
                new CampaignPreviewService(
                        jdbcTemplate,
                        senderSuppressionRepository,
                        cleanupRecentInboxWorkingSetService,
                        Clock.fixed(Instant.parse("2026-05-24T00:00:00Z"), ZoneOffset.UTC));

        CampaignPreviewResult previewResult =
                campaignPreviewService.preview(tenantId, List.of("newsletter@example.test"));

        assertThat(previewResult.archivableHistoryCount()).isEqualTo(2);
        assertThat(previewResult.perSender()).hasSize(1);
        PerSenderPreview senderPreview = previewResult.perSender().getFirst();
        assertThat(senderPreview.senderEmail()).isEqualTo("newsletter@example.test");
        assertThat(senderPreview.unsubscribeMethod()).isEqualTo(UnsubscribeMethod.ONE_CLICK);
        assertThat(senderPreview.willArchive()).isTrue();
        assertThat(senderPreview.messageCount()).isEqualTo(2);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "campaign-preview-" + tenantId);
        return tenantId;
    }
}
