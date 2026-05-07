package com.zeromail.core.llm.gateway.sanitization;

import com.zeromail.core.llm.model.SanitizationContext;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class JsoupHtmlStripSanitizer implements Sanitizer {

    @Override
    public SanitizationContext apply(SanitizationContext context) {
        String stripped = Jsoup.clean(context.content(), Safelist.none());
        return context.withContent(stripped);
    }
}
