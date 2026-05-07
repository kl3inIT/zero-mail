package com.zeromail.core.llm.gateway.sanitization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Plan 02 lands SanitizationPipeline")
@SpringBootTest
class SanitizationPipelineWave0Test {

    @Autowired
    SanitizationPipeline sanitizationPipeline;

    @Test
    void strips_script_tags_from_html_before_model_call() {
        assertThat(sanitizationPipeline.sanitize("<script>alert(1)</script>hi").content())
                .isEqualTo("hi");
    }
}
