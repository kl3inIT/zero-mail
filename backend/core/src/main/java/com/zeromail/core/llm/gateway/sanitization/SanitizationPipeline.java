package com.zeromail.core.llm.gateway.sanitization;

import com.zeromail.core.llm.application.SanitizationContext;
import com.zeromail.core.llm.exception.SanitizationException;
import com.zeromail.core.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

@Service
public class SanitizationPipeline {

    private static final Logger log = LoggerFactory.getLogger(SanitizationPipeline.class);

    private final List<Sanitizer> sanitizers;

    public SanitizationPipeline(List<Sanitizer> sanitizers) {
        Objects.requireNonNull(sanitizers, "sanitizers");
        List<Sanitizer> orderedSanitizers = new ArrayList<>(sanitizers);
        AnnotationAwareOrderComparator.sort(orderedSanitizers);
        this.sanitizers = List.copyOf(orderedSanitizers);
    }

    public SanitizationContext sanitize(String rawHtml) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        SanitizationContext current = SanitizationContext.initial(rawHtml);
        for (Sanitizer step : sanitizers) {
            try {
                current = step.apply(current);
            } catch (RuntimeException stepFailure) {
                throw new SanitizationException(step.getClass().getSimpleName(), stepFailure);
            }
        }
        log.info(
                "event=sanitization_completed tenantId={} truncated={} tokenCount={}",
                tenantId,
                current.truncated(),
                current.tokenCount());
        return current;
    }

    /**
     * Sanitizes a structured JSON envelope (e.g. the rule-compile prompt payload) where user text
     * already lives inside properly quoted JSON string values. The Jsoup HTML-strip stage is
     * skipped so that literal '&lt;' and '&gt;' substrings inside user content (quoted angle
     * addresses, regex literals, "&lt;reply requested&gt;") survive verbatim. NFC normalization,
     * Unicode-tag stripping, and token truncation still apply.
     */
    public SanitizationContext sanitizeStructuredJson(String compilerPayload) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        SanitizationContext current = SanitizationContext.initial(compilerPayload);
        for (Sanitizer step : sanitizers) {
            if (step instanceof JsoupHtmlStripSanitizer) {
                continue;
            }
            try {
                current = step.apply(current);
            } catch (RuntimeException stepFailure) {
                throw new SanitizationException(step.getClass().getSimpleName(), stepFailure);
            }
        }
        log.info(
                "event=sanitization_structured_completed tenantId={} truncated={} tokenCount={}",
                tenantId,
                current.truncated(),
                current.tokenCount());
        return current;
    }
}
