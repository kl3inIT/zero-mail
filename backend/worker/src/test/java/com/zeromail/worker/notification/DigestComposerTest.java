package com.zeromail.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.analytics.domain.TimeWindow;
import com.zeromail.core.analytics.projection.AnalyticsSummaryProjection;
import com.zeromail.core.analytics.projection.RuleHitProjection;
import com.zeromail.core.analytics.projection.TopSenderProjection;
import com.zeromail.core.analytics.usecases.AnalyticsSummaryQueryService;
import com.zeromail.core.notification.domain.DigestPayload;
import com.zeromail.core.notification.usecases.DigestComposer;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DigestComposerTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000005c03");
    private static final ZoneId TENANT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate DIGEST_DAY_LOCAL = LocalDate.parse("2026-05-13");
    private static final Instant SEND_MOMENT = Instant.parse("2026-05-13T13:00:00Z");

    @Test
    void compose_maps_non_zero_analytics_summary_to_digest_payload() {
        AnalyticsSummaryQueryService analyticsSummaryQueryService =
                mock(AnalyticsSummaryQueryService.class);
        when(analyticsSummaryQueryService.summarize(
                        TENANT_ID,
                        TimeWindow.between(SEND_MOMENT.minusSeconds(604_800), SEND_MOMENT)))
                .thenReturn(
                        new AnalyticsSummaryProjection(
                                12,
                                7,
                                410,
                                List.of(new TopSenderProjection("founder@example.test", 4)),
                                List.of(new RuleHitProjection("Archive Rule", 5, 3, 1))));
        DigestComposer digestComposer = new DigestComposer(analyticsSummaryQueryService);

        DigestPayload payload =
                digestComposer.compose(
                        TENANT_ID,
                        TENANT_ZONE,
                        Locale.forLanguageTag("vi"),
                        DIGEST_DAY_LOCAL,
                        SEND_MOMENT,
                        URI.create("https://zero-mail.test/app"));

        assertThat(payload.zeroActivity()).isFalse();
        assertThat(payload.totals().volumeObserved()).isEqualTo(12);
        assertThat(payload.totals().volumeApplied()).isEqualTo(7);
        assertThat(payload.totals().timeSavedSeconds()).isEqualTo(410);
        assertThat(payload.topSenders().getFirst().senderEmail()).isEqualTo("founder@example.test");
        assertThat(payload.topRules().getFirst().ruleName()).isEqualTo("Archive Rule");
        assertThat(payload.ctaUrl().toString())
                .isEqualTo("https://zero-mail.test/app/analytics?source=digest&window=7d");
        assertThat(payload.optOutUrl().toString())
                .isEqualTo(
                        "https://zero-mail.test/app/settings?section=notifications&source=digest");
    }

    @Test
    void compose_marks_zero_activity_and_uses_empty_lists() {
        AnalyticsSummaryQueryService analyticsSummaryQueryService =
                mock(AnalyticsSummaryQueryService.class);
        when(analyticsSummaryQueryService.summarize(
                        TENANT_ID,
                        TimeWindow.between(SEND_MOMENT.minusSeconds(604_800), SEND_MOMENT)))
                .thenReturn(new AnalyticsSummaryProjection(0, 0, 0, List.of(), List.of()));
        DigestComposer digestComposer = new DigestComposer(analyticsSummaryQueryService);

        DigestPayload payload =
                digestComposer.compose(
                        TENANT_ID,
                        TENANT_ZONE,
                        Locale.ENGLISH,
                        DIGEST_DAY_LOCAL,
                        SEND_MOMENT,
                        URI.create("https://zero-mail.test/"));

        assertThat(payload.zeroActivity()).isTrue();
        assertThat(payload.topSenders()).isEmpty();
        assertThat(payload.topRules()).isEmpty();
        assertThat(payload.ctaUrl().toString())
                .isEqualTo("https://zero-mail.test/analytics?source=digest&window=7d");
    }
}
