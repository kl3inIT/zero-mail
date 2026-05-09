package com.zeromail.core.llm.gateway.sanitization;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {
        SanitizationPipeline.class,
        JsoupHtmlStripSanitizer.class,
        NfcNormalizeSanitizer.class,
        UnicodeTagStripSanitizer.class,
        JtokkitConfig.class,
        JtokkitTruncateSanitizer.class
})
class SanitizationPipelineWave0Test {

    private static final String TENANT_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    SanitizationPipeline sanitizationPipeline;

    @Test
    void strips_script_tags_from_html_before_model_call() throws Exception {
        assertThat(ScopedValue.where(TenantContext.TENANT, TENANT_ID)
                        .call(() -> sanitizationPipeline.sanitize("<script>alert(1)</script>hi").content()))
                .isEqualTo("hi");
    }
}
