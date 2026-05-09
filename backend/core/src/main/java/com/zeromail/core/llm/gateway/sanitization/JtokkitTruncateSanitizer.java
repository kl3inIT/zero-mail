package com.zeromail.core.llm.gateway.sanitization;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingResult;
import com.knuddels.jtokkit.api.EncodingType;
import com.zeromail.core.llm.model.SanitizationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class JtokkitTruncateSanitizer implements Sanitizer {

    // CONTEXT D-B4: 4096-token budget minus 200-token Anthropic safety headroom.
    public static final int HARD_CAP_TOKENS = 3896;

    private final Encoding cl100kBase;

    public JtokkitTruncateSanitizer(EncodingRegistry encodingRegistry) {
        this.cl100kBase = encodingRegistry.getEncoding(EncodingType.CL100K_BASE);
    }

    @Override
    public SanitizationContext apply(SanitizationContext context) {
        EncodingResult encodingResult = cl100kBase.encode(context.content(), HARD_CAP_TOKENS);
        String truncated = encodingResult.isTruncated()
                ? cl100kBase.decode(encodingResult.getTokens())
                : context.content();
        return context.withContent(truncated)
                .withTokenCount(encodingResult.getTokens().size(), encodingResult.isTruncated());
    }
}
