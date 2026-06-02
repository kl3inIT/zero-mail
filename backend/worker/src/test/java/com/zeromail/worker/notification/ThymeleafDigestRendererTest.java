package com.zeromail.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.notification.domain.DigestPayload;
import com.zeromail.core.notification.domain.DigestRuleHit;
import com.zeromail.core.notification.domain.DigestTopSender;
import com.zeromail.core.notification.domain.DigestTotals;
import com.zeromail.worker.notification.config.DigestRendererConfig;
import com.zeromail.worker.notification.email.ThymeleafDigestRenderer;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;

class ThymeleafDigestRendererTest {

    private final ThymeleafDigestRenderer renderer = renderer();

    @Test
    void renders_vi_html_from_digest_message_source() {
        String html = renderer.renderHtml(activePayload(Locale.forLanguageTag("vi")));

        assertThat(html).contains("Tuần qua trên Zero Mail");
        assertThat(html).contains("Đây là những gì Zero Mail đã làm cho bạn trong tuần qua.");
        assertThat(html).contains("Xem chi tiết");
    }

    @Test
    void renders_en_html_from_same_template() {
        String html = renderer.renderHtml(activePayload(Locale.ENGLISH));

        assertThat(html).contains("This week on Zero Mail");
        assertThat(html).contains("Zero Mail did for you this week.");
        assertThat(html).contains("View details");
    }

    @Test
    void renders_zero_activity_copy() {
        String html = renderer.renderHtml(zeroActivityPayload(Locale.ENGLISH));

        assertThat(html).contains("No senders or rules to report. Your inbox stayed quiet");
        assertThat(renderer.subject(zeroActivityPayload(Locale.ENGLISH)))
                .isEqualTo("This week on Zero Mail · no activity");
    }

    @Test
    void renders_plaintext_top_sender_rows() {
        String text = renderer.renderText(activePayload(Locale.ENGLISH));

        assertThat(text).contains("1. founder@example.test  (47)");
        assertThat(text)
                .contains("View details: https://zero-mail.test/analytics?source=digest&window=7d");
    }

    private static ThymeleafDigestRenderer renderer() {
        DigestRendererConfig digestRendererConfig = new DigestRendererConfig();
        MessageSource messageSource = digestRendererConfig.digestMessageSource();
        SpringTemplateEngine templateEngine =
                digestRendererConfig.digestTemplateEngine(messageSource);
        return new ThymeleafDigestRenderer(templateEngine, messageSource);
    }

    public static DigestPayload activePayload(Locale locale) {
        return new DigestPayload(
                locale,
                UUID.fromString("00000000-0000-0000-0000-000000005c03"),
                LocalDate.parse("2026-05-13"),
                new DigestTotals(60, 47, 18 * 60),
                List.of(
                        new DigestTopSender("founder@example.test", 47),
                        new DigestTopSender("alerts@example.test", 8)),
                List.of(new DigestRuleHit("Archive invoices", 12, 1)),
                URI.create("https://zero-mail.test/analytics?source=digest&window=7d"),
                URI.create("https://zero-mail.test/settings?section=notifications&source=digest"),
                false);
    }

    public static DigestPayload zeroActivityPayload(Locale locale) {
        return new DigestPayload(
                locale,
                UUID.fromString("00000000-0000-0000-0000-000000005c04"),
                LocalDate.parse("2026-05-13"),
                new DigestTotals(0, 0, 0),
                List.of(),
                List.of(),
                URI.create("https://zero-mail.test/analytics?source=digest&window=7d"),
                URI.create("https://zero-mail.test/settings?section=notifications&source=digest"),
                true);
    }
}
