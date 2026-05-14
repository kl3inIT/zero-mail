package com.zeromail.core.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.notification.domain.DigestPayload;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DigestPayloadShapeArchTest {

    @Test
    void digest_payload_has_no_email_specific_fields() {
        assertThat(
                        Arrays.stream(DigestPayload.class.getRecordComponents())
                                .map(RecordComponent::getName)
                                .toList())
                .doesNotContain(
                        "htmlBody",
                        "mimeType",
                        "subject",
                        "to",
                        "toAddress",
                        "htmlContent",
                        "bodyHtml");
    }
}
