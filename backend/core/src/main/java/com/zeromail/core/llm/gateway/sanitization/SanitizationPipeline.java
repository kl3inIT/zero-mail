package com.zeromail.core.llm.gateway.sanitization;

import com.zeromail.core.llm.model.SanitizationContext;
import com.zeromail.core.llm.model.SanitizationException;
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
        log.info("event=sanitization_completed tenantId={} truncated={} tokenCount={}",
                tenantId, current.truncated(), current.tokenCount());
        return current;
    }
}
