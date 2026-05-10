package com.zeromail.core.llm.gateway.sanitization;

import com.zeromail.core.llm.application.SanitizationContext;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class UnicodeTagStripSanitizer implements Sanitizer {

    static final Pattern HIDDEN_CONTROL_PATTERN = Pattern.compile(
            "[\\x{E0000}-\\x{E007F}\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]");

    @Override
    public SanitizationContext apply(SanitizationContext context) {
        String tagStripped = HIDDEN_CONTROL_PATTERN.matcher(context.content()).replaceAll("");
        return context.withContent(tagStripped);
    }
}
