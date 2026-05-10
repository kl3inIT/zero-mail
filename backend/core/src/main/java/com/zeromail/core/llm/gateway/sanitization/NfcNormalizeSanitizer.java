package com.zeromail.core.llm.gateway.sanitization;

import com.zeromail.core.llm.application.SanitizationContext;
import java.text.Normalizer;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class NfcNormalizeSanitizer implements Sanitizer {

    @Override
    public SanitizationContext apply(SanitizationContext context) {
        String normalized = Normalizer.normalize(context.content(), Normalizer.Form.NFC);
        return context.withContent(normalized);
    }
}
