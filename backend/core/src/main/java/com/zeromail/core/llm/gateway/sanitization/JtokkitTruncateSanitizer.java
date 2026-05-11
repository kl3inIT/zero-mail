package com.zeromail.core.llm.gateway.sanitization;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingResult;
import com.knuddels.jtokkit.api.EncodingType;
import com.zeromail.core.llm.usecases.SanitizationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class JtokkitTruncateSanitizer implements Sanitizer {

    // CONTEXT D-B4: 4096-token budget minus 200-token Anthropic safety headroom.
    public static final int HARD_CAP_TOKENS = 3896;

    private final EncodingRegistry encodingRegistry;
    private volatile Encoding cl100kBase;

    public JtokkitTruncateSanitizer(EncodingRegistry encodingRegistry) {
        this.encodingRegistry = encodingRegistry;
    }

    @Override
    public SanitizationContext apply(SanitizationContext context) {
        Encoding cl100kBaseEncoding = cl100kBase();
        EncodingResult encodingResult =
                cl100kBaseEncoding.encode(context.content(), HARD_CAP_TOKENS);
        String truncated =
                encodingResult.isTruncated()
                        ? cl100kBaseEncoding.decode(encodingResult.getTokens())
                        : context.content();
        return context.withContent(truncated)
                .withTokenCount(encodingResult.getTokens().size(), encodingResult.isTruncated());
    }

    private Encoding cl100kBase() {
        Encoding currentEncoding = cl100kBase;
        if (currentEncoding == null) {
            synchronized (this) {
                currentEncoding = cl100kBase;
                if (currentEncoding == null) {
                    currentEncoding = encodingRegistry.getEncoding(EncodingType.CL100K_BASE);
                    cl100kBase = currentEncoding;
                }
            }
        }
        return currentEncoding;
    }
}
