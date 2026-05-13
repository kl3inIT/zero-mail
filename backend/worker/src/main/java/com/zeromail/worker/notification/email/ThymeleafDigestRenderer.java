package com.zeromail.worker.notification.email;

import com.zeromail.core.notification.domain.DigestPayload;
import com.zeromail.core.notification.domain.DigestTopSender;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Component
public class ThymeleafDigestRenderer {

    static final String HTML_TEMPLATE = "digest.html.thymeleaf";
    static final String TEXT_TEMPLATE = "digest.txt.thymeleaf";

    private final SpringTemplateEngine templateEngine;
    private final MessageSource digestMessageSource;

    public ThymeleafDigestRenderer(
            SpringTemplateEngine digestTemplateEngine,
            @Qualifier("digestMessageSource") MessageSource digestMessageSource) {
        this.templateEngine =
                Objects.requireNonNull(
                        digestTemplateEngine, "digestTemplateEngine must not be null");
        this.digestMessageSource =
                Objects.requireNonNull(digestMessageSource, "digestMessageSource must not be null");
    }

    public String renderHtml(DigestPayload payload) {
        return templateEngine.process(HTML_TEMPLATE, contextFor(payload));
    }

    public String renderText(DigestPayload payload) {
        return templateEngine.process(TEXT_TEMPLATE, contextFor(payload));
    }

    public String subject(DigestPayload payload) {
        String key = payload.zeroActivity() ? "digest.subject.zero" : "digest.subject";
        return message(key, payload.locale(), payload.totals().volumeApplied(), timeSaved(payload));
    }

    String preheader(DigestPayload payload) {
        if (payload.zeroActivity()) {
            return message("digest.preheader.zero", payload.locale());
        }
        String senderSummary =
                payload.topSenders().stream()
                        .map(DigestTopSender::senderEmail)
                        .limit(3)
                        .reduce((left, right) -> left + ", " + right)
                        .orElseGet(() -> message("digest.preheader.zero", payload.locale()));
        return truncate(senderSummary, 70);
    }

    private Context contextFor(DigestPayload payload) {
        Context context = new Context(payload.locale());
        context.setVariables(
                Map.of(
                        "payload", payload,
                        "subject", subject(payload),
                        "preheader", preheader(payload),
                        "timeSaved", timeSaved(payload),
                        "appDomain", payload.ctaUrl().getHost(),
                        "digestDate", payload.digestDayLocal().minusDays(1).toString()));
        return context;
    }

    private String timeSaved(DigestPayload payload) {
        Duration duration = Duration.ofSeconds(payload.totals().timeSavedSeconds());
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        if (hours > 0) {
            return "%dh %02dm".formatted(hours, minutes);
        }
        return "%dm".formatted(minutes);
    }

    private String message(String key, Locale locale, Object... arguments) {
        return digestMessageSource.getMessage(key, arguments, locale);
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }
}
