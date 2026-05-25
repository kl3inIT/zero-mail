package com.zeromail.core.llm.gateway.sanitization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JsoupSafeHtmlSanitizerTest {

    private final JsoupSafeHtmlSanitizer sanitizer = new JsoupSafeHtmlSanitizer();

    @Test
    void sanitizeEmailHtmlKeepsEmailImagesAndRemovesExecutableContent() {
        String sanitized =
                sanitizer.sanitizeEmailHtml(
                        """
                                <div style="color:#202124">
                                  <script>alert('no')</script>
                                  <a href="javascript:alert('no')">bad link</a>
                                  <a href="https://example.com">good link</a>
                                  <img src="https://example.com/logo.png" width="120" alt="Logo">
                                  <img src="data:image/png;base64,AAAA" alt="Inline">
                                </div>
                                """);

        assertThat(sanitized).contains("style=\"color:#202124\"");
        assertThat(sanitized).contains("href=\"https://example.com\"");
        assertThat(sanitized).contains("src=\"https://example.com/logo.png\"");
        assertThat(sanitized).contains("src=\"data:image/png;base64,AAAA\"");
        assertThat(sanitized).doesNotContain("<script");
        assertThat(sanitized).doesNotContain("javascript:");
    }

    @Test
    void replaceCidImageSourcesRewritesEncodedCidImageReferences() {
        String renderedHtml =
                sanitizer.replaceCidImageSources(
                        "<div><img src=\"cid:logo%20image@example.test\"></div>",
                        Map.of("logo image@example.test", "data:image/png;base64,aW1hZ2U="));

        assertThat(renderedHtml).contains("src=\"data:image/png;base64,aW1hZ2U=\"");
        assertThat(renderedHtml).doesNotContain("cid:logo%20image@example.test");
    }
}
